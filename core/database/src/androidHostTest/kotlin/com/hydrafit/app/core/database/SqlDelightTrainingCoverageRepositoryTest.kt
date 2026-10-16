package com.hydrafit.app.core.database

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.hydrafit.app.core.domain.fatigue.CoverageSet
import com.hydrafit.app.core.domain.fatigue.MuscleGroup
import com.hydrafit.app.core.domain.fatigue.TrainingCoverageCalculator
import com.hydrafit.app.core.domain.workout.LoadKind
import com.hydrafit.app.core.domain.workout.WorkoutSet
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

class SqlDelightTrainingCoverageRepositoryTest {
    private lateinit var driver: JdbcSqliteDriver
    private lateinit var database: HydraFitDatabase
    private lateinit var repository: SqlDelightTrainingCoverageRepository
    private lateinit var writer: SqlDelightWorkoutLogRepository
    private val day = 86_400_000L

    @BeforeTest
    fun setUp() {
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        HydraFitDatabase.Schema.create(driver)
        database = HydraFitDatabase(driver)
        SeedExerciseCatalog(database).seed()
        SeedEquipmentCatalog(database).seed()
        database.plannerEngineQueries.insertIgnoreRow("DETERMINISTIC")
        database.workoutSessionQueries.insertSession("s", 10 * day, 10 * day, 10L)
        database.exerciseOverrideQueries.upsert(
            "dumbbell-curl",
            null,
            null,
            null,
            null,
            null,
            "BICEPS:1.0,FOREARMS:0.3"
        )
        repository = SqlDelightTrainingCoverageRepository(database)
        writer = SqlDelightWorkoutLogRepository(database)
    }

    @AfterTest
    fun tearDown() {
        driver.close()
    }

    @Test
    fun productionWriterSnapshotsDriveCoverageWithoutTodaysCatalogFallback() = runBlocking {
        writer.add(set())
        writer.add(set())
        writer.add(set().copy(isWarmup = true))
        val snapshot = repository.observeSets().first()
        database.exerciseOverrideQueries.upsert(
            "dumbbell-curl",
            null,
            null,
            null,
            null,
            null,
            "QUADS:1.0"
        )
        assertEquals(snapshot, repository.observeSets().first())
        val result = TrainingCoverageCalculator.calculate(snapshot, 10 * day, 0L)
        assertEquals(2, result.entries.first { it.muscle == MuscleGroup.BICEPS }.workingSets)
        assertEquals(
            0.6,
            result.entries.first { it.muscle == MuscleGroup.FOREARMS }.estimatedInvolvementCredits,
            1e-12
        )
        database.workoutLogQueries.insertSet(
            "dumbbell-curl", 8, 10.0, 10 * day, 0, null, null, null, null, null, "s", "EXTERNAL",
            null
        )
        val unknown = repository.observeSets().first()
        assertNull(unknown.last().involvements)
        val withUnknown = TrainingCoverageCalculator.calculate(unknown, 10 * day, 0L)
        assertEquals(1, withUnknown.unclassifiedSetsInWindow)
        assertEquals(0, withUnknown.entries.first { it.muscle == MuscleGroup.QUADS }.workingSets)
    }

    @Test
    fun timedSetsCountAsCoverageWorkWithoutBeingReadAsReps() = runBlocking {
        writer.add(set())
        writer.add(
            set().copy(
                reps = null,
                weightKg = null,
                loadKind = LoadKind.BODYWEIGHT,
                durationSeconds = 45
            )
        )

        val snapshot = repository.observeSets()
            .first()
        // A timed set is recorded work, so the coverage projection keeps both rows.
        assertEquals(2, snapshot.size)
        val result = TrainingCoverageCalculator.calculate(snapshot, 10 * day, 0L)
        assertEquals(2, result.entries.first { it.muscle == MuscleGroup.BICEPS }.workingSets)
        assertFalse(result.hasUnclassifiedHistory)
    }

    @Test
    fun liveCorrectionsDeletesAndWholeRestoreRecomputeWithoutCountDeduplication() = runBlocking {
        val emissions = Channel<List<CoverageSet>>(Channel.UNLIMITED)
        val job = launch(Dispatchers.Default) {
            repository.observeSets().collect { emissions.send(it) }
        }
        suspend fun next() = withTimeout(5_000) { emissions.receive() }
        try {
            assertTrue(next().isEmpty())
            writer.add(set())
            assertEquals(10 * day, next().single().performedAtMillis)
            writer.updateSetPerformedAt(1L, day)
            val corrected = TrainingCoverageCalculator.calculate(next(), 10 * day, 0L)
            val biceps = corrected.entries.first { it.muscle == MuscleGroup.BICEPS }
            assertEquals(0, biceps.workingSets)
            assertEquals(day, biceps.lastTrainedAtMillis)
            val backup = SqlDelightBackupRepository(database, SqlDelightBackupCatalog())
            val saved = backup.export("t", 10 * day)
            writer.delete(1L)
            assertTrue(next().isEmpty())
            backup.restore(saved)
            assertEquals(day, next().single().performedAtMillis)
            database.transaction {
                database.workoutLogQueries.deleteAllSets()
                database.workoutLogQueries.insertSet(
                    "dumbbell-curl",
                    8,
                    10.0,
                    10 * day,
                    0,
                    "",
                    null,
                    null,
                    null,
                    null,
                    "s",
                    "EXTERNAL",
                    null
                )
            }
            val replacement = next().single()
            assertEquals(emptyMap(), replacement.involvements)
            assertFalse(
                TrainingCoverageCalculator.calculate(listOf(replacement), 10 * day, 0L)
                    .hasUnclassifiedHistory
            )
        } finally {
            job.cancel()
            job.join()
            emissions.close()
        }
    }

    private fun set() = WorkoutSet(
        exerciseId = "dumbbell-curl",
        reps = 8,
        weightKg = 10.0,
        performedAtMillis = 10 * day,
        sessionId = "s"
    )
}
