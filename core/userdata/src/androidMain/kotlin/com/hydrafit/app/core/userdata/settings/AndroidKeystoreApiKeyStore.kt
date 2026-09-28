package com.hydrafit.app.core.userdata.settings

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class AndroidKeystoreApiKeyStore(context: Context) : ApiKeyStore {

    private val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun load(): String? {
        val iv = preferences.getString(KEY_IV, null) ?: return null
        val cipherText = preferences.getString(KEY_VALUE, null) ?: return null
        return runCatching {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(DECRYPT_MODE, secretKey(), GCMParameterSpec(GCM_TAG_BITS, iv.decode()))
            cipher.doFinal(cipherText.decode()).decodeToString()
        }.getOrNull()
    }

    override fun save(apiKey: String) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(ENCRYPT_MODE, secretKey())
        val cipherText = cipher.doFinal(apiKey.encodeToByteArray())
        preferences.edit()
            .putString(KEY_IV, cipher.iv.encode())
            .putString(KEY_VALUE, cipherText.encode())
            .apply()
    }

    override fun clear() {
        preferences.edit().clear().apply()
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
        const val KEY_ALIAS = "hydrafit_gemini_api_key"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_TAG_BITS = 128
        const val PREFS_NAME = "hydrafit_secure"
        const val KEY_IV = "api_key_iv"
        const val KEY_VALUE = "api_key_value"
        const val ENCRYPT_MODE = Cipher.ENCRYPT_MODE
        const val DECRYPT_MODE = Cipher.DECRYPT_MODE
    }
}
