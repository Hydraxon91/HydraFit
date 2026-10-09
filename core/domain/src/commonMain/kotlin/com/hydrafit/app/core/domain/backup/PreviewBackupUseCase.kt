package com.hydrafit.app.core.domain.backup

/**
 * Reads and fully validates a backup file without writing anything, so the UI can show what a
 * restore would replace. Maps every rejected file to a typed [BackupFailure].
 */
class PreviewBackupUseCase(private val validator: BackupValidator) {
    operator fun invoke(text: String): BackupFile {
        if (text.encodeToByteArray().size.toLong() > BackupJson.MAX_BYTES) {
            throw BackupException(BackupFailure.TOO_LARGE)
        }
        val file = BackupJson.decode(text)
        validator.validate(file)
        return file
    }
}
