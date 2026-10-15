package com.hydrafit.app.core.database

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.hydrafit.app.core.domain.equipment.Exercise
import com.hydrafit.app.core.domain.fatigue.MuscleGroup
import com.hydrafit.app.core.domain.transfer.PreviewWorkoutImport
import com.hydrafit.app.core.domain.transfer.WorkoutCsvParser
import com.hydrafit.app.core.domain.transfer.WorkoutCsvSource
import com.hydrafit.app.core.domain.transfer.WorkoutImportChoices
import com.hydrafit.app.core.domain.transfer.WorkoutImportCommitFailure
import com.hydrafit.app.core.domain.transfer.WorkoutImportException
import com.hydrafit.app.core.domain.transfer.WorkoutImportExerciseChoice
import com.hydrafit.app.core.domain.transfer.WorkoutImportPreview
import com.hydrafit.app.core.domain.transfer.WorkoutImportSessionChoice
import com.hydrafit.app.core.domain.workout.LoadKind
import com.hydrafit.app.core.domain.workout.WorkoutTimingProvenance
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

/**
 * The import ledger and the transactional commit it protects: closed imported sessions, frozen
 * attribution, unchanged-source skipping and no partial history when a commit is refused.
 */
class SqlDelightWorkoutImportRepositoryTest {
    private lateinit var driver: JdbcSqliteDriver
    private lateinit var database: HydraFitDatabase
    private lateinit var repository: SqlDelightWorkoutImportRepository

    private val press = Exercise(
        id = "bench-press",
        name = "Bench Press",
        requiredEquipment = emptySet(),
        primaryMuscles = setOf(MuscleGroup.CHEST_UPPER),
        involvements = mapOf(MuscleGroup.CHEST_UPPER to 0.7, MuscleGroup.TRICEPS to 0.5)
    )
    private val squat = Exercise(
        id = "back-squat",
        name = "Back Squat",
        requiredEquipment = emptySet(),
        primaryMuscles = setOf(MuscleGroup.QUADS)
    )

    private val csv = """
        Date,Exercise,Weight (lbs),Reps
        2024-01-02,Bench Press,100,8
        2024-01-02,Bench Press,100,8
        2024-01-02,Back Squat,60,5
    """.trimIndent()

    @BeforeTest
    fun setUp() {
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        HydraFitDatabase.Schema.create(driver)
        database = HydraFitDatabase(driver)
        repository = SqlDelightWorkoutImportRepository(database)
        database.exerciseQueries.insert("bench-press", "Bench Press", "", "BENCH_PRESS", "EXTERNAL")
        database.exerciseQueries.insert("back-squat", "Back Squat", "", "SQUAT", "EXTERNAL")
    }

    @AfterTest
    fun tearDown() {
        driver.close()
    }

    @Test
    fun commitWritesAClosedSessionWithFrozenAttributionAndOneLedgerRow() = runBlocking {
        val preview = preview(sessions = listOf(pressSession()))

        val result = repository.commit(preview)

        assertEquals(1, result.importedSessions)
        assertEquals(2, result.importedSets)
        assertEquals(0, result.skippedSessions)
        val session = database.workoutSessionQueries.selectAllSessions().executeAsOne()
        assertTrue(session.endedAtMillis != null)
        assertTrue(session.occurrenceId == null)
        val sets = database.workoutLogQueries.selectAllSets().executeAsList()
        assertEquals(2, sets.size)
        assertEquals(listOf(session.id, session.id), sets.map { it.sessionId })
        assertEquals(
            listOf("CHEST_UPPER:0.7,TRICEPS:0.5", "CHEST_UPPER:0.7,TRICEPS:0.5"),
            sets.map {
                it.involvements
            }
        )
        assertEquals(
            listOf(WorkoutTimingProvenance.CATCH_UP.name, WorkoutTimingProvenance.CATCH_UP.name),
            sets.map { it.timingProvenance }
        )
        sets.forEach { assertEquals(45.35923700100354, it.weightKg!!, 1e-9) }
        assertTrue(sets.all { it.occurrenceId == null && it.weekNumber == null && it.rir == null })
        val ledger = database.workoutImportQueries.selectAllProvenance().executeAsOne()
        assertEquals("FITNOTES", ledger.source)
        assertEquals(session.id, ledger.importedSessionId)
        assertEquals(preview.sessions.single().content, ledger.content)
    }

    @Test
    fun repeatCommitSkipsTheUnchangedSourceWithoutDuplicatingHistory() = runBlocking {
        val preview = preview(sessions = listOf(pressSession()))
        repository.commit(preview)

        val again = repository.commit(preview)

        assertEquals(0, again.importedSessions)
        assertEquals(1, again.skippedSessions)
        assertEquals(2, database.workoutLogQueries.selectAllSets().executeAsList().size)
        assertEquals(
            1L,
            database.workoutSessionQueries.selectAllSessions().executeAsList().size.toLong()
        )
    }

