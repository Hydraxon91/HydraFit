package com.hydrafit.app.core.domain.engine

import com.hydrafit.app.core.domain.equipment.EquipmentTag
import com.hydrafit.app.core.domain.equipment.Exercise
import com.hydrafit.app.core.domain.equipment.MovementPattern
import com.hydrafit.app.core.domain.fatigue.MuscleGroup
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The triceps slot and the lower-day compound slots of the deterministic planner. */
class PlannerSlotSelectionTest {

    private val engine = DeterministicWorkoutPlannerEngine(
        object : ExerciseCatalog {
            override suspend fun all(): List<Exercise> = emptyList()
        }
    )

    @Test
    fun aDipDoesNotBeatAPushdownJustForItsChestCredit() {
        val plan = engine.plan(upperLowerRequest(), upperCatalogWithDips())

        val triceps = tricepsIds(plan)
        // The dip credits lower chest, which has the larger deficit, but the arm slot is ranked by
        // the triceps shortfall alone, so equal triceps work falls to equipment: cable before bench.
        assertTrue("dips" !in triceps.first(), "triceps=$triceps")
    }

    @Test
    fun theTricepsExerciseRotatesBetweenTheTwoUpperDays() {
        val plan = engine.plan(upperLowerRequest(), upperCatalogWithDips())

        val triceps = tricepsIds(plan)
        assertEquals(2, triceps.size, "triceps=$triceps")
        assertTrue(triceps[0] != triceps[1], "triceps=$triceps")
    }

    @Test
    fun aLowerDayLeadsWithASquatThenAHingeAndOnlyThenAnOptionalLunge() {
        val plan = engine.plan(upperLowerRequest(), lowerCatalog())

        val lowerDays = plan.days.filter { it.focus == SplitFocus.LOWER }
        lowerDays.forEach { day ->
            val patterns = day.exercises.map { it.pattern() }.filter { it.isCompound }
            assertEquals(
                listOf(MovementPattern.SQUAT, MovementPattern.HINGE, MovementPattern.LUNGE)
                    .take(patterns.size),
                patterns,
                "day=${day.dayIndex} patterns=$patterns"
            )
        }
        // The first lower day is short of its half of the quad target after one squat, so the lunge
        // fills the third slot there.
        assertTrue(lowerDays.first().exercises.any { it.pattern() == MovementPattern.LUNGE })
    }

    @Test
    fun theSquatSlotPrefersASquatOrLegPressOverALunge() {
        val plan = engine.plan(upperLowerRequest(), lowerCatalog())

        val squatSlots = plan.days.filter { it.focus == SplitFocus.LOWER }
            .map { day -> day.exercises.first { it.pattern() == MovementPattern.SQUAT } }
        // Barbell squat is ranked ahead of the leg press by equipment on the first day, and the leg
        // press is the fresh option on the second; neither is ever a lunge.
        assertEquals(listOf("back-squat", "leg-press"), squatSlots.map { it.exerciseId })
    }

    @Test
    fun aLungeIsAddedOnlyWhileTheQuadsAreBelowTheirPacedTarget() {
        val lowSets = engine.plan(upperLowerRequest(), lowerCatalog())
        val highSets = engine.plan(upperLowerRequest(setsPerExercise = 8), lowerCatalog())

        val firstLower = { plan: WeeklyPlan -> plan.days.first { it.focus == SplitFocus.LOWER } }
        // Four squat sets leave the first lower day short of its half of the weekly quad target, so
        // it gets the lunge; eight sets meet it, so the lunge slot stays empty.
        assertTrue(firstLower(lowSets).exercises.any { it.pattern() == MovementPattern.LUNGE })
        assertTrue(firstLower(highSets).exercises.none { it.pattern() == MovementPattern.LUNGE })
    }

    @Test
    fun aGluteKickbackNeverFillsTheLegCurlSlot() {
        val plan = engine.plan(upperLowerRequest(), lowerCatalog() + kneeMachines())

        plan.days.filter { it.focus == SplitFocus.LOWER }.forEach { day ->
            val ids = day.exercises.map { it.exerciseId }
            // The kickback lists the hamstrings at 0.5 and ranks first on equipment, but the curl
            // slot needs an exercise whose own target is the hamstrings.
            assertTrue("leg-curl" in ids, "day=${day.dayIndex} ids=$ids")
        }
    }

    @Test
    fun theLegExtensionIsPickedBeforeTheLungeSoAMetQuadTargetDropsTheLunge() {
        val withExtension = engine.plan(upperLowerRequest(), lowerCatalog() + kneeMachines())

        withExtension.days.filter { it.focus == SplitFocus.LOWER }.forEach { day ->
            val ids = day.exercises.map { it.exerciseId }
            // A squat and a leg extension already meet the paced quad target, so no lunge is added.
            assertTrue("leg-extension" in ids, "ids=$ids")
            assertTrue("lunge" !in ids, "ids=$ids")
        }
    }

    @Test
    fun aLungeStillFillsTheQuadSlotWhenNoSquatIsAvailable() {
        val plan = engine.plan(
            upperLowerRequest(),
            lowerCatalog().filter { it.movementPattern != MovementPattern.SQUAT }
        )

        plan.days.filter { it.focus == SplitFocus.LOWER }.forEach { day ->
            assertTrue(day.exercises.any { it.pattern() == MovementPattern.LUNGE })
        }
    }

    private fun tricepsIds(plan: WeeklyPlan): List<String> = plan.days
        .filter { it.focus == SplitFocus.UPPER }
        .map { day ->
            day.exercises.first { it.pattern() == MovementPattern.TRICEPS_ISOLATION }
                .exerciseId
        }

