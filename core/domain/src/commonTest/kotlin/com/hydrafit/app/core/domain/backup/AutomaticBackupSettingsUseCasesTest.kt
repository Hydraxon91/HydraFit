package com.hydrafit.app.core.domain.backup

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class AutomaticBackupSettingsUseCasesTest {

    @Test
    fun configureStoresAReadyFolder() = runTest {
        val stateStore = FakeAutomaticBackupStateStore()
        val configure = ConfigureAutomaticBackupFolderUseCase(FakeBackupFolderStore(), stateStore)

        val result = configure("tree://a")

        assertEquals(FolderCapabilities.Ready, result)
        assertEquals("tree://a", stateStore.state.folderHandle)
    }

    @Test
    fun configureRefusesAnUnusableFolderAndKeepsThePreviousOne() = runTest {
        val stateStore = FakeAutomaticBackupStateStore(
            AutomaticBackupState(folderHandle = "tree://good")
        )
        val folderStore = FakeBackupFolderStore().apply {
            capabilities = FolderCapabilities.Unavailable(FolderUnavailableReason.PERMISSION_LOST)
        }
        val configure = ConfigureAutomaticBackupFolderUseCase(folderStore, stateStore)

        val result = configure("tree://bad")

        assertEquals(
            FolderCapabilities.Unavailable(FolderUnavailableReason.PERMISSION_LOST),
            result
        )
        assertEquals("tree://good", stateStore.state.folderHandle)
        assertEquals(0, stateStore.saves)
    }

    @Test
    fun selectingADifferentFolderResetsTheLedgerAndFailure() = runTest {
        val stateStore = FakeAutomaticBackupStateStore(
            AutomaticBackupState(
                folderHandle = "tree://old",
                lastFailure = AutomaticBackupFailureRecord(
                    AutomaticBackupFailureKind.WRITE_FAILED,
                    1L
                ),
                documents = listOf(RecordedBackupDocument("d1", "a.json", 1L, verified = true))
            )
        )
        val configure = ConfigureAutomaticBackupFolderUseCase(FakeBackupFolderStore(), stateStore)

        configure("tree://new")

        val state = stateStore.state
        assertEquals("tree://new", state.folderHandle)
        assertEquals(emptyList(), state.documents)
        assertNull(state.lastFailure)
    }

    @Test
    fun selectingTheSameFolderKeepsTheLedger() = runTest {
        val documents = listOf(RecordedBackupDocument("d1", "a.json", 1L, verified = true))
        val stateStore = FakeAutomaticBackupStateStore(
            AutomaticBackupState(folderHandle = "tree://same", documents = documents)
        )
        val configure = ConfigureAutomaticBackupFolderUseCase(FakeBackupFolderStore(), stateStore)

        configure("tree://same")

        assertEquals(documents, stateStore.state.documents)
        assertEquals(0, stateStore.saves)
    }

    @Test
    fun enablingRequiresAConfiguredFolder() = runTest {
        val stateStore = FakeAutomaticBackupStateStore()
        val enable = SetAutomaticBackupsEnabledUseCase(FakeBackupFolderStore(), stateStore)

        assertEquals(AutomaticBackupEnableResult.FolderNotConfigured, enable(true))
        assertFalse(stateStore.state.enabled)
    }

    @Test
    fun enablingRequiresACurrentlyUsableFolder() = runTest {
        val stateStore = FakeAutomaticBackupStateStore(
            AutomaticBackupState(folderHandle = "tree://a")
        )
        val folderStore = FakeBackupFolderStore().apply {
            capabilities = FolderCapabilities.Unavailable(FolderUnavailableReason.FOLDER_MISSING)
        }
        val enable = SetAutomaticBackupsEnabledUseCase(folderStore, stateStore)

        assertEquals(
            AutomaticBackupEnableResult.FolderUnavailable(FolderUnavailableReason.FOLDER_MISSING),
            enable(true)
        )
        assertFalse(stateStore.state.enabled)
    }

    @Test
    fun enablingWithAReadyFolderTurnsAutomationOn() = runTest {
        val stateStore = FakeAutomaticBackupStateStore(
            AutomaticBackupState(folderHandle = "tree://a")
        )
        val enable = SetAutomaticBackupsEnabledUseCase(FakeBackupFolderStore(), stateStore)

        assertEquals(AutomaticBackupEnableResult.Enabled, enable(true))
        assertTrue(stateStore.state.enabled)
    }

    @Test
    fun disablingKeepsEveryRecordedDocument() = runTest {
        val documents = listOf(RecordedBackupDocument("d1", "a.json", 1L, verified = true))
        val stateStore = FakeAutomaticBackupStateStore(
            AutomaticBackupState(enabled = true, folderHandle = "tree://a", documents = documents)
        )
        val enable = SetAutomaticBackupsEnabledUseCase(FakeBackupFolderStore(), stateStore)

        assertEquals(AutomaticBackupEnableResult.Disabled, enable(false))
        assertFalse(stateStore.state.enabled)
        assertEquals(documents, stateStore.state.documents)
    }
}
