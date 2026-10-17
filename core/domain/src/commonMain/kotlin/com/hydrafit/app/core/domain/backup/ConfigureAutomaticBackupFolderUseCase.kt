package com.hydrafit.app.core.domain.backup

/**
 * Validates a user-chosen folder and, when it can host automatic snapshots, stores its handle. A
 * folder that cannot read, create and delete is refused with its reason and the stored selection is
 * left unchanged, so picking a bad folder never breaks an existing working setup. Selecting a
 * different folder resets the snapshot ledger: recorded identities describe the folder they were
 * created in, and retention must never count or delete another folder's files.
 */
class ConfigureAutomaticBackupFolderUseCase(
    private val folderStore: BackupFolderStore,
    private val stateStore: AutomaticBackupStateStore
) {
    suspend operator fun invoke(handle: String): FolderCapabilities {
        val capabilities = folderStore.capabilities(handle)
        if (capabilities is FolderCapabilities.Ready) {
            val state = stateStore.load()
            if (state.folderHandle != handle) {
                stateStore.save(
                    state.copy(folderHandle = handle, documents = emptyList(), lastFailure = null)
                )
            }
        }
        return capabilities
    }
}
