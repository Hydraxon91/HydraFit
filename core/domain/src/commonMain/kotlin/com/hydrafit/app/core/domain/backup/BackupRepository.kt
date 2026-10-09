package com.hydrafit.app.core.domain.backup

/**
 * Reads and writes the logical backup snapshot. The implementation reads every included section
 * inside one database transaction so an export is internally consistent.
 */
interface BackupRepository {
    /** Reads a consistent snapshot of all included user-owned data. Never mutates. */
    suspend fun export(appVersion: String, exportedAtMillis: Long): BackupFile
}
