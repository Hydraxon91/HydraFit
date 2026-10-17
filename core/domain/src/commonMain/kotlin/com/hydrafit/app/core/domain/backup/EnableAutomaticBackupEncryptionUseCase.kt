package com.hydrafit.app.core.domain.backup

/**
 * Turns on encrypted automatic backups: derives fresh unattended material from the password (a
 * random salt plus its PBKDF2 key), stores it through the platform key store and flips the stored
 * flag. The password itself is never persisted. On a platform without the crypto primitives this
 * refuses with [AutomaticBackupEncryptionResult.Unsupported] and stores nothing.
 */
class EnableAutomaticBackupEncryptionUseCase(
    private val encryption: BackupEncryption,
    private val keyStore: BackupEncryptionKeyStore,
    private val stateStore: AutomaticBackupStateStore
) {
    suspend operator fun invoke(password: CharArray): AutomaticBackupEncryptionResult {
        if (!encryption.isSupported) return AutomaticBackupEncryptionResult.Unsupported
        val key = encryption.createUnattendedKey(password)
        keyStore.store(key)
        stateStore.update { it.copy(encryptionEnabled = true) }
        return AutomaticBackupEncryptionResult.Enabled
    }
}
