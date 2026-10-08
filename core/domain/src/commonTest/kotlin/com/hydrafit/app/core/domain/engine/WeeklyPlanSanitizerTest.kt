package com.hydrafit.app.core.domain.engine

import com.hydrafit.app.core.domain.equipment.EquipmentTag
import com.hydrafit.app.core.domain.equipment.Exercise
import com.hydrafit.app.core.domain.equipment.ExerciseLoadCapability
import com.hydrafit.app.core.domain.equipment.MovementPattern
import com.hydrafit.app.core.domain.fatigue.MuscleGroup
import com.hydrafit.app.core.domain.workout.LoadKind
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
        // The set override carries volume; reps stay at the goal's compound band.
        assertEquals(TrainingGoal.BALANCED.compoundReps, compound.reps)
        assertEquals(2, isolation.sets)
        assertEquals(TrainingGoal.BALANCED.isolationReps, isolation.reps)
        assertEquals(2, sanitized.armCoverage.size)
        assertTrue(
            sanitized.armCoverage.all {
                it.unmetReason == ArmCoverageUnmetReason.NO_COMPATIBLE_AVAILABLE_CANDIDATE
            }
        )
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
    fun deloadWeekScalesSetsAndModelWeights() = runTest {
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
                            suggestedWeightKg = 100.0
                        ),
                        PlannedExercise("lateral-raise", sets = 3, reps = 8)
                    )
                )
            )
        )

        val sanitized = sanitizer.sanitize(
            plan,
            request(setsPerExercise = 4, includeWorkoutData = true, isDeload = true)
        )!!

        val compound = sanitized.days.single().exercises.first { it.exerciseId == "bench-press" }
        // 4 sets x 0.7 = 2.8 -> 3 sets
        assertEquals(3, compound.sets)
        // model weight 100 x 0.8 = 80
        assertEquals(80.0, compound.suggestedWeightKg)
        assertTrue(sanitized.armCoverage.none { it.isTargetEnforced })
    }

    @Test
    fun carriesTheWeekAndCycleOntoThePlan() = runTest {
        val sanitized = sanitizer.sanitize(
            planOf(listOf("bench-press", "lateral-raise")),
            request().copy(weekNumber = 3, cycleNumber = 2)
        )!!

        assertEquals(3, sanitized.weekNumber)
        assertEquals(2, sanitized.cycleNumber)
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
            days = listOf(
                dayOf(0, listOf("bench-press", "lateral-raise"), SplitFocus.PUSH),
                dayOf(1, listOf("overhead-press", "lateral-raise"), SplitFocus.PULL),
                dayOf(2, listOf("barbell-row", "lateral-raise"), SplitFocus.LEGS),
                dayOf(3, listOf("bench-press", "lateral-raise"), SplitFocus.UPPER)
            )
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

    @Test
    fun stripsAModelWeightForABodyweightOnlyExercise() = runTest {
        val plan = WeeklyPlan(
            engine = PlannerEngineId.GEMINI_API,
            days = listOf(
                WorkoutDay(
                    dayIndex = 0,
                    focus = SplitFocus.FULL_BODY,
                    exercises = listOf(
                        PlannedExercise(
                            exerciseId = "ab-roll",
                            sets = 3,
                            reps = 10,
                            suggestedWeightKg = 32.5
                        ),
                        PlannedExercise(
                            exerciseId = "bench-press",
                            sets = 3,
                            reps = 8,
                            suggestedWeightKg = 82.5
                        )
                    )
                )
            )
        )

        val sanitized = sanitizer.sanitize(plan, request(includeWorkoutData = true))!!

        val entry = sanitized.days.single().exercises
            .first { it.exerciseId == "ab-roll" }
        assertNull(entry.suggestedWeightKg)
        assertEquals(LoadKind.BODYWEIGHT, entry.loadKind)
        assertEquals(ExerciseLoadCapability.BODYWEIGHT_ONLY, entry.loadCapability)
    }

    private fun planOf(exerciseIds: List<String>) = WeeklyPlan(
        engine = PlannerEngineId.LOCAL_LLM,
        days = listOf(dayOf(0, exerciseIds))
    )

    private fun dayOf(
        index: Int,
        exerciseIds: List<String>,
        focus: SplitFocus = SplitFocus.FULL_BODY
    ) = WorkoutDay(
        dayIndex = index,
        focus = focus,
        exercises = exerciseIds.map { PlannedExercise(it, sets = 3, reps = 8) }
    )

    private fun request(
        daysPerWeek: Int = 1,
        goal: TrainingGoal = TrainingGoal.BALANCED,
        setsPerExercise: Int = goal.defaultSets,
        accessorySetsPerExercise: Int = goal.accessorySets,
        includeWorkoutData: Boolean = false,
        isDeload: Boolean = false
    ) = PlanRequest(
        daysPerWeek = daysPerWeek,
        availableEquipment = setOf(EquipmentTag.BARBELL),
        muscleFatigue = emptyMap(),
        nowMillis = 0L,
        goal = goal,
        setsPerExercise = setsPerExercise,
        accessorySetsPerExercise = accessorySetsPerExercise,
        includeWorkoutData = includeWorkoutData,
        isDeload = isDeload
    )

    private object FakeCatalog : ExerciseCatalog {
        override suspend fun all(): List<Exercise> = listOf(
            exercise("bench-press", MovementPattern.HORIZONTAL_PUSH, EquipmentTag.BARBELL),
            exercise("overhead-press", MovementPattern.VERTICAL_PUSH, EquipmentTag.BARBELL),
            exercise("barbell-row", MovementPattern.HORIZONTAL_PULL, EquipmentTag.BARBELL),
            exercise("lateral-raise", MovementPattern.SHOULDER_ISOLATION, EquipmentTag.BARBELL),
            exercise("dumbbell-curl", MovementPattern.BICEPS_ISOLATION, EquipmentTag.DUMBBELL),
            exercise(
                "ab-roll",
                MovementPattern.CORE,
                EquipmentTag.BODYWEIGHT,
                loadCapability = ExerciseLoadCapability.BODYWEIGHT_ONLY
            )
        )

        private fun exercise(
            id: String,
            pattern: MovementPattern,
            equipment: EquipmentTag,
            loadCapability: ExerciseLoadCapability = ExerciseLoadCapability.EXTERNAL
        ) = Exercise(
            id = id,
            name = id,
            requiredEquipment = setOf(equipment),
            primaryMuscles = setOf(MuscleGroup.CHEST_UPPER),
            movementPattern = pattern,
            loadCapability = loadCapability
        )
    }
}
