package com.hydrafit.app.core.domain.backup

/**
 * The encryption material stored for unattended automatic backups: the key derived from the
 * user's backup password plus the salt it was derived with. The plaintext password is never
 * stored; the material is protected by a platform keystore and only the automatic-backup run
 * needs it — manual exports and imports always derive from the entered password.
 */
data class StoredBackupKey(val salt: ByteArray, val key: ByteArray)

/** Persists the unattended encryption material; [load] returns null when none is stored. */
interface BackupEncryptionKeyStore {
    suspend fun load(): StoredBackupKey?

    suspend fun store(key: StoredBackupKey)

    suspend fun clear()
}

/** Fallback for platforms without an encryption key store (iOS in 0.9.4): no unattended material. */
class UnsupportedBackupEncryptionKeyStore : BackupEncryptionKeyStore {
    override suspend fun load(): StoredBackupKey? = null

    override suspend fun store(key: StoredBackupKey) =
        throw UnsupportedOperationException("Encrypted backups are not supported on this platform")

    override suspend fun clear() =
        throw UnsupportedOperationException("Encrypted backups are not supported on this platform")
}