    private val patternsById: Map<String, MovementPattern> =
        (upperCatalogWithDips() + lowerCatalog() + kneeMachines()).associate {
            it.id to
                it.movementPattern
        }

    private fun PlannedExercise.pattern(): MovementPattern = patternsById.getValue(exerciseId)

    private fun upperLowerRequest(setsPerExercise: Int = 4) = PlanRequest(
        daysPerWeek = 4,
        availableEquipment = setOf(
            EquipmentTag.BENCH,
            EquipmentTag.CABLE_MACHINE,
            EquipmentTag.DUMBBELL,
            EquipmentTag.BARBELL,
            EquipmentTag.LEG_PRESS_MACHINE,
            EquipmentTag.LEG_EXTENSION_MACHINE,
            EquipmentTag.LEG_CURL_MACHINE
        ),
        muscleFatigue = emptyMap(),
        splitPreference = SplitType.UPPER_LOWER,
        nowMillis = 0L,
        setsPerExercise = setsPerExercise,
        accessorySetsPerExercise = 4
    )

    private fun upperCatalogWithDips(): List<Exercise> = listOf(
        ex("hpush", MovementPattern.HORIZONTAL_PUSH, emptySet(), MuscleGroup.CHEST_UPPER to 0.7),
        ex("vpush", MovementPattern.VERTICAL_PUSH, emptySet(), MuscleGroup.FRONT_DELTS to 1.0),
        ex("hpull", MovementPattern.HORIZONTAL_PULL, emptySet(), MuscleGroup.UPPER_BACK to 1.0),
        ex("vpull", MovementPattern.VERTICAL_PULL, emptySet(), MuscleGroup.LATS to 1.0),
        ex("curl", MovementPattern.BICEPS_ISOLATION, emptySet(), MuscleGroup.BICEPS to 1.0),
        ex(
            "dips",
            MovementPattern.TRICEPS_ISOLATION,
            setOf(EquipmentTag.BENCH),
            MuscleGroup.TRICEPS to 1.0,
            MuscleGroup.CHEST_LOWER to 0.5
        ),
        ex(
            "feet-elevated-dips",
            MovementPattern.TRICEPS_ISOLATION,
            setOf(EquipmentTag.BENCH),
            MuscleGroup.TRICEPS to 1.0,
            MuscleGroup.CHEST_LOWER to 0.5
        ),
        ex(
            "pushdown",
            MovementPattern.TRICEPS_ISOLATION,
            setOf(EquipmentTag.CABLE_MACHINE),
            MuscleGroup.TRICEPS to 1.0
        ),
        ex(
            "overhead-extension",
            MovementPattern.TRICEPS_ISOLATION,
            setOf(EquipmentTag.DUMBBELL),
            MuscleGroup.TRICEPS to 1.0
        ),
        ex("squat", MovementPattern.SQUAT, emptySet(), MuscleGroup.QUADS to 1.0),
        ex("hinge", MovementPattern.HINGE, emptySet(), MuscleGroup.HAMSTRINGS to 1.0)
    )

    private fun kneeMachines(): List<Exercise> = listOf(
        ex(
            "leg-extension",
            MovementPattern.LEG_ISOLATION,
            setOf(EquipmentTag.LEG_EXTENSION_MACHINE),
            MuscleGroup.QUADS to 1.0
        ),
        ex(
            "leg-curl",
            MovementPattern.LEG_ISOLATION,
            setOf(EquipmentTag.LEG_CURL_MACHINE),
            MuscleGroup.HAMSTRINGS to 1.0
        ),
        ex(
            "kickback",
            MovementPattern.LEG_ISOLATION,
            setOf(EquipmentTag.CABLE_MACHINE),
            MuscleGroup.GLUTES to 1.0,
            MuscleGroup.HAMSTRINGS to 0.5
        )
    )

    private fun lowerCatalog(): List<Exercise> = listOf(
        ex(
            "back-squat",
            MovementPattern.SQUAT,
            setOf(EquipmentTag.BARBELL),
            MuscleGroup.QUADS to 1.0,
            MuscleGroup.GLUTES to 1.0
        ),
        ex(
            "leg-press",
            MovementPattern.SQUAT,
            setOf(EquipmentTag.LEG_PRESS_MACHINE),
            MuscleGroup.QUADS to 1.0,
            MuscleGroup.GLUTES to 0.7
        ),
        ex(
            "rdl",
            MovementPattern.HINGE,
            setOf(EquipmentTag.BARBELL),
            MuscleGroup.HAMSTRINGS to 1.0,
            MuscleGroup.GLUTES to 1.0
        ),
        ex(
            "lunge",
            MovementPattern.LUNGE,
            setOf(EquipmentTag.DUMBBELL),
            MuscleGroup.QUADS to 1.0,
            MuscleGroup.GLUTES to 1.0
        ),
        ex("hpush", MovementPattern.HORIZONTAL_PUSH, emptySet(), MuscleGroup.CHEST_UPPER to 1.0),
        ex("hpull", MovementPattern.HORIZONTAL_PULL, emptySet(), MuscleGroup.LATS to 1.0)
    )

    private fun ex(
        id: String,
        pattern: MovementPattern,
        required: Set<EquipmentTag>,
        vararg involvements: Pair<MuscleGroup, Double>
    ) = Exercise(
        id = id,
        name = id,
        requiredEquipment = required,
        primaryMuscles = involvements.map { it.first }.toSet(),
        movementPattern = pattern,
        involvements = involvements.toMap()
    )
}
