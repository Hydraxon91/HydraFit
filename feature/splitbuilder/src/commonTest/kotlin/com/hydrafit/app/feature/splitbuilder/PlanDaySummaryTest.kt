package com.hydrafit.app.feature.splitbuilder

import com.hydrafit.app.core.domain.engine.PlannedExercise
import com.hydrafit.app.core.domain.engine.SplitFocus
import com.hydrafit.app.core.domain.engine.WorkoutDay
import kotlin.test.Test
import kotlin.test.assertEquals

class PlanDaySummaryTest {

    @Test
    fun aFoldedDayCountsEverySetAcrossItsExercises() {
        val day = WorkoutDay(
            dayIndex = 2,
            focus = SplitFocus.LOWER,
            exercises = listOf(
                PlannedExercise(exerciseId = "squat", sets = 4, reps = 6),
                PlannedExercise(exerciseId = "leg_curl", sets = 2, reps = 12),
                PlannedExercise(exerciseId = "plank", sets = 3, durationSeconds = 45)
            )
        )

        assertEquals(9, day.totalSets())
    }

    @Test
    fun aDayWithoutExercisesHasNoSets() {
        assertEquals(
            0,
            WorkoutDay(dayIndex = 0, focus = SplitFocus.UPPER, exercises = emptyList()).totalSets()
        )
    }
}
