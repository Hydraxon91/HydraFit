package com.hydrafit.app.core.domain.backup

import kotlinx.coroutines.CancellationException

/**
 * Validates a user-chosen folder and, when it can host automatic snapshots, stores its handle.
 * Validation exercises the whole retention contract once at selection time: the folder must pass
 * the capability probe and a tiny probe document must be creatable **and** deletable, because
 * pruning depends on delete support. A folder that cannot do all of it is refused with its reason
 * and the stored selection is left unchanged, so picking a bad folder never breaks an existing
 * working setup. Selecting a different folder resets the snapshot ledger: recorded identities
 * describe the folder they were created in, and retention must never count or delete another
 * folder's files.
 */
class ConfigureAutomaticBackupFolderUseCase(
    private val folderStore: BackupFolderStore,
    private val stateStore: AutomaticBackupStateStore
) {
    suspend operator fun invoke(handle: String): FolderCapabilities {
        val capabilities = folderStore.capabilities(handle)
        if (capabilities is FolderCapabilities.Unavailable) return capabilities
        if (!probeWritable(handle)) {
            return FolderCapabilities.Unavailable(FolderUnavailableReason.UNSUPPORTED_PROVIDER)
        }
        val state = stateStore.load()
        if (state.folderHandle != handle) {
            stateStore.update {
                it.copy(folderHandle = handle, documents = emptyList(), lastFailure = null)
            }
        }
        return FolderCapabilities.Ready
    }

    /**
     * Creates and deletes one tiny probe document through the same port the runs use, so a provider
     * that accepts files but refuses deletions cannot be selected as a snapshot folder. The probe is
     * removed through the store's idempotent delete; if the provider refuses the delete, the probe
     * may remain (never recorded in any ledger, never pruned) and the folder is reported unsupported.
     */
    private suspend fun probeWritable(handle: String): Boolean {
        val document = try {
            folderStore.create(handle, PROBE_NAME, PROBE_CONTENT)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            return false
        }
        return try {
            folderStore.delete(handle, document.documentId)
            true
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            false
        }
    }

    private companion object {
        const val PROBE_NAME = "hydrafit-probe.json"
        val PROBE_CONTENT = "{}".encodeToByteArray()
    }
}
