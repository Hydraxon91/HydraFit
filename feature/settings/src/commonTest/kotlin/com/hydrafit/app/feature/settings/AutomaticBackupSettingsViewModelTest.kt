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
import com.hydrafit.app.core.domain.backup.BackupException
import com.hydrafit.app.core.domain.backup.BackupFailure
import com.hydrafit.app.core.domain.backup.BackupFile
import com.hydrafit.app.core.domain.backup.BackupFolderStore
import com.hydrafit.app.core.domain.backup.BackupMergeKeys
import com.hydrafit.app.core.domain.backup.BackupMergeSelection
import com.hydrafit.app.core.domain.backup.BackupRepository
import com.hydrafit.app.core.domain.backup.BackupRestPreferenceRecord
import com.hydrafit.app.core.domain.backup.BackupValidator
import com.hydrafit.app.core.domain.backup.ConfigureAutomaticBackupFolderUseCase
import com.hydrafit.app.core.domain.backup.DisableAutomaticBackupEncryptionUseCase
import com.hydrafit.app.core.domain.backup.EnableAutomaticBackupEncryptionUseCase
import com.hydrafit.app.core.domain.backup.ExportBackupUseCase
import com.hydrafit.app.core.domain.backup.FolderCapabilities
import com.hydrafit.app.core.domain.backup.FolderUnavailableReason
import com.hydrafit.app.core.domain.backup.RecordedBackupDocument
import com.hydrafit.app.core.domain.backup.RecoverAutomaticBackupEncryptionUseCase
import com.hydrafit.app.core.domain.backup.RunAutomaticBackupUseCase
import com.hydrafit.app.core.domain.backup.SetAutomaticBackupsEnabledUseCase
import com.hydrafit.app.core.domain.backup.StoredBackupDocument
import com.hydrafit.app.core.domain.backup.StoredBackupKey
import com.hydrafit.app.core.domain.backup.UnsupportedBackupCrypto
import com.hydrafit.app.core.domain.backup.VerifyBackupSnapshotUseCase
import com.hydrafit.app.core.domain.time.TimeProvider
import com.hydrafit.app.core.userdata.settings.AppVersionProvider
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
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

    @Test
    fun enablingEncryptionStoresTheKeyAndReportsIt() = runTest(dispatcher) {
        val keyStore = FakeBackupEncryptionKeyStore()
        val store = FakeAutomaticBackupStateStore(
            AutomaticBackupState(enabled = true, folderHandle = "content://f")
        )
        val viewModel = viewModel(stateStore = store, keyStore = keyStore)
        advanceUntilIdle()

        viewModel.encryptWithPassword("pw")
        advanceUntilIdle()

        assertEquals(AutomaticBackupStatus.EncryptionOn, viewModel.state.value.status)
        assertTrue(store.state.encryptionEnabled)
        assertFalse(viewModel.state.value.encryptionKeyMissing)
    }

    @Test
    fun disablingEncryptionClearsTheStoredKey() = runTest(dispatcher) {
        val keyStore = FakeBackupEncryptionKeyStore()
        keyStore.store(StoredBackupKey(salt = ByteArray(16), key = ByteArray(32)))
        val store = FakeAutomaticBackupStateStore(
            AutomaticBackupState(
                enabled = true,
                encryptionEnabled = true,
                folderHandle = "content://f"
            )
        )
        val viewModel = viewModel(stateStore = store, keyStore = keyStore)
        advanceUntilIdle()

        viewModel.disableEncryption()
        advanceUntilIdle()

        assertEquals(AutomaticBackupStatus.EncryptionOff, viewModel.state.value.status)
        assertFalse(store.state.encryptionEnabled)
        assertNull(keyStore.load())
    }

    @Test
    fun anUnsupportedPlatformRefusesEncryption() = runTest(dispatcher) {
        val store = FakeAutomaticBackupStateStore(
            AutomaticBackupState(enabled = true, folderHandle = "content://f")
        )
        val viewModel = viewModel(stateStore = store, crypto = UnsupportedBackupCrypto())
        advanceUntilIdle()

        assertFalse(viewModel.state.value.isEncryptionSupported)
        viewModel.encryptWithPassword("pw")
        advanceUntilIdle()

        assertEquals(AutomaticBackupStatus.EncryptionUnsupported, viewModel.state.value.status)
        assertFalse(store.state.encryptionEnabled)
    }

    @Test
    fun encryptionKeyMissingWhenEnabledWithoutStoredMaterial() = runTest(dispatcher) {
        val store = FakeAutomaticBackupStateStore(
            AutomaticBackupState(
                enabled = true,
                encryptionEnabled = true,
                folderHandle = "content://f"
            )
        )
        val viewModel = viewModel(stateStore = store)
        advanceUntilIdle()

        assertTrue(viewModel.state.value.encryptionEnabled)
        assertTrue(viewModel.state.value.encryptionKeyMissing)
    }

    @Test
    fun recoveryStoresTheKeyAgainAndReportsIt() = runTest(dispatcher) {
        val crypto = FakeBackupCrypto()
        val keyStore = FakeBackupEncryptionKeyStore()
        val sealed = encryptionFor(crypto, keyStore)
            .sealWithPassword("plain", "right".toCharArray())
        val folderStore = snapshotFolderStore("doc-1", sealed)
        val store = encryptedEnabledState()
        val viewModel = viewModel(
            stateStore = store,
            folderStore = folderStore,
            crypto = crypto,
            keyStore = keyStore
        )
        advanceUntilIdle()

        viewModel.recoverEncryption("right")
        advanceUntilIdle()

        assertEquals(AutomaticBackupStatus.EncryptionRecovered, viewModel.state.value.status)
        assertFalse(viewModel.state.value.encryptionKeyMissing)
        assertNotNull(keyStore.load())
    }

    @Test
    fun recoveryWithAWrongPasswordReportsIt() = runTest(dispatcher) {
        val crypto = FakeBackupCrypto()
        val keyStore = FakeBackupEncryptionKeyStore()
        val sealed = encryptionFor(crypto, keyStore)
            .sealWithPassword("plain", "right".toCharArray())
        val folderStore = snapshotFolderStore("doc-1", sealed)
        val viewModel = viewModel(
            stateStore = encryptedEnabledState(),
            folderStore = folderStore,
            crypto = crypto,
            keyStore = keyStore
        )
        advanceUntilIdle()

        viewModel.recoverEncryption("wrong")
        advanceUntilIdle()

        assertEquals(AutomaticBackupStatus.EncryptionWrongPassword, viewModel.state.value.status)
        assertTrue(viewModel.state.value.encryptionKeyMissing)
    }

    @Test
    fun recoveryWithoutAnEncryptedSnapshotReportsIt() = runTest(dispatcher) {
        val viewModel = viewModel(stateStore = encryptedEnabledState())
        advanceUntilIdle()

        viewModel.recoverEncryption("pw")
        advanceUntilIdle()

        assertEquals(AutomaticBackupStatus.EncryptionNoSnapshot, viewModel.state.value.status)
    }

    private fun snapshotFolderStore(documentId: String, sealed: String): FakeBackupFolderStore =
        FakeBackupFolderStore().apply { putDocument(documentId, sealed.encodeToByteArray()) }

    private fun encryptedEnabledState(): FakeAutomaticBackupStateStore =
        FakeAutomaticBackupStateStore(
            AutomaticBackupState(
                enabled = true,
                encryptionEnabled = true,
                folderHandle = "content://f",
                documents = listOf(
                    RecordedBackupDocument("doc-1", "auto.json", 1L, verified = true)
                )
            )
        )

    private fun viewModel(
        stateStore: FakeAutomaticBackupStateStore = FakeAutomaticBackupStateStore(),
        folderStore: FakeBackupFolderStore = FakeBackupFolderStore(),
        scheduler: FakeAutomaticBackupScheduler = FakeAutomaticBackupScheduler(),
        repository: BackupRepository = FakeAutomaticBackupRepository(),
        crypto: BackupCrypto = FakeBackupCrypto(),
        keyStore: BackupEncryptionKeyStore = FakeBackupEncryptionKeyStore()
    ): AutomaticBackupSettingsViewModel {
        val encryption = encryptionFor(crypto, keyStore)
        return AutomaticBackupSettingsViewModel(
            stateStore = stateStore,
            folderStore = folderStore,
            configureFolder = ConfigureAutomaticBackupFolderUseCase(folderStore, stateStore),
            enableBackups = SetAutomaticBackupsEnabledUseCase(folderStore, stateStore, scheduler),
            runBackup = RunAutomaticBackupUseCase(
                exportBackup = ExportBackupUseCase(repository, dispatcher),
                encryption = encryption,
                folderStore = folderStore,
                stateStore = stateStore,
                timeProvider = TimeProvider { 1L },
                dispatcher = dispatcher
            ),
            appVersionProvider = object : AppVersionProvider {
                override val versionName: String = "t"
            },
            encryption = encryption,
            keyStore = keyStore,
            enableEncryption = EnableAutomaticBackupEncryptionUseCase(
                encryption,
                keyStore,
                stateStore
            ),
            disableEncryptionUseCase = DisableAutomaticBackupEncryptionUseCase(
                keyStore,
                stateStore
            ),
            recoverEncryptionUseCase = RecoverAutomaticBackupEncryptionUseCase(
                encryption,
                keyStore,
                stateStore,
                folderStore
            )
        )
    }

    private fun encryptionFor(
        crypto: BackupCrypto,
        keyStore: BackupEncryptionKeyStore
    ): BackupEncryption = BackupEncryption(
        crypto = crypto,
        keyStore = keyStore,
        verifySnapshot = VerifyBackupSnapshotUseCase(BackupValidator(emptyBackupCatalog())),
        dispatcher = dispatcher
    )
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

    fun putDocument(documentId: String, bytes: ByteArray) {
        documents[documentId] = bytes
    }

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

