package com.hydrafit.app.core.userdata.backup

import com.hydrafit.app.core.domain.backup.BackupException
import com.hydrafit.app.core.domain.backup.BackupFailure
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Host-side vector and behavior tests for the JCE-backed primitives (the same providers the app
 * uses; Android Keystore wiring is not involved here). The PBKDF2 expected value was generated
 * with:
 * `python3 -c "import hashlib; print(hashlib.pbkdf2_hmac('sha256', b'hydrafit-vector',
 * b'0123456789abcdef', 600000, 32).hex())"`.
 */
class AndroidBackupCryptoTest {

    private val crypto = AndroidBackupCrypto()

    @Test
    fun derivesThePinnedPbkdf2Vector() {
        val key = crypto.deriveKey(
            password = "hydrafit-vector".toCharArray(),
            salt = "0123456789abcdef".encodeToByteArray(),
            iterations = 600_000,
            keyBytes = 32
        )

        assertEquals(
            "0d757a433e0dbf9566f2334a41c895d9b980f49a23c142aaabd1385e5be89c86",
            key.toHexString()
        )
    }

    @Test
    fun roundTripsGcmAndRejectsAnotherKeyOrTampering() {
        val key = crypto.randomBytes(32)
        val nonce = crypto.randomBytes(12)
        val plaintext = "payload".encodeToByteArray()

        val ciphertext = crypto.encrypt(key, nonce, plaintext)

        assertEquals(plaintext.toList(), crypto.decrypt(key, nonce, ciphertext).toList())

        val wrongKey = crypto.randomBytes(32)
        assertEquals(
            BackupFailure.ENCRYPTION_FAILED,
            assertFailsWith<BackupException> { crypto.decrypt(wrongKey, nonce, ciphertext) }.failure
        )

        val tampered = ciphertext.copyOf().also { it[0] = (it[0].toInt() xor 1).toByte() }
        assertEquals(
            BackupFailure.ENCRYPTION_FAILED,
            assertFailsWith<BackupException> { crypto.decrypt(key, nonce, tampered) }.failure
        )
    }

    private fun ByteArray.toHexString(): String =
        joinToString("") { byte -> (byte.toInt() and 0xFF).toString(16).padStart(2, '0') }
}
