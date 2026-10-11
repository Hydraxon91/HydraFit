package com.hydrafit.app.core.domain.workout

import com.hydrafit.app.core.domain.equipment.ExerciseLoadCapability
import com.hydrafit.app.core.domain.equipment.MovementPattern
import com.hydrafit.app.core.domain.schedule.OccurrenceEntry
import com.hydrafit.app.core.domain.schedule.WorkoutOccurrence
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GuidedWorkoutProgressTest {

    @Test
    fun keepsEntryOrderAndReportsPrescribedVersusPerformed() {
        val occurrence = occurrenceWith(
            entry(id = 40, position = 0, exerciseId = "back-squat", sets = 3),
            entry(id = 41, position = 1, exerciseId = "bench-press", sets = 2)
        )

        val progress = buildGuidedWorkoutProgress(
            occurrence,
            performedWorkingSetsByEntry = mapOf(40L to 1, 41L to 2)
        )

        assertEquals(listOf("back-squat", "bench-press"), progress.exercises.map { it.exerciseId })
        assertEquals(5, progress.prescribedSets)
        assertEquals(3, progress.performedSets)
        assertEquals(2, progress.exercises[0].remainingSets)
        assertFalse(progress.exercises[0].isComplete)
        assertEquals(0, progress.exercises[1].remainingSets)
        assertTrue(progress.exercises[1].isComplete)
        assertFalse(progress.allComplete)
    }

    @Test
    fun countsAnEntryWithNoRecordedSetsAsFullyRemaining() {
        val occurrence = occurrenceWith(entry(id = 40, position = 0, sets = 4))

        val progress = buildGuidedWorkoutProgress(
            occurrence,
            performedWorkingSetsByEntry = emptyMap()
        )

        assertEquals(4, progress.exercises.single().remainingSets)
        assertEquals(0, progress.exercises.single().performedWorkingSets)
    }

    @Test
    fun capsPerformedSetsAtThePrescriptionSoOverLoggingCannotOverstateProgress() {
        val occurrence = occurrenceWith(entry(id = 40, position = 0, sets = 2))

        val progress = buildGuidedWorkoutProgress(
            occurrence,
            performedWorkingSetsByEntry = mapOf(40L to 5)
        )

        assertEquals(2, progress.performedSets)
        assertEquals(2, progress.prescribedSets)
        assertTrue(progress.allComplete)
    }

    @Test
    fun sortsEntriesByPositionRegardlessOfStorageOrder() {
        val occurrence = occurrenceWith(
            entry(id = 41, position = 1, exerciseId = "bench-press", sets = 2),
            entry(id = 40, position = 0, exerciseId = "back-squat", sets = 2)
        )

        val progress = buildGuidedWorkoutProgress(
            occurrence,
            performedWorkingSetsByEntry = emptyMap()
        )

        assertEquals(listOf(40L, 41L), progress.exercises.map { it.occurrenceEntryId })
    }

    @Test
    fun anOccurrenceWithNoEntriesIsComplete() {
        val progress = buildGuidedWorkoutProgress(
            occurrenceWith(),
            performedWorkingSetsByEntry = emptyMap()
        )

        assertTrue(progress.exercises.isEmpty())
        assertTrue(progress.allComplete)
        assertEquals(0, progress.prescribedSets)
    }

    private fun occurrenceWith(vararg entries: OccurrenceEntry) = WorkoutOccurrence(
        id = 30L,
        activationId = 5L,
        activationWorkoutId = 9L,
        queuePosition = 0,
        entries = entries.toList()
    )

    private fun entry(id: Long, position: Int, exerciseId: String = "back-squat", sets: Int) =
        OccurrenceEntry(
            id = id,
            position = position,
            exerciseId = exerciseId,
            exerciseName = exerciseId,
            movementPattern = MovementPattern.SQUAT,
            sets = sets,
            reps = 8,
            weightKg = 100.0,
            loadCapability = ExerciseLoadCapability.EXTERNAL,
            loadKind = LoadKind.EXTERNAL
        )
}
