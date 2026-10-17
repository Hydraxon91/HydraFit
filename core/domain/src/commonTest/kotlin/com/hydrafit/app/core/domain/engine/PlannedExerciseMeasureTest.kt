package com.hydrafit.app.core.domain.engine

import com.hydrafit.app.core.domain.equipment.MovementPattern
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class PlannedExerciseMeasureTest {

    @Test
    fun rejectsBothOrNeitherMeasure() {
        assertFailsWith<IllegalArgumentException> {
            PlannedExercise("plank", sets = 3, reps = 10, durationSeconds = 45)
        }
        assertFailsWith<IllegalArgumentException> {
            PlannedExercise("plank", sets = 3, reps = null, durationSeconds = null)
        }
    }

    @Test
    fun acceptsExactlyOneMeasure() {
        val counted = PlannedExercise("plank", sets = 3, reps = 10)
        assertEquals(10, counted.reps)
        assertNull(counted.durationSeconds)

        val timed = PlannedExercise("plank", sets = 3, durationSeconds = 45)
        assertNull(timed.reps)
        assertEquals(45, timed.durationSeconds)
    }

    @Test
    fun acceptedExerciseEnforcesTheSameInvariant() {
        assertFailsWith<IllegalArgumentException> {
            AcceptedExercise(
                exerciseId = "plank",
                sets = 3,
                reps = 10,
                durationSeconds = 45,
                name = "Plank",
                movementPattern = MovementPattern.CORE
            )
        }
        assertFailsWith<IllegalArgumentException> {
            AcceptedExercise(
                exerciseId = "plank",
                sets = 3,
                reps = null,
                durationSeconds = null,
                name = "Plank",
                movementPattern = MovementPattern.CORE
            )
        }
    }

    @Test
    fun toWeeklyPlanCarriesTheTimedMeasure() {
        val plan = AcceptedPlan(
            engine = PlannerEngineId.DETERMINISTIC,
            acceptedAtMillis = 0L,
            days = listOf(
                AcceptedDay(
                    dayIndex = 0,
                    focus = SplitFocus.PUSH,
                    exercises = listOf(
                        AcceptedExercise(
                            exerciseId = "plank",
                            sets = 3,
                            durationSeconds = 45,
                            name = "Plank",
                            movementPattern = MovementPattern.CORE
                        )
                    )
                )
            )
        )

        val slot = plan.toWeeklyPlan().days.single().exercises.single()
        assertNull(slot.reps)
        assertEquals(45, slot.durationSeconds)
    }
}