/**
 * Deterministic, key-dependent test cipher: the same password round-trips, while a different
 * password derives a different key whose tag does not authenticate, mirroring the real primitive's
 * wrong-password failure without any cryptographic strength.
 */
private class FakeBackupCrypto : BackupCrypto {
    override val isSupported: Boolean = true

    override fun randomBytes(size: Int): ByteArray = ByteArray(size) { index -> index.toByte() }

    override fun deriveKey(
        password: CharArray,
        salt: ByteArray,
        iterations: Int,
        keyBytes: Int
    ): ByteArray = ByteArray(keyBytes) { index ->
        val pass = password[index % password.size].code
        (pass xor salt[index % salt.size].toInt() xor index).toByte()
    }

    override fun encrypt(key: ByteArray, nonce: ByteArray, plaintext: ByteArray): ByteArray =
        ByteArray(plaintext.size + 1) { index ->
            if (index == plaintext.size) {
                tag(key)
            } else {
                val mixed = plaintext[index].toInt() xor key[index % key.size].toInt() xor
                    nonce[index % nonce.size].toInt()
                mixed.toByte()
            }
        }

    override fun decrypt(key: ByteArray, nonce: ByteArray, ciphertext: ByteArray): ByteArray {
        val bodySize = ciphertext.size - 1
        if (bodySize < 0 || ciphertext[bodySize] != tag(key)) {
            throw BackupException(BackupFailure.ENCRYPTION_FAILED, "tag mismatch")
        }
        return ByteArray(bodySize) { index ->
            val mixed = ciphertext[index].toInt() xor key[index % key.size].toInt() xor
                nonce[index % nonce.size].toInt()
            mixed.toByte()
        }
    }

