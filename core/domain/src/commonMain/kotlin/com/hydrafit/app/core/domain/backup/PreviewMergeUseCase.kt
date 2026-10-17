package com.hydrafit.app.core.domain.backup

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Validates a chosen backup and computes the add-only records a merge would insert, without writing
 * anything, so the UI can show what merging adds, what stays local, and what the merge does not
 * carry, before the user confirms. The live-identity read and the selection run off the caller's
 * dispatcher ([Dispatchers.Default] by default), so a full identity read never blocks the main
 * thread.
 */
class PreviewMergeUseCase(
    private val preview: PreviewBackupUseCase,
    private val repository: BackupRepository,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default
) {
    suspend operator fun invoke(text: String): BackupMergeSelection = withContext(dispatcher) {
        BackupMergePolicy.select(preview(text), repository.mergeKeys())
    }
}
