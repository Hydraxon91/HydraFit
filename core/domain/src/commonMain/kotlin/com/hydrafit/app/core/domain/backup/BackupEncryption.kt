package com.hydrafit.app.core.domain.backup

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Seals and opens encrypted backup envelopes. A file's key is PBKDF2-HMAC-SHA256 (600,000
 * iterations) over the password and the per-file random salt recorded in the envelope; the
 * ciphertext is AES-256-GCM over the whole logical backup payload, so the envelope is portable to
 * any install that knows the password and is versioned independently of the backup format.
 *
 * Unattended automatic runs use material stored by the platform key store instead of a password:
 * [sealUnattended] seals with the stored derived key, created from the stored salt, so every
 * automatic file opens anywhere with the same password. A missing or non-matching stored key never
 * falls back to plaintext; the caller sees a typed failure.
 *
 * The derivation and cipher work runs on [dispatcher] because PBKDF2 at this cost must never run
 * on the main thread.
 */
class BackupEncryption(
    private val crypto: BackupCrypto,
    private val keyStore: BackupEncryptionKeyStore,
    private val verifySnapshot: VerifyBackupSnapshotUseCase,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default
) {
    val isSupported: Boolean get() = crypto.isSupported

    /** Cheap content detection: whether [text] is an encrypted envelope rather than a plain payload. */
    fun looksEncrypted(text: String): Boolean = BackupEnvelopeJson.isEnvelope(text)

    /** Derives fresh unattended material for [password]; the caller stores it via the key store. */
    suspend fun createUnattendedKey(password: CharArray): StoredBackupKey =
        withContext(dispatcher) {
            val salt = crypto.randomBytes(BACKUP_ENVELOPE_SALT_BYTES)
            StoredBackupKey(salt = salt, key = derive(password, salt))
        }

    /** Manual export and other password-driven sealing; a fresh random salt per file. */
    suspend fun sealWithPassword(plaintext: String, password: CharArray): String =
        withContext(dispatcher) {
            val salt = crypto.randomBytes(BACKUP_ENVELOPE_SALT_BYTES)
            seal(plaintext, salt, derive(password, salt))
        }

    /**
     * Manual import; throws [BackupException] with ENCRYPTION_FAILED for a wrong password or a
     * damaged file, and MALFORMED/UNSUPPORTED_VERSION/TOO_LARGE for an unusable envelope.
     */
    suspend fun unsealWithPassword(text: String, password: CharArray): String =
        withContext(dispatcher) {
            val envelope = BackupEnvelopeJson.decode(text)
            val salt = BackupEnvelopeJson.saltBytes(envelope)
            open(envelope, derive(password, salt)).decodeToString()
        }

    /**
     * Recovery support: derives the key a stored file was written with (its own recorded salt) and
     * authenticates the password against it before returning reusable material. Throws
     * ENCRYPTION_FAILED on a wrong password; nothing is stored by this call.
     */
    suspend fun deriveKeyForStoredFile(text: String, password: CharArray): StoredBackupKey =
        withContext(dispatcher) {
            val envelope = BackupEnvelopeJson.decode(text)
            val salt = BackupEnvelopeJson.saltBytes(envelope)
            val key = derive(password, salt)
            open(envelope, key)
            StoredBackupKey(salt = salt, key = key)
        }

    /** Automatic-run sealing with the stored unattended material. */
    suspend fun sealUnattended(plaintext: String): String = withContext(dispatcher) {
        val stored = keyStore.load()
            ?: throw BackupException(BackupFailure.ENCRYPTION_KEY_UNAVAILABLE)
        seal(plaintext, stored.salt, stored.key)
    }

    /**
     * Content-detected verification for reconciliation: a plain snapshot goes through the strict
     * validator; an encrypted one is opened with the stored unattended material (only a file whose
     * salt matches the stored one can be opened unattended) and then validated. Never throws:
     * anything unreadable stays unverified, which never blocks a later run and never authorizes
     * deletion.
     */
    suspend fun verifyStored(text: String): Boolean {
        if (!BackupEnvelopeJson.isEnvelope(text)) return verifySnapshot(text)
        val envelope = try {
            BackupEnvelopeJson.decode(text)
        } catch (_: BackupException) {
            return false
        }
        val stored = try {
            keyStore.load()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            return false
        } ?: return false
        val storedSaltMatches = try {
            stored.salt.contentEquals(BackupEnvelopeJson.saltBytes(envelope))
        } catch (_: BackupException) {
            return false
        }
        if (!storedSaltMatches) return false
        val plaintext = try {
            open(envelope, stored.key).decodeToString()
        } catch (_: BackupException) {
            return false
        }
        return verifySnapshot(plaintext)
    }

    private fun derive(password: CharArray, salt: ByteArray): ByteArray =
        crypto.deriveKey(password, salt, BACKUP_ENVELOPE_ITERATIONS, BACKUP_ENVELOPE_KEY_BYTES)

    private fun seal(plaintext: String, salt: ByteArray, key: ByteArray): String {
        val nonce = crypto.randomBytes(BACKUP_ENVELOPE_NONCE_BYTES)
        val ciphertext = crypto.encrypt(key, nonce, plaintext.encodeToByteArray())
        return BackupEnvelopeJson.encode(
            BackupEnvelope(
                format = ENCRYPTED_BACKUP_FORMAT,
                envelopeVersion = BACKUP_ENVELOPE_VERSION,
                kdf = BackupEnvelopeKdf(
                    algorithm = BACKUP_ENVELOPE_KDF_ALGORITHM,
                    salt = BackupEnvelopeJson.toBase64(salt),
                    iterations = BACKUP_ENVELOPE_ITERATIONS
                ),
                nonce = BackupEnvelopeJson.toBase64(nonce),
                ciphertext = BackupEnvelopeJson.toBase64(ciphertext)
            )
        )
    }

    private fun open(envelope: BackupEnvelope, key: ByteArray): ByteArray = try {
        crypto.decrypt(
            key,
            BackupEnvelopeJson.nonceBytes(envelope),
            BackupEnvelopeJson.ciphertextBytes(envelope)
        )
    } catch (e: BackupException) {
        throw e
    } catch (e: Exception) {
        throw BackupException(BackupFailure.ENCRYPTION_FAILED, e.message)
    }
}
