package com.hydrafit.app.core.domain.backup

import com.hydrafit.app.core.domain.time.TimeProvider
import kotlinx.coroutines.CancellationException

/**
 * Applies a staged backup at process start, before the first screen and before ordinary use, so the
 * replacement transaction never races a live writer. The payload was validated when it was staged,
 * and is validated again here before the single replacement write. On any failure the current data is
 * left intact, the staged payload is dropped (single-shot) and the reason is recorded for one-time
 * display in Settings.
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
            val file = preview(pending.payload)
            repository.restore(file)
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
