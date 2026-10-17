package com.hydrafit.app.feature.settings

import com.hydrafit.app.core.domain.backup.AutomaticBackupFailureKind
import com.hydrafit.app.core.domain.backup.AutomaticBackupFailureRecord
import com.hydrafit.app.core.domain.backup.AutomaticBackupScheduler
import com.hydrafit.app.core.domain.backup.AutomaticBackupState
import com.hydrafit.app.core.domain.backup.AutomaticBackupStateStore
import com.hydrafit.app.core.domain.backup.BACKUP_FORMAT
import com.hydrafit.app.core.domain.backup.BACKUP_FORMAT_VERSION
import com.hydrafit.app.core.domain.backup.BackupCatalog
import com.hydrafit.app.core.domain.backup.BackupCatalogManifest
import com.hydrafit.app.core.domain.backup.BackupCrypto
import com.hydrafit.app.core.domain.backup.BackupEncryption
import com.hydrafit.app.core.domain.backup.BackupEncryptionKeyStore
import com.hydrafit.app.core.domain.backup.BackupFile
import com.hydrafit.app.core.domain.backup.BackupFolderStore
import com.hydrafit.app.core.domain.backup.BackupRepository
import com.hydrafit.app.core.domain.backup.BackupRestPreferenceRecord
import com.hydrafit.app.core.domain.backup.BackupValidator
import com.hydrafit.app.core.domain.backup.ConfigureAutomaticBackupFolderUseCase
import com.hydrafit.app.core.domain.backup.ExportBackupUseCase
import com.hydrafit.app.core.domain.backup.FolderCapabilities
import com.hydrafit.app.core.domain.backup.FolderUnavailableReason
import com.hydrafit.app.core.domain.backup.RecordedBackupDocument
import com.hydrafit.app.core.domain.backup.RunAutomaticBackupUseCase
import com.hydrafit.app.core.domain.backup.SetAutomaticBackupsEnabledUseCase
import com.hydrafit.app.core.domain.backup.StoredBackupDocument
import com.hydrafit.app.core.domain.backup.StoredBackupKey
import com.hydrafit.app.core.domain.backup.VerifyBackupSnapshotUseCase
import com.hydrafit.app.core.domain.time.TimeProvider
import com.hydrafit.app.core.userdata.settings.AppVersionProvider
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
class AutomaticBackupSettingsViewModelTest {

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
    fun mapsTheStoredStateToTheUiState() = runTest(dispatcher) {
        val store = FakeAutomaticBackupStateStore(
            AutomaticBackupState(
                enabled = true,
                folderHandle = "content://folder",
                lastSuccessMillis = 5L,
                lastFailure = AutomaticBackupFailureRecord(
                    AutomaticBackupFailureKind.WRITE_FAILED,
                    6L
                ),
                documents = listOf(
                    RecordedBackupDocument("a", "a.json", 1L, verified = true),
                    RecordedBackupDocument("b", "b.json", 2L, verified = false)
                )
            )
        )

        val viewModel = viewModel(stateStore = store)
        advanceUntilIdle()

        val state = viewModel.state.value
        assertTrue(state.enabled)
        assertTrue(state.folderConfigured)
        assertTrue(state.hasSucceededBefore)
        assertEquals(AutomaticBackupFailureKind.WRITE_FAILED, state.lastFailure)
        assertEquals(1, state.keptDocuments)
        assertFalse(state.busy)
    }

    @Test
    fun reportsUnsupportedFolderSurface() = runTest(dispatcher) {
        val viewModel = viewModel(folderStore = FakeBackupFolderStore(isSupported = false))
        advanceUntilIdle()

        assertFalse(viewModel.state.value.isSupported)
    }

    @Test
    fun choosingAReadyFolderStoresTheHandleWithoutAStatus() = runTest(dispatcher) {
        val store = FakeAutomaticBackupStateStore()
        val viewModel = viewModel(stateStore = store)

        viewModel.chooseFolder("content://folder")
        advanceUntilIdle()

        assertEquals("content://folder", store.state.folderHandle)
        assertTrue(viewModel.state.value.folderConfigured)
        assertNull(viewModel.state.value.status)
    }

    @Test
    fun choosingAnUnusableFolderReportsTheReason() = runTest(dispatcher) {
        val folderStore = FakeBackupFolderStore(
            capabilitiesResult = FolderCapabilities.Unavailable(
                FolderUnavailableReason.PERMISSION_LOST
            )
        )
        val store = FakeAutomaticBackupStateStore()
        val viewModel = viewModel(stateStore = store, folderStore = folderStore)

        viewModel.chooseFolder("content://folder")
        advanceUntilIdle()

        assertNull(store.state.folderHandle)
        assertEquals(
            AutomaticBackupStatus.FolderUnavailable(FolderUnavailableReason.PERMISSION_LOST),
            viewModel.state.value.status
        )
    }

