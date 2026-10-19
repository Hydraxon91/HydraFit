package com.hydrafit.app.feature.logger

import com.hydrafit.app.core.domain.equipment.ExerciseLoadCapability
import com.hydrafit.app.core.domain.routine.Superset
import com.hydrafit.app.core.domain.workout.GuidedExerciseProgress
import com.hydrafit.app.core.domain.workout.GuidedWorkoutProgress
import com.hydrafit.app.core.domain.workout.LoadKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Guided superset state and rest selection. These are the pure seams behind the Logger's next-turn,
 * skip, resume, unpair and rest behavior, so the rules are covered without a timer or a device.
 */
class WorkoutLoggerSupersetTest {
    private val pair = Superset("arms", 10, 120)

    private fun entry(id: Long, performed: Int, sets: Int = 3) = GuidedExerciseProgress(
        occurrenceEntryId = id,
        exerciseId = "exercise-$id",
        exerciseName = "Exercise $id",
        prescribedSets = sets,
        performedWorkingSets = performed,
        reps = 10,
        weightKg = 10.0,
        loadKind = LoadKind.EXTERNAL,
        loadCapability = ExerciseLoadCapability.EXTERNAL,
        superset = pair
    )

    private fun progress(a: Int, b: Int, aSets: Int = 3, bSets: Int = 3) = GuidedWorkoutProgress(
        occurrenceId = 1,
        exercises = listOf(entry(10, a, aSets), entry(20, b, bSets))
    )

    @Test
    fun aPartnerBehindInTheSameRoundStartsATransition() {
        assertEquals(10 to true, supersetRestSelection(progress(0, 0), emptySet(), 10))
        assertEquals(10 to true, supersetRestSelection(progress(1, 0), emptySet(), 10))
    }

    @Test
    fun aCompletedRoundStartsPairRest() {
        assertEquals(120 to false, supersetRestSelection(progress(1, 0), emptySet(), 20))
        // Logging B first (A skipped) leaves A owing the round, so B's set is still a transition.
        assertEquals(10 to true, supersetRestSelection(progress(0, 0), emptySet(), 20))
    }

    @Test
    fun unequalCountsFinishSoloWithPairRestNotATransition() {
        // A is complete (2 of 2). B's third set must not reuse A's transition just because A is behind.
        val uneven = progress(a = 2, b = 2, aSets = 2, bSets = 4)
        assertEquals(120 to false, supersetRestSelection(uneven, emptySet(), 20))
    }

    @Test
    fun unpairedPairsAndUnpairedKeysStartNoSupersetRest() {
        assertNull(supersetRestSelection(progress(0, 0), setOf("arms"), 10))
        assertNull(supersetRestSelection(progress(0, 0), emptySet(), 30))
        assertNull(supersetRestSelection(null, emptySet(), 10))
    }

    @Test
    fun zeroTransitionIsReportedAsAZeroOverride() {
        val zero = pair.copy(transitionSeconds = 0)
        val guided = GuidedWorkoutProgress(
            occurrenceId = 1,
            exercises = listOf(
                entry(10, 0).copy(superset = zero),
                entry(20, 0).copy(superset = zero)
            )
        )
        assertEquals(0 to true, supersetRestSelection(guided, emptySet(), 10))
    }

    @Test
    fun theUiStateSelectsTheOutstandingTurnAndHonoursSkipAndUnpair() {
        // A is behind after B logged first, so the unskipped selection is A.
        val state = WorkoutLoggerUiState(
            guidedEnabled = true,
            guidedProgress = progress(0, 1)
        )
        assertEquals(10L, state.nextGuidedEntryId)
        // Skipping A must actually change the selection to B; this fails if skip is ignored.
        assertEquals(
            20L,
            state.copy(supersetSkippedEntryId = 10).nextGuidedEntryId
        )
        // Unpairing with no skip falls back to plain list order, which is A again.
        assertEquals(
            10L,
            state.copy(supersetUnpairedKeys = setOf("arms")).nextGuidedEntryId
        )
    }
}
