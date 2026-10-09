package com.hydrafit.app.core.domain.backup

/**
 * Reads and writes the logical backup snapshot. The implementation reads every included section
 * inside one database transaction for a consistent export, and replaces all included user-owned data
 * inside one transaction on restore. The caller validates before [restore].
 */
interface BackupRepository {
    /** Reads a consistent snapshot of all included user-owned data. Never mutates. */
    suspend fun export(appVersion: String, exportedAtMillis: Long): BackupFile

    /** Replaces all included user-owned data with [file] in one transaction, preserving ids. */
    suspend fun restore(file: BackupFile)
}
