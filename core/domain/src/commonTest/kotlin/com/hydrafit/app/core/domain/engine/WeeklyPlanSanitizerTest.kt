package com.hydrafit.app.core.domain.engine

import com.hydrafit.app.core.domain.equipment.EquipmentTag
import com.hydrafit.app.core.domain.equipment.Exercise
import com.hydrafit.app.core.domain.equipment.MovementPattern
import com.hydrafit.app.core.domain.fatigue.MuscleGroup
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest

class WeeklyPlanSanitizerTest {

    private val sanitizer = WeeklyPlanSanitizer(FakeCatalog)

    @Test
    fun appliesRequestedSetsAndCompoundIsolationReps() = runTest {
        val plan = planOf(listOf("bench-press", "lateral-raise"))

        val sanitized = sanitizer.sanitize(plan, request(setsPerExercise = 5))!!

        val compound = sanitized.days.single().exercises.first { it.exerciseId == "bench-press" }
        val isolation = sanitized.days.single().exercises.first { it.exerciseId == "lateral-raise" }
        assertEquals(5, compound.sets)
        assertEquals(DeterministicWorkoutPlannerEngine.COMPOUND_REPS, compound.reps)
        assertEquals(5, isolation.sets)
        assertEquals(DeterministicWorkoutPlannerEngine.ISOLATION_REPS, isolation.reps)
    }

    @Test
    fun dropsUnknownExercises() = runTest {
        val plan = planOf(listOf("bench-press", "lateral-raise", "not-a-real-id"))

        val sanitized = sanitizer.sanitize(plan, request())!!

        assertEquals(
            listOf("bench-press", "lateral-raise"),
            sanitized.days.single().exercises.map { it.exerciseId }
        )
    }

    @Test
    fun dropsExercisesWhoseEquipmentIsNotAvailable() = runTest {
        val plan = planOf(listOf("bench-press", "lateral-raise", "dumbbell-curl"))

        val sanitized = sanitizer.sanitize(plan, request())!!

        assertEquals(
            listOf("bench-press", "lateral-raise"),
            sanitized.days.single().exercises.map { it.exerciseId }
        )
    }

    @Test
    fun rejectsAPlanWithTooFewDays() = runTest {
        val plan = WeeklyPlan(
            engine = PlannerEngineId.LOCAL_LLM,
            days = listOf(dayOf(0, listOf("bench-press", "lateral-raise")))
        )

        assertNull(sanitizer.sanitize(plan, request(daysPerWeek = 3)))
    }

    @Test
    fun rejectsADayWithTooFewUsableExercises() = runTest {
        val plan = planOf(listOf("bench-press", "not-a-real-id"))

        assertNull(sanitizer.sanitize(plan, request()))
    }

    @Test
    fun trimsExtraDaysAndReindexes() = runTest {
        val plan = WeeklyPlan(
            engine = PlannerEngineId.GEMINI_API,
            days = List(4) { index -> dayOf(index, listOf("bench-press", "lateral-raise")) }
        )

        val sanitized = sanitizer.sanitize(plan, request(daysPerWeek = 3))!!

        assertEquals(3, sanitized.days.size)
        assertEquals(listOf(0, 1, 2), sanitized.days.map { it.dayIndex })
    }

    private fun planOf(exerciseIds: List<String>) = WeeklyPlan(
        engine = PlannerEngineId.LOCAL_LLM,
        days = listOf(dayOf(0, exerciseIds))
    )

    private fun dayOf(index: Int, exerciseIds: List<String>) = WorkoutDay(
        dayIndex = index,
        focus = SplitFocus.FULL_BODY,
        exercises = exerciseIds.map { PlannedExercise(it, sets = 3, reps = 8) }
    )

    private fun request(daysPerWeek: Int = 1, setsPerExercise: Int = 3) = PlanRequest(
        daysPerWeek = daysPerWeek,
        availableEquipment = setOf(EquipmentTag.BARBELL),
        muscleFatigue = emptyMap(),
        nowMillis = 0L,
        setsPerExercise = setsPerExercise
    )

    private object FakeCatalog : ExerciseCatalog {
        override suspend fun all(): List<Exercise> = listOf(
            exercise("bench-press", MovementPattern.HORIZONTAL_PUSH, EquipmentTag.BARBELL),
            exercise("lateral-raise", MovementPattern.SHOULDER_ISOLATION, EquipmentTag.BARBELL),
            exercise("dumbbell-curl", MovementPattern.BICEPS_ISOLATION, EquipmentTag.DUMBBELL)
        )

        private fun exercise(id: String, pattern: MovementPattern, equipment: EquipmentTag) =
            Exercise(
                id = id,
                name = id,
                requiredEquipment = setOf(equipment),
                primaryMuscles = setOf(MuscleGroup.CHEST),
                movementPattern = pattern
            )
    }
}
