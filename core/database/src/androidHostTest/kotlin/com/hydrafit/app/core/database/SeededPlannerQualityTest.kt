package com.hydrafit.app.core.database

import com.hydrafit.app.core.domain.engine.DeterministicWorkoutPlannerEngine
import com.hydrafit.app.core.domain.engine.ExerciseCatalog
import com.hydrafit.app.core.domain.engine.PlanRequest
import com.hydrafit.app.core.domain.engine.SplitType
import com.hydrafit.app.core.domain.engine.TrainingGoal
import com.hydrafit.app.core.domain.engine.WeeklyPlan
import com.hydrafit.app.core.domain.engine.WeeklyVolumeTargets
import com.hydrafit.app.core.domain.equipment.Exercise
import com.hydrafit.app.core.domain.fatigue.MuscleGroup
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/**
 * The deterministic planner against the real seeded catalog with every piece of equipment, for each
 * supported frequency. A hand-built catalog cannot show these invariants: they depend on how many
 * real alternatives exist for each slot.
 */
class SeededPlannerQualityTest {
    private val catalog = object : ExerciseCatalog {
        override suspend fun all(): List<Exercise> = DefaultExercises.all
    }
    private val byId = DefaultExercises.all.associateBy { it.id }
    private val everything = DefaultExercises.all.flatMap { it.requiredEquipment }.toSet()

    private suspend fun plan(days: Int, goal: TrainingGoal = TrainingGoal.HYPERTROPHY): WeeklyPlan =
        DeterministicWorkoutPlannerEngine(catalog).generatePlan(
            PlanRequest(
                daysPerWeek = days,
                availableEquipment = everything,
                muscleFatigue = emptyMap(),
                splitPreference = SplitType.AUTO,
                nowMillis = 0L,
                goal = goal
            )
        )

    @Test
    fun everyFrequencyMeetsTheDirectArmTargetForBothArms() = runTest {
        for (goal in TrainingGoal.entries) {
            for (days in 2..6) {
                val coverage = plan(days, goal).armCoverage
                assertTrue(
                    coverage.all { it.directIsolationSets >= it.targetSets },
                    "goal=$goal days=$days coverage=$coverage"
                )
            }
        }
    }

    @Test
    fun theTwoArmsAreTrainedEvenlyAtEveryFrequency() = runTest {
        for (days in 2..6) {
            val sets = plan(days).armCoverage.associate { it.muscle to it.directIsolationSets }
            val biceps = sets.getValue(MuscleGroup.BICEPS)
            val triceps = sets.getValue(MuscleGroup.TRICEPS)
            assertTrue(
                kotlin.math.abs(biceps - triceps) <= 3,
                "days=$days biceps=$biceps triceps=$triceps"
            )
        }
    }

    @Test
    fun noExerciseRepeatsAcrossTheWeekWhenAllEquipmentIsAvailable() = runTest {
        for (days in 2..6) {
            val ids = plan(days).days.flatMap { day -> day.exercises.map { it.exerciseId } }
            val repeated = ids.groupingBy { it }.eachCount().filterValues { it > 1 }.keys
            assertEquals(emptySet(), repeated, "days=$days")
        }
    }

    @Test
    fun bothChestRegionsGetRealPressingVolumeFromThreeDaysUp() = runTest {
        for (days in 3..6) {
            val weighted = WeeklyVolumeTargets.weightedSetsByMuscle(plan(days).days, byId)
            val upper = weighted.getValue(MuscleGroup.CHEST_UPPER)
            val lower = weighted.getValue(MuscleGroup.CHEST_LOWER)
            assertTrue(upper >= 3.0 && lower >= 3.0, "days=$days upper=$upper lower=$lower")
        }
    }

    @Test
    fun aTwoDayWeekTrainsBothASquatAndAHingePattern() = runTest {
        val patterns = plan(2).days.flatMap { it.exercises }
            .map { byId.getValue(it.exerciseId).movementPattern.name }
        assertTrue("SQUAT" in patterns && "HINGE" in patterns, "patterns=$patterns")
    }
}
