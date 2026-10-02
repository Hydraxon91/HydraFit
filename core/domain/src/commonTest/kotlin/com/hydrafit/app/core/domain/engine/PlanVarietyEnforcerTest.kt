package com.hydrafit.app.core.domain.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PlanVarietyEnforcerTest {

    private val enforcer = PlanVarietyEnforcer()
    private val accessoryIds = setOf("fly")

    @Test
    fun dropsDuplicateExercisesWithinADay() {
        val plan = weekly(
            day(0, SplitFocus.PUSH, "bench", "bench", "fly")
        )

        val enforced = requireNotNull(enforce(plan))

        assertEquals(listOf("bench", "fly"), enforced.days.single().exercises.map { it.exerciseId })
    }

    @Test
    fun keepsDaysThatLegitimatelyRepeatTheSameFocus() {
        val plan = weekly(
            day(0, SplitFocus.FULL_BODY, "bench", "fly"),
            day(1, SplitFocus.FULL_BODY, "row", "fly"),
            day(2, SplitFocus.FULL_BODY, "squat", "fly")
        )

        val enforced = requireNotNull(enforce(plan, daysPerWeek = 3))

        assertEquals(3, enforced.days.size)
        assertEquals(List(3) { SplitFocus.FULL_BODY }, enforced.days.map { it.focus })
    }

    @Test
    fun rejectsAWeekThatCollapsesOntoOneFocus() {
        val plan = weekly(
            day(0, SplitFocus.PUSH, "bench", "fly"),
            day(1, SplitFocus.PUSH, "row", "fly"),
            day(2, SplitFocus.PUSH, "squat", "fly")
        )

        assertNull(enforce(plan, daysPerWeek = 3, split = SplitType.PUSH_PULL_LEGS))
    }

    @Test
    fun keepsAccessoryExercisesRepeatedAcrossDays() {
        val plan = weekly(
            day(0, SplitFocus.PUSH, "bench", "fly"),
            day(1, SplitFocus.PULL, "row", "fly")
        )

        val enforced = requireNotNull(enforce(plan, daysPerWeek = 2))

        assertEquals(listOf("fly"), enforced.days.map { it.exercises.last().exerciseId }.distinct())
    }

    @Test
    fun dropsACompoundExerciseRepeatedOnAnotherDay() {
        val plan = weekly(
            day(0, SplitFocus.PUSH, "bench", "fly"),
            day(1, SplitFocus.PULL, "bench", "row", "fly")
        )

        val enforced = requireNotNull(enforce(plan, daysPerWeek = 2))

        assertEquals(listOf("row", "fly"), enforced.days[1].exercises.map { it.exerciseId })
    }

    @Test
    fun returnsNullWhenADayFallsBelowTheMinimumAfterDedupe() {
        val plan = weekly(
            day(0, SplitFocus.PUSH, "bench", "fly"),
            day(1, SplitFocus.PULL, "bench")
        )

        assertNull(enforce(plan, daysPerWeek = 2))
    }

    @Test
    fun returnsNullWhenTheFocusCycleIsIncomplete() {
        val plan = weekly(
            day(0, SplitFocus.PUSH, "bench", "fly"),
            day(1, SplitFocus.PUSH, "row", "fly")
        )

        assertNull(enforce(plan, daysPerWeek = 2, split = SplitType.UPPER_LOWER))
    }

    @Test
    fun treatsUnknownExercisesAsCompoundSoRepeatsAreRemoved() {
        val plan = weekly(
            day(0, SplitFocus.PUSH, "mystery", "fly"),
            day(1, SplitFocus.PULL, "mystery", "fly")
        )

        assertNull(enforce(plan, daysPerWeek = 2))
    }

    private fun enforce(
        plan: WeeklyPlan,
        daysPerWeek: Int = plan.days.size,
        split: SplitType = SplitType.AUTO
    ): WeeklyPlan? = enforcer.enforce(plan, request(daysPerWeek, split)) { it !in accessoryIds }

    private fun request(daysPerWeek: Int, split: SplitType = SplitType.AUTO) = PlanRequest(
        daysPerWeek = daysPerWeek,
        availableEquipment = emptySet(),
        muscleFatigue = emptyMap(),
        nowMillis = 0L,
        splitPreference = split
    )

    private fun weekly(vararg days: WorkoutDay) =
        WeeklyPlan(engine = PlannerEngineId.LOCAL_LLM, days = days.toList())

    private fun day(index: Int, focus: SplitFocus, vararg exerciseIds: String) = WorkoutDay(
        dayIndex = index,
        focus = focus,
        exercises = exerciseIds.map { PlannedExercise(it, sets = 3, reps = 8) }
    )
}
