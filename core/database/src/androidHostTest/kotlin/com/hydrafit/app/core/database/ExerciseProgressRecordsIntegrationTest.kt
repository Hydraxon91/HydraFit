package com.hydrafit.app.core.database

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.hydrafit.app.core.domain.time.TimeProvider
import com.hydrafit.app.core.domain.workout.CorrectWorkoutSetTimeUseCase
import com.hydrafit.app.core.domain.workout.DeleteWorkoutSetUseCase
import com.hydrafit.app.core.domain.workout.EndWorkoutSessionUseCase
import com.hydrafit.app.core.domain.workout.LogWorkoutSetUseCase
import com.hydrafit.app.core.domain.workout.ObserveOccurrenceRecordsUseCase
import com.hydrafit.app.core.domain.workout.ObserveOpenWorkoutSessionUseCase
import com.hydrafit.app.core.domain.workout.ProgressRecordType
import com.hydrafit.app.core.domain.workout.SessionConfig
import com.hydrafit.app.core.domain.workout.StartWorkoutSessionUseCase
import com.hydrafit.app.core.domain.workout.WorkoutSet
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/**
 * End-to-end coverage for occurrence record improvements through the real logging path: the
 * production logger and the correction/deletion use cases write through the SQLDelight repositories,
 * and the summary consumer reads the improvements back. Consumer-only fixtures would not prove that
 * the writers preserve the ids, times and occurrence links the record replay depends on.
 */
class ExerciseProgressRecordsIntegrationTest {

    private lateinit var driver: JdbcSqliteDriver
    private lateinit var database: HydraFitDatabase
    private lateinit var progressRepository: SqlDelightExerciseProgressRepository
    private lateinit var logSet: LogWorkoutSetUseCase
    private lateinit var deleteSet: DeleteWorkoutSetUseCase
    private lateinit var correctTime: CorrectWorkoutSetTimeUseCase
    private lateinit var records: ObserveOccurrenceRecordsUseCase

    @BeforeTest
    fun setUp() {
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        HydraFitDatabase.Schema.create(driver)
        database = HydraFitDatabase(driver)
        val logRepository = SqlDelightWorkoutLogRepository(database)
        val sessionRepository = SqlDelightWorkoutSessionRepository(database)
        progressRepository = SqlDelightExerciseProgressRepository(database)
        logSet = LogWorkoutSetUseCase(
            logRepository,
            StartWorkoutSessionUseCase(sessionRepository),
            EndWorkoutSessionUseCase(sessionRepository),
            ObserveOpenWorkoutSessionUseCase(sessionRepository),
            SessionConfig()
        )
        deleteSet = DeleteWorkoutSetUseCase(logRepository)
        correctTime = CorrectWorkoutSetTimeUseCase(
            SqlDelightSessionResegmenter(database),
            TimeProvider { NOW }
        )
        records = ObserveOccurrenceRecordsUseCase(progressRepository)
        database.exerciseQueries.insertCustom(
            "squat",
            "Squat",
            "BARBELL",
            "SQUAT",
            0L,
            "EXTERNAL",
            null
        )
        database.exerciseQueries.insertCustom(
            "bench",
            "Bench press",
            "BARBELL",
            "PUSH",
            0L,
            "EXTERNAL",
            null
        )
    }

    @AfterTest
    fun tearDown() = driver.close()

