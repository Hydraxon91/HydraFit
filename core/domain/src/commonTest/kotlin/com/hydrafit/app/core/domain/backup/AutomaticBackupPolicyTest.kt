package com.hydrafit.app.core.domain.backup

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AutomaticBackupPolicyTest {

    @Test
    fun isNeverDueWhenAutomationIsOff() {
        val state = AutomaticBackupState(enabled = false)

        assertFalse(AutomaticBackupPolicy.isDue(state, 10_000L))
    }

    @Test
    fun isDueWhenNoSnapshotHasVerifiedYet() {
        val state = AutomaticBackupState(enabled = true, lastSuccessMillis = null)

        assertTrue(AutomaticBackupPolicy.isDue(state, 10_000L))
    }

    @Test
    fun isDueExactlyAtTheIntervalBoundary() {
        val lastSuccess = 1_000L
        val state = AutomaticBackupState(enabled = true, lastSuccessMillis = lastSuccess)

        assertFalse(
            AutomaticBackupPolicy.isDue(
                state,
                lastSuccess + AutomaticBackupPolicy.INTERVAL_MILLIS - 1
            )
        )
        assertTrue(
            AutomaticBackupPolicy.isDue(state, lastSuccess + AutomaticBackupPolicy.INTERVAL_MILLIS)
        )
    }

    @Test
    fun keepsEveryDocumentWithinRetention() {
        val state = stateWith(
            (1..AutomaticBackupPolicy.RETAINED_SNAPSHOTS).map { verified("doc-$it", it.toLong()) }
        )

        assertEquals(emptyList(), AutomaticBackupPolicy.documentsToPrune(state))
    }

    @Test
    fun prunesTheOldestVerifiedDocumentsBeyondRetention() {
        val state = stateWith((1..9).map { verified("doc-$it", it.toLong()) })

        assertEquals(listOf("doc-1", "doc-2"), AutomaticBackupPolicy.documentsToPrune(state))
    }

    @Test
    fun neverSelectsAnUnverifiedDocument() {
        // An unverified document is older than everything kept, and another is newer than the
        // newest verified one; neither may be selected, and it must not displace a verified file.
        val state = stateWith(
            listOf(unverified("doc-bad-old", 0L)) +
                (1..7).map { verified("doc-$it", it.toLong()) } +
                listOf(unverified("doc-bad-new", 8L))
        )

        assertEquals(emptyList(), AutomaticBackupPolicy.documentsToPrune(state))
    }

    @Test
    fun prunesOnlyVerifiedDocumentsWhenMoreThanRetentionExists() {
        val state = stateWith(
            (1..8).map { verified("doc-$it", it.toLong()) } +
                listOf(unverified("doc-bad", 0L))
        )

        assertEquals(listOf("doc-1"), AutomaticBackupPolicy.documentsToPrune(state))
    }

    @Test
    fun equalCreationTimesPruneDeterministically() {
        val state = stateWith((1..8).map { verified("doc-$it", 5L) })

        // Same timestamp everywhere: the id tie-break keeps the lexicographically smallest id.
        assertEquals(listOf("doc-8"), AutomaticBackupPolicy.documentsToPrune(state))
    }

    private fun stateWith(documents: List<RecordedBackupDocument>) =
        AutomaticBackupState(enabled = true, documents = documents)

    private fun verified(id: String, createdAtMillis: Long) =
        RecordedBackupDocument(id, "$id.json", createdAtMillis, verified = true)

    private fun unverified(id: String, createdAtMillis: Long) =
        RecordedBackupDocument(id, "$id.json", createdAtMillis, verified = false)
}
