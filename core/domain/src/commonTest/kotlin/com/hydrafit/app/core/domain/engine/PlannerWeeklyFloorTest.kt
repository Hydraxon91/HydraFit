package com.hydrafit.app.core.domain.engine

import com.hydrafit.app.core.domain.equipment.Exercise
import com.hydrafit.app.core.domain.equipment.MovementPattern
import com.hydrafit.app.core.domain.fatigue.MuscleGroup
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The weekly floors for prime movers, the once-a-week shrug and the session-length caps of the
 * deterministic planner. Fixtures list explicit involvements so the weekly ledger is predictable.
 */
class PlannerWeeklyFloorTest {

    private val engine = DeterministicWorkoutPlannerEngine(
        object : ExerciseCatalog {
            override suspend fun all(): List<Exercise> = emptyList()
        }
    )

    @Test
    fun floorFillsAnUnderFloorPrimeMoverBeforeAnOrdinaryAccessory() {
        val plan = engine.plan(upperLowerRequest(), upperCatalog())

        val upperIds = plan.days.first { it.focus == SplitFocus.UPPER }
            .exercises.map { it.exerciseId }
        // Side delts and the chest start the week with no isolation work and sit under their floor,
        // so both isolations are chosen ahead of the forearm exercise that has no floor.
        assertTrue("lateral" in upperIds, "upperIds=$upperIds")
        assertTrue("fly" in upperIds, "upperIds=$upperIds")
        assertTrue("wrist-curl" !in upperIds, "upperIds=$upperIds")
    }

    @Test
    fun theWeeklyLedgerReachesTheFloorOfEveryPrimeMoverWithAnIsolationPath() {
        // A longer session has the slots, and the extra isolations give each floor a second exercise
        // (a day never repeats one), so the floors are limited by the catalog and not by the cap.
        val catalog = upperCatalog() + extraIsolation()
        val plan = engine.plan(upperLowerRequest(sessionLength = SessionLength.LONGER), catalog)

        val ledger = WeeklyVolumeTargets.weightedSetsByMuscle(
            plan.days,
            catalog.associateBy { it.id }
        )
        val shortfalls = WeeklyVolumeTargets.floorShortfalls(
            ledger,
            WeeklyVolumeTargets.forGoal(TrainingGoal.BALANCED)
        )

        // Chest and side delts have isolation exercises, so their floors are filled. Muscles that
        // only a compound reaches (upper back, lats, legs here) may stay under their floor: a day
        // keeps one compound per pattern, and the gap is reported rather than forced.
        val reachable = setOf(
            MuscleGroup.CHEST_UPPER,
            MuscleGroup.CHEST_LOWER,
            MuscleGroup.SIDE_DELTS
        )
        assertTrue(shortfalls.keys.none { it in reachable }, "shortfalls=$shortfalls")
    }

    @Test
    fun armCoverageStillWinsASlotWhenFloorsCompeteForTheSameDay() {
        val plan = engine.plan(upperLowerRequest(), upperCatalog())

        val bicepsSets = directSets(plan, MovementPattern.BICEPS_ISOLATION)
        val tricepsSets = directSets(plan, MovementPattern.TRICEPS_ISOLATION)
        val target = DirectArmCoverage.targetSetsPerMuscle(4, TrainingGoal.BALANCED)

        assertTrue(bicepsSets >= target, "bicepsSets=$bicepsSets target=$target")
        assertTrue(tricepsSets >= target, "tricepsSets=$tricepsSets target=$target")
    }

    @Test
    fun theShrugIsPlannedAtMostOncePerWeekWithThreeSets() {
        val plan = engine.plan(upperLowerRequest(), trapOnlyCatalog())

        val shrugs = plan.days.flatMap { it.exercises }
            .filter { it.exerciseId == "shrug" }
        assertEquals(1, shrugs.size, "shrugs=$shrugs")
        assertEquals(3, shrugs.single().sets)
    }

