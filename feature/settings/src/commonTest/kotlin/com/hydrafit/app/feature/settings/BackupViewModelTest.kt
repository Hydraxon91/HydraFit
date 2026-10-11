package com.hydrafit.app.feature.settings

import com.hydrafit.app.core.domain.backup.BACKUP_FORMAT
import com.hydrafit.app.core.domain.backup.BACKUP_FORMAT_VERSION
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
import com.hydrafit.app.core.domain.time.TimeProvider
import com.hydrafit.app.core.userdata.backup.BackupFileStore
import com.hydrafit.app.core.userdata.settings.AppVersionProvider
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
        staging: BackupStagingRepository = FakeBackupStagingRepository()
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
            timeProvider = TimeProvider { 1L }
        )
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
    private var content: String = ""
) : BackupFileStore {
    val written = mutableListOf<String>()

    override suspend fun read(handle: String): String = content

    override suspend fun write(handle: String, text: String) {
        written += text
    }
}

private fun emptyBackupFile(): BackupFile = BackupFile(
    format = BACKUP_FORMAT,
    formatVersion = BACKUP_FORMAT_VERSION,
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
