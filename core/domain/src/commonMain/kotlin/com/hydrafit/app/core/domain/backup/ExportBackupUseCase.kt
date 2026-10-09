package com.hydrafit.app.core.domain.backup

/**
 * Builds the backup JSON in memory. The caller supplies the app version (a `:core:userdata`
 * concern) and the export time; persistence to a user-selected file is a platform adapter.
 */
class ExportBackupUseCase(private val repository: BackupRepository) {
    suspend operator fun invoke(appVersion: String, exportedAtMillis: Long): String {
        val file = repository.export(appVersion, exportedAtMillis)
        val text = BackupJson.encode(file)
        if (text.encodeToByteArray().size.toLong() > BackupJson.MAX_BYTES) {
            throw BackupException(BackupFailure.TOO_LARGE)
        }
        return text
    }
}
