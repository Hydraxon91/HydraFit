package com.hydrafit.app.core.domain.backup

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** Boundary tests for the resource limits enforced by [BackupJson]. */
class BackupJsonTest {

    @Test
    fun rejectsNestingDeeperThanTheLimit() {
        val brackets = BackupLimits.MAX_DEPTH + 1
        assertFailure(BackupFailure.OVER_LIMIT, "[".repeat(brackets) + "]".repeat(brackets))
    }

    @Test
    fun nestingAtTheLimitIsNotRejectedAsOverLimit() {
        val brackets = BackupLimits.MAX_DEPTH
        // A valid JSON value at the depth limit parses, then fails only as a non-object.
        assertFailure(BackupFailure.MALFORMED, "[".repeat(brackets) + "]".repeat(brackets))
    }

    @Test
    fun rejectsAStringLongerThanTheLimit() {
        val text = "\"" + "a".repeat(BackupLimits.MAX_STRING_LENGTH + 1) + "\""
        assertFailure(BackupFailure.OVER_LIMIT, text)
    }

    @Test
    fun acceptsAStringAtTheLengthLimit() {
        val file = emptyBackupFile().copy(
            appVersion = "A".repeat(BackupLimits.MAX_STRING_LENGTH)
        )

        assertEquals(file, BackupJson.decode(BackupJson.encode(file)))
    }

    @Test
    fun rejectsMoreRecordsThanTheLimit() {
        val file = emptyBackupFile().copy(
            preferences = List(BackupLimits.MAX_RECORDS + 1) { index ->
                BackupPreferenceRecord("ex-$index", "NEUTRAL")
            }
        )

        val failure = assertFailsWith<BackupException> { BackupJson.encode(file) }
        assertEquals(BackupFailure.OVER_LIMIT, failure.failure)
    }

    private fun assertFailure(expected: BackupFailure, text: String) {
        val failure = assertFailsWith<BackupException> { BackupJson.decode(text) }
        assertEquals(expected, failure.failure)
    }
}