    @Test
    fun aFullDayDropsTheShrugBeforeAFloorOrArmSlot() {
        val plan = engine.plan(upperLowerRequest(), upperCatalog())

        val shrugDays = plan.days.filter { day -> day.exercises.any { it.exerciseId == "shrug" } }
        // Four compounds, two arms and two floor fills already fill an upper day to the standard
        // maximum of eight, so the optional shrug has no slot on either upper day.
        assertTrue(shrugDays.isEmpty(), "shrugDays=${shrugDays.map { it.dayIndex }}")
        assertTrue(
            plan.days.all {
                it.exercises.size <= PlannerExerciseCounts.MUST_HAVE_MAX_PER_DAY
            }
        )
    }

    @Test
    fun aLongerSessionRaisesTheDailyCapsWithoutPassingEleven() {
        val catalog = upperCatalog() + extraIsolation()
        val standard = engine.plan(upperLowerRequest(), catalog)
        val longer = engine.plan(
            upperLowerRequest(sessionLength = SessionLength.LONGER),
            catalog
        )

        val standardMax = standard.days.maxOf { it.exercises.size }
        val longerMax = longer.days.maxOf { it.exercises.size }
        assertTrue(
            standardMax <= PlannerExerciseCounts.MUST_HAVE_MAX_PER_DAY,
            "standard=$standardMax"
        )
        assertTrue(longerMax > standardMax, "standard=$standardMax longer=$longerMax")
        assertTrue(
            longerMax <= PlannerExerciseCounts.LONGER_MUST_HAVE_MAX_PER_DAY,
            "longer=$longerMax"
        )
    }

    @Test
    fun floorShortfallsListsOnlyPrimeMoversUnderTheirFloor() {
        val base = WeeklyVolumeTargets.forGoal(TrainingGoal.BALANCED)
        val ledger = MuscleGroup.entries.associateWith { 0.0 } +
            mapOf(MuscleGroup.CHEST_UPPER to 30.0, MuscleGroup.LATS to 2.0)

        val shortfalls = WeeklyVolumeTargets.floorShortfalls(ledger, base)

        assertTrue(MuscleGroup.CHEST_UPPER !in shortfalls)
        assertTrue(MuscleGroup.LATS in shortfalls)
        // Biceps are guaranteed by direct arm coverage, not by a floor, and calves have no floor.
        assertTrue(MuscleGroup.BICEPS !in shortfalls)
        assertTrue(MuscleGroup.CALVES !in shortfalls)
        assertEquals(WeeklyVolumeTargets.FLOOR_MUSCLES - MuscleGroup.CHEST_UPPER, shortfalls.keys)
    }

    @Test
    fun aDeloadScalesTheFloorWithTheVolumeWindow() {
        val full = WeeklyVolumeTargets.forGoal(TrainingGoal.BALANCED)
        val deload = WeeklyVolumeTargets.forGoal(TrainingGoal.BALANCED, 0.7)

        val fullFloor = WeeklyVolumeTargets.forMuscle(full, MuscleGroup.LATS)!!.minSets
        val deloadFloor = WeeklyVolumeTargets.forMuscle(deload, MuscleGroup.LATS)!!.minSets

        assertEquals(fullFloor * 0.7, deloadFloor, 1e-9)
    }

    @Test
    fun theStoredSessionLengthFallsBackToStandard() {
        assertEquals(SessionLength.LONGER, SessionLength.fromStored("LONGER"))
        assertEquals(SessionLength.STANDARD, SessionLength.fromStored("STANDARD"))
        assertEquals(SessionLength.STANDARD, SessionLength.fromStored(null))
        assertEquals(SessionLength.STANDARD, SessionLength.fromStored("no-longer-known"))
    }

    private fun directSets(plan: WeeklyPlan, pattern: MovementPattern): Int {
        val ids = (upperCatalog().filter { it.movementPattern == pattern }).map { it.id }.toSet()
        return plan.days.flatMap { it.exercises }
            .filter { it.exerciseId in ids }
            .sumOf { it.sets }
    }