    private fun tag(key: ByteArray): Byte =
        key.fold(7) { acc, byte -> (acc * 31 + byte) % 251 }.toByte()
}

/** In-memory unattended material; empty until a set-password or recovery flow stores it. */
private class FakeBackupEncryptionKeyStore : BackupEncryptionKeyStore {
    private var stored: StoredBackupKey? = null

    override suspend fun load(): StoredBackupKey? = stored

    override suspend fun store(key: StoredBackupKey) {
        stored = key
    }

    override suspend fun clear() {
        stored = null
    }
}

private class FakeAutomaticBackupRepository : BackupRepository {
    override suspend fun export(appVersion: String, exportedAtMillis: Long): BackupFile =
        validBackupFile(appVersion, exportedAtMillis)

    override suspend fun restore(file: BackupFile) = Unit

    override suspend fun mergeKeys(): BackupMergeKeys = emptyMergeKeys()

    override suspend fun merge(selection: BackupMergeSelection) = Unit
}

private fun emptyMergeKeys(): BackupMergeKeys = BackupMergeKeys(
    sessionIds = emptySet(),
    bodyWeightDays = emptySet(),
    personalRecordExerciseIds = emptySet(),
    customExerciseIds = emptySet(),
    exerciseOverrideIds = emptySet(),
    exerciseNoteIds = emptySet(),
    exercisePreferenceIds = emptySet(),
    exerciseExclusionIds = emptySet()
)

private fun emptyBackupCatalog(): BackupCatalog = object : BackupCatalog {
    override fun seedExerciseIds(): Set<String> = emptySet()

    override fun builtInEquipmentIds(): Set<String> = emptySet()
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
