package com.hydrafit.app.core.domain.transfer

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

class WorkoutImportPartitionTest {
    @Test
    fun membershipPreservesRepeatedRowsAsDistinctPositions() {
        val split = WorkoutImportPartition(2, listOf(listOf(0), listOf(1)), emptySet())
        val merged = WorkoutImportPartition(2, listOf(listOf(0, 1)), emptySet())
        assertNotEquals(split.canonical(), merged.canonical())
    }

    @Test
    fun exclusionsArePartOfTheApprovedPartition() {
        val first = WorkoutImportPartition(2, listOf(listOf(0)), setOf(1))
        val second = WorkoutImportPartition(2, listOf(listOf(1)), setOf(0))
        assertNotEquals(first.canonical(), second.canonical())
    }

    @Test
    fun selectionOrderWithinASessionDoesNotChangeMembership() {
        assertEquals(
            WorkoutImportPartition(3, listOf(listOf(0, 2)), setOf(1)).canonical(),
            WorkoutImportPartition(3, listOf(listOf(2, 0)), setOf(1)).canonical()
        )
    }

    @Test
    fun everyPositionMustBeAssignedExactlyOnce() {
        listOf(
            WorkoutImportPartition(2, listOf(listOf(0)), emptySet()),
            WorkoutImportPartition(2, listOf(listOf(0, 0)), emptySet()),
            WorkoutImportPartition(2, listOf(listOf(0)), setOf(0)),
            WorkoutImportPartition(2, listOf(listOf(0, 2)), emptySet()),
            WorkoutImportPartition(2, listOf(emptyList()), setOf(0, 1))
        ).forEach { partition ->
            assertFailsWith<IllegalArgumentException> { partition.canonical() }
        }
    }

    @Test
    fun aCanonicalPartitionReadsBackExactlyAsWritten() {
        // The stored manifest must round-trip, including two sessions with identical membership shape
        // and an explicit exclusion, so a repeat import can reuse the approved split.
        listOf(
            WorkoutImportPartition(1, listOf(listOf(0)), emptySet()),
            WorkoutImportPartition(4, listOf(listOf(0, 1), listOf(2), listOf(3)), emptySet()),
            WorkoutImportPartition(3, listOf(listOf(0), listOf(2)), setOf(1)),
            WorkoutImportPartition(2, listOf(listOf(1)), setOf(0))
        ).forEach { partition ->
            val decoded = WorkoutImportPartition.decode(partition.canonical())
            assertEquals(partition, decoded, partition.canonical())
        }
    }

    @Test
    fun aManifestThatCannotBeReadBackIsRefusedRatherThanTrusted() {
        val valid = WorkoutImportPartition(
            3,
            listOf(listOf(0, 1), listOf(2)),
            emptySet()
        ).canonical()
        listOf(
            "",
            "not-a-manifest",
            // A truncated length prefix, a non-numeric one, and a wrong version marker.
            "12:workout-partition-v1",
            "x:",
            // A prefix so large that adding it would overflow a signed Int must be malformed, not an
            // exception: this is exactly what a hand-edited manifest can carry.
            "2147483647:x",
            valid.dropLast(1),
            valid.replace("workout-partition-v1", "workout-partition-v2"),
            // An extra trailing field is not a manifest this build wrote.
            valid + "1:9",
            // Membership that does not cover the bucket exactly once.
            canonicalFields(
                listOf(
                    "workout-partition-v1",
                    "2",
                    canonicalFields(listOf(canonicalFields(listOf("0")))),
                    canonicalFields(emptyList())
                )
            )
        ).forEach { encoded ->
            assertNull(WorkoutImportPartition.decode(encoded), encoded)
        }
    }
}
