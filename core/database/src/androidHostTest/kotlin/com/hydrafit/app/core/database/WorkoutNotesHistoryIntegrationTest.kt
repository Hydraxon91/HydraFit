package com.hydrafit.app.core.database

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.hydrafit.app.core.domain.backup.ApplyStagedBackupUseCase
import com.hydrafit.app.core.domain.backup.BackupException
import com.hydrafit.app.core.domain.backup.BackupFailure
import com.hydrafit.app.core.domain.backup.BackupJson
import com.hydrafit.app.core.domain.backup.BackupValidator
import com.hydrafit.app.core.domain.backup.PreviewBackupUseCase
import com.hydrafit.app.core.domain.backup.RestoreBackupUseCase
import com.hydrafit.app.core.domain.time.TimeProvider
import com.hydrafit.app.core.domain.workout.CorrectWorkoutSetUseCase
import com.hydrafit.app.core.domain.workout.EndWorkoutSessionUseCase
import com.hydrafit.app.core.domain.workout.LoadKind
import com.hydrafit.app.core.domain.workout.LogWorkoutSetUseCase
import com.hydrafit.app.core.domain.workout.ObserveOpenWorkoutSessionUseCase
import com.hydrafit.app.core.domain.workout.SessionConfig
import com.hydrafit.app.core.domain.workout.StartWorkoutSessionUseCase
import com.hydrafit.app.core.domain.workout.WorkoutHistoryEntry
import com.hydrafit.app.core.domain.workout.WorkoutHistoryKey
import com.hydrafit.app.core.domain.workout.WorkoutHistoryPage
import com.hydrafit.app.core.domain.workout.WorkoutHistoryRequest
import com.hydrafit.app.core.domain.workout.WorkoutHistorySetPage
import com.hydrafit.app.core.domain.workout.WorkoutHistorySetRequest
import com.hydrafit.app.core.domain.workout.WorkoutSet
import com.hydrafit.app.core.domain.workout.WorkoutSetCorrection
import com.hydrafit.app.core.domain.workout.WorkoutTimingProvenance
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest

private const val DAY_ONE = 0L
private const val DAY_TWO = 86_400_000L

/**
 * The 0.7.1 integration path exercised through real producers and consumers rather than fixtures:
 * logged sets -> note storage -> bounded history -> a full backup round trip -> the existing
 * correction use case, re-read by the history repository and the fatigue-facing log projection.
 */
class WorkoutNotesHistoryIntegrationTest {

    private lateinit var driver: JdbcSqliteDriver
    private lateinit var database: HydraFitDatabase
    private lateinit var history: SqlDelightWorkoutHistoryRepository

    @BeforeTest
    fun setUp() {
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        HydraFitDatabase.Schema.create(driver)
        database = HydraFitDatabase(driver)
        SeedExerciseCatalog(database).seed()
        SeedEquipmentCatalog(database).seed()
        history = SqlDelightWorkoutHistoryRepository(database)
    }

    @AfterTest
    fun tearDown() {
        driver.close()
    }

