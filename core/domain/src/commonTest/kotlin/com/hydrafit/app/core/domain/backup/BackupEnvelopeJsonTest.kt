package com.hydrafit.app.core.domain.backup

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class BackupEnvelopeJsonTest {

    private val envelope = BackupEnvelope(
        format = ENCRYPTED_BACKUP_FORMAT,
        envelopeVersion = BACKUP_ENVELOPE_VERSION,
        kdf = BackupEnvelopeKdf(
            algorithm = BACKUP_ENVELOPE_KDF_ALGORITHM,
            salt = BackupEnvelopeJson.toBase64(ByteArray(16) { it.toByte() }),
            iterations = BACKUP_ENVELOPE_ITERATIONS
        ),
        nonce = BackupEnvelopeJson.toBase64(ByteArray(12) { it.toByte() }),
        ciphertext = BackupEnvelopeJson.toBase64("payload".encodeToByteArray())
    )

    @Test
    fun roundTripsAnEnvelopeAndDetectsIt() {
        val text = BackupEnvelopeJson.encode(envelope)

        assertTrue(BackupEnvelopeJson.isEnvelope(text))
        assertEquals(envelope, BackupEnvelopeJson.decode(text))
        assertEquals(
            "payload".encodeToByteArray().toList(),
            BackupEnvelopeJson.ciphertextBytes(envelope).toList()
        )
    }

    @Test
    fun rejectsUnknownAndMissingFields() {
        val text = BackupEnvelopeJson.encode(envelope)

        assertFailure(
            text.replace("{\"format\":", "{\"extra\":1,\"format\":"),
            BackupFailure.MALFORMED
        )
        assertFailure(text.replace(Regex(",\"nonce\":\"[^\"]*\""), ""), BackupFailure.MALFORMED)
    }

    @Test
    fun rejectsAnUnsupportedVersionOrFormat() {
        val text = BackupEnvelopeJson.encode(envelope)

        assertFailure(
            text.replace("\"envelopeVersion\":$BACKUP_ENVELOPE_VERSION", "\"envelopeVersion\":2"),
            BackupFailure.UNSUPPORTED_VERSION
        )
        assertFailure(
            text.replace(ENCRYPTED_BACKUP_FORMAT, "other"),
            BackupFailure.UNSUPPORTED_VERSION
        )
    }

    @Test
    fun rejectsUnsupportedKdfParameters() {
        val text = BackupEnvelopeJson.encode(envelope)

        assertFailure(
            text.replace(BACKUP_ENVELOPE_KDF_ALGORITHM, "PBKDF2-HMAC-SHA1"),
            BackupFailure.INVALID_VALUE
        )
        assertFailure(
            text.replace("\"iterations\":$BACKUP_ENVELOPE_ITERATIONS", "\"iterations\":1000"),
            BackupFailure.INVALID_VALUE
        )
    }

    @Test
    fun rejectsWrongSaltOrNonceSizesAndUndecodableCiphertext() {
        val text = BackupEnvelopeJson.encode(envelope)
        val salt = BackupEnvelopeJson.toBase64(ByteArray(16) { it.toByte() })
        val nonce = BackupEnvelopeJson.toBase64(ByteArray(12) { it.toByte() })
        val shortBase64 = BackupEnvelopeJson.toBase64(ByteArray(8) { it.toByte() })
        val shortSalt = BackupEnvelopeJson.toBase64("salt".encodeToByteArray())

        assertFailure(
            text.replace("\"salt\":\"$salt\"", "\"salt\":\"$shortSalt\""),
            BackupFailure.INVALID_VALUE
        )
        assertFailure(
            text.replace("\"nonce\":\"$nonce\"", "\"nonce\":\"$shortBase64\""),
            BackupFailure.INVALID_VALUE
        )

        val decoded = BackupEnvelopeJson.decode(text)
        assertFailsWith<BackupException> {
            BackupEnvelopeJson.ciphertextBytes(decoded.copy(ciphertext = "!!!"))
        }
    }

    @Test
    fun doesNotDetectPlainPayloads() {
        assertTrue(!BackupEnvelopeJson.isEnvelope(validAutomaticBackupText()))
    }

    private fun assertFailure(text: String, failure: BackupFailure) {
        val thrown = assertFailsWith<BackupException> { BackupEnvelopeJson.decode(text) }
        assertEquals(failure, thrown.failure)
    }
}
