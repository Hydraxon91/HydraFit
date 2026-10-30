package com.hydrafit.app.feature.settings

import com.hydrafit.app.core.domain.backup.AutomaticBackupFailureKind
import com.hydrafit.app.core.domain.backup.AutomaticBackupState
import com.hydrafit.app.core.domain.backup.AutomaticBackupStateStore
import com.hydrafit.app.core.domain.backup.BackupCatalog
import com.hydrafit.app.core.domain.backup.BackupEncryption
import com.hydrafit.app.core.domain.backup.BackupEncryptionKeyStore
import com.hydrafit.app.core.domain.backup.BackupFile
import com.hydrafit.app.core.domain.backup.BackupFolderStore
import com.hydrafit.app.core.domain.backup.BackupMergeKeys
import com.hydrafit.app.core.domain.backup.BackupMergeSelection
import com.hydrafit.app.core.domain.backup.BackupRepository
import com.hydrafit.app.core.domain.backup.BackupValidator
import com.hydrafit.app.core.domain.backup.ClearAutomaticBackupFilesUseCase
import com.hydrafit.app.core.domain.backup.ExportBackupUseCase
import com.hydrafit.app.core.domain.backup.FolderCapabilities
import com.hydrafit.app.core.domain.backup.RecordedBackupDocument
import com.hydrafit.app.core.domain.backup.RunAutomaticBackupUseCase
import com.hydrafit.app.core.domain.backup.StoredBackupDocument
import com.hydrafit.app.core.domain.backup.StoredBackupKey
import com.hydrafit.app.core.domain.backup.UnsupportedBackupCrypto
import com.hydrafit.app.core.domain.backup.VerifyBackupSnapshotUseCase
import com.hydrafit.app.core.domain.storage.ClearDataCategoryUseCase
import com.hydrafit.app.core.domain.storage.DataCategory
import com.hydrafit.app.core.domain.storage.DataCountKind
import com.hydrafit.app.core.domain.storage.DataManagementRepository
import com.hydrafit.app.core.domain.storage.ExerciseDataClearException
import com.hydrafit.app.core.domain.storage.ExerciseDataClearFailure
import com.hydrafit.app.core.domain.storage.StorageUsagePort
import com.hydrafit.app.core.domain.storage.StorageUsageUseCase
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
class StorageDataViewModelTest {

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
    fun loadsUsageOnInit() = runTest(dispatcher) {
        val viewModel = viewModel()

        advanceUntilIdle()

        val usage = assertNotNull(viewModel.state.value.usage)
        assertEquals(4096L, usage.databaseBytes)
        assertEquals(2048L, usage.automaticBackupBytes)
        assertEquals(2, usage.automaticBackupFileCount)
    }

    @Test
    fun routesEachDatabaseCategoryToTheClearUseCase() = runTest(dispatcher) {
        val repository = StorageTestDataRepository()
        val viewModel = viewModel(repository = repository)
        advanceUntilIdle()

        val categories = listOf(
            DataCategory.WORKOUT_HISTORY,
            DataCategory.PLANNING,
            DataCategory.BODY_AND_RECORDS,
            DataCategory.TRAINING_SETUP,
            DataCategory.EXERCISE_DATA
        )
        categories.forEach { category ->
            viewModel.request(category)
            viewModel.confirm()
            advanceUntilIdle()
        }

        assertEquals(categories, repository.cleared)
    }

    @Test
    fun routesBackupFilesToTheSnapshotUseCase() = runTest(dispatcher) {
        val folderStore = StorageTestFolderStore()
        val stateStore = StorageTestBackupStateStore(
            AutomaticBackupState(
                folderHandle = "content://f",
                documents = listOf(
                    RecordedBackupDocument("a", "a.json", 1L, verified = true),
                    RecordedBackupDocument("b", "b.json", 2L, verified = true)
                )
            )
        )
        val viewModel = viewModel(folderStore = folderStore, stateStore = stateStore)
        advanceUntilIdle()

        viewModel.request(DataCategory.BACKUP_FILES)
        viewModel.confirm()
        advanceUntilIdle()

        assertEquals(listOf("a", "b"), folderStore.deleted)
        assertTrue(stateStore.state.documents.isEmpty())
        assertEquals(StorageDataStatus.BackupFilesRemoved(2), viewModel.state.value.status)
    }