    @Test
    fun loggedSetsAndNotesSurviveBackupAndCorrectionEndToEnd() = runTest {
        val sessionNotes = SqlDelightSessionNoteRepository(database)
        val exerciseNotes = SqlDelightExerciseNoteRepository(database)
        val logSets = logWorkoutSetUseCase()

        // Producer: two sets auto-start one session; the earlier of the two is a warm-up.
        logSets(
            performedSet(reps = 8, weightKg = 60.0, performedAtMillis = 60_000L, isWarmup = true),
            0L
        )
        logSets(performedSet(reps = 5, weightKg = 100.0, performedAtMillis = 120_000L), 0L)
        val sessionId = database.workoutSessionQueries.selectAllSessions().executeAsList()
            .single()
            .id

        // Producer: a saved note, an explicitly cleared note on an empty session, never-set elsewhere.
        exerciseNotes.save("back-squat", "Elbows tucked, brace hard")
        sessionNotes.save(sessionId, "Felt strong today")
        database.workoutSessionQueries.insertSession("cleared-note", DAY_TWO, null, 1L)
        sessionNotes.clear("cleared-note")
        val targetSession = WorkoutHistoryKey.Session(sessionId)

        assertEquals(2, history.entry(targetSession)!!.recordedSetCount)
        assertEquals("Felt strong today", history.entry(targetSession)!!.note)

        val recorded = history.sets(targetSession).sets
        assertEquals(listOf(5, 8), recorded.map { it.reps })
        assertTrue(recorded.last().isWarmup)
        assertEquals(60.0, recorded.last().weightKg)
        assertEquals(LoadKind.EXTERNAL, recorded.last().loadKind)
        assertEquals("Back Squat", recorded.last().exerciseName)
        assertEquals(WorkoutTimingProvenance.UNKNOWN, recorded.last().timingProvenance)

        // Search covers the current catalog name and the session note, never the setup note.
        assertEquals(listOf<WorkoutHistoryKey>(targetSession), searchKeys("back squat"))
        assertEquals(listOf<WorkoutHistoryKey>(targetSession), searchKeys("felt strong"))
        assertTrue(searchKeys("elbows tucked").isEmpty())

        // A cleared note keeps its otherwise empty session visible; an empty unnoted one is absent.
        val cleared = history.entry(WorkoutHistoryKey.Session("cleared-note"))!!
        assertEquals(0, cleared.recordedSetCount)
        assertEquals("", cleared.note)

        // Backup: export -> encode -> validate -> stage -> apply, through the real chain.
        val catalog = SqlDelightBackupCatalog()
        val preview = PreviewBackupUseCase(BackupValidator(catalog))
        val staging = SqlDelightBackupStagingRepository(database)
        val backup = SqlDelightBackupRepository(database, catalog)
        val stageRestore = RestoreBackupUseCase(preview, staging)
        val applyStaged = ApplyStagedBackupUseCase(preview, backup, staging, TimeProvider { 0L })

        val exported = backup.export(appVersion = "integration", exportedAtMillis = 1L)
        stageRestore(BackupJson.encode(exported), "integration", 1L)
        applyStaged()

        // Restored data reads back identically, including the folded search projections. The sections
        // in scope are compared rather than the whole file: an absent planner-settings row
        // legitimately materialises as the default row during restore.
        val restored = backup.export(appVersion = "integration", exportedAtMillis = 1L)
        assertEquals(exported.workoutSets, restored.workoutSets)
        assertEquals(exported.workoutSessions, restored.workoutSessions)
        assertEquals(exported.exerciseNotes, restored.exerciseNotes)
        assertEquals(exported.sessionNotes, restored.sessionNotes)
        assertEquals(listOf<WorkoutHistoryKey>(targetSession), searchKeys("back squat"))
        assertTrue(searchKeys("elbows tucked").isEmpty())
        assertEquals("Felt strong today", history.entry(targetSession)!!.note)
        assertEquals("", history.entry(WorkoutHistoryKey.Session("cleared-note"))!!.note)
        assertEquals("Elbows tucked, brace hard", exerciseNotes.note("back-squat"))

        // Correction through the existing use case: the same row, warm-up kept, note untouched.
        val warmup = history.sets(targetSession).sets.single { it.isWarmup }
        val correctedAt = DAY_TWO + 60_000L
        CorrectWorkoutSetUseCase(
            SqlDelightSessionResegmenter(database),
            TimeProvider { correctedAt }
        )(
            warmup.id,
            WorkoutSetCorrection(
                reps = 12,
                weightKg = 65.0,
                rir = 1,
                performedAtMillis = warmup.performedAtMillis
            )
        )

        val corrected = history.sets(targetSession).sets.single { it.id == warmup.id }
        assertEquals(12, corrected.reps)
        assertEquals(65.0, corrected.weightKg)
        assertEquals(1, corrected.rir)
        assertTrue(corrected.isWarmup)
        assertEquals("Felt strong today", history.entry(targetSession)!!.note)

        // The fatigue-facing projection reads the same corrected row, and no set was duplicated.
        val fatigueSets = SqlDelightWorkoutLogRepository(database).loggedSets()
        assertEquals(2, fatigueSets.size)
        assertEquals(12, fatigueSets.single { it.isWarmup }.reps)

        // The browser's live read re-emits the corrected page.
        assertEquals(
            12,
            history.observeEntrySets(targetSession, WorkoutHistorySetRequest())
                .first()
                .sets
                .single { it.id == warmup.id }
                .reps
        )
    }

