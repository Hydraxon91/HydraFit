package com.hydrafit.app.core.domain.backup

import kotlinx.serialization.Serializable

/** Stable identifier of the encrypted-backup envelope, separate from the logical backup format. */
const val ENCRYPTED_BACKUP_FORMAT: String = "hydrafit-backup-encrypted"

/** The envelope version this build writes and the only one it reads. */
const val BACKUP_ENVELOPE_VERSION: Int = 1

/** The only supported key-derivation algorithm; the envelope records it literally. */
const val BACKUP_ENVELOPE_KDF_ALGORITHM: String = "PBKDF2-HMAC-SHA256"

/**
 * The OWASP-baseline PBKDF2 iteration count, recorded in every envelope. Changing it later is a
 * versioned decision, never a silent rewrite: a reader accepts exactly this value.
 */
const val BACKUP_ENVELOPE_ITERATIONS: Int = 600_000

/** Salt, nonce and key sizes of the envelope's AEAD construction (AES-256-GCM, 128-bit tag). */
const val BACKUP_ENVELOPE_SALT_BYTES: Int = 16
const val BACKUP_ENVELOPE_NONCE_BYTES: Int = 12
const val BACKUP_ENVELOPE_KEY_BYTES: Int = 32
const val BACKUP_ENVELOPE_GCM_TAG_BITS: Int = 128

/** The envelope's key-derivation parameters. */
@Serializable
data class BackupEnvelopeKdf(val algorithm: String, val salt: String, val iterations: Int)

/**
 * One encrypted snapshot: a base64 JSON envelope whose ciphertext is a whole logical backup
 * payload. Every field is required and unknown fields are rejected; the envelope version evolves
 * independently of the backup format version.
 */
@Serializable
data class BackupEnvelope(
    val format: String,
    val envelopeVersion: Int,
    val kdf: BackupEnvelopeKdf,
    val nonce: String,
    val ciphertext: String
)
