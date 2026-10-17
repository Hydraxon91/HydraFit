package com.hydrafit.app.core.domain.backup

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AutomaticBackupStateJsonTest {

    @Test
    fun roundTripsAFullyPopulatedState() {
        val state = AutomaticBackupState(
            enabled = true,
            folderHandle = "content://com.example.documents/tree/backups",
            lastSuccessMillis = 5L,
            lastFailure = AutomaticBackupFailureRecord(
                kind = AutomaticBackupFailureKind.WRITE_UNVERIFIED,
                occurredAtMillis = 6L
            ),
            documents = listOf(
                RecordedBackupDocument("d1", "a.json", 1L, verified = true),
                RecordedBackupDocument("d2", "b.json", 2L, verified = false)
            )
        )

        assertEquals(
            state,
            AutomaticBackupStateJson.decodeOrNull(AutomaticBackupStateJson.encode(state))
        )
    }

    @Test
    fun roundTripsTheDisabledDefault() {
        assertEquals(
            AutomaticBackupState(),
            AutomaticBackupStateJson.decodeOrNull(
                AutomaticBackupStateJson.encode(AutomaticBackupState())
            )
        )
    }

    @Test
    fun rejectsAnUnknownField() {
        val encoded = AutomaticBackupStateJson.encode(AutomaticBackupState())

        assertNull(
            AutomaticBackupStateJson.decodeOrNull(
                encoded.replace("{\"format\":", "{\"extra\":1,\"format\":")
            )
        )
    }

    @Test
    fun rejectsAnUnknownFormatVersion() {
        val encoded = AutomaticBackupStateJson.encode(AutomaticBackupState())

        assertNull(
            AutomaticBackupStateJson.decodeOrNull(
                encoded.replace(
                    "\"formatVersion\":$AUTOMATIC_BACKUP_STATE_FORMAT_VERSION",
                    "\"formatVersion\":${AUTOMATIC_BACKUP_STATE_FORMAT_VERSION + 1}"
                )
            )
        )
    }

    @Test
    fun rejectsAForeignFormatIdentifier() {
        val encoded = AutomaticBackupStateJson.encode(AutomaticBackupState())

        assertNull(
            AutomaticBackupStateJson.decodeOrNull(
                encoded.replace(AUTOMATIC_BACKUP_STATE_FORMAT, "something-else")
            )
        )
    }

    @Test
    fun rejectsMalformedText() {
        assertNull(AutomaticBackupStateJson.decodeOrNull("not json"))
        assertNull(AutomaticBackupStateJson.decodeOrNull(""))
    }
}
