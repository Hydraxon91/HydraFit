package com.hydrafit.app.feature.settings

import com.hydrafit.app.core.domain.backup.BACKUP_FORMAT
import com.hydrafit.app.core.domain.backup.BackupApplyError
import com.hydrafit.app.core.domain.backup.BackupCatalog
import com.hydrafit.app.core.domain.backup.BackupCatalogManifest
import com.hydrafit.app.core.domain.backup.BackupFailure
import com.hydrafit.app.core.domain.backup.BackupFile
import com.hydrafit.app.core.domain.backup.BackupJson
import com.hydrafit.app.core.domain.backup.BackupRepository
import com.hydrafit.app.core.domain.backup.BackupRestPreferenceRecord
import com.hydrafit.app.core.domain.backup.BackupStagingRepository
import com.hydrafit.app.core.domain.backup.BackupValidator
import com.hydrafit.app.core.domain.backup.ExportBackupUseCase
import com.hydrafit.app.core.domain.backup.PendingBackup
import com.hydrafit.app.core.domain.backup.PreviewBackupUseCase
import com.hydrafit.app.core.domain.backup.RestoreBackupUseCase
import com.hydrafit.app.core.domain.engine.ExerciseCatalog
import com.hydrafit.app.core.domain.equipment.Exercise
import com.hydrafit.app.core.domain.fatigue.LoggedSet
import com.hydrafit.app.core.domain.time.TimeProvider
import com.hydrafit.app.core.domain.transfer.ExportWorkoutHistoryCsvUseCase
import com.hydrafit.app.core.domain.transfer.WorkoutHistoryCsvWriter
import com.hydrafit.app.core.domain.workout.WorkoutLogRepository
import com.hydrafit.app.core.domain.workout.WorkoutSession
import com.hydrafit.app.core.domain.workout.WorkoutSessionRepository
import com.hydrafit.app.core.domain.workout.WorkoutSet
import com.hydrafit.app.core.userdata.backup.BackupFileStore
import com.hydrafit.app.core.userdata.settings.AppVersionProvider
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
class BackupViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun exportsToTheChosenDocument() = runTest(dispatcher) {
        val store = FakeBackupFileStore()
        val viewModel = viewModel(store, FakeBackupRepository())

        viewModel.export("content://dest")
        advanceUntilIdle()