    @Test
    fun allDataNeedsTheTypedWord() = runTest(dispatcher) {
        val repository = StorageTestDataRepository()
        val viewModel = viewModel(repository = repository)
        advanceUntilIdle()

        viewModel.request(DataCategory.ALL_DATA)
        viewModel.typedConfirmation("wrong")
        viewModel.confirm()
        advanceUntilIdle()
        assertTrue(repository.cleared.isEmpty())
        assertEquals(DataCategory.ALL_DATA, viewModel.state.value.pending)

        viewModel.typedConfirmation("remove")
        viewModel.confirm()
        advanceUntilIdle()
        assertEquals(listOf(DataCategory.ALL_DATA), repository.cleared)
    }

    @Test
    fun aConfirmedRemovalRefreshesUsage() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.request(DataCategory.WORKOUT_HISTORY)
        viewModel.confirm()
        advanceUntilIdle()

        val usage = assertNotNull(viewModel.state.value.usage)
        val history = usage.categories.first { it.category == DataCategory.WORKOUT_HISTORY }
        assertEquals(0, history.counts.first { it.kind == DataCountKind.SESSIONS }.count)
    }

    @Test
    fun aReferencedExerciseRemovalIsReportedAsBlockedAndRemovesNothing() = runTest(dispatcher) {
        val repository = StorageTestDataRepository(
            failure = ExerciseDataClearException(
                ExerciseDataClearFailure.CUSTOM_EXERCISES_REFERENCED
            )
        )
        val viewModel = viewModel(repository = repository)
        advanceUntilIdle()
        val usageBefore = assertNotNull(viewModel.state.value.usage)

        viewModel.request(DataCategory.EXERCISE_DATA)
        viewModel.confirm()
        advanceUntilIdle()

        assertEquals(
            StorageDataStatus.CategoryBlocked(ExerciseDataClearFailure.CUSTOM_EXERCISES_REFERENCED),
            viewModel.state.value.status
        )
        assertNull(viewModel.state.value.pending)
        assertFalse(viewModel.state.value.inProgress)
        assertEquals(listOf(DataCategory.EXERCISE_DATA), repository.cleared)
        assertEquals(usageBefore, viewModel.state.value.usage)
    }

    @Test
    fun aGenericRemovalFailureIsStillReportedAsFailed() = runTest(dispatcher) {
        val repository = StorageTestDataRepository(failure = IllegalStateException("boom"))
        val viewModel = viewModel(repository = repository)
        advanceUntilIdle()

        viewModel.request(DataCategory.WORKOUT_HISTORY)
        viewModel.confirm()
        advanceUntilIdle()

        assertEquals(StorageDataStatus.Failed(null), viewModel.state.value.status)
    }

    @Test
    fun backUpNowFailureIsSurfacedAndTheDialogStaysOpen() = runTest(dispatcher) {
        val stateStore = StorageTestBackupStateStore(
            AutomaticBackupState(enabled = true, folderHandle = null)
        )
        val viewModel = viewModel(stateStore = stateStore)
        advanceUntilIdle()

        viewModel.request(DataCategory.ALL_DATA)
        viewModel.backUpNow()
        advanceUntilIdle()

        assertEquals(
            StorageBackupStatus.Failed(AutomaticBackupFailureKind.FOLDER_NOT_CONFIGURED),
            viewModel.state.value.backupStatus
        )
        assertEquals(DataCategory.ALL_DATA, viewModel.state.value.pending)
    }

    @Test
    fun cancelClearsThePendingRequest() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.request(DataCategory.WORKOUT_HISTORY)
        viewModel.cancel()

        assertNull(viewModel.state.value.pending)
        assertFalse(viewModel.state.value.inProgress)
    }

    private fun viewModel(
        repository: StorageTestDataRepository = StorageTestDataRepository(),
        usagePort: StorageTestUsagePort = StorageTestUsagePort(),
        folderStore: StorageTestFolderStore = StorageTestFolderStore(),
        stateStore: StorageTestBackupStateStore = StorageTestBackupStateStore()
    ): StorageDataViewModel = StorageDataViewModel(
        storageUsage = StorageUsageUseCase(repository, usagePort),
        clearCategory = ClearDataCategoryUseCase(repository),
        clearBackupFiles = ClearAutomaticBackupFilesUseCase(folderStore, stateStore),
        runBackup = runBackupUseCase(folderStore, stateStore),
        appVersionProvider = object : AppVersionProvider {
            override val versionName: String = "t"
        },
        dispatcher = dispatcher
    )

    /**
     * A real automation use case over fakes. The back-up-now tests stop before the export stage, so
     * the export and encryption collaborators are never exercised and may fail loudly if reached.
     */
    private fun runBackupUseCase(
        folderStore: BackupFolderStore,
        stateStore: AutomaticBackupStateStore
    ): RunAutomaticBackupUseCase = RunAutomaticBackupUseCase(
        exportBackup = ExportBackupUseCase(StorageTestBackupRepository(), dispatcher),
        encryption = BackupEncryption(
            crypto = UnsupportedBackupCrypto(),
            keyStore = StorageTestKeyStore(),
            verifySnapshot = VerifyBackupSnapshotUseCase(BackupValidator(StorageTestCatalog())),
            dispatcher = dispatcher
        ),
        folderStore = folderStore,
        stateStore = stateStore,
        timeProvider = TimeProvider { 1L },
        dispatcher = dispatcher
    )
}