    @Test
    fun enablingWithAUsableFolderTurnsAutomationOn() = runTest(dispatcher) {
        val scheduler = FakeAutomaticBackupScheduler()
        val store = FakeAutomaticBackupStateStore(
            AutomaticBackupState(folderHandle = "content://f")
        )
        val viewModel = viewModel(stateStore = store, scheduler = scheduler)

        viewModel.setEnabled(true)
        advanceUntilIdle()

        assertTrue(store.state.enabled)
        assertEquals(AutomaticBackupStatus.Enabled, viewModel.state.value.status)
        assertEquals(1, scheduler.scheduleCount)
    }

    @Test
    fun enablingWithoutAFolderIsRefused() = runTest(dispatcher) {
        val store = FakeAutomaticBackupStateStore()
        val viewModel = viewModel(stateStore = store)

        viewModel.setEnabled(true)
        advanceUntilIdle()

        assertFalse(store.state.enabled)
        assertEquals(AutomaticBackupStatus.FolderNotConfigured, viewModel.state.value.status)
    }

    @Test
    fun enablingWithAnUnusableFolderIsRefused() = runTest(dispatcher) {
        val folderStore = FakeBackupFolderStore(
            capabilitiesResult = FolderCapabilities.Unavailable(
                FolderUnavailableReason.UNSUPPORTED_PROVIDER
            )
        )
        val store = FakeAutomaticBackupStateStore(
            AutomaticBackupState(folderHandle = "content://f")
        )
        val viewModel = viewModel(stateStore = store, folderStore = folderStore)

        viewModel.setEnabled(true)
        advanceUntilIdle()

        assertFalse(store.state.enabled)
        assertEquals(
            AutomaticBackupStatus.FolderUnavailable(FolderUnavailableReason.UNSUPPORTED_PROVIDER),
            viewModel.state.value.status
        )
    }

    @Test
    fun disablingCancelsTheSchedule() = runTest(dispatcher) {
        val scheduler = FakeAutomaticBackupScheduler()
        val store = FakeAutomaticBackupStateStore(
            AutomaticBackupState(enabled = true, folderHandle = "content://f")
        )
        val viewModel = viewModel(stateStore = store, scheduler = scheduler)

        viewModel.setEnabled(false)
        advanceUntilIdle()

        assertFalse(store.state.enabled)
        assertEquals(AutomaticBackupStatus.Disabled, viewModel.state.value.status)
        assertEquals(1, scheduler.cancelCount)
    }

    @Test
    fun backingUpNowReportsSuccessAndRefreshes() = runTest(dispatcher) {
        val store = FakeAutomaticBackupStateStore(
            AutomaticBackupState(enabled = true, folderHandle = "content://f")
        )
        val viewModel = viewModel(stateStore = store)

        viewModel.runNow()
        advanceUntilIdle()

        assertEquals(AutomaticBackupStatus.RunSucceeded, viewModel.state.value.status)
        assertTrue(viewModel.state.value.hasSucceededBefore)
        assertEquals(1, viewModel.state.value.keptDocuments)
    }

    @Test
    fun backingUpNowReportsARunFailure() = runTest(dispatcher) {
        val folderStore = FakeBackupFolderStore(
            capabilitiesResult = FolderCapabilities.Unavailable(
                FolderUnavailableReason.FOLDER_MISSING
            )
        )
        val store = FakeAutomaticBackupStateStore(
            AutomaticBackupState(enabled = true, folderHandle = "content://f")
        )
        val viewModel = viewModel(stateStore = store, folderStore = folderStore)

        viewModel.runNow()
        advanceUntilIdle()

        assertEquals(
            AutomaticBackupStatus.RunFailed(AutomaticBackupFailureKind.FOLDER_UNAVAILABLE),
            viewModel.state.value.status
        )
        assertEquals(
            AutomaticBackupFailureKind.FOLDER_UNAVAILABLE,
            viewModel.state.value.lastFailure
        )
        assertFalse(viewModel.state.value.hasSucceededBefore)
    }

    @Test
    fun aSecondRunWhileBusyIsIgnored() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>()
        val folderStore = FakeBackupFolderStore(createGate = gate)
        val store = FakeAutomaticBackupStateStore(
            AutomaticBackupState(enabled = true, folderHandle = "content://f")
        )
        val viewModel = viewModel(stateStore = store, folderStore = folderStore)
        advanceUntilIdle()

