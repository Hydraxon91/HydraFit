package com.hydrafit.app.core.domain.engine

import kotlin.test.Test
import kotlin.test.assertEquals

class ExerciseMeasureDefaultsTest {

    @Test
    fun prescribesPlankAsThirtyTimedSeconds() {
        assertEquals(
            ExerciseMeasure.Timed(30),
            ExerciseMeasureDefaults.preferredMeasure("plank")
        )
    }

    @Test
    fun defaultsEveryOtherExerciseToRepetitions() {
        assertEquals(
            ExerciseMeasure.Repetitions,
            ExerciseMeasureDefaults.preferredMeasure("bench-press")
        )
        // A custom id has no app-owned preference and stays counted.
        assertEquals(
            ExerciseMeasure.Repetitions,
            ExerciseMeasureDefaults.preferredMeasure("user-plank-variant")
        )
    }
}
