package com.hydrafit.app.core.domain.backup

import kotlinx.coroutines.CancellationException

/**
 * Restores unattended encryption after keystore key loss: re-entering the backup password is
 * authenticated against the newest readable encrypted snapshot, and only then is the material
 * stored again under the (new) keystore key. Scans at most the retention window's newest recorded
 * snapshots, newest first. Plain snapshots are skipped; nothing is stored unless the password
 * actually opens an encrypted file.
 */
class RecoverAutomaticBackupEncryptionUseCase(
    private val encryption: BackupEncryption,
    private val keyStore: BackupEncryptionKeyStore,
    private val stateStore: AutomaticBackupStateStore,
    private val folderStore: BackupFolderStore
) {
    suspend operator fun invoke(password: CharArray): AutomaticBackupEncryptionResult {
        val state = stateStore.load()
        val handle = state.folderHandle
            ?: return AutomaticBackupEncryptionResult.NoEncryptedSnapshot
        val candidates = state.documents
            .filter { it.verified }
            .sortedByDescending { it.createdAtMillis }
            .take(AutomaticBackupPolicy.RETAINED_SNAPSHOTS)
        for (document in candidates) {
            val text = try {
                folderStore.read(handle, document.documentId).decodeToString()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Throwable) {
                continue
            }
            if (!encryption.looksEncrypted(text)) continue
            val key = try {
                encryption.deriveKeyForStoredFile(text, password)
            } catch (_: BackupException) {
                return AutomaticBackupEncryptionResult.WrongPassword
            }
            keyStore.store(key)
            stateStore.update { it.copy(encryptionEnabled = true) }
            return AutomaticBackupEncryptionResult.Recovered
        }
        return AutomaticBackupEncryptionResult.NoEncryptedSnapshot
    }
}