        viewModel.runNow()
        advanceUntilIdle()
        viewModel.runNow()
        advanceUntilIdle()
        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(1, folderStore.createCount)
        assertEquals(AutomaticBackupStatus.RunSucceeded, viewModel.state.value.status)
    }

    @Test
    fun runCancellationIsRethrownAndNotReportedAsAFailure() = runTest(dispatcher) {
        val folderStore = FakeBackupFolderStore(
            createFailure = CancellationException("cancelled")
        )
        val store = FakeAutomaticBackupStateStore(
            AutomaticBackupState(enabled = true, folderHandle = "content://f")
        )
        val viewModel = viewModel(stateStore = store, folderStore = folderStore)

        viewModel.runNow()
        advanceUntilIdle()

        assertNull(viewModel.state.value.status)
        assertEquals(0, folderStore.createCount)
    }

    @Test
    fun dismissingClearsTheStatusLine() = runTest(dispatcher) {
        val store = FakeAutomaticBackupStateStore()
        val viewModel = viewModel(stateStore = store)

        viewModel.setEnabled(true)
        advanceUntilIdle()
        assertEquals(AutomaticBackupStatus.FolderNotConfigured, viewModel.state.value.status)

        viewModel.dismissStatus()
        assertNull(viewModel.state.value.status)
    }

    private fun viewModel(
        stateStore: FakeAutomaticBackupStateStore = FakeAutomaticBackupStateStore(),
        folderStore: FakeBackupFolderStore = FakeBackupFolderStore(),
        scheduler: FakeAutomaticBackupScheduler = FakeAutomaticBackupScheduler(),
        repository: BackupRepository = FakeAutomaticBackupRepository()
    ): AutomaticBackupSettingsViewModel {
        val catalog = object : BackupCatalog {
            override fun seedExerciseIds(): Set<String> = emptySet()

            override fun builtInEquipmentIds(): Set<String> = emptySet()
        }
        return AutomaticBackupSettingsViewModel(
            stateStore = stateStore,
            folderStore = folderStore,
            configureFolder = ConfigureAutomaticBackupFolderUseCase(folderStore, stateStore),
            enableBackups = SetAutomaticBackupsEnabledUseCase(folderStore, stateStore, scheduler),
            runBackup = RunAutomaticBackupUseCase(
                exportBackup = ExportBackupUseCase(repository, dispatcher),
                encryption = BackupEncryption(
                    crypto = FakeBackupCrypto(),
                    keyStore = FakeBackupEncryptionKeyStore(),
                    verifySnapshot = VerifyBackupSnapshotUseCase(BackupValidator(catalog)),
                    dispatcher = dispatcher
                ),
                folderStore = folderStore,
                stateStore = stateStore,
                timeProvider = TimeProvider { 1L },
                dispatcher = dispatcher
            ),
            appVersionProvider = object : AppVersionProvider {
                override val versionName: String = "t"
            }
        )
    }
}

private class FakeAutomaticBackupStateStore(
    var state: AutomaticBackupState = AutomaticBackupState()
) : AutomaticBackupStateStore {
    override suspend fun load(): AutomaticBackupState = state

    override suspend fun update(
        change: (AutomaticBackupState) -> AutomaticBackupState
    ): AutomaticBackupState {
        state = change(state)
        return state
    }
}

private class FakeBackupFolderStore(
    override val isSupported: Boolean = true,
    private var capabilitiesResult: FolderCapabilities = FolderCapabilities.Ready,
    private val createGate: CompletableDeferred<Unit>? = null,
    private val createFailure: Throwable? = null
) : BackupFolderStore {
    private val documents = mutableMapOf<String, ByteArray>()
    var createCount: Int = 0

    override suspend fun capabilities(handle: String): FolderCapabilities = capabilitiesResult

    override suspend fun create(
        handle: String,
        displayName: String,
        bytes: ByteArray
    ): StoredBackupDocument {
        createGate?.await()
        createFailure?.let { throw it }
        createCount++
        val id = "doc-$createCount"
        documents[id] = bytes
        return StoredBackupDocument(id, displayName)
    }

    override suspend fun read(handle: String, documentId: String): ByteArray =
        documents[documentId] ?: ByteArray(0)

    override suspend fun delete(handle: String, documentId: String) {
        documents.remove(documentId)
    }
}

private class FakeAutomaticBackupScheduler : AutomaticBackupScheduler {
    var scheduleCount: Int = 0
    var cancelCount: Int = 0

    override fun schedule() {
        scheduleCount++
    }

    override fun cancel() {
        cancelCount++
    }
}

/** Identity test cipher: the ViewModel tests exercise the plaintext paths only. */
private class FakeBackupCrypto : BackupCrypto {
    override val isSupported: Boolean = true

    override fun randomBytes(size: Int): ByteArray = ByteArray(size)

    override fun deriveKey(
        password: CharArray,
        salt: ByteArray,
        iterations: Int,
        keyBytes: Int
    ): ByteArray = ByteArray(keyBytes)

    override fun encrypt(key: ByteArray, nonce: ByteArray, plaintext: ByteArray): ByteArray =
        plaintext

    override fun decrypt(key: ByteArray, nonce: ByteArray, ciphertext: ByteArray): ByteArray =
        ciphertext
}

/** Empty unattended material; the ViewModel tests keep encryption off. */
private class FakeBackupEncryptionKeyStore : BackupEncryptionKeyStore {
    override suspend fun load(): StoredBackupKey? = null

    override suspend fun store(key: StoredBackupKey) = Unit

    override suspend fun clear() = Unit
}

private class FakeAutomaticBackupRepository : BackupRepository {
    override suspend fun export(appVersion: String, exportedAtMillis: Long): BackupFile =
        validBackupFile(appVersion, exportedAtMillis)

    override suspend fun restore(file: BackupFile) = Unit
}

private fun validBackupFile(appVersion: String, exportedAtMillis: Long): BackupFile = BackupFile(
    format = BACKUP_FORMAT,
    formatVersion = BACKUP_FORMAT_VERSION,
    appVersion = appVersion,
    exportedAtMillis = exportedAtMillis,
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
