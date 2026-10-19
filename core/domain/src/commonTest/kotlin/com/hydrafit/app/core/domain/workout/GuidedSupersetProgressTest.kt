package com.hydrafit.app.core.domain.workout

import com.hydrafit.app.core.domain.equipment.ExerciseLoadCapability
import com.hydrafit.app.core.domain.routine.Superset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class GuidedSupersetProgressTest {
    private val pair = Superset("arms", 10, 120)

    private fun progress(a: Int, b: Int, aSets: Int = 3, bSets: Int = 3) = GuidedWorkoutProgress(
        occurrenceId = 1,
        exercises = listOf(entry(10, aSets, a), entry(20, bSets, b))
    )

    private fun entry(id: Long, sets: Int, performed: Int) = GuidedExerciseProgress(
        occurrenceEntryId = id,
        exerciseId = id.toString(),
        exerciseName = id.toString(),
        prescribedSets = sets,
        performedWorkingSets = performed,
        reps = 10,
        weightKg = 10.0,
        loadKind = LoadKind.EXTERNAL,
        loadCapability = ExerciseLoadCapability.EXTERNAL,
        superset = pair
    )

    @Test
    fun alternatesByRecordedWorkingSetsAndResumesWithoutFabricatingHistory() {
        assertEquals(10L, progress(0, 0).nextExercise()?.occurrenceEntryId)
        assertEquals(20L, progress(1, 0).nextExercise()?.occurrenceEntryId)
        assertEquals(10L, progress(1, 1).nextExercise()?.occurrenceEntryId)
        // Skipping one turn passes over that member once; resuming selects the outstanding work.
        assertEquals(20L, progress(0, 0).nextExercise(setOf(10))?.occurrenceEntryId)
        assertNull(progress(0, 0).nextExercise(setOf(10, 20)))
        assertFalse(progress(0, 0).allComplete)
    }

    @Test
    fun aSessionLocalUnpairFallsBackToPlainOrderForTheRestOfTheWorkout() {
        // Paired, B is behind and takes the turn; unpaired, plain list order resumes.
        assertEquals(20L, progress(1, 0).nextExercise()?.occurrenceEntryId)
        assertEquals(
            10L,
            progress(1, 0).nextExercise(unpairedKeys = setOf("arms"))?.occurrenceEntryId
        )
        assertEquals(
            10L,
            progress(1, 1).nextExercise(unpairedKeys = setOf("arms"))?.occurrenceEntryId
        )
    }

    @Test
    fun unequalCountsContinueSoloAndFinishOnlyWhenBothComplete() {
        assertEquals(20L, progress(1, 1, aSets = 1).nextExercise()?.occurrenceEntryId)
        assertNull(progress(1, 3, aSets = 1).nextExercise())
    }
}
