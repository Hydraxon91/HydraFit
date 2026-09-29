package com.hydrafit.app.core.domain.engine

import com.hydrafit.app.core.domain.equipment.EquipmentTag
import com.hydrafit.app.core.domain.equipment.Exercise
import com.hydrafit.app.core.domain.equipment.MovementPattern
import com.hydrafit.app.core.domain.fatigue.MuscleGroup
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class WeeklyPlanSanitizerTest {

    private val sanitizer = WeeklyPlanSanitizer(FakeCatalog)

    @Test
    fun appliesRequestedSetsAndVolumeAwareReps() = runTest {
        val plan = planOf(listOf("bench-press", "lateral-raise"))

        val sanitized = sanitizer.sanitize(
            plan,
            request(setsPerExercise = 5, accessorySetsPerExercise = 2)
        )!!

        val compound = sanitized.days.single().exercises.first { it.exerciseId == "bench-press" }
        val isolation = sanitized.days.single().exercises.first { it.exerciseId == "lateral-raise" }
        assertEquals(5, compound.sets)
        // Balanced compound volume 3 x 6 = 18 -> 18/5 = 3.6 -> 4 reps
        assertEquals(4, compound.reps)
        assertEquals(2, isolation.sets)
        // Balanced accessory volume 2 x 12 = 24 -> 24/2 = 12 reps
        assertEquals(12, isolation.reps)
    }

    @Test
    fun appliesTheSelectedGoalsTargetsToModelPlans() = runTest {
        val plan = planOf(listOf("bench-press", "lateral-raise"))

        val sanitized = sanitizer.sanitize(plan, request(goal = TrainingGoal.STRENGTH))!!
        val exercises = sanitized.days.single().exercises.associateBy { it.exerciseId }

        assertEquals(TrainingGoal.STRENGTH.defaultSets, exercises.getValue("bench-press").sets)
        assertEquals(TrainingGoal.STRENGTH.compoundReps, exercises.getValue("bench-press").reps)
        assertEquals(TrainingGoal.STRENGTH.accessorySets, exercises.getValue("lateral-raise").sets)
        assertEquals(TrainingGoal.STRENGTH.isolationReps, exercises.getValue("lateral-raise").reps)
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

    @Test
    fun carriesAValidatedSuggestedWeightOnlyWhenSharingIsOn() = runTest {
        val plan = WeeklyPlan(
            engine = PlannerEngineId.GEMINI_API,
            days = listOf(
                WorkoutDay(
                    dayIndex = 0,
                    focus = SplitFocus.FULL_BODY,
                    exercises = listOf(
                        PlannedExercise(
                            exerciseId = "bench-press",
                            sets = 3,
                            reps = 8,
                            suggestedWeightKg = 82.5
                        ),
                        PlannedExercise(
                            exerciseId = "lateral-raise",
                            sets = 3,
                            reps = 12,
                            suggestedWeightKg = 99_999.0
                        )
                    )
                )
            )
        )

        val off = sanitizer.sanitize(plan, request())!!
        assertTrue(off.days.single().exercises.all { it.suggestedWeightKg == null })

        val on = sanitizer.sanitize(plan, request(includeWorkoutData = true))!!
        val byId = on.days.single().exercises.associateBy { it.exerciseId }
        assertEquals(82.5, byId.getValue("bench-press").suggestedWeightKg)
        assertNull(byId.getValue("lateral-raise").suggestedWeightKg)
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

    private fun request(
        daysPerWeek: Int = 1,
        goal: TrainingGoal = TrainingGoal.BALANCED,
        setsPerExercise: Int = goal.defaultSets,
        accessorySetsPerExercise: Int = goal.accessorySets,
        includeWorkoutData: Boolean = false
    ) = PlanRequest(
        daysPerWeek = daysPerWeek,
        availableEquipment = setOf(EquipmentTag.BARBELL),
        muscleFatigue = emptyMap(),
        nowMillis = 0L,
        goal = goal,
        setsPerExercise = setsPerExercise,
        accessorySetsPerExercise = accessorySetsPerExercise,
        includeWorkoutData = includeWorkoutData
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
