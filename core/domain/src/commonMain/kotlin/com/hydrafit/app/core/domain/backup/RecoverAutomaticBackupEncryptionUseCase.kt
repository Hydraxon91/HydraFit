package com.hydrafit.app.core.domain.backup

import kotlinx.coroutines.CancellationException

/**
 * Restores unattended encryption after keystore key loss: re-entering the backup password is
 * authenticated against a readable encrypted snapshot, and only then is the material stored again
 * under the (new) keystore key. Scans at most the retention window's newest recorded snapshots,
 * newest first, continuing past snapshots this password cannot open (a damaged file, or one sealed
 * with a previous password) until one authenticates. Plain snapshots are skipped; nothing is stored
 * unless the password actually opens an encrypted file, and only that file's own salt and key are
 * stored.
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
        var sawEncrypted = false
        for (document in candidates) {
            val text = try {
                folderStore.read(handle, document.documentId).decodeToString()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Throwable) {
                continue
            }
            if (!encryption.looksEncrypted(text)) continue
            sawEncrypted = true
            val key = try {
                encryption.deriveKeyForStoredFile(text, password)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: BackupException) {
                continue
            }
            keyStore.store(key)
            stateStore.update { it.copy(encryptionEnabled = true) }
            return AutomaticBackupEncryptionResult.Recovered
        }
        return if (sawEncrypted) {
            AutomaticBackupEncryptionResult.WrongPassword
        } else {
            AutomaticBackupEncryptionResult.NoEncryptedSnapshot
        }
    }
}
