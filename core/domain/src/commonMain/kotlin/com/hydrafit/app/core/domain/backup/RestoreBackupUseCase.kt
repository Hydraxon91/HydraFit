package com.hydrafit.app.core.domain.backup

/**
 * Validates a chosen backup now and stages it for the next process start, when [ApplyStagedBackupUseCase]
 * either replaces the data or merges the selection inside one transaction before the first screen.
 * The staged text is a [StagedBackupDocument] so the apply mode travels with the payload; validating
 * here fails fast on an invalid file, and the payload is validated again at apply time.
 * [consumePendingApplyError] surfaces a failure from a previous startup apply once, for display in
 * Settings.
 */
class RestoreBackupUseCase(
    private val preview: PreviewBackupUseCase,
    private val staging: BackupStagingRepository
) {
    suspend operator fun invoke(text: String, appVersion: String, stagedAtMillis: Long) {
        stageValidated(text, appVersion, stagedAtMillis, BackupApplyMode.REPLACE)
    }

    /** Validates and stages [text] as a merge apply instead of a replacement. */
    suspend fun stageMerge(text: String, appVersion: String, stagedAtMillis: Long) {
        stageValidated(text, appVersion, stagedAtMillis, BackupApplyMode.MERGE)
    }

    private suspend fun stageValidated(
        text: String,
        appVersion: String,
        stagedAtMillis: Long,
        mode: BackupApplyMode
    ) {
        preview(text)
        staging.stage(
            StagedBackupDocumentJson.encode(mode, text),
            appVersion,
            stagedAtMillis
        )
    }

    /** Returns and clears the one-time failure recorded by a staged apply at startup, if any. */
    suspend fun consumePendingApplyError(): BackupFailure? {
        val error = staging.applyError() ?: return null
        staging.clearApplyError()
        return error.failure
    }
}
