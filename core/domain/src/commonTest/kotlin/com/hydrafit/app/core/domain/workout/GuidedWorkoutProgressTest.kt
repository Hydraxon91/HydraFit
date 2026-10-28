package com.hydrafit.app.core.domain.workout

import com.hydrafit.app.core.domain.engine.ExerciseTargetExplanation
import com.hydrafit.app.core.domain.engine.FatigueSetEffect
import com.hydrafit.app.core.domain.engine.SetCountExplanation
import com.hydrafit.app.core.domain.engine.SetTarget
import com.hydrafit.app.core.domain.equipment.ExerciseLoadCapability
import com.hydrafit.app.core.domain.equipment.MovementPattern
import com.hydrafit.app.core.domain.schedule.GuidedWorkoutTargetOverride
import com.hydrafit.app.core.domain.schedule.GuidedWorkoutTargets
import com.hydrafit.app.core.domain.schedule.OccurrenceEntry
import com.hydrafit.app.core.domain.schedule.WorkoutOccurrence
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
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

    @Test
    fun carriesTheFrozenSlotsRecordedReasoningWithoutUsingItForProgress() {
        val recorded = ExerciseTargetExplanation(
            setCount = SetCountExplanation(
                baseSets = 3,
                deloadApplied = false,
                deloadVolumeScale = 0.7,
                fatigueEffect = FatigueSetEffect.NONE,
                finalSets = 3
            )
        )
        val occurrence = occurrenceWith(
            entry(id = 40, position = 0, sets = 3),
            entry(id = 41, position = 1, sets = 3).copy(explanation = recorded)
        )

        val progress = buildGuidedWorkoutProgress(
            occurrence,
            performedWorkingSetsByEntry = mapOf(40L to 1, 41L to 1)
        )

        assertEquals(null, progress.exercises[0].explanation)
        assertEquals(recorded, progress.exercises[1].explanation)
        assertEquals(2, progress.exercises[0].remainingSets)
        assertEquals(2, progress.exercises[1].remainingSets)
    }

    @Test
    fun carriesATimedSlotAsSecondsWhileCompletionStaysCountedBySets() {
        val occurrence = occurrenceWith(
            entry(id = 40, position = 0, sets = 3).copy(reps = null, durationSeconds = 90)
        )

        val progress = buildGuidedWorkoutProgress(
            occurrence,
            performedWorkingSetsByEntry = mapOf(40L to 2)
        )

        val exercise = progress.exercises.single()
        assertNull(exercise.reps)
        assertEquals(90, exercise.durationSeconds)
        assertEquals(3, progress.prescribedSets)
        assertEquals(2, progress.performedSets)
        assertEquals(1, exercise.remainingSets)
        assertFalse(exercise.isComplete)
    }

    @Test
    fun publishesEffectiveNextOrderedAndScalarTargetIncludingExplicitNullLoad() {
        val ordered = entry(id = 40, position = 0, sets = 3).copy(
            setTargets = com.hydrafit.app.core.domain.engine.SlotSetTargets.of(
                listOf(
                    SetTarget(reps = 6, weightKg = 80.0),
                    SetTarget(reps = 7, weightKg = 82.5),
                    SetTarget(reps = 8, weightKg = 85.0)
                )
            ),
            guidedTargets = GuidedWorkoutTargets(
                overrides = listOf(
                    GuidedWorkoutTargetOverride(1, SetTarget(reps = 9, weightKg = null))
                )
            )
        )
        val scalar = entry(id = 41, position = 1, sets = 2).copy(
            guidedTargets = GuidedWorkoutTargets(
                overrides = listOf(
                    GuidedWorkoutTargetOverride(0, SetTarget(reps = 10, weightKg = 120.0))
                )
            )
        )

        val progress = buildGuidedWorkoutProgress(
            occurrenceWith(ordered, scalar),
            mapOf(40L to 1, 41L to 0)
        )

        assertEquals(9, progress.exercises[0].reps)
        assertNull(progress.exercises[0].weightKg)
        assertEquals(9, progress.exercises[0].nextTarget?.target?.reps)
        assertEquals(10, progress.exercises[1].reps)
        assertEquals(120.0, progress.exercises[1].weightKg)
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