    @Test
    fun aSetWithoutASessionIsRefusedByBackupValidationRatherThanBackfilled() = runTest {
        val logSets = logWorkoutSetUseCase()
        logSets(
            performedSet(reps = 6, weightKg = 40.0, performedAtMillis = DAY_ONE),
            0L
        )
        val sessionId = database.workoutSessionQueries.selectAllSessions().executeAsList()
            .single()
            .id
        val setId = database.workoutLogQueries.selectAllSets().executeAsList().single().id
        database.workoutLogQueries.assignSession(sessionId = null, id = setId)

        // An unassigned set is reported by its own identity, never grouped with the empty session.
        val key = WorkoutHistoryKey.UnassignedSet(setId)
        assertEquals(1, history.entry(key)!!.recordedSetCount)
        assertNull(history.entry(WorkoutHistoryKey.Session(sessionId)))

        // Restoring it would silently change what is stored, so the payload is rejected instead.
        val catalog = SqlDelightBackupCatalog()
        val preview = PreviewBackupUseCase(BackupValidator(catalog))
        val staging = SqlDelightBackupStagingRepository(database)
        val exported = SqlDelightBackupRepository(database, catalog)
            .export(appVersion = "t", exportedAtMillis = 1L)

        val failure = assertFailsWith<BackupException> {
            RestoreBackupUseCase(preview, staging)(BackupJson.encode(exported), "t", 1L)
        }
        assertEquals(BackupFailure.STARTUP_UNSTABLE, failure.failure)

        // Nothing was written: the rejected file leaves the local rows exactly as they were.
        assertEquals(1, database.workoutLogQueries.selectAllSets().executeAsList().size)
        assertEquals(1, database.workoutSessionQueries.selectAllSessions().executeAsList().size)
        assertEquals(1, history.entry(key)!!.recordedSetCount)

        // The correction path still works on an unassigned set and keeps its identity.
        CorrectWorkoutSetUseCase(
            SqlDelightSessionResegmenter(database),
            TimeProvider { DAY_TWO }
        )(
            setId,
            WorkoutSetCorrection(reps = 9, weightKg = 45.0, rir = null, performedAtMillis = DAY_ONE)
        )

        val corrected = history.sets(key).sets.single()
        assertEquals(setId, corrected.id)
        assertEquals(9, corrected.reps)
        assertEquals(45.0, corrected.weightKg)
        assertEquals(WorkoutTimingProvenance.UNKNOWN, corrected.timingProvenance)
    }

    private suspend fun searchKeys(search: String): List<WorkoutHistoryKey> =
        history.entries(WorkoutHistoryRequest(search = search)).entries.map { it.key }

    private fun performedSet(
        reps: Int,
        weightKg: Double?,
        performedAtMillis: Long,
        isWarmup: Boolean = false
    ) = WorkoutSet(
        exerciseId = "back-squat",
        reps = reps,
        weightKg = weightKg,
        loadKind = LoadKind.EXTERNAL,
        performedAtMillis = performedAtMillis,
        isWarmup = isWarmup
    )

    private fun logWorkoutSetUseCase(): LogWorkoutSetUseCase {
        val sessions = SqlDelightWorkoutSessionRepository(database)
        return LogWorkoutSetUseCase(
            repository = SqlDelightWorkoutLogRepository(database),
            startWorkoutSession = StartWorkoutSessionUseCase(sessions),
            endWorkoutSession = EndWorkoutSessionUseCase(sessions),
            observeOpenWorkoutSession = ObserveOpenWorkoutSessionUseCase(sessions),
            config = SessionConfig()
        )
    }

    private suspend fun SqlDelightWorkoutHistoryRepository.entry(
        key: WorkoutHistoryKey
    ): WorkoutHistoryEntry? = observeEntry(key, 0L).first()

    private suspend fun SqlDelightWorkoutHistoryRepository.entries(
        request: WorkoutHistoryRequest
    ): WorkoutHistoryPage = observeEntries(request).first()

    private suspend fun SqlDelightWorkoutHistoryRepository.sets(
        key: WorkoutHistoryKey,
        request: WorkoutHistorySetRequest = WorkoutHistorySetRequest()
    ): WorkoutHistorySetPage = observeEntrySets(key, request).first()
}
