package com.hydrafit.app.core.database

import com.hydrafit.app.core.domain.engine.DeterministicWorkoutPlannerEngine
import com.hydrafit.app.core.domain.engine.ExerciseCatalog
import com.hydrafit.app.core.domain.engine.PlanRequest
import com.hydrafit.app.core.domain.engine.PlannerCandidateEligibility
import com.hydrafit.app.core.domain.engine.PlannerExerciseCounts
import com.hydrafit.app.core.domain.engine.PlannerLibraryOnlyExercises
import com.hydrafit.app.core.domain.engine.PlannerSupersetPairing
import com.hydrafit.app.core.domain.engine.PlannerSupersetPreference
import com.hydrafit.app.core.domain.engine.SplitFocus
import com.hydrafit.app.core.domain.engine.SplitType
import com.hydrafit.app.core.domain.engine.TrainingGoal
import com.hydrafit.app.core.domain.engine.WeeklyPlan
import com.hydrafit.app.core.domain.engine.WeeklyVolumeTargets
import com.hydrafit.app.core.domain.equipment.Exercise
import com.hydrafit.app.core.domain.equipment.MovementPattern
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
    fun everyPlannableSeededArmIsolationCanPairAndPlansWithBothArmsPair() = runTest {
        val plannable = PlannerCandidateEligibility.candidates(
            DefaultExercises.all,
            PlanRequest(
                daysPerWeek = 4,
                availableEquipment = everything,
                muscleFatigue = emptyMap(),
                nowMillis = 0L
            )
        )
        val biceps = plannable.filter { it.movementPattern == MovementPattern.BICEPS_ISOLATION }
        val triceps = plannable.filter { it.movementPattern == MovementPattern.TRICEPS_ISOLATION }
        assertEquals(
            biceps.map { it.id }.toSet(),
            PlannerSupersetPairing.BICEPS_ISOLATION_IDS,
            "the curated biceps ids must match the plannable seeded biceps isolations"
        )
        assertEquals(
            triceps.map { it.id }.toSet(),
            PlannerSupersetPairing.TRICEPS_ISOLATION_IDS,
            "the curated triceps ids must match the plannable seeded triceps isolations"
        )
        for (days in 2..5) {
            val paired = PlannerSupersetPairing.apply(plan(days), PlannerSupersetPreference(10, 60))
            val pairs = paired.days.sumOf { day -> day.exercises.count { it.superset != null } / 2 }
            assertTrue(pairs > 0, "days=$days")
        }
    }

    @Test
    fun anExerciseRepeatsOnlyWhenNothingUnusedTrainsItsMuscle() = runTest {
        // A repeat is allowed only for an exercise that is the sole plannable option for one of
        // the muscles it mainly trains (the single leg extension, or the plank and hanging leg
        // raise for obliques and hip flexors); anything with an unused alternative must not repeat.
        fun mainMuscles(exercise: Exercise) =
            exercise.effectiveInvolvements.filterValues { it >= 0.5 }.keys
        for (days in 2..6) {
            val plannable = PlannerCandidateEligibility.candidates(
                DefaultExercises.all,
                PlanRequest(
                    daysPerWeek = days,
                    availableEquipment = everything,
                    muscleFatigue = emptyMap(),
                    nowMillis = 0L
                )
            )
            val ids = plan(days).days.flatMap { day -> day.exercises.map { it.exerciseId } }
            val unused = plannable.filter { it.id !in ids }
            val repeated = ids.groupingBy { it }.eachCount().filterValues { it > 1 }.keys
            repeated.forEach { id ->
                // An alternative must fill the same slot: same movement pattern, same main muscle
                // (a squat is not a substitute for the leg-extension slot).
                val exercise = byId.getValue(id)
                assertTrue(
                    mainMuscles(exercise).any { muscle ->
                        unused.none {
                            it.movementPattern == exercise.movementPattern &&
                                muscle in mainMuscles(it)
                        }
                    },
                    "days=$days repeated=$id"
                )
            }
        }
    }

    @Test
    fun theLibraryOnlyListNamesRealSeededExercisesAndNeverReachesAPlan() = runTest {
        val libraryOnly = PlannerLibraryOnlyExercises.ids
        assertEquals(emptySet(), libraryOnly - byId.keys, "stale library-only ids")
        for (goal in TrainingGoal.entries) {
            for (days in 2..6) {
                val planned = plan(days, goal).days.flatMap { day ->
                    day.exercises.map { it.exerciseId }
                }
                assertEquals(
                    emptySet(),
                    planned.toSet() intersect libraryOnly,
                    "goal=$goal days=$days"
                )
            }
        }
    }

    @Test
    fun moreSetsPerExerciseNeverAddsExercisesAndArmTargetsHoldAtEveryCount() = runTest {
        for (goal in TrainingGoal.entries) {
            for (days in 2..6) {
                var previousExercises = Int.MAX_VALUE
                for (sets in 1..6) {
                    val plan = DeterministicWorkoutPlannerEngine(catalog).generatePlan(
                        PlanRequest(
                            daysPerWeek = days,
                            availableEquipment = everything,
                            muscleFatigue = emptyMap(),
                            splitPreference = SplitType.AUTO,
                            nowMillis = 0L,
                            goal = goal,
                            setsPerExercise = sets,
                            accessorySetsPerExercise = sets
                        )
                    )
                    val label = "goal=$goal days=$days sets=$sets"
                    val exercises = plan.days.sumOf { it.exercises.size }
                    assertTrue(exercises <= previousExercises, "$label exercises=$exercises")
                    previousExercises = exercises
                    assertTrue(
                        plan.days.all {
                            it.exercises.size <=
                                PlannerExerciseCounts.MUST_HAVE_MAX_PER_DAY
                        },
                        label
                    )
                    assertTrue(
                        plan.armCoverage.all { it.directIsolationSets >= it.targetSets },
                        "$label coverage=${plan.armCoverage}"
                    )
                }
            }
        }
    }

    @Test
    fun everyLowerOrLegsDayHasALegExtensionAndALegCurl() = runTest {
        fun trains(id: String, muscle: MuscleGroup): Boolean = byId.getValue(id).let {
            it.movementPattern == MovementPattern.LEG_ISOLATION &&
                (it.effectiveInvolvements[muscle] ?: 0.0) >= 0.5
        }
        for (goal in TrainingGoal.entries) {
            for (days in 2..6) {
                for (sets in listOf(2, 4, 6)) {
                    val plan = DeterministicWorkoutPlannerEngine(catalog).generatePlan(
                        PlanRequest(
                            daysPerWeek = days,
                            availableEquipment = everything,
                            muscleFatigue = emptyMap(),
                            splitPreference = SplitType.AUTO,
                            nowMillis = 0L,
                            goal = goal,
                            setsPerExercise = sets,
                            accessorySetsPerExercise = sets
                        )
                    )
                    plan.days.filter { it.focus == SplitFocus.LOWER || it.focus == SplitFocus.LEGS }
                        .forEach { day ->
                            val ids = day.exercises.map { it.exerciseId }
                            val label = "goal=$goal days=$days sets=$sets " +
                                "day=${day.dayIndex} ids=$ids"
                            assertTrue(ids.any { trains(it, MuscleGroup.QUADS) }, label)
                            assertTrue(ids.any { trains(it, MuscleGroup.HAMSTRINGS) }, label)
                        }
                }
            }
        }
    }

    @Test
    fun anInventoryWithNoEquipmentCanStillPlanEveryFocus() = runTest {
        for (days in 2..6) {
            val plan = DeterministicWorkoutPlannerEngine(catalog).generatePlan(
                PlanRequest(
                    daysPerWeek = days,
                    availableEquipment = emptySet(),
                    muscleFatigue = emptyMap(),
                    splitPreference = SplitType.AUTO,
                    nowMillis = 0L
                )
            )
            assertTrue(plan.days.all { it.exercises.isNotEmpty() }, "days=$days")
        }
    }

    @Test
    fun shrugsAreAPullOrUpperDayAccessoryAndNeverExceedTheDailyMaximum() = runTest {
        val shrugIds = DefaultExercises.all
            .filter { it.movementPattern == MovementPattern.TRAP_ISOLATION }
            .map { it.id }
            .toSet()
        var shrugWeeks = 0
        for (days in 2..6) {
            val week = plan(days)
            // The shrug is optional work: at most one a week, never more than three sets.
            val weekShrugs = week.days.flatMap { it.exercises }.filter { it.exerciseId in shrugIds }
            assertTrue(weekShrugs.size <= 1, "days=$days shrugs=${weekShrugs.size}")
            assertTrue(weekShrugs.all { it.sets <= 3 }, "days=$days")
            if (weekShrugs.isNotEmpty()) shrugWeeks++
            week.days.forEach { day ->
                if (day.focus != SplitFocus.PULL && day.focus != SplitFocus.UPPER) {
                    assertEquals(
                        0,
                        day.exercises.count { it.exerciseId in shrugIds },
                        "days=$days focus=${day.focus}"
                    )
                }
                assertTrue(day.exercises.size <= PlannerExerciseCounts.MUST_HAVE_MAX_PER_DAY)
            }
        }
        // Floors and arm work come first, so a week may have no slot left; some week still does.
        assertTrue(shrugWeeks > 0, "no pull or upper day was given a shrug")
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
