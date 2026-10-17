package com.hydrafit.app.core.userdata.backup

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.hydrafit.app.core.domain.backup.BackupEncryptionKeyStore
import com.hydrafit.app.core.domain.backup.StoredBackupKey
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Stores the unattended encryption material — the derived key plus its salt, never the user's
 * password — encrypted under a dedicated Android Keystore AES key. Losing the Keystore key (or a
 * write that never landed) makes [load] return null; the material is then recovered by re-entering
 * the backup password, which is authenticated against an existing encrypted snapshot before
 * anything is stored again. A corrupt entry reads as absent rather than guessing at key material.
 */
class AndroidBackupEncryptionKeyStore(context: Context) : BackupEncryptionKeyStore {
    private val preferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override suspend fun load(): StoredBackupKey? = withContext(Dispatchers.IO) {
        val iv = preferences.getString(KEY_IV, null) ?: return@withContext null
        val cipherText = preferences.getString(KEY_VALUE, null) ?: return@withContext null
        runCatching {
            val cipher = Cipher.getInstance(CIPHER)
            cipher.init(DECRYPT_MODE, secretKey(), GCMParameterSpec(GCM_TAG_BITS, iv.decode()))
            parse(cipher.doFinal(cipherText.decode()))
        }.getOrNull()
    }

    override suspend fun store(key: StoredBackupKey) {
        withContext(Dispatchers.IO) {
            val cipher = Cipher.getInstance(CIPHER)
            cipher.init(ENCRYPT_MODE, secretKey())
            val cipherText = cipher.doFinal(encode(key))
            preferences.edit()
                .putString(KEY_IV, cipher.iv.encode())
                .putString(KEY_VALUE, cipherText.encode())
                .commit()
        }
    }

    override suspend fun clear() {
        withContext(Dispatchers.IO) {
            preferences.edit().clear().commit()
        }
    }

    private fun encode(key: StoredBackupKey): ByteArray =
        byteArrayOf(key.salt.size.toByte()) + key.salt + key.key

    private fun parse(bytes: ByteArray): StoredBackupKey? {
        val saltSize = bytes.firstOrNull()?.toInt() ?: return null
        if (saltSize !in 1..bytes.size - 2) return null
        val salt = bytes.copyOfRange(1, 1 + saltSize)
        val key = bytes.copyOfRange(1 + saltSize, bytes.size)
        if (key.isEmpty()) return null
        return StoredBackupKey(salt = salt, key = key)
    }

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let {
            return it.secretKey
        }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()
        )
        return generator.generateKey()
    }

    private fun ByteArray.encode(): String = Base64.encodeToString(this, Base64.NO_WRAP)

    private fun String.decode(): ByteArray = Base64.decode(this, Base64.NO_WRAP)

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "hydrafit_backup_key"
        const val CIPHER = "AES/GCM/NoPadding"
        const val GCM_TAG_BITS = 128
        const val PREFS_NAME = "hydrafit_backup_secure"
        const val KEY_IV = "backup_key_iv"
        const val KEY_VALUE = "backup_key_value"
        const val ENCRYPT_MODE = Cipher.ENCRYPT_MODE
        const val DECRYPT_MODE = Cipher.DECRYPT_MODE
    }
}
