package com.hydrafit.app.core.domain.engine

import com.hydrafit.app.core.domain.equipment.EquipmentTag
import com.hydrafit.app.core.domain.equipment.Exercise
import com.hydrafit.app.core.domain.fatigue.MuscleGroup
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DeterministicWorkoutPlannerEngineTest {

    private val engine = DeterministicWorkoutPlannerEngine(EmptyCatalog)

    @Test
    fun reportsDeterministicEngineId() {
        val plan = engine.plan(request(daysPerWeek = 2), catalog())

        assertEquals(PlannerEngineId.DETERMINISTIC, plan.engine)
    }

    @Test
    fun producesRequestedNumberOfDays() {
        val plan = engine.plan(request(daysPerWeek = 4), catalog())

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
            exercise("squat", MuscleGroup.QUADS, required = setOf(EquipmentTag.BARBELL)),
            exercise("goblet-squat", MuscleGroup.QUADS, required = setOf(EquipmentTag.DUMBBELL))
        )

        val plan = engine.plan(
            request(daysPerWeek = 2, equipment = setOf(EquipmentTag.DUMBBELL)),
            exercises
        )

        val plannedIds = plan.days.flatMap { day -> day.exercises.map { it.exerciseId } }.toSet()
        assertEquals(setOf("goblet-squat"), plannedIds)
    }

    @Test
    fun onlySelectsExercisesMatchingTheDayFocus() {
        val exercises = listOf(
            exercise("bench-press", MuscleGroup.CHEST),
            exercise("barbell-row", MuscleGroup.BACK)
        )

        val plan = engine.plan(
            request(daysPerWeek = 3, split = SplitType.PUSH_PULL_LEGS),
            exercises
        )

        val pushIds = plan.days.first {
            it.focus == SplitFocus.PUSH
        }.exercises.map { it.exerciseId }
        val pullIds = plan.days.first {
            it.focus == SplitFocus.PULL
        }.exercises.map { it.exerciseId }
        assertEquals(listOf("bench-press"), pushIds)
        assertEquals(listOf("barbell-row"), pullIds)
    }

    @Test
    fun prefersExercisesWhosePrimaryMusclesAreLessFatigued() {
        val exercises = listOf(
            exercise("bench-press", MuscleGroup.CHEST),
            exercise("overhead-press", MuscleGroup.SHOULDERS)
        )

        val plan = engine.plan(
            request(
                daysPerWeek = 3,
                split = SplitType.PUSH_PULL_LEGS,
                fatigue = mapOf(MuscleGroup.CHEST to 0.9)
            ),
            exercises
        )

        val pushIds = plan.days.first {
            it.focus == SplitFocus.PUSH
        }.exercises.map { it.exerciseId }
        assertEquals(listOf("overhead-press", "bench-press"), pushIds)
    }

    @Test
    fun limitsNumberOfExercisesPerDay() {
        val exercises = List(8) { index -> exercise("chest-$index", MuscleGroup.CHEST) }

        val plan = engine.plan(request(daysPerWeek = 2), exercises)

        plan.days.forEach { day ->
            assertEquals(
                DeterministicWorkoutPlannerEngine.MAX_EXERCISES_PER_DAY,
                day.exercises.size
            )
        }
    }

    @Test
    fun assignsDefaultSetsAndReps() {
        val plan = engine.plan(
            request(daysPerWeek = 2),
            listOf(exercise("push-up", MuscleGroup.CHEST))
        )

        val planned = plan.days.first().exercises.single()
        assertEquals(DeterministicWorkoutPlannerEngine.DEFAULT_SETS, planned.sets)
        assertEquals(DeterministicWorkoutPlannerEngine.DEFAULT_REPS, planned.reps)
    }

    @Test
    fun isDeterministicForTheSameInput() {
        val exercises = catalog()
        val request = request(daysPerWeek = 4)

        assertEquals(engine.plan(request, exercises), engine.plan(request, exercises))
    }

    @Test
    fun returnsDaysWithNoExercisesWhenNothingMatchesTheFocus() {
        val plan = engine.plan(
            request(daysPerWeek = 3, split = SplitType.PUSH_PULL_LEGS),
            listOf(exercise("plank", MuscleGroup.CORE))
        )

        assertTrue(plan.days.all { it.exercises.isEmpty() })
    }

    private fun request(
        daysPerWeek: Int,
        equipment: Set<EquipmentTag> = emptySet(),
        fatigue: Map<MuscleGroup, Double> = emptyMap(),
        split: SplitType = SplitType.AUTO
    ) = PlanRequest(
        daysPerWeek = daysPerWeek,
        availableEquipment = equipment,
        muscleFatigue = fatigue,
        splitPreference = split,
        nowMillis = 0L
    )

    private fun exercise(
        id: String,
        primary: MuscleGroup,
        required: Set<EquipmentTag> = emptySet()
    ) = Exercise(
        id = id,
        name = id,
        requiredEquipment = required,
        primaryMuscles = setOf(primary)
    )

    private fun catalog(): List<Exercise> = listOf(
        exercise("bench-press", MuscleGroup.CHEST),
        exercise("barbell-row", MuscleGroup.BACK),
        exercise("squat", MuscleGroup.QUADS)
    )

    private object EmptyCatalog : ExerciseCatalog {
        override suspend fun all(): List<Exercise> = emptyList()
    }
}
