package com.hydrafit.app.core.domain.engine

import com.hydrafit.app.core.domain.equipment.Exercise
import com.hydrafit.app.core.domain.equipment.MovementPattern
import com.hydrafit.app.core.domain.fatigue.MuscleGroup
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Accessories sized to the gap they close: no whole exercise for a fraction of a set, a sized
 * exercise between two sets and the normal count, the knee isolations sized by their own muscle,
 * and the larger direct-arm target of a Longer session. Fixtures list explicit involvements so the
 * weekly ledger is predictable.
 */
class PlannerSetSizingTest {

    private val engine = DeterministicWorkoutPlannerEngine(
        object : ExerciseCatalog {
            override suspend fun all(): List<Exercise> = emptyList()
        }
    )

    @Test
    fun aGapUnderTwoWeightedSetsAddsNoSecondAccessoryForTheSameMuscle() {
        // Rear delts: the Balanced target is 8, half of it owed on the first upper day. One 3-set
        // rear-delt exercise leaves 1 set open, which used to add the second one as well.
        val catalog = baseCatalog(rearRaiseWeight = 1.0) + listOf(
            ex("reverse-fly", MovementPattern.SHOULDER_ISOLATION, MuscleGroup.REAR_DELTS to 1.0)
        )
        val plan = engine.plan(upperLowerRequest(accessorySets = 3), catalog)

        val ids = plan.days.first().exercises.map { it.exerciseId }
        val rear = ids.filter { it == "rear-raise" || it == "reverse-fly" }
        assertEquals(1, rear.size, "ids=$ids")
    }

    @Test
    fun aSizedAccessoryTakesOnlyTheSetsItsGapNeedsAndRecordsThemAsItsBase() {
        // 4 rear-delt sets are owed on the first upper day and the row credits 2 of them, so the
        // raise needs 2 of the 4 accessory sets.
        val plan = engine.plan(
            upperLowerRequest(accessorySets = 4),
            baseCatalog(rearRaiseWeight = 1.0, rowRearDelts = 0.5)
        )

        val raise = plan.days.first().exercises.single { it.exerciseId == "rear-raise" }
        assertEquals(2, raise.sets)
        assertEquals(2, raise.explanation?.setCount?.baseSets)
    }

    @Test
    fun theKneeIsolationsStayOnEveryLowerDayButOnlyWithTheSetsTheirMuscleNeeds() {
        // The squat and hinge at 4 sets leave the quads 2 and the hamstrings 1 short of their paced
        // targets, so the leg extension gets 2 sets and the leg curl the minimum of 2.
        val plan = engine.plan(
            upperLowerRequest(accessorySets = 4),
            baseCatalog(rearRaiseWeight = 1.0)
        )

        val lower = plan.days.first { it.focus == SplitFocus.LOWER }.exercises
        val extension = lower.single { it.exerciseId == "leg-extension" }
        val curl = lower.single { it.exerciseId == "leg-curl" }
        assertEquals(2, extension.sets)
        assertEquals(2, curl.sets)
        plan.days.filter { it.focus == SplitFocus.LOWER }.forEach { day ->
            val ids = day.exercises.map { it.exerciseId }
            assertTrue("leg-extension" in ids && "leg-curl" in ids, "day ${day.dayIndex}: $ids")
        }
    }

    @Test
    fun aLongerSessionPlansTwelveDirectSetsPerArmOverTwoExercisesAnUpperDay() {
        val plan = engine.plan(
            upperLowerRequest(accessorySets = 4, sessionLength = SessionLength.LONGER),
            baseCatalog(rearRaiseWeight = 1.0) + secondArmExercises()
        )

        assertEquals(12, directSets(plan, MovementPattern.BICEPS_ISOLATION))
        assertEquals(12, directSets(plan, MovementPattern.TRICEPS_ISOLATION))
        plan.days.filter { it.focus == SplitFocus.UPPER }.forEach { day ->
            val curls = day.exercises.count { it.exerciseId in setOf("curl", "hammer-curl") }
            assertEquals(2, curls, "day ${day.dayIndex}: ${day.exercises.map { it.exerciseId }}")
        }
    }

    @Test
    fun aStandardSessionKeepsTheSixSetArmTargetWithSizedCurls() {
        val plan = engine.plan(
            upperLowerRequest(accessorySets = 4),
            baseCatalog(rearRaiseWeight = 1.0) + secondArmExercises()
        )

        // Each upper day owes half of 6, so one curl of 3 sets rather than the 4-set accessory count.
        assertEquals(6, directSets(plan, MovementPattern.BICEPS_ISOLATION))
        assertEquals(6, directSets(plan, MovementPattern.TRICEPS_ISOLATION))
    }