private fun storageTestCounts(): MutableMap<DataCountKind, Int> = mutableMapOf(
    DataCountKind.SESSIONS to 3,
    DataCountKind.SETS to 10,
    DataCountKind.SESSION_NOTES to 1,
    DataCountKind.CARDIO_ACTIVITIES to 4,
    DataCountKind.ACCEPTED_PLANS to 1,
    DataCountKind.ROUTINES to 2,
    DataCountKind.ACTIVATIONS to 1,
    DataCountKind.SCHEDULED_WORKOUTS to 4,
    DataCountKind.BODY_WEIGHT_MEASUREMENTS to 5,
    DataCountKind.PERSONAL_RECORDS to 2,
    DataCountKind.EQUIPMENT_PROFILES to 1,
    DataCountKind.CUSTOM_EXERCISES to 2
)

private class StorageTestDataRepository(
    var counts: MutableMap<DataCountKind, Int> = storageTestCounts(),
    var failure: Throwable? = null
) : DataManagementRepository {
    val cleared = mutableListOf<DataCategory>()

    override suspend fun counts(): Map<DataCountKind, Int> = counts.toMap()

    override suspend fun clear(category: DataCategory) {
        cleared += category
        failure?.let { throw it }
        if (category == DataCategory.ALL_DATA) {
            counts.keys.forEach { counts[it] = 0 }
        } else {
            StorageUsageUseCase.KINDS[category]?.forEach { counts[it] = 0 }
        }
    }
}

private class StorageTestUsagePort(
    var databaseBytes: Long = 4096L,
    var automaticBackupBytes: Long? = 2048L,
    var automaticBackupFileCount: Int? = 2
) : StorageUsagePort {
    override suspend fun databaseBytes(): Long = databaseBytes

    override suspend fun automaticBackupBytes(): Long? = automaticBackupBytes

    override suspend fun automaticBackupFileCount(): Int? = automaticBackupFileCount
}

private class StorageTestFolderStore : BackupFolderStore {
    override val isSupported: Boolean = true
    val deleted = mutableListOf<String>()

    override suspend fun capabilities(handle: String): FolderCapabilities = FolderCapabilities.Ready

    override suspend fun create(
        handle: String,
        displayName: String,
        bytes: ByteArray
    ): StoredBackupDocument = error("The back-up-now tests never reach the create stage")

    override suspend fun read(handle: String, documentId: String): ByteArray =
        error("The back-up-now tests never reach the read stage")

    override suspend fun delete(handle: String, documentId: String) {
        deleted += documentId
    }
}

private class StorageTestBackupStateStore(
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

private class StorageTestBackupRepository : BackupRepository {
    override suspend fun export(appVersion: String, exportedAtMillis: Long): BackupFile =
        error("The back-up-now tests never reach the export stage")

    override suspend fun restore(file: BackupFile) = error("Not used")

    override suspend fun mergeKeys(): BackupMergeKeys = error("Not used")

    override suspend fun merge(selection: BackupMergeSelection) = error("Not used")
}

private class StorageTestKeyStore : BackupEncryptionKeyStore {
    override suspend fun load(): StoredBackupKey? = null

    override suspend fun store(key: StoredBackupKey) = Unit

    override suspend fun clear() = Unit
}

private class StorageTestCatalog : BackupCatalog {
    override fun seedExerciseIds(): Set<String> = emptySet()

    override fun builtInEquipmentIds(): Set<String> = emptySet()
}
