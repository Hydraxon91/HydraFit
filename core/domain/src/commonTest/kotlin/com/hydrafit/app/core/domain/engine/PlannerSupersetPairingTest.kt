package com.hydrafit.app.core.domain.engine

import com.hydrafit.app.core.domain.fatigue.MuscleGroup
import com.hydrafit.app.core.domain.routine.Superset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlinx.coroutines.test.runTest

class PlannerSupersetPairingTest {
    private val preference =
        PlannerSupersetPreference(transitionSeconds = 10, pairRestSeconds = 120)
    private val plan = WeeklyPlan(
        engine = PlannerEngineId.DETERMINISTIC,
        days = listOf(
            WorkoutDay(0, SplitFocus.PULL, listOf(slot("barbell-curl"), slot("triceps-pushdown")))
        )
    )

    private fun slot(id: String, sets: Int = 3) = PlannedExercise(
        exerciseId = id,
        sets = sets,
        reps = 10,
        suggestedWeightKg = 20.0,
        loadKind = com.hydrafit.app.core.domain.workout.LoadKind.EXTERNAL
    )

    @Test
    fun aNullPreferenceLeavesTheGeneratedPlanIdentical() {
        assertSame(plan, PlannerSupersetPairing.apply(plan, null))
    }

    @Test
    fun curatedAdjacentPairsShareOneGroupInEitherOrderAndKeepPrescriptions() {
        val forward = PlannerSupersetPairing.apply(plan, preference)
        val reverse = PlannerSupersetPairing.apply(
            plan.copy(
                days = listOf(
                    plan.days.single().copy(exercises = plan.days.single().exercises.reversed())
                )
            ),
            preference
        )
        listOf(forward, reverse).forEach { result ->
            val entries = result.days.single().exercises
            val group = requireNotNull(entries[0].superset)
            assertEquals(group, entries[1].superset)
            assertEquals(10, group.transitionSeconds)
            assertEquals(120, group.pairRestSeconds)
            assertEquals(listOf(3, 3), entries.map { it.sets })
            assertEquals(listOf(10, 10), entries.map { it.reps })
        }
        assertEquals(Superset("planner-0-0", 10, 120), forward.days.single().exercises[0].superset)
    }

    @Test
    fun nonAdjacentSamePatternCustomAndCompoundSlotsAreNeverPaired() {
        listOf(
            listOf(slot("barbell-curl"), slot("hammer-curl")),
            listOf(slot("barbell-curl"), slot("back-squat"), slot("triceps-pushdown")),
            listOf(slot("my-custom-curl"), slot("triceps-pushdown")),
            listOf(slot("barbell-curl"), slot("close-grip-bench-press"))
        ).forEach { entries ->
            val result = PlannerSupersetPairing.apply(
                plan.copy(days = listOf(plan.days.single().copy(exercises = entries))),
                preference
            )
            assertEquals(entries, result.days.single().exercises)
        }
    }

    @Test
    fun anAlreadyPairedSlotIsLeftAloneAndRunsDoNotOverlap() {
        val manual = Superset("manual", 0, 90)
        val entries = listOf(
            slot("barbell-curl").copy(superset = manual),
            slot("triceps-pushdown"),
            slot("hammer-curl"),
            slot("triceps-pushdown")
        )
        val result = PlannerSupersetPairing.apply(
            plan.copy(days = listOf(plan.days.single().copy(exercises = entries))),
            preference
        ).days.single().exercises
        assertEquals(manual, result[0].superset)
        assertEquals(result[1].superset, result[2].superset)
        assertEquals("planner-0-1", result[1].superset?.key)
        assertNull(result[3].superset)
    }

    @Test
    fun invalidRestIsRejectedBeforeGeneration() {
        assertFailsWith<IllegalArgumentException> {
            PlannerSupersetPreference(0, 0).requireValid()
        }
        val request = PlanRequest(
            daysPerWeek = 2,
            availableEquipment = emptySet(),
            muscleFatigue = emptyMap<MuscleGroup, Double>(),
            nowMillis = 1L
        )
        assertNull(request.plannerSupersetPreference)
    }

    @Test
    fun generationAppliesTheSameRuleToEveryEngine() = runTest {
        val engine = object : WorkoutPlannerEngine {
            override val id: PlannerEngineId = PlannerEngineId.DETERMINISTIC
            override suspend fun generatePlan(request: PlanRequest): WeeklyPlan = plan
        }
        val request = PlanRequest(
            daysPerWeek = 1,
            availableEquipment = emptySet(),
            muscleFatigue = emptyMap(),
            nowMillis = 1L,
            plannerSupersetPreference = preference
        )
        val generated = GenerateWeeklySplitUseCase({ engine })(request)
        assertEquals(
            generated.days.single().exercises[0].superset,
            generated.days.single().exercises[1].superset
        )
    }
}