    @Test
    fun theLongerArmTargetAppliesFromThreeDaysAndNotToEndurance() {
        val longer = SessionLength.LONGER
        assertEquals(12, DirectArmCoverage.targetSetsPerMuscle(4, TrainingGoal.HYPERTROPHY, longer))
        assertEquals(12, DirectArmCoverage.targetSetsPerMuscle(6, TrainingGoal.BALANCED, longer))
        assertEquals(4, DirectArmCoverage.targetSetsPerMuscle(2, TrainingGoal.BALANCED, longer))
        assertEquals(4, DirectArmCoverage.targetSetsPerMuscle(4, TrainingGoal.ENDURANCE, longer))
        assertEquals(6, DirectArmCoverage.targetSetsPerMuscle(4, TrainingGoal.HYPERTROPHY))
        // Two upper days with two arm places each: 12 sets fit in 3-set exercises, so a 2-set
        // accessory count is raised to 3 rather than to 6.
        val upperLower =
            listOf(SplitFocus.UPPER, SplitFocus.LOWER, SplitFocus.UPPER, SplitFocus.LOWER)
        assertEquals(
            3,
            DirectArmCoverage.setsPerArmExercise(MuscleGroup.BICEPS, upperLower, 12, 2, longer)
        )
    }

    @Test
    fun eachChestRegionTargetsTenSetsOnBalanced() {
        val balanced = WeeklyVolumeTargets.forGoal(TrainingGoal.BALANCED)
        assertEquals(
            10.0,
            WeeklyVolumeTargets.forMuscle(balanced, MuscleGroup.CHEST_UPPER)?.targetSets
        )
        assertEquals(
            10.0,
            WeeklyVolumeTargets.forMuscle(balanced, MuscleGroup.CHEST_LOWER)?.targetSets
        )
    }

    private fun directSets(plan: WeeklyPlan, pattern: MovementPattern): Int {
        val ids = (baseCatalog(1.0) + secondArmExercises())
            .filter { it.movementPattern == pattern }
            .map { it.id }
            .toSet()
        return plan.days.flatMap { it.exercises }
            .filter { it.exerciseId in ids }
            .sumOf { it.sets }
    }

    private fun upperLowerRequest(
        accessorySets: Int,
        sessionLength: SessionLength = SessionLength.DEFAULT
    ) = PlanRequest(
        daysPerWeek = 4,
        availableEquipment = emptySet(),
        muscleFatigue = emptyMap(),
        splitPreference = SplitType.UPPER_LOWER,
        nowMillis = 0L,
        setsPerExercise = 4,
        accessorySetsPerExercise = accessorySets,
        sessionLength = sessionLength
    )

    // Like the seeded catalog, the press credits the triceps and the row the biceps, so the arms'
    // volume targets are met by the direct work plus those credits.
    private fun baseCatalog(rearRaiseWeight: Double, rowRearDelts: Double = 0.0): List<Exercise> =
        listOf(
            ex(
                "hpush",
                MovementPattern.HORIZONTAL_PUSH,
                MuscleGroup.CHEST_UPPER to 1.0,
                MuscleGroup.CHEST_LOWER to 1.0,
                MuscleGroup.TRICEPS to 0.5
            ),
            ex("vpush", MovementPattern.VERTICAL_PUSH, MuscleGroup.FRONT_DELTS to 1.0),
            ex(
                "hpull",
                MovementPattern.HORIZONTAL_PULL,
                *listOfNotNull(
                    MuscleGroup.UPPER_BACK to 1.0,
                    MuscleGroup.BICEPS to 0.5,
                    (MuscleGroup.REAR_DELTS to rowRearDelts).takeIf { rowRearDelts > 0.0 }
                ).toTypedArray()
            ),
            ex("vpull", MovementPattern.VERTICAL_PULL, MuscleGroup.LATS to 1.0),
            ex("curl", MovementPattern.BICEPS_ISOLATION, MuscleGroup.BICEPS to 1.0),
            ex("pushdown", MovementPattern.TRICEPS_ISOLATION, MuscleGroup.TRICEPS to 1.0),
            ex(
                "rear-raise",
                MovementPattern.SHOULDER_ISOLATION,
                MuscleGroup.REAR_DELTS to rearRaiseWeight
            ),
            ex("squat", MovementPattern.SQUAT, MuscleGroup.QUADS to 1.0),
            ex("hinge", MovementPattern.HINGE, MuscleGroup.HAMSTRINGS to 1.0),
            ex("leg-extension", MovementPattern.LEG_ISOLATION, MuscleGroup.QUADS to 1.0),
            ex("leg-curl", MovementPattern.LEG_ISOLATION, MuscleGroup.HAMSTRINGS to 1.0)
        )

    private fun secondArmExercises(): List<Exercise> = listOf(
        ex("hammer-curl", MovementPattern.BICEPS_ISOLATION, MuscleGroup.BICEPS to 1.0),
        ex("kickback", MovementPattern.TRICEPS_ISOLATION, MuscleGroup.TRICEPS to 1.0)
    )

    private fun ex(
        id: String,
        pattern: MovementPattern,
        vararg involvements: Pair<MuscleGroup, Double>
    ) = Exercise(
        id = id,
        name = id,
        requiredEquipment = emptySet(),
        primaryMuscles = involvements.map { it.first }.toSet(),
        movementPattern = pattern,
        involvements = involvements.toMap()
    )
}