    private fun upperLowerRequest(sessionLength: SessionLength = SessionLength.DEFAULT) =
        PlanRequest(
            daysPerWeek = 4,
            availableEquipment = emptySet(),
            muscleFatigue = emptyMap(),
            splitPreference = SplitType.UPPER_LOWER,
            nowMillis = 0L,
            setsPerExercise = 4,
            accessorySetsPerExercise = 4,
            sessionLength = sessionLength
        )

    private fun trapOnlyCatalog(): List<Exercise> = listOf(
        ex("hpush", MovementPattern.HORIZONTAL_PUSH, MuscleGroup.CHEST_UPPER to 1.0),
        ex("vpush", MovementPattern.VERTICAL_PUSH, MuscleGroup.FRONT_DELTS to 1.0),
        ex("hpull", MovementPattern.HORIZONTAL_PULL, MuscleGroup.UPPER_BACK to 1.0),
        ex("vpull", MovementPattern.VERTICAL_PULL, MuscleGroup.LATS to 1.0),
        ex("shrug", MovementPattern.TRAP_ISOLATION, MuscleGroup.TRAPS to 1.0),
        ex("squat", MovementPattern.SQUAT, MuscleGroup.QUADS to 1.0),
        ex("hinge", MovementPattern.HINGE, MuscleGroup.HAMSTRINGS to 1.0)
    )

    private fun upperCatalog(): List<Exercise> = listOf(
        ex("hpush", MovementPattern.HORIZONTAL_PUSH, MuscleGroup.CHEST_UPPER to 0.7),
        ex("vpush", MovementPattern.VERTICAL_PUSH, MuscleGroup.FRONT_DELTS to 1.0),
        ex("hpull", MovementPattern.HORIZONTAL_PULL, MuscleGroup.UPPER_BACK to 1.0),
        ex("vpull", MovementPattern.VERTICAL_PULL, MuscleGroup.LATS to 1.0),
        ex("curl", MovementPattern.BICEPS_ISOLATION, MuscleGroup.BICEPS to 1.0),
        ex("pushdown", MovementPattern.TRICEPS_ISOLATION, MuscleGroup.TRICEPS to 1.0),
        ex("lateral", MovementPattern.SHOULDER_ISOLATION, MuscleGroup.SIDE_DELTS to 1.0),
        ex(
            "fly",
            MovementPattern.CHEST_FLY,
            MuscleGroup.CHEST_UPPER to 1.0,
            MuscleGroup.CHEST_LOWER to 1.0
        ),
        ex("wrist-curl", MovementPattern.FOREARM_ISOLATION, MuscleGroup.FOREARMS to 1.0),
        ex("shrug", MovementPattern.TRAP_ISOLATION, MuscleGroup.TRAPS to 1.0),
        ex("squat", MovementPattern.SQUAT, MuscleGroup.QUADS to 1.0, MuscleGroup.GLUTES to 1.0),
        ex("hinge", MovementPattern.HINGE, MuscleGroup.HAMSTRINGS to 1.0, MuscleGroup.GLUTES to 1.0)
    )

    private fun extraIsolation(): List<Exercise> = listOf(
        ex("rear-raise", MovementPattern.SHOULDER_ISOLATION, MuscleGroup.REAR_DELTS to 1.0),
        ex("hammer-curl", MovementPattern.BICEPS_ISOLATION, MuscleGroup.BICEPS to 1.0),
        ex("kickback", MovementPattern.TRICEPS_ISOLATION, MuscleGroup.TRICEPS to 1.0),
        ex(
            "pec-deck",
            MovementPattern.CHEST_FLY,
            MuscleGroup.CHEST_UPPER to 1.0,
            MuscleGroup.CHEST_LOWER to 1.0
        ),
        ex("front-raise", MovementPattern.SHOULDER_ISOLATION, MuscleGroup.FRONT_DELTS to 1.0)
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
