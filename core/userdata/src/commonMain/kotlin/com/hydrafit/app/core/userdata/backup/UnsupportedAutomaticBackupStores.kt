package com.hydrafit.app.core.userdata.backup

import com.hydrafit.app.core.domain.backup.AutomaticBackupState
import com.hydrafit.app.core.domain.backup.AutomaticBackupStateStore
import com.hydrafit.app.core.domain.backup.BackupFolderStore
import com.hydrafit.app.core.domain.backup.FolderCapabilities
import com.hydrafit.app.core.domain.backup.FolderUnavailableReason
import com.hydrafit.app.core.domain.backup.StoredBackupDocument

/** Fallback for platforms with no automatic-backup folder surface (iOS in 0.9.3). */
class UnsupportedBackupFolderStore : BackupFolderStore {
    override val isSupported: Boolean = false

    override suspend fun capabilities(handle: String): FolderCapabilities =
        FolderCapabilities.Unavailable(FolderUnavailableReason.UNSUPPORTED_PROVIDER)

    override suspend fun create(
        handle: String,
        displayName: String,
        bytes: ByteArray
    ): StoredBackupDocument =
        throw UnsupportedOperationException("Automatic backups are not supported on this platform")

    override suspend fun read(handle: String, documentId: String): ByteArray =
        throw UnsupportedOperationException("Automatic backups are not supported on this platform")

    override suspend fun delete(handle: String, documentId: String) =
        throw UnsupportedOperationException("Automatic backups are not supported on this platform")
}

/**
 * Fallback for platforms with no automatic-backup state surface (iOS in 0.9.3). Reads as the
 * disabled default so an app-start reconciliation is harmless here; a write throws, because any
 * path that would persist automation state is expected to be platform-gated before it runs.
 */
class UnsupportedAutomaticBackupStateStore : AutomaticBackupStateStore {
    override suspend fun load(): AutomaticBackupState = AutomaticBackupState()

    override suspend fun update(
        change: (AutomaticBackupState) -> AutomaticBackupState
    ): AutomaticBackupState =
        throw UnsupportedOperationException("Automatic backups are not supported on this platform")
}
