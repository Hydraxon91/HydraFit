package com.hydrafit.app.core.domain.backup

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Builds the backup JSON in memory, off the main thread. The caller supplies the app version (a
 * `:core:userdata` concern) and the export time; persistence to a user-selected file is a platform
 * adapter. The dispatcher is injectable so tests can run on a test scheduler.
 */
class ExportBackupUseCase(
    private val repository: BackupRepository,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default
) {
    suspend operator fun invoke(appVersion: String, exportedAtMillis: Long): String =
        withContext(dispatcher) {
            BackupJson.encode(repository.export(appVersion, exportedAtMillis))
        }
}
