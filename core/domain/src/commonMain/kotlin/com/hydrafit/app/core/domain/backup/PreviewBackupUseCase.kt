package com.hydrafit.app.core.domain.backup

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Reads and fully validates a backup file off the main thread without writing anything, so the UI can
 * show what a restore would replace. Maps every rejected file to a typed [BackupFailure], including
 * the resource limits enforced by [BackupJson]. The dispatcher is injectable for tests.
 */
class PreviewBackupUseCase(
    private val validator: BackupValidator,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default
) {
    suspend operator fun invoke(text: String): BackupFile = withContext(dispatcher) {
        BackupJson.decode(text).also { validator.validate(it) }
    }
}
