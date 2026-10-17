package com.hydrafit.app.core.domain.backup

/**
 * The platform's authenticated-encryption primitives for backup envelopes: cryptographically
 * secure random bytes, PBKDF2-HMAC-SHA256 key derivation and AES-GCM encryption with a 128-bit
 * authentication tag. An operation that cannot authenticate throws [BackupException] with
 * [BackupFailure.ENCRYPTION_FAILED]. [isSupported] is false where the platform lacks these
 * primitives (Android below 8.0); callers must then refuse encrypted flows explicitly instead of
 * substituting anything weaker.
 */
interface BackupCrypto {
    val isSupported: Boolean

    fun randomBytes(size: Int): ByteArray

    fun deriveKey(password: CharArray, salt: ByteArray, iterations: Int, keyBytes: Int): ByteArray

    /** Returns ciphertext with the [plaintext]'s authentication tag appended for [nonce]. */
    fun encrypt(key: ByteArray, nonce: ByteArray, plaintext: ByteArray): ByteArray

    /** Throws [BackupException] with ENCRYPTION_FAILED when the tag does not authenticate. */
    fun decrypt(key: ByteArray, nonce: ByteArray, ciphertext: ByteArray): ByteArray
}

/** Fallback for platforms without the required crypto primitives (iOS in 0.9.4). */
class UnsupportedBackupCrypto : BackupCrypto {
    override val isSupported: Boolean = false

    override fun randomBytes(size: Int): ByteArray =
        throw UnsupportedOperationException("Encrypted backups are not supported on this platform")

    override fun deriveKey(
        password: CharArray,
        salt: ByteArray,
        iterations: Int,
        keyBytes: Int
    ): ByteArray =
        throw UnsupportedOperationException("Encrypted backups are not supported on this platform")

    override fun encrypt(key: ByteArray, nonce: ByteArray, plaintext: ByteArray): ByteArray =
        throw UnsupportedOperationException("Encrypted backups are not supported on this platform")

    override fun decrypt(key: ByteArray, nonce: ByteArray, ciphertext: ByteArray): ByteArray =
        throw UnsupportedOperationException("Encrypted backups are not supported on this platform")
}
