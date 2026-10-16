package com.hydrafit.app.core.domain.transfer

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals

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
}
