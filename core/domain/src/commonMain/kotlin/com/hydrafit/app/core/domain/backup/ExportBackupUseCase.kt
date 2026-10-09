package com.hydrafit.app.core.domain.backup

/**
 * Builds the backup JSON in memory. The caller supplies the app version (a `:core:userdata`
 * concern) and the export time; persistence to a user-selected file is a platform adapter.
 */
class ExportBackupUseCase(private val repository: BackupRepository) {
    suspend operator fun invoke(appVersion: String, exportedAtMillis: Long): String {
        val file = repository.export(appVersion, exportedAtMillis)
        return BackupJson.encode(file)
    }
}
