package com.hydrafit.app.core.userdata.backup

/**
 * Reads and writes backup text against a user-chosen document handle. On Android the handle is a
 * `content://` SAF URI; platforms without a document surface report [isSupported] false and throw.
 */
interface BackupFileStore {
    val isSupported: Boolean

    suspend fun read(handle: String): String

    suspend fun write(handle: String, text: String)
}

/** Fallback binding for platforms with no backup document surface (iOS in 0.5.0). */
class UnsupportedBackupFileStore : BackupFileStore {
    override val isSupported: Boolean = false

    override suspend fun read(handle: String): String =
        throw UnsupportedOperationException("Backup files are not supported on this platform")

    override suspend fun write(handle: String, text: String) =
        throw UnsupportedOperationException("Backup files are not supported on this platform")
}
