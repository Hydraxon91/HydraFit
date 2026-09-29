package com.hydrafit.app.core.domain.engine

import com.hydrafit.app.core.domain.equipment.EquipmentTag
import com.hydrafit.app.core.domain.equipment.Exercise
import com.hydrafit.app.core.domain.equipment.MovementPattern
import com.hydrafit.app.core.domain.fatigue.MuscleGroup
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DeterministicWorkoutPlannerEngineTest {

    private val engine = DeterministicWorkoutPlannerEngine(EmptyCatalog)
    private val everything = setOf(
        EquipmentTag.BARBELL,
        EquipmentTag.DUMBBELL,
        EquipmentTag.BENCH,
        EquipmentTag.PULL_UP_BAR
    )

    @Test
    fun reportsDeterministicEngineId() {
        val plan = engine.plan(request(daysPerWeek = 2), catalog())

        assertEquals(PlannerEngineId.DETERMINISTIC, plan.engine)
    }

    @Test
    fun producesRequestedNumberOfDays() {
        val plan = engine.plan(request(daysPerWeek = 4, equipment = everything), catalog())

        assertEquals(4, plan.days.size)
        assertEquals(listOf(0, 1, 2, 3), plan.days.map { it.dayIndex })
    }

    @Test
    fun rejectsDaysOutsideSupportedRange() {
        assertFailsWith<IllegalArgumentException> {
            engine.plan(request(daysPerWeek = 1), catalog())
        }
        assertFailsWith<IllegalArgumentException> {
            engine.plan(request(daysPerWeek = 7), catalog())
        }
    }

    @Test
    fun rejectsSetsOutsideSupportedRange() {
        assertFailsWith<IllegalArgumentException> {
            engine.plan(request(daysPerWeek = 2, setsPerExercise = 0), catalog())
        }
        assertFailsWith<IllegalArgumentException> {
            engine.plan(request(daysPerWeek = 2, setsPerExercise = 9), catalog())
        }
    }

    @Test
    fun autoSelectsFullBodyForTwoOrThreeDays() {
        val focuses = engine.plan(request(daysPerWeek = 2), catalog()).days.map { it.focus }

        assertEquals(listOf(SplitFocus.FULL_BODY, SplitFocus.FULL_BODY), focuses)
    }

    @Test
    fun autoSelectsUpperLowerForFourDays() {
        val focuses = engine.plan(request(daysPerWeek = 4), catalog()).days.map { it.focus }

        assertEquals(
            listOf(SplitFocus.UPPER, SplitFocus.LOWER, SplitFocus.UPPER, SplitFocus.LOWER),
            focuses
        )
    }

    @Test
    fun autoSelectsPushPullLegsForFiveDays() {
        val focuses = engine.plan(request(daysPerWeek = 5), catalog()).days.map { it.focus }

        assertEquals(
            listOf(
                SplitFocus.PUSH,
                SplitFocus.PULL,
                SplitFocus.LEGS,
                SplitFocus.PUSH,
                SplitFocus.PULL
            ),
            focuses
        )
    }

    @Test
    fun explicitPreferenceOverridesAutoSelection() {
        val focuses = engine
            .plan(request(daysPerWeek = 3, split = SplitType.PUSH_PULL_LEGS), catalog())
            .days
            .map { it.focus }

        assertEquals(listOf(SplitFocus.PUSH, SplitFocus.PULL, SplitFocus.LEGS), focuses)
    }

    @Test
    fun excludesExercisesWhoseEquipmentIsUnavailable() {
        val exercises = listOf(
            exercise("squat", MovementPattern.SQUAT, required = setOf(EquipmentTag.BARBELL)),
            exercise("goblet-squat", MovementPattern.SQUAT, required = setOf(EquipmentTag.DUMBBELL))
        )

        val plan = engine.plan(
            request(daysPerWeek = 2, equipment = setOf(EquipmentTag.DUMBBELL)),
            exercises
        )

        val plannedIds = plan.days.flatMap { day -> day.exercises.map { it.exerciseId } }.toSet()
        assertEquals(setOf("goblet-squat"), plannedIds)
    }

    @Test
    fun prefersBarbellBenchWhenAvailable() {
        val exercises = listOf(
            exercise(
                "barbell-bench-press",
                MovementPattern.HORIZONTAL_PUSH,
                MuscleGroup.CHEST,
                setOf(EquipmentTag.BARBELL, EquipmentTag.BENCH)
            ),
            exercise(
                "dumbbell-bench-press",
                MovementPattern.HORIZONTAL_PUSH,
                MuscleGroup.CHEST,
                setOf(EquipmentTag.DUMBBELL, EquipmentTag.BENCH)
            ),
            exercise("push-up", MovementPattern.HORIZONTAL_PUSH, MuscleGroup.CHEST)
        )

        val plan = engine.plan(request(daysPerWeek = 2, equipment = everything), exercises)

        val pushIds = plan.days.flatMap { it.exercises.map { it.exerciseId } }
        assertTrue("barbell-bench-press" in pushIds)
    }

    @Test
    fun prefersDumbbellBenchWhenBarbellIsUnavailable() {
        val exercises = listOf(
            exercise(
                "barbell-bench-press",
                MovementPattern.HORIZONTAL_PUSH,
                MuscleGroup.CHEST,
                setOf(EquipmentTag.BARBELL, EquipmentTag.BENCH)
            ),
            exercise(
                "dumbbell-bench-press",
                MovementPattern.HORIZONTAL_PUSH,
                MuscleGroup.CHEST,
                setOf(EquipmentTag.DUMBBELL, EquipmentTag.BENCH)
            )
        )

        val plan = engine.plan(
            request(
                daysPerWeek = 2,
                equipment = setOf(EquipmentTag.DUMBBELL, EquipmentTag.BENCH)
            ),
            exercises
        )

        val pushIds = plan.days.flatMap { it.exercises.map { it.exerciseId } }
        assertTrue("dumbbell-bench-press" in pushIds)
    }

    @Test
    fun fallsBackToBodyweightWhenNothingElseIsAvailable() {
        val exercises = listOf(
            exercise(
                "barbell-bench-press",
                MovementPattern.HORIZONTAL_PUSH,
                MuscleGroup.CHEST,
                setOf(EquipmentTag.BARBELL, EquipmentTag.BENCH)
            ),
            exercise("push-up", MovementPattern.HORIZONTAL_PUSH, MuscleGroup.CHEST)
        )

        val plan = engine.plan(request(daysPerWeek = 2, equipment = emptySet()), exercises)

        val pushIds = plan.days.flatMap { it.exercises.map { it.exerciseId } }
        assertEquals(listOf("push-up"), pushIds)
    }

    @Test
    fun neverRepeatsAPatternWithinADay() {
        val exercises = listOf(
            exercise("bench-press", MovementPattern.HORIZONTAL_PUSH, MuscleGroup.CHEST),
            exercise("push-up", MovementPattern.HORIZONTAL_PUSH, MuscleGroup.CHEST)
        )

        val plan = engine.plan(
            request(daysPerWeek = 3, split = SplitType.PUSH_PULL_LEGS, equipment = everything),
            exercises
        )

        val pushDay = plan.days.first { it.focus == SplitFocus.PUSH }
        assertEquals(listOf("bench-press"), pushDay.exercises.map { it.exerciseId })
    }

    @Test
    fun fullBodyDaysUseDifferentTemplates() {
        val plan = engine.plan(
            request(daysPerWeek = 3, equipment = everything),
            catalog()
        )

        val idsByDay = plan.days.map { day -> day.exercises.map { it.exerciseId } }
        assertTrue(idsByDay[0].isNotEmpty())
        assertTrue(idsByDay[0] != idsByDay[1])
        assertTrue(idsByDay[1] != idsByDay[2])
    }

    @Test
    fun prefersExercisesWhosePrimaryMusclesAreLessFatigued() {
        val exercises = listOf(
            exercise("bench-press", MovementPattern.HORIZONTAL_PUSH, MuscleGroup.CHEST),
            exercise("push-up", MovementPattern.HORIZONTAL_PUSH, MuscleGroup.SHOULDERS)
        )

        val plan = engine.plan(
            request(
                daysPerWeek = 3,
                split = SplitType.PUSH_PULL_LEGS,
                fatigue = mapOf(MuscleGroup.CHEST to 0.6)
            ),
            exercises
        )

        val pushIds = plan.days.first {
            it.focus == SplitFocus.PUSH
        }.exercises.map { it.exerciseId }
        assertEquals(listOf("push-up"), pushIds)
    }

    @Test
    fun prefersAFreshAlternativeOverASorePreferredEquipment() {
        val exercises = listOf(
            exercise(
                "barbell-bench-press",
                MovementPattern.HORIZONTAL_PUSH,
                MuscleGroup.CHEST,
                setOf(EquipmentTag.BARBELL, EquipmentTag.BENCH)
            ),
            exercise("push-up", MovementPattern.HORIZONTAL_PUSH, MuscleGroup.SHOULDERS)
        )

        val plan = engine.plan(
            request(
                daysPerWeek = 3,
                split = SplitType.PUSH_PULL_LEGS,
                equipment = everything,
                fatigue = mapOf(MuscleGroup.CHEST to 0.6)
            ),
            exercises
        )

        val pushIds = plan.days.first { it.focus == SplitFocus.PUSH }
            .exercises.map { it.exerciseId }
        assertEquals(listOf("push-up"), pushIds)
    }

    @Test
    fun skipsExercisesAboveTheFatigueSkipThreshold() {
        val plan = engine.plan(
            request(
                daysPerWeek = 3,
                split = SplitType.PUSH_PULL_LEGS,
                fatigue = mapOf(MuscleGroup.CHEST to 0.9)
            ),
            listOf(exercise("bench-press", MovementPattern.HORIZONTAL_PUSH, MuscleGroup.CHEST))
        )

        val pushDay = plan.days.first { it.focus == SplitFocus.PUSH }
        assertTrue(pushDay.exercises.isEmpty())
    }

    @Test
    fun reducesSetsWhenAPrimaryMuscleIsFatigued() {
        val plan = engine.plan(
            request(
                daysPerWeek = 3,
                split = SplitType.PUSH_PULL_LEGS,
                fatigue = mapOf(MuscleGroup.CHEST to 0.6),
                setsPerExercise = 4
            ),
            listOf(exercise("bench-press", MovementPattern.HORIZONTAL_PUSH, MuscleGroup.CHEST))
        )

        val planned = plan.days.first { it.focus == SplitFocus.PUSH }.exercises.single()
        assertEquals(3, planned.sets)
    }

    @Test
    fun honorsRequestedSetsCount() {
        val plan = engine.plan(
            request(daysPerWeek = 3, split = SplitType.PUSH_PULL_LEGS, setsPerExercise = 5),
            listOf(exercise("bench-press", MovementPattern.HORIZONTAL_PUSH, MuscleGroup.CHEST))
        )

        val planned = plan.days.first { it.focus == SplitFocus.PUSH }.exercises.single()
        assertEquals(5, planned.sets)
    }

    @Test
    fun usesCompoundRepsForCompoundPatternsAndHigherRepsForIsolation() {
        val plan = engine.plan(
            request(daysPerWeek = 3, split = SplitType.PUSH_PULL_LEGS),
            listOf(
                exercise("bench-press", MovementPattern.HORIZONTAL_PUSH, MuscleGroup.CHEST),
                exercise("pushdown", MovementPattern.TRICEPS_ISOLATION, MuscleGroup.TRICEPS)
            )
        )

        val push = plan.days.first { it.focus == SplitFocus.PUSH }.exercises.associateBy {
            it.exerciseId
        }
        assertEquals(
            DeterministicWorkoutPlannerEngine.COMPOUND_REPS,
            push.getValue("bench-press").reps
        )
        assertEquals(
            DeterministicWorkoutPlannerEngine.ISOLATION_REPS,
            push.getValue("pushdown").reps
        )
    }

    @Test
    fun appliesGoalSpecificRepTargetsAndDefaultSets() {
        TrainingGoal.entries.forEach { goal ->
            val plan = engine.plan(
                request(daysPerWeek = 3, split = SplitType.PUSH_PULL_LEGS, goal = goal),
                listOf(
                    exercise("bench-press", MovementPattern.HORIZONTAL_PUSH, MuscleGroup.CHEST),
                    exercise("pushdown", MovementPattern.TRICEPS_ISOLATION, MuscleGroup.TRICEPS)
                )
            )
            val push = plan.days.first { it.focus == SplitFocus.PUSH }.exercises.associateBy {
                it.exerciseId
            }

            assertEquals(goal.defaultSets, push.getValue("bench-press").sets)
            assertEquals(goal.compoundReps, push.getValue("bench-press").reps)
            assertEquals(goal.defaultSets, push.getValue("pushdown").sets)
            assertEquals(goal.isolationReps, push.getValue("pushdown").reps)
        }
    }

    @Test
    fun isDeterministicForTheSameInput() {
        val exercises = catalog()
        val request = request(daysPerWeek = 4, equipment = everything)

        assertEquals(engine.plan(request, exercises), engine.plan(request, exercises))
    }

    @Test
    fun returnsDaysWithNoExercisesWhenNothingMatchesTheFocus() {
        val plan = engine.plan(
            request(daysPerWeek = 3, split = SplitType.PUSH_PULL_LEGS),
            listOf(exercise("plank", MovementPattern.CORE))
        )

        val byFocus = plan.days.associateBy { it.focus }
        assertTrue(byFocus.getValue(SplitFocus.PUSH).exercises.isEmpty())
        assertTrue(byFocus.getValue(SplitFocus.PULL).exercises.isEmpty())
        assertEquals(
            listOf("plank"),
            byFocus.getValue(SplitFocus.LEGS).exercises.map {
                it.exerciseId
            }
        )
    }

    private fun request(
        daysPerWeek: Int,
        equipment: Set<EquipmentTag> = emptySet(),
        fatigue: Map<MuscleGroup, Double> = emptyMap(),
        split: SplitType = SplitType.AUTO,
        goal: TrainingGoal = TrainingGoal.BALANCED,
        setsPerExercise: Int = goal.defaultSets
    ) = PlanRequest(
        daysPerWeek = daysPerWeek,
        availableEquipment = equipment,
        muscleFatigue = fatigue,
        splitPreference = split,
        nowMillis = 0L,
        goal = goal,
        setsPerExercise = setsPerExercise
    )

    private fun exercise(
        id: String,
        pattern: MovementPattern,
        primary: MuscleGroup = MuscleGroup.CORE,
        required: Set<EquipmentTag> = emptySet()
    ) = Exercise(
        id = id,
        name = id,
        requiredEquipment = required,
        primaryMuscles = setOf(primary),
        movementPattern = pattern
    )

    private fun catalog(): List<Exercise> = listOf(
        exercise(
            "back-squat",
            MovementPattern.SQUAT,
            MuscleGroup.QUADS,
            setOf(EquipmentTag.BARBELL)
        ),
        exercise(
            "goblet-squat",
            MovementPattern.SQUAT,
            MuscleGroup.QUADS,
            setOf(EquipmentTag.DUMBBELL)
        ),
        exercise("bench-press", MovementPattern.HORIZONTAL_PUSH, MuscleGroup.CHEST),
        exercise("barbell-row", MovementPattern.HORIZONTAL_PULL, MuscleGroup.BACK),
        exercise("plank", MovementPattern.CORE),
        exercise("rdl", MovementPattern.HINGE, MuscleGroup.HAMSTRINGS),
        exercise("ohp", MovementPattern.VERTICAL_PUSH, MuscleGroup.SHOULDERS),
        exercise(
            "pull-up",
            MovementPattern.VERTICAL_PULL,
            MuscleGroup.BACK,
            setOf(EquipmentTag.PULL_UP_BAR)
        ),
        exercise("calf-raise", MovementPattern.CALF_RAISE, MuscleGroup.CALVES),
        exercise("lunge", MovementPattern.LUNGE, MuscleGroup.QUADS),
        exercise("curl", MovementPattern.BICEPS_ISOLATION, MuscleGroup.BICEPS)
    )

    private object EmptyCatalog : ExerciseCatalog {
        override suspend fun all(): List<Exercise> = emptyList()
    }
}
