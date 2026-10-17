package com.hydrafit.app.core.userdata.storage

import android.content.Context
import com.hydrafit.app.core.domain.backup.AutomaticBackupStateStore
import com.hydrafit.app.core.domain.backup.BackupFolderStore
import com.hydrafit.app.core.domain.storage.StorageUsagePort

/**
 * Reports the database file size and the automatic-snapshot folder usage on Android. The database
 * size is the main file's length (the write-ahead log, if present, is not included); snapshot bytes
 * sum the recorded documents' read-back sizes and are null when the provider cannot report them, so
 * the UI labels an unavailable size instead of guessing.
 */
class AndroidStorageUsagePort(
    private val context: Context,
    private val stateStore: AutomaticBackupStateStore,
    private val folderStore: BackupFolderStore
) : StorageUsagePort {

    override suspend fun databaseBytes(): Long {
        val file = context.getDatabasePath(DATABASE_NAME)
        return if (file.exists()) file.length() else 0L
    }

    override suspend fun automaticBackupBytes(): Long? {
        if (!folderStore.isSupported) return null
        val state = stateStore.load()
        val handle = state.folderHandle ?: return 0L
        val sizes = state.documents.map { document ->
            runCatching { folderStore.read(handle, document.documentId).size.toLong() }.getOrNull()
        }
        if (sizes.any { it == null }) return null
        return sizes.filterNotNull().sum()
    }

    override suspend fun automaticBackupFileCount(): Int? {
        if (!folderStore.isSupported) return null
        return stateStore.load().documents.size
    }

    private companion object {
        /** The SQLDelight database file name used by the Android driver factory. */
        const val DATABASE_NAME = "hydrafit.db"
    }
}