    @Test
    fun deletedImportedHistoryStaysDeletedOnARepeatImport() = runBlocking {
        val preview = preview(sessions = listOf(pressSession()))
        repository.commit(preview)
        database.workoutLogQueries.deleteAllSets()
        database.workoutSessionQueries.deleteAllSessions()

        val again = repository.commit(preview)

        assertEquals(1, again.skippedSessions)
        assertEquals(0, database.workoutLogQueries.selectAllSets().executeAsList().size)
        assertEquals(
            1L,
            database.workoutImportQueries.selectAllProvenance().executeAsList().size.toLong()
        )
    }

    @Test
    fun changedSourceForAnImportedWorkoutRefusesTheWholeCommit() = runBlocking {
        val preview = preview(sessions = listOf(pressSession()))
        // Simulate another writer importing the same source workout with different content.
        database.workoutImportQueries.insertProvenance(
            source = WorkoutCsvSource.FITNOTES.name,
            candidateKey = preview.sessions.single().candidateKey,
            content = "different",
            importedSessionId = "other"
        )

        val failure = assertFailsWith<WorkoutImportException> { repository.commit(preview) }

        assertEquals(WorkoutImportCommitFailure.SOURCE_CHANGED, failure.failure)
        assertEquals(0, database.workoutLogQueries.selectAllSets().executeAsList().size)
        assertEquals(0, database.workoutSessionQueries.selectAllSessions().executeAsList().size)
    }

    @Test
    fun aRefusedSecondSessionRollsBackTheFirstOne() = runBlocking {
        val preview = preview(sessions = listOf(pressSession(), squatSession()))
        database.workoutImportQueries.insertProvenance(
            source = WorkoutCsvSource.FITNOTES.name,
            candidateKey = preview.sessions[1].candidateKey,
            content = "different",
            importedSessionId = "other"
        )

        assertFailsWith<WorkoutImportException> { repository.commit(preview) }

        assertEquals(0, database.workoutLogQueries.selectAllSets().executeAsList().size)
        assertEquals(0, database.workoutSessionQueries.selectAllSessions().executeAsList().size)
        assertEquals(
            1L,
            database.workoutImportQueries.selectAllProvenance().executeAsList().size.toLong()
        )
    }

    @Test
    fun provenanceReadsAreScopedToTheRequestedSourceAndKeys() = runBlocking {
        val preview = preview(sessions = listOf(pressSession()))
        repository.commit(preview)
        val session = preview.sessions.single()

        assertEquals(
            1,
            repository.provenance(WorkoutCsvSource.FITNOTES, setOf(session.candidateKey)).size
        )
        assertEquals(
            0,
            repository.provenance(WorkoutCsvSource.HEVY, setOf(session.candidateKey)).size
        )
        assertEquals(0, repository.provenance(WorkoutCsvSource.FITNOTES, setOf("other")).size)
    }

    private fun pressSession() = WorkoutImportSessionChoice(
        sourceRows = listOf(2, 3),
        utcOffsetMinutes = 0,
        confirmedSession = true,
        confirmedInferredSetTimes = true,
        dateOnlySecondOfDay = 36_000
    )

    private fun squatSession() = WorkoutImportSessionChoice(
        sourceRows = listOf(4),
        utcOffsetMinutes = 0,
        confirmedSession = true,
        confirmedInferredSetTimes = true,
        dateOnlySecondOfDay = 72_000
    )

    private fun preview(sessions: List<WorkoutImportSessionChoice>): WorkoutImportPreview {
        val parsed = WorkoutCsvParser.parse(WorkoutCsvSource.FITNOTES, csv)
        val assigned = sessions.flatMap { it.sourceRows }.toSet()
        return PreviewWorkoutImport.build(
            parsed = parsed,
            choices = WorkoutImportChoices(
                exercises = mapOf(
                    "Bench Press" to WorkoutImportExerciseChoice(
                        exerciseId = press.id,
                        loadKind = LoadKind.EXTERNAL,
                        useSourceWeight = true,
                        confirmedLoadMeaning = true,
                        unknownWarmupIsWorking = true
                    ),
                    "Back Squat" to WorkoutImportExerciseChoice(
                        exerciseId = squat.id,
                        loadKind = LoadKind.EXTERNAL,
                        useSourceWeight = true,
                        confirmedLoadMeaning = true,
                        unknownWarmupIsWorking = true
                    )
                ),
                sessions = sessions,
                excludedRows = parsed.rows.map { it.sourceRowIndex }.toSet() - assigned,
                acknowledgedUnsupportedFields = true,
                acknowledgedHistoryEffects = true
            ),
            catalog = listOf(press, squat)
        )
    }
}
