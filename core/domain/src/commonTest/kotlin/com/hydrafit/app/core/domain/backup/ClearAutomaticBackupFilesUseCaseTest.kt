package com.hydrafit.app.core.domain.backup

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class ClearAutomaticBackupFilesUseCaseTest {

    @Test
    fun deletesRecordedDocumentsAndKeepsConfiguration() = runTest {
        val folder = ClearFolderStore()
        val state = ClearStateStore(
            AutomaticBackupState(
                enabled = true,
                encryptionEnabled = true,
                folderHandle = "folder-1",
                lastSuccessMillis = 99L,
                documents = listOf(recordedDoc("d1"), recordedDoc("d2", verified = false))
            )
        )

        val deleted = ClearAutomaticBackupFilesUseCase(folder, state)()

        assertEquals(2, deleted)
        assertEquals(listOf("folder-1" to "d1", "folder-1" to "d2"), folder.deleted)
        val after = state.load()
        assertEquals(emptyList(), after.documents)
        assertNull(after.lastSuccessMillis)
        assertNull(after.lastFailure)
        assertTrue(after.enabled)
        assertTrue(after.encryptionEnabled)
        assertEquals("folder-1", after.folderHandle)
    }

    @Test
    fun doesNothingWithoutAChosenFolder() = runTest {
        val folder = ClearFolderStore()
        val state = ClearStateStore(
            AutomaticBackupState(documents = listOf(recordedDoc("d1")))
        )

        assertEquals(0, ClearAutomaticBackupFilesUseCase(folder, state)())
        assertEquals(emptyList(), folder.deleted)
        assertEquals(1, state.load().documents.size)
    }

    @Test
    fun keepsTheLedgerWhenADeletionFails() = runTest {
        val folder = ClearFolderStore(failOn = "d2")
        val state = ClearStateStore(
            AutomaticBackupState(
                folderHandle = "folder-1",
                documents = listOf(recordedDoc("d1"), recordedDoc("d2"))
            )
        )

        assertFailsWith<BackupException> { ClearAutomaticBackupFilesUseCase(folder, state)() }

        assertEquals(listOf("folder-1" to "d1"), folder.deleted)
        assertEquals(2, state.load().documents.size)
    }
}

private fun recordedDoc(id: String, verified: Boolean = true) = RecordedBackupDocument(
    documentId = id,
    displayName = "$id.json",
    createdAtMillis = 1L,
    verified = verified
)

private class ClearFolderStore(private val failOn: String? = null) : BackupFolderStore {
    override val isSupported: Boolean = true
    val deleted = mutableListOf<Pair<String, String>>()

    override suspend fun capabilities(handle: String): FolderCapabilities = FolderCapabilities.Ready

    override suspend fun create(
        handle: String,
        displayName: String,
        bytes: ByteArray
    ): StoredBackupDocument = throw UnsupportedOperationException()

    override suspend fun read(handle: String, documentId: String): ByteArray =
        throw UnsupportedOperationException()

    override suspend fun delete(handle: String, documentId: String) {
        if (documentId == failOn) throw BackupException(BackupFailure.IO)
        deleted += handle to documentId
    }
}

private class ClearStateStore(private var state: AutomaticBackupState) :
    AutomaticBackupStateStore {
    override suspend fun load(): AutomaticBackupState = state

    override suspend fun update(
        change: (AutomaticBackupState) -> AutomaticBackupState
    ): AutomaticBackupState {
        state = change(state)
        return state
    }
}
