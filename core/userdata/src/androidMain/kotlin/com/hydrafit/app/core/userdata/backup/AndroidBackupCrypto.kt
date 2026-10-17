package com.hydrafit.app.core.userdata.backup

import android.os.Build
import com.hydrafit.app.core.domain.backup.BACKUP_ENVELOPE_GCM_TAG_BITS
import com.hydrafit.app.core.domain.backup.BackupCrypto
import com.hydrafit.app.core.domain.backup.BackupException
import com.hydrafit.app.core.domain.backup.BackupFailure
import java.security.GeneralSecurityException
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * The platform primitives for encrypted backups: `SecureRandom`, PBKDF2-HMAC-SHA256 and
 * AES-256-GCM with a 128-bit tag. Native PBKDF2-HMAC-SHA256 arrived in Android 8.0, so
 * [isSupported] gates every encrypted flow below that; nothing weaker substitutes and no
 * third-party crypto is introduced.
 */
class AndroidBackupCrypto : BackupCrypto {
    override val isSupported: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O

    override fun randomBytes(size: Int): ByteArray =
        ByteArray(size).also { SecureRandom().nextBytes(it) }

    override fun deriveKey(
        password: CharArray,
        salt: ByteArray,
        iterations: Int,
        keyBytes: Int
    ): ByteArray {
        val spec = PBEKeySpec(password, salt, iterations, keyBytes * 8)
        return try {
            SecretKeyFactory.getInstance(KEY_DERIVATION).generateSecret(spec).encoded
        } catch (e: GeneralSecurityException) {
            throw BackupException(BackupFailure.ENCRYPTION_FAILED, e.message)
        } finally {
            spec.clearPassword()
        }
    }

    override fun encrypt(key: ByteArray, nonce: ByteArray, plaintext: ByteArray): ByteArray = try {
        cipher(Cipher.ENCRYPT_MODE, key, nonce).doFinal(plaintext)
    } catch (e: GeneralSecurityException) {
        throw BackupException(BackupFailure.ENCRYPTION_FAILED, e.message)
    }

    override fun decrypt(key: ByteArray, nonce: ByteArray, ciphertext: ByteArray): ByteArray = try {
        cipher(Cipher.DECRYPT_MODE, key, nonce).doFinal(ciphertext)
    } catch (_: AEADBadTagException) {
        throw BackupException(BackupFailure.ENCRYPTION_FAILED)
    } catch (e: GeneralSecurityException) {
        throw BackupException(BackupFailure.ENCRYPTION_FAILED, e.message)
    }

    private fun cipher(mode: Int, key: ByteArray, nonce: ByteArray): Cipher =
        Cipher.getInstance(CIPHER).apply {
            init(
                mode,
                SecretKeySpec(key, KEY_ALGORITHM),
                GCMParameterSpec(BACKUP_ENVELOPE_GCM_TAG_BITS, nonce)
            )
        }

    private companion object {
        const val KEY_DERIVATION = "PBKDF2WithHmacSHA256"
        const val CIPHER = "AES/GCM/NoPadding"
        const val KEY_ALGORITHM = "AES"
    }
}
