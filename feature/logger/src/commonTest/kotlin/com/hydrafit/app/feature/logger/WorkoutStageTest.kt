package com.hydrafit.app.feature.logger

import com.hydrafit.app.core.domain.workout.RestCountdown
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WorkoutStageTest {
    private val occurrence = ActiveOccurrence(
        occurrenceId = 1L,
        workoutName = "Day 1",
        performedSets = 0,
        prescribedSets = 9
    )

    @Test
    fun anIdleWorkoutStartsOnItsOverview() {
        val state = WorkoutLoggerUiState(activeOccurrence = occurrence)

        assertTrue(state.hasActiveWorkout)
        assertFalse(state.showsTrainingStage)
    }

    @Test
    fun choosingTrainingShowsTheFocusedArrangement() {
        val state = WorkoutLoggerUiState(
            activeOccurrence = occurrence,
            workoutStage = WorkoutStage.TRAINING
        )

        assertTrue(state.showsTrainingStage)
    }

    @Test
    fun aRunningTimerForcesTrainingSoItCannotBeCollapsed() {
        val countdown = sampleCountdown()
        val rest = WorkoutLoggerUiState(
            activeOccurrence = occurrence,
            workoutStage = WorkoutStage.OVERVIEW,
            restTimer = RestTimerState(countdown, remainingMillis = 5_000L)
        )
        val timed = WorkoutLoggerUiState(
            activeOccurrence = occurrence,
            workoutStage = WorkoutStage.OVERVIEW,
            setTimer = SetTimerState(
                draft = DraftSet(
                    exerciseId = "squat",
                    name = "Back Squat",
                    sets = 1,
                    reps = 5,
                    weightKg = null
                ),
                phase = SetTimerPhase.PREPARING,
                remainingSeconds = 3
            )
        )

        assertTrue(rest.showsTrainingStage)
        assertTrue(timed.showsTrainingStage)
    }

    @Test
    fun withoutAnActiveWorkoutNeitherStageOwnsTheScreen() {
        val state = WorkoutLoggerUiState(workoutStage = WorkoutStage.TRAINING)

        assertFalse(state.hasActiveWorkout)
        assertFalse(state.showsTrainingStage)
    }

    private fun sampleCountdown(): RestCountdown = RestCountdown(
        completedAtElapsedMillis = 0L,
        durationMillis = 120_000L
    )
}
