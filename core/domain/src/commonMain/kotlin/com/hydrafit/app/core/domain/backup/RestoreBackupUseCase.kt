package com.hydrafit.app.core.domain.backup

/**
 * Validates the whole payload first, then replaces the included user-owned data in one transaction.
 * A rejected file throws before any write, and a failure inside the transaction rolls the whole
 * replacement back, so the current data is left intact.
 */
class RestoreBackupUseCase(
    private val preview: PreviewBackupUseCase,
    private val repository: BackupRepository
) {
    suspend operator fun invoke(text: String) {
        val file = preview(text)
        repository.restore(file)
    }
}
