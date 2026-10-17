package com.hydrafit.app.core.domain.backup

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class StagedBackupDocumentJsonTest {

    @Test
    fun roundTripsModeAndPayload() {
        val encoded = StagedBackupDocumentJson.encode(BackupApplyMode.MERGE, "{\"x\":1}")

        assertTrue(StagedBackupDocumentJson.isDocument(encoded))
        val document = StagedBackupDocumentJson.decodeOrLegacy(encoded)
        assertEquals(BackupApplyMode.MERGE, document.mode)
        assertEquals("{\"x\":1}", document.payload)
    }

    @Test
    fun legacyRawTextAppliesAsReplace() {
        val raw = BackupJson.encode(emptyBackupFile())

        val document = StagedBackupDocumentJson.decodeOrLegacy(raw)

        assertEquals(BackupApplyMode.REPLACE, document.mode)
        assertEquals(raw, document.payload)
    }

    @Test
    fun rejectsUnknownWrapperVersion() {
        val text = "{\"format\":\"$STAGED_APPLY_FORMAT\",\"version\":2," +
            "\"mode\":\"REPLACE\",\"payload\":\"x\"}"

        val failure = assertFailsWith<BackupException> {
            StagedBackupDocumentJson.decodeOrLegacy(text)
        }

        assertEquals(BackupFailure.UNSUPPORTED_VERSION, failure.failure)
    }

    @Test
    fun rejectsUnknownModeAndUnknownFields() {
        val unknownMode = "{\"format\":\"$STAGED_APPLY_FORMAT\",\"version\":1," +
            "\"mode\":\"DELETE\",\"payload\":\"x\"}"
        val unknownField = "{\"format\":\"$STAGED_APPLY_FORMAT\",\"version\":1," +
            "\"mode\":\"REPLACE\",\"payload\":\"x\",\"extra\":1}"

        assertEquals(
            BackupFailure.MALFORMED,
            assertFailsWith<BackupException> {
                StagedBackupDocumentJson.decodeOrLegacy(unknownMode)
            }.failure
        )
        assertEquals(
            BackupFailure.MALFORMED,
            assertFailsWith<BackupException> {
                StagedBackupDocumentJson.decodeOrLegacy(unknownField)
            }.failure
        )
    }
}
