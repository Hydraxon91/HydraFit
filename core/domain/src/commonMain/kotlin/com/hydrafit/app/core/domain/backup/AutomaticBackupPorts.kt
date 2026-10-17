package com.hydrafit.app.core.domain.backup

/** A snapshot document this app wrote in the user-chosen folder. */
data class StoredBackupDocument(val documentId: String, val displayName: String)

/** Why the chosen folder cannot host automatic snapshots. */
enum class FolderUnavailableReason {
    /** The persisted read/write permission is gone; the user must choose the folder again. */
    PERMISSION_LOST,

    /** The folder no longer exists at the recorded location. */
    FOLDER_MISSING,

    /** The provider does not support the read/create/delete operations retention requires. */
    UNSUPPORTED_PROVIDER
}

/** Whether the chosen folder can host automatic snapshots right now. */
sealed interface FolderCapabilities {
    data object Ready : FolderCapabilities

    data class Unavailable(val reason: FolderUnavailableReason) : FolderCapabilities
}

/**
 * Creates, reads and deletes snapshot documents in the user-chosen folder. The platform
 * implementation resolves the persisted folder grant. An operation that fails throws
 * [BackupException] with [BackupFailure.IO]; [delete] treats an already-missing document as success
 * so repeated pruning is safe.
 */
interface BackupFolderStore {
    /** The current capabilities of [handle]; a normally unavailable folder is reported, not thrown. */
    suspend fun capabilities(handle: String): FolderCapabilities

    /** Creates a new document. The provider's returned identity is authoritative from here on. */
    suspend fun create(handle: String, displayName: String, bytes: ByteArray): StoredBackupDocument

    /** Reads a previously created document back in full, bounded by the snapshot size limit. */
    suspend fun read(handle: String, documentId: String): ByteArray

    /** Deletes a document this app created; an already-missing document counts as deleted. */
    suspend fun delete(handle: String, documentId: String)
}

/** Persists the device-local automation state; a missing document reads as the disabled default. */
interface AutomaticBackupStateStore {
    suspend fun load(): AutomaticBackupState

    suspend fun save(state: AutomaticBackupState)
}
