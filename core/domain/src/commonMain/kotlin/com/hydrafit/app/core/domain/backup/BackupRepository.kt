package com.hydrafit.app.core.domain.backup

/**
 * Reads and writes the logical backup snapshot. The implementation reads every included section
 * inside one database transaction for a consistent export, replaces all included user-owned data
 * inside one transaction on restore, and applies an add-only merge selection inside one transaction.
 * The caller validates before [restore] or [merge].
 */
interface BackupRepository {
    /** Reads a consistent snapshot of all included user-owned data. Never mutates. */
    suspend fun export(appVersion: String, exportedAtMillis: Long): BackupFile

    /** Replaces all included user-owned data with [file] in one transaction, preserving ids. */
    suspend fun restore(file: BackupFile)

    /** The identities the live install already holds, read for insert-if-absent merge selection. */
    suspend fun mergeKeys(): BackupMergeKeys

    /** Applies [selection] in one transaction; a failure leaves current data intact. */
    suspend fun merge(selection: BackupMergeSelection)
}
