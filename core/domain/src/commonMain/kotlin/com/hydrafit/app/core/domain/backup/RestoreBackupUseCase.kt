package com.hydrafit.app.core.domain.backup

/**
 * Validates a chosen backup now and stages it for the next process start, when [ApplyStagedBackupUseCase]
 * replaces the data inside one transaction before the first screen. Validating here fails fast on an
 * invalid file; the staged text is validated again at apply time. [consumePendingApplyError] surfaces a
 * failure from a previous startup apply once, for display in Settings.
 */
class RestoreBackupUseCase(
    private val preview: PreviewBackupUseCase,
    private val staging: BackupStagingRepository
) {
    suspend operator fun invoke(text: String, appVersion: String, stagedAtMillis: Long) {
        preview(text)
        staging.stage(text, appVersion, stagedAtMillis)
    }

    /** Returns and clears the one-time failure recorded by a staged apply at startup, if any. */
    suspend fun consumePendingApplyError(): BackupFailure? {
        val error = staging.applyError() ?: return null
        staging.clearApplyError()
        return error.failure
    }
}
