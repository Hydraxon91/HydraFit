package com.hydrafit.app.feature.logger

import com.hydrafit.app.core.domain.equipment.ExerciseLoadCapability
import com.hydrafit.app.core.domain.workout.GuidedExerciseProgress
import com.hydrafit.app.core.domain.workout.GuidedWorkoutProgress
import com.hydrafit.app.core.domain.workout.LoadKind
import com.hydrafit.app.core.domain.workout.WarmupAction
import com.hydrafit.app.core.domain.workout.WarmupGuidance
import com.hydrafit.app.core.domain.workout.WarmupPreparationItem
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class WarmupGuidancePresentationTest {

    @Test
    fun showsTheGuidanceOnlyWhileGuidedModeIsActive() {
        assertNull(guidedState(guidedEnabled = false).visibleWarmupGuidance)
        assertNotNull(guidedState(guidedEnabled = true).visibleWarmupGuidance)
    }

    @Test
    fun hidesTheGuidanceWhenTheUserHidIt() {
        val state = guidedState(guidedEnabled = true).copy(warmupGuidanceHidden = true)
        assertNull(state.visibleWarmupGuidance)
    }

    @Test
    fun showsNothingWithoutAGuidedWorkoutOrAnyGuidance() {
        val noWorkout = WorkoutLoggerUiState(
            guidedEnabled = true,
            warmupGuidance = guidance()
        )
        assertNull(noWorkout.visibleWarmupGuidance)

        val noGuidance = WorkoutLoggerUiState(
            guidedEnabled = true,
            guidedProgress = progress()
        )
        assertNull(noGuidance.visibleWarmupGuidance)

        val empty = WorkoutLoggerUiState(
            guidedEnabled = true,
            guidedProgress = progress(),
            warmupGuidance = WarmupGuidance(preparation = emptyList(), ramps = emptyList())
        )
        assertNull(empty.visibleWarmupGuidance)
    }

    private fun guidedState(guidedEnabled: Boolean) = WorkoutLoggerUiState(
        guidedEnabled = guidedEnabled,
        guidedProgress = progress(),
        warmupGuidance = guidance()
    )

    private fun guidance() = WarmupGuidance(
        preparation = listOf(
            WarmupPreparationItem(WarmupAction.EASY_GENERAL_MOVEMENT, durationSeconds = 180)
        ),
        ramps = emptyList()
    )

    private fun progress() = GuidedWorkoutProgress(
        occurrenceId = 30L,
        exercises = listOf(
            GuidedExerciseProgress(
                occurrenceEntryId = 40L,
                exerciseId = "back-squat",
                exerciseName = "Back Squat",
                prescribedSets = 2,
                performedWorkingSets = 0,
                reps = 8,
                weightKg = 100.0,
                loadKind = LoadKind.EXTERNAL,
                loadCapability = ExerciseLoadCapability.EXTERNAL
            )
        )
    )
}
