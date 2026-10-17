package com.hydrafit.app.core.domain.backup

/**
 * Validates a chosen backup and computes the add-only records a merge would insert, without writing
 * anything, so the UI can show what merging adds, what stays local, and what the merge does not
 * carry, before the user confirms.
 */
class PreviewMergeUseCase(
    private val preview: PreviewBackupUseCase,
    private val repository: BackupRepository
) {
    suspend operator fun invoke(text: String): BackupMergeSelection =
        BackupMergePolicy.select(preview(text), repository.mergeKeys())
}
