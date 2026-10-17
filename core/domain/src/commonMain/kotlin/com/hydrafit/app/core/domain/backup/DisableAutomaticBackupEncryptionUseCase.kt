package com.hydrafit.app.core.domain.backup

/**
 * Turns off encrypted automatic backups: clears the unattended material and the stored flag.
 * Existing encrypted snapshots keep their own passwords and remain readable with them; future
 * automatic snapshots are plain again.
 */
class DisableAutomaticBackupEncryptionUseCase(
    private val keyStore: BackupEncryptionKeyStore,
    private val stateStore: AutomaticBackupStateStore
) {
    suspend operator fun invoke(): AutomaticBackupEncryptionResult {
        keyStore.clear()
        stateStore.update { it.copy(encryptionEnabled = false) }
        return AutomaticBackupEncryptionResult.Disabled
    }
}
