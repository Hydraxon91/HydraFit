package com.hydrafit.app.core.domain.backup

/** A validated backup waiting for the next process start to be applied, before the first screen. */
data class PendingBackup(val payload: String, val appVersion: String, val stagedAtMillis: Long)

/** A failed staged apply, surfaced once in Settings and then cleared. */
data class BackupApplyError(
    val failure: BackupFailure,
    val message: String?,
    val occurredAtMillis: Long
)

/**
 * Persists the staged backup between the Settings confirmation and the next process start. The
 * application writes the payload here instead of replacing live data, so the replacement transaction
 * runs at startup when no other writer exists.
 */
interface BackupStagingRepository {
    /** Stores [payload] as the pending backup, replacing any earlier staged payload. */
    suspend fun stage(payload: String, appVersion: String, stagedAtMillis: Long)

    /** The pending backup, or null when nothing is staged. Does not clear it. */
    suspend fun staged(): PendingBackup?

    /** Removes the pending backup. */
    suspend fun clearStaged()

    /** Records the most recent apply failure for one-time display in Settings. */
    suspend fun recordApplyError(failure: BackupFailure, message: String?, occurredAtMillis: Long)

    /** The most recent apply failure, or null. Does not clear it. */
    suspend fun applyError(): BackupApplyError?

    /** Removes the recorded apply failure. */
    suspend fun clearApplyError()
}