        assertTrue(store.written.single().contains("\"format\":\"hydrafit-backup\""))
        assertEquals(BackupStatus.Exported, viewModel.state.value.status)
    }

    @Test
    fun validFileShowsPreviewAndCancelStagesNothing() = runTest(dispatcher) {
        val repository = FakeBackupRepository()
        val staging = FakeBackupStagingRepository()
        val store = FakeBackupFileStore(content = BackupJson.encode(repository.exported))
        val viewModel = viewModel(store, repository, staging)

        viewModel.`import`("content://src")
        advanceUntilIdle()

        assertNotNull(viewModel.state.value.preview)
        viewModel.cancelPreview()
        assertNull(viewModel.state.value.preview)
        assertNull(staging.staged.lastOrNull())
    }

    @Test
    fun invalidFileReportsMalformedWithoutPreview() = runTest(dispatcher) {
        val viewModel =
            viewModel(FakeBackupFileStore(content = "not a backup"), FakeBackupRepository())

        viewModel.`import`("content://src")
        advanceUntilIdle()

        assertNull(viewModel.state.value.preview)
        assertEquals(BackupStatus.Failed(BackupFailure.MALFORMED), viewModel.state.value.status)
    }

    @Test
    fun confirmRestoreStagesAndClearsThePreview() = runTest(dispatcher) {
        val repository = FakeBackupRepository()
        val staging = FakeBackupStagingRepository()
        val store = FakeBackupFileStore(content = BackupJson.encode(repository.exported))
        val viewModel = viewModel(store, repository, staging)

        viewModel.`import`("content://src")
        advanceUntilIdle()
        viewModel.confirmRestore()
        advanceUntilIdle()

        assertNotNull(staging.staged.lastOrNull())
        assertNull(viewModel.state.value.preview)
        assertEquals(BackupStatus.RestoreStaged, viewModel.state.value.status)
    }

    @Test
    fun surfacesAPendingApplyErrorAndClearsIt() = runTest(dispatcher) {
        val staging = FakeBackupStagingRepository()
        staging.pendingError = BackupApplyError(BackupFailure.STARTUP_UNSTABLE, null, 1L)
        val viewModel = viewModel(FakeBackupFileStore(), FakeBackupRepository(), staging)

        advanceUntilIdle()

        assertEquals(
            BackupStatus.Failed(BackupFailure.STARTUP_UNSTABLE),
            viewModel.state.value.status
        )
        assertNull(staging.pendingError)
    }

    @Test
    fun reportsUnsupportedStore() = runTest(dispatcher) {
        val viewModel = viewModel(
            FakeBackupFileStore(isSupported = false),
            FakeBackupRepository()
        )

        assertFalse(viewModel.state.value.isSupported)
    }

    private fun viewModel(
        store: BackupFileStore,
        repository: BackupRepository,
        staging: BackupStagingRepository = FakeBackupStagingRepository(),
        logs: WorkoutLogRepository = FakeWorkoutLogRepository()
    ): BackupViewModel {
        val catalog = object : BackupCatalog {
            override fun seedExerciseIds(): Set<String> = emptySet()

            override fun builtInEquipmentIds(): Set<String> = emptySet()
        }
        val preview = PreviewBackupUseCase(BackupValidator(catalog), dispatcher)
        return BackupViewModel(
            exportBackup = ExportBackupUseCase(repository, dispatcher),
            previewBackup = preview,
            restoreBackup = RestoreBackupUseCase(preview, staging),
            fileStore = store,
            appVersionProvider = object : AppVersionProvider {
                override val versionName: String = "t"
            },
            timeProvider = TimeProvider { 1L },
            exportWorkoutHistoryCsv = ExportWorkoutHistoryCsvUseCase(
                logs,
                FakeWorkoutSessionRepository(),
                FakeExerciseCatalog()
            )
        )
    }

    @Test
    fun historyExportWritesTheUseCaseTextUnchanged() = runTest(dispatcher) {
        val store = FakeBackupFileStore()
        val viewModel = viewModel(store, FakeBackupRepository())

        viewModel.exportCsv("content://csv")
        advanceUntilIdle()

        assertEquals(listOf("content://csv"), store.writtenHandles)
        assertEquals(WorkoutHistoryCsvWriter.write(emptyList()), store.written.single())
        assertEquals(BackupStatus.HistoryCsvExported, viewModel.state.value.status)
        assertNotEquals(BackupStatus.Exported, viewModel.state.value.status)
    }

    @Test
    fun historyExportWriteFailureSurfacesThroughTheFailurePath() = runTest(dispatcher) {
        val store = FakeBackupFileStore(writeFailure = IllegalStateException("no space"))
        val viewModel = viewModel(store, FakeBackupRepository())

        viewModel.exportCsv("content://csv")
        advanceUntilIdle()

        assertEquals(BackupStatus.Failed(BackupFailure.IO), viewModel.state.value.status)
        assertTrue(store.written.isEmpty())
    }

    @Test
    fun historyExportFailureBeforeTheWriteLeavesTheDocumentUntouched() = runTest(dispatcher) {
        val store = FakeBackupFileStore()
        val viewModel = viewModel(
            store = store,
            repository = FakeBackupRepository(),
            logs = FakeWorkoutLogRepository(failure = IllegalStateException("unreadable"))
        )

        viewModel.exportCsv("content://csv")
        advanceUntilIdle()

        assertEquals(BackupStatus.Failed(BackupFailure.IO), viewModel.state.value.status)
        assertTrue(store.written.isEmpty())
    }

    @Test
    fun historyExportCancellationIsRethrownNotReportedAsIo() = runTest(dispatcher) {
        val store = FakeBackupFileStore(writeFailure = CancellationException("cancelled"))
        val viewModel = viewModel(store, FakeBackupRepository())

        viewModel.exportCsv("content://csv")
        advanceUntilIdle()

        // Rethrown, so the export never publishes a failure status (and never writes).
        assertNull(viewModel.state.value.status)
        assertTrue(store.written.isEmpty())
    }

    @Test
    fun aSecondHistoryExportWhileInProgressDoesNotWriteAgain() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>()
        val store = FakeBackupFileStore(writeGate = gate)
        val viewModel = viewModel(store, FakeBackupRepository())

        viewModel.exportCsv("content://one")
        advanceUntilIdle()
        viewModel.exportCsv("content://two")
        advanceUntilIdle()
        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(listOf("content://one"), store.writtenHandles)
        assertEquals(BackupStatus.HistoryCsvExported, viewModel.state.value.status)
    }

    private class FakeWorkoutLogRepository(private val failure: Throwable? = null) :
        WorkoutLogRepository {
        override suspend fun add(set: WorkoutSet) = Unit

        override suspend fun assignSession(setId: Long, sessionId: String) = Unit

        override suspend fun delete(id: Long) = Unit

        override suspend fun updateSetPerformedAt(setId: Long, performedAtMillis: Long) = Unit

        override suspend fun all(): List<WorkoutSet> {
            failure?.let { throw it }
            return emptyList()
        }

        override suspend fun lastSetBySession(sessionId: String): WorkoutSet? = null

        override suspend fun setsForOccurrence(occurrenceId: Long): List<WorkoutSet> = emptyList()

        override fun setsFlow(): Flow<List<WorkoutSet>> = emptyFlow()

        override suspend fun loggedSets(): List<LoggedSet> = emptyList()

        override fun loggedSetsFlow(): Flow<List<LoggedSet>> = emptyFlow()

        override suspend fun clear() = Unit
    }

    private class FakeWorkoutSessionRepository : WorkoutSessionRepository {
        override suspend fun create(session: WorkoutSession) = Unit

        override suspend fun end(id: String, endedAtMillis: Long) = Unit

        override suspend fun open(): WorkoutSession? = null

        override fun openFlow(): Flow<WorkoutSession?> = emptyFlow()

        override suspend fun all(): List<WorkoutSession> = emptyList()
    }

    private class FakeExerciseCatalog : ExerciseCatalog {
        override suspend fun all(): List<Exercise> = emptyList()
    }
}