    @Test
    fun productionLoggerCorrectionAndDeletionRecomputeOccurrenceImprovements() = runBlocking {
        logSet(setAt(NOW - 10 * DAY, 60.0, "squat"), UTC)
        logSet(setAt(NOW - 2 * DAY, 80.0, "squat").copy(occurrenceId = OCCURRENCE), UTC)
        logSet(setAt(NOW - DAY, 85.0, "squat").copy(occurrenceId = OCCURRENCE), UTC)

        // Writer invariant: the production logger stamped every set with an explicit session.
        val stored = database.workoutLogQueries.selectAllSets().executeAsList()
        assertEquals(3, stored.size)
        assertTrue(stored.all { it.sessionId != null })

        val first = loadImprovements()
        assertEquals(listOf(80.0, 85.0), first.map { it.event.record.value })
        assertEquals(listOf(80.0, 85.0), first.map { it.set?.weightKg })

        // Moving the later set before the earlier one flips which set is the improvement.
        correctTime(first.last().event.setId, NOW - 3 * DAY)
        val corrected = loadImprovements()
        assertEquals(listOf(85.0), corrected.map { it.event.record.value })

        // Deleting the winner promotes the remaining set back into a record improvement.
        deleteSet(corrected.single().event.setId)
        val promoted = loadImprovements()
        assertEquals(listOf(80.0), promoted.map { it.event.record.value })
        assertEquals(listOf(80.0), promoted.map { it.set?.weightKg })
    }

    @Test
    fun restoreShapedReplacementRebuildsImprovementsAndRetractionHidesThem() = runBlocking {
        logSet(setAt(NOW - DAY, 50.0, "squat").copy(occurrenceId = OCCURRENCE), UTC)
        // A first observation establishes the baseline instead of an improvement.
        assertTrue(occurrenceImprovements().isEmpty())

        // A restore replaces rows with their exported identity, baseline included; the old derived
        // state must vanish even though the replacement has the same row count it derailed from.
        database.transaction {
            database.workoutLogQueries.deleteAllSets()
            database.workoutLogQueries.insertSetWithId(
                41L,
                "squat",
                10,
                40.0,
                NOW - 6 * DAY,
                0,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                "EXTERNAL",
                null
            )
            database.workoutLogQueries.insertSetWithId(
                42L,
                "squat",
                10,
                90.0,
                NOW - 4 * DAY,
                0,
                null,
                null,
                null,
                null,
                null,
                null,
                OCCURRENCE,
                null,
                "EXTERNAL",
                null
            )
        }
        val replaced = loadImprovements().single()
        assertEquals(42L, replaced.event.setId)
        assertEquals(90.0, replaced.event.record.value)
        assertEquals(90.0, replaced.set?.weightKg)

        // A restored row that no longer belongs to the occurrence retracts its improvement.
        database.workoutLogQueries.assignOccurrence(null, null, 42L)
        assertTrue(occurrenceImprovements().isEmpty())
    }

    @Test
    fun multiExerciseOccurrenceKeepsStableIdentityOrderAndEarlierBaselines() = runBlocking {
        logSet(setAt(NOW - 5 * DAY, 30.0, "bench"), UTC)
        logSet(setAt(NOW - 5 * DAY, 60.0, "squat"), UTC)
        logSet(setAt(NOW - DAY, 40.0, "bench").copy(occurrenceId = OCCURRENCE), UTC)
        logSet(setAt(NOW - DAY, 80.0, "squat").copy(occurrenceId = OCCURRENCE), UTC)

        val load = loadImprovements()
        assertEquals(listOf("bench", "squat"), load.map { it.exerciseId })
        assertEquals(listOf(40.0, 80.0), load.map { it.event.record.value })
        assertEquals(listOf(40.0, 80.0), load.map { it.set?.weightKg })
        // Both improving sets also carry their estimated one-rep-max companion event.
        assertEquals(4, occurrenceImprovements().size)
    }

    private suspend fun occurrenceImprovements() = records(OCCURRENCE, NOW, 0L).first()

    private suspend fun loadImprovements() = occurrenceImprovements()
        .filter { it.event.record.key.type == ProgressRecordType.LOAD_AT_REPS }

    private fun setAt(time: Long, weight: Double, exerciseId: String) = WorkoutSet(
        exerciseId = exerciseId,
        reps = 10,
        weightKg = weight,
        performedAtMillis = time
    )

    private companion object {
        const val DAY = 86_400_000L
        const val NOW = 20 * DAY
        const val OCCURRENCE = 7L
        const val UTC = 0L
    }
}
