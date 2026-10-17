package com.hydrafit.app.core.domain.backup

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlinx.serialization.json.Json

/**
 * The JSON codec for the encrypted envelope. Every field is required and unknown fields are
 * rejected, and the codec re-validates the recorded parameters (algorithm, iteration count, salt
 * and nonce sizes) so an unsupported or tampered header can never change how the payload is
 * opened. Size limits match the logical backup's read/write bound.
 */
@OptIn(ExperimentalEncodingApi::class)
object BackupEnvelopeJson {
    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = false
    }

    fun encode(envelope: BackupEnvelope): String {
        val text = json.encodeToString(BackupEnvelope.serializer(), envelope)
        if (text.encodeToByteArray().size.toLong() > BackupLimits.MAX_BYTES) {
            throw BackupException(BackupFailure.TOO_LARGE)
        }
        return text
    }

    /** Decodes and validates the envelope; any inconsistency is a typed rejection. */
    fun decode(text: String): BackupEnvelope {
        if (text.encodeToByteArray().size.toLong() > BackupLimits.MAX_BYTES) {
            throw BackupException(BackupFailure.TOO_LARGE)
        }
        val envelope = try {
            json.decodeFromString(BackupEnvelope.serializer(), text)
        } catch (e: Exception) {
            throw BackupException(BackupFailure.MALFORMED, e.message)
        }
        if (envelope.format != ENCRYPTED_BACKUP_FORMAT ||
            envelope.envelopeVersion != BACKUP_ENVELOPE_VERSION
        ) {
            throw BackupException(BackupFailure.UNSUPPORTED_VERSION)
        }
        if (envelope.kdf.algorithm != BACKUP_ENVELOPE_KDF_ALGORITHM ||
            envelope.kdf.iterations != BACKUP_ENVELOPE_ITERATIONS
        ) {
            throw BackupException(BackupFailure.INVALID_VALUE)
        }
        if (saltBytes(envelope).size != BACKUP_ENVELOPE_SALT_BYTES ||
            nonceBytes(envelope).size != BACKUP_ENVELOPE_NONCE_BYTES ||
            envelope.ciphertext.isEmpty()
        ) {
            throw BackupException(BackupFailure.INVALID_VALUE)
        }
        return envelope
    }

    /** Cheap content detection used by the import path and reconciliation; never throws. */
    fun isEnvelope(text: String): Boolean =
        text.startsWith("{\"format\":\"$ENCRYPTED_BACKUP_FORMAT\"")

    fun saltBytes(envelope: BackupEnvelope): ByteArray = fromBase64(envelope.kdf.salt)

    fun nonceBytes(envelope: BackupEnvelope): ByteArray = fromBase64(envelope.nonce)

    fun ciphertextBytes(envelope: BackupEnvelope): ByteArray = fromBase64(envelope.ciphertext)

    fun toBase64(bytes: ByteArray): String = Base64.encode(bytes)

    private fun fromBase64(value: String): ByteArray = try {
        Base64.decode(value)
    } catch (e: IllegalArgumentException) {
        throw BackupException(BackupFailure.MALFORMED, e.message)
    }
}
