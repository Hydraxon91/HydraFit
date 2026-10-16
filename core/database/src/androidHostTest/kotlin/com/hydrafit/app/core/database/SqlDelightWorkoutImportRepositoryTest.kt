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
import com.hydrafit.app.core.domain.transfer.WorkoutImportIssue
import com.hydrafit.app.core.domain.transfer.WorkoutImportPreview
import com.hydrafit.app.core.domain.transfer.WorkoutImportProblem
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
        val bucket = database.workoutImportBucketQueries.selectAllBuckets().executeAsOne()
        assertEquals("FITNOTES", bucket.source)
        assertEquals(preview.buckets.single().content, bucket.content)
        assertEquals(preview.buckets.single().partition, bucket.partition)
        assertEquals(
            listOf(session.id),
            database.workoutImportBucketQueries.selectLinks("FITNOTES", bucket.candidateKey)
                .executeAsList().map { it.importedSessionId }
        )
        assertEquals(
            0,
            database.workoutImportQueries.selectAllProvenance().executeAsList().size
        )
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
        val bucket = database.workoutImportBucketQueries.selectAllBuckets().executeAsOne()
        // The link is deliberately retained after the imported session is deleted, so the repeated
        // export cannot silently restore removed history.
        assertEquals(
            1,
            database.workoutImportBucketQueries.selectLinks("FITNOTES", bucket.candidateKey)
                .executeAsList().size
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
    fun bucketReadsAreScopedToTheRequestedSourceAndKeys() = runBlocking {
        val preview = preview(sessions = listOf(pressSession()))
        repository.commit(preview)
        val bucket = preview.buckets.single()

        assertEquals(
            1,
            repository.buckets(WorkoutCsvSource.FITNOTES, setOf(bucket.candidateKey)).size
        )
        assertEquals(
            0,
            repository.buckets(WorkoutCsvSource.HEVY, setOf(bucket.candidateKey)).size
        )
        assertEquals(0, repository.buckets(WorkoutCsvSource.FITNOTES, setOf("other")).size)
    }

    @Test
    fun aSplitBucketKeepsBothIdenticalSessionsAndSkipsThemTogetherOnRepeat() = runBlocking {
        val preview = preview(
            sessions = listOf(
                pressSession().copy(sourceRows = listOf(2), dateOnlySecondOfDay = 36_000),
                pressSession().copy(sourceRows = listOf(3), dateOnlySecondOfDay = 72_000)
            )
        )
        // One source bucket, two identical sessions: the old content-only identity could not tell
        // them apart; membership can.
        assertEquals(1, preview.buckets.size)

        val result = repository.commit(preview)

        assertEquals(2, result.importedSessions)
        assertEquals(2, result.importedSets)
        assertEquals(2, database.workoutSessionQueries.selectAllSessions().executeAsList().size)
        val bucket = database.workoutImportBucketQueries.selectAllBuckets().executeAsOne()
        assertEquals(preview.buckets.single().partition, bucket.partition)
        assertEquals(
            2,
            database.workoutImportBucketQueries.selectLinks("FITNOTES", bucket.candidateKey)
                .executeAsList().size
        )

        val again = repository.commit(preview)
        assertEquals(0, again.importedSessions)
        assertEquals(2, again.skippedSessions)
        assertEquals(2, database.workoutSessionQueries.selectAllSessions().executeAsList().size)
    }

    @Test
    fun aChangedPartitionRefusesTheWholeCommit() = runBlocking {
        repository.commit(preview(sessions = listOf(pressSession())))

        // Same source bucket, but the approved split no longer matches what was recorded.
        val split = preview(
            sessions = listOf(
                pressSession().copy(sourceRows = listOf(2), dateOnlySecondOfDay = 36_000),
                pressSession().copy(sourceRows = listOf(3), dateOnlySecondOfDay = 72_000)
            )
        )
        val failure = assertFailsWith<WorkoutImportException> { repository.commit(split) }

        assertEquals(WorkoutImportCommitFailure.SOURCE_CHANGED, failure.failure)
        assertEquals(1, database.workoutSessionQueries.selectAllSessions().executeAsList().size)
    }

    @Test
    fun aPreviewThatStillCarriesProblemsIsRefusedWithATypedFailure() = runBlocking {
        val preview = preview(sessions = listOf(pressSession()))

        val refused = assertFailsWith<WorkoutImportException> {
            repository.commit(
                preview.copy(
                    problems = listOf(
                        WorkoutImportProblem(WorkoutImportIssue.SESSION_OVERLAP)
                    )
                )
            )
        }

        // A caller that ignores canImport gets a typed refusal, not a bare require the caller can only
        // report as an unknown failure.
        assertEquals(WorkoutImportCommitFailure.NOT_IMPORTABLE, refused.failure)
        assertEquals(0, database.workoutLogQueries.selectAllSets().executeAsList().size)
        assertEquals(0, database.workoutSessionQueries.selectAllSessions().executeAsList().size)
    }

    @Test
    fun historyCreatedAfterThePreviewRefusesTheCommitAndWritesNothing() = runBlocking {
        val preview = preview(sessions = listOf(pressSession()))
        val planned = preview.sessions.single()
        // Another session is created after the preview, overlapping the planned one.
        database.workoutSessionQueries.insertSession(
            id = "other",
            startedAtMillis = planned.startedAtMillis,
            endedAtMillis = planned.endedAtMillis,
            localEpochDay = planned.localEpochDay
        )

        val failure = assertFailsWith<WorkoutImportException> { repository.commit(preview) }

        assertEquals(WorkoutImportCommitFailure.SESSION_OVERLAP, failure.failure)
        assertEquals(1, database.workoutSessionQueries.selectAllSessions().executeAsList().size)
        assertEquals(0, database.workoutLogQueries.selectAllSets().executeAsList().size)
        assertEquals(0, database.workoutImportBucketQueries.selectAllBuckets().executeAsList().size)
    }

    @Test
    fun provenanceReadsAreScopedToTheRequestedSourceAndKeys() = runBlocking {
        // Legacy rows, inserted directly: the bucket ledger does not write provenance any more.
        database.workoutImportQueries.insertProvenance(
            source = WorkoutCsvSource.FITNOTES.name,
            candidateKey = "key",
            content = "content",
            importedSessionId = "session-a"
        )

        assertEquals(1, repository.provenance(WorkoutCsvSource.FITNOTES, setOf("key")).size)
        assertEquals(0, repository.provenance(WorkoutCsvSource.HEVY, setOf("key")).size)
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
