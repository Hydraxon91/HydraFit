package com.hydrafit.app.core.domain.backup

/**
 * Deletes every automatic snapshot this app recorded in the user-chosen folder and clears the
 * document ledger, while keeping the automation configuration (enabled, encryption, folder grant).
 * Only recorded identities are deleted, so manual exports and foreign files are never touched. A
 * failed deletion propagates and the ledger is kept, so a retry stays safe — [BackupFolderStore.delete]
 * treats an already-missing document as success.
 */
class ClearAutomaticBackupFilesUseCase(
    private val folderStore: BackupFolderStore,
    private val stateStore: AutomaticBackupStateStore
) {
    /** Returns how many recorded snapshots were removed. */
    suspend operator fun invoke(): Int {
        val state = stateStore.load()
        val handle = state.folderHandle ?: return 0
        val documents = state.documents
        documents.forEach { document -> folderStore.delete(handle, document.documentId) }
        stateStore.update {
            it.copy(documents = emptyList(), lastSuccessMillis = null, lastFailure = null)
        }
        return documents.size
    }
}
