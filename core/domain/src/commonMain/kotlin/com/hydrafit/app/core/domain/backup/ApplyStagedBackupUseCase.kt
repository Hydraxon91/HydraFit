package com.hydrafit.app.core.domain.backup

import com.hydrafit.app.core.domain.time.TimeProvider
import kotlinx.coroutines.CancellationException

/**
 * Applies a staged backup at process start, before the first screen and before ordinary use, so the
 * replacement or merge transaction never races a live writer. The staged document carries the apply
 * mode: replace restores the file, merge reads the live identities and applies only the add-only
 * selection. The payload was validated when it was staged, and is validated again here before the
 * single write. On any failure the current data is left intact, the staged payload is dropped
 * (single-shot) and the reason is recorded for one-time display in Settings.
 */
class ApplyStagedBackupUseCase(
    private val preview: PreviewBackupUseCase,
    private val repository: BackupRepository,
    private val staging: BackupStagingRepository,
    private val timeProvider: TimeProvider
) {
    suspend operator fun invoke() {
        val pending = staging.staged() ?: return
        try {
            val document = StagedBackupDocumentJson.decodeOrLegacy(pending.payload)
            val file = preview(document.payload)
            when (document.mode) {
                BackupApplyMode.REPLACE -> repository.restore(file)
                BackupApplyMode.MERGE -> repository.merge(
                    BackupMergePolicy.select(file, repository.mergeKeys())
                )
            }
            staging.clearStaged()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (failure: BackupException) {
            staging.recordApplyError(failure.failure, failure.message, timeProvider.nowMillis())
            staging.clearStaged()
        } catch (error: Exception) {
            staging.recordApplyError(BackupFailure.IO, error.message, timeProvider.nowMillis())
            staging.clearStaged()
        }
    }
}