private class FakeBackupRepository : BackupRepository {
    var exported: BackupFile = emptyBackupFile()
    var restored: BackupFile? = null

    override suspend fun export(appVersion: String, exportedAtMillis: Long): BackupFile = exported

    override suspend fun restore(file: BackupFile) {
        restored = file
    }
}

private class FakeBackupStagingRepository : BackupStagingRepository {
    val staged = mutableListOf<PendingBackup>()
    var pendingError: BackupApplyError? = null

    override suspend fun stage(payload: String, appVersion: String, stagedAtMillis: Long) {
        staged += PendingBackup(payload, appVersion, stagedAtMillis)
    }

    override suspend fun staged(): PendingBackup? = staged.lastOrNull()

    override suspend fun clearStaged() {
        staged.clear()
    }

    override suspend fun recordApplyError(
        failure: BackupFailure,
        message: String?,
        occurredAtMillis: Long
    ) {
        pendingError = BackupApplyError(failure, message, occurredAtMillis)
    }

    override suspend fun applyError(): BackupApplyError? = pendingError

    override suspend fun clearApplyError() {
        pendingError = null
    }
}

private class FakeBackupFileStore(
    override val isSupported: Boolean = true,
    private var content: String = "",
    private val writeGate: CompletableDeferred<Unit>? = null,
    private val writeFailure: Throwable? = null
) : BackupFileStore {
    val written = mutableListOf<String>()
    val writtenHandles = mutableListOf<String>()

    override suspend fun read(handle: String): String = content

    override suspend fun write(handle: String, text: String) {
        writeGate?.await()
        writeFailure?.let { throw it }
        written += text
        writtenHandles += handle
    }
}

private fun emptyBackupFile(): BackupFile = BackupFile(
    format = BACKUP_FORMAT,
    formatVersion = 13,
    appVersion = "t",
    exportedAtMillis = 1L,
    catalog = BackupCatalogManifest(emptyList(), seedProfiles = emptyList()),
    settings = null,
    customExercises = emptyList(),
    exerciseOverrides = emptyList(),
    equipment = emptyList(),
    selectedEquipment = emptyList(),
    workoutSets = emptyList(),
    workoutSessions = emptyList(),
    plans = emptyList(),
    planDays = emptyList(),
    planEntries = emptyList(),
    volumeExplanations = emptyList(),
    volumeExplanationStates = emptyList(),
    routines = emptyList(),
    routineWorkouts = emptyList(),
    routineEntries = emptyList(),
    activations = emptyList(),
    activationWorkouts = emptyList(),
    activationEntries = emptyList(),
    occurrences = emptyList(),
    occurrenceEntries = emptyList(),
    scheduleState = null,
    personalRecords = emptyList(),
    preferences = emptyList(),
    exclusions = emptyList(),
    restPreferences = listOf(BackupRestPreferenceRecord(null, 120L))
)
