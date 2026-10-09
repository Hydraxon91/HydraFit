package com.hydrafit.app.core.database

import com.hydrafit.app.core.domain.equipment.EquipmentTag
import com.hydrafit.app.core.domain.equipment.ExerciseLoadCapability
import com.hydrafit.app.core.domain.equipment.MovementPattern
import com.hydrafit.app.core.domain.equipment.MovementPatternGuardrail
import com.hydrafit.app.core.domain.fatigue.MuscleGroup
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Structural checks on the seeded exercise catalog (`DefaultExercises.all`, which is the
 * hand-written baseline plus the CAT-P1 research batch in [DefaultExercisesCatalogC1]).
 *
 * These assert *shape*, not scientific correctness: ids are unique slugs, every required
 * equipment tag resolves to a built-in, enum values are valid, and involvement weights stay
 * on the CAT-P0 tier scale. The baseline seed's former finer weights (0.6/0.4/0.2) were
 * normalized to the scale (0.7/0.5/0.3) on 2026-10-06, so the whole catalog is now tier-aligned.
 */
class DefaultExercisesDataQualityTest {

    private val catalog = DefaultExercises.all
    private val newBatch = DefaultExercisesCatalogC1.all
    private val p7Batch = DefaultExercisesCatalogP7.all
    private val builtInEquipmentIds = EquipmentTag.BUILT_IN.map { it.id }.toSet()
    private val tierScale = setOf(0.3, 0.5, 0.7, 1.0)

    @Test
    fun catalogHasNoDuplicateIds() {
        val duplicates = catalog.groupingBy { it.id }.eachCount().filterValues { it > 1 }
        assertTrue(
            duplicates.isEmpty(),
            "Duplicate exercise ids in the catalog: ${duplicates.keys}"
        )
    }

    @Test
    fun everyRequiredEquipmentResolvesToBuiltIn() {
        val unresolved = catalog
            .flatMap { exercise -> exercise.requiredEquipment.map { exercise.id to it.id } }
            .filter { (_, tagId) -> tagId !in builtInEquipmentIds }
        assertTrue(unresolved.isEmpty(), "requiredEquipment tags that do not resolve: $unresolved")
    }

    @Test
    fun everyIdIsASlug() {
        val slug = Regex("^[a-z0-9]+(-[a-z0-9]+)*$")
        val bad = catalog.map { it.id }.filterNot { slug.matches(it) }
        assertTrue(bad.isEmpty(), "Non-slug exercise ids: $bad")
    }

    @Test
    fun everyRowHasIdentifierAndPrimaryMuscle() {
        val bad = catalog.filter {
            it.id.isBlank() || it.name.isBlank() || it.primaryMuscles.isEmpty()
        }
        assertTrue(bad.isEmpty(), "Rows missing id/name/primary muscle: ${bad.map { it.id }}")
    }

    @Test
    fun everyMovementPatternIsAnEnumValue() {
        val valid = MovementPattern.entries.toSet()
        val bad = catalog.filterNot { it.movementPattern in valid }
        assertTrue(bad.isEmpty(), "Invalid movement patterns: ${bad.map { it.id }}")
    }

    @Test
    fun everyMuscleIsAnEnumValue() {
        val valid = MuscleGroup.entries.toSet()
        val bad = catalog.filter { exercise ->
            (exercise.primaryMuscles + exercise.secondaryMuscles + exercise.involvements.keys)
                .any { it !in valid }
        }
        assertTrue(bad.isEmpty(), "Rows referencing unknown muscles: ${bad.map { it.id }}")
    }

    @Test
    fun involvementsStayOnTheTierScale() {
        val bad = catalog.flatMap { exercise ->
            exercise.involvements.map { (muscle, weight) -> Triple(exercise.id, muscle, weight) }
        }.filter { (_, _, weight) -> weight !in tierScale }
        assertTrue(bad.isEmpty(), "Weights off the 0.3/0.5/0.7/1.0 scale: $bad")
    }

    /**
     * Known pattern/involvement disagreements in the fresh seed. Each is a catalog classification
     * decision (reclassify the pattern, extend the guardrail's expected-muscle set, or correct the
     * weights) tracked separately in `docs/live-testing-2026-10-08.md`; none is a regression.
     * Listing them keeps this test strict: any *new* conflict still fails, and fixing one prompts
     * an update here.
     */
    private val knownPatternConflicts = setOf(
        "upright-barbell-row",
        "upright-cable-row",
        "cable-deadlifts",
        "band-hip-adductions",
        "cable-hip-adduction"
    )

    @Test
    fun everyRowPassesTheMovementPatternGuardrailExceptKnownConflicts() {
        val conflicts = catalog
            .filter { exercise ->
                MovementPatternGuardrail.conflicts(
                    exercise.movementPattern,
                    exercise.effectiveInvolvements
                )
            }
            .map { it.id }
            .toSet()
        assertEquals(
            knownPatternConflicts,
            conflicts,
            "Pattern/involvement conflicts changed; update the known-conflicts set if intended"
        )
    }

    @Test
    fun newBatchPrimaryAndSecondaryPartitionItsInvolvements() {
        newBatch.forEach { exercise ->
            val primary = exercise.primaryMuscles
            val secondary = exercise.secondaryMuscles
            assertTrue(
                (primary intersect secondary).isEmpty(),
                "${exercise.id}: primary and secondary muscles overlap (${primary intersect secondary})"
            )
            assertEquals(
                exercise.involvements.keys,
                primary + secondary,
                "${exercise.id}: primary+secondary must equal the involvement muscles"
            )
        }
    }

    @Test
    fun newBatchUsesEachProposedEquipmentTag() {
        val used = newBatch.flatMap { it.requiredEquipment.map { tag -> tag.id } }.toSet()
        val proposed = setOf("DIP_BAR", "SMITH_MACHINE", "HACK_SQUAT_MACHINE", "CALF_RAISE_MACHINE")
        assertTrue(
            used.containsAll(proposed),
            "Proposed tags unused by any CAT-P1 row: ${proposed - used}"
        )
    }

    @Test
    fun newBatchIsTheExpectedSize() {
        assertEquals(112, newBatch.size)
    }

    @Test
    fun p7RowsUseApprovedIdsEquipmentPatternsAndTieredInvolvements() {
        val expected = mapOf(
            "flat-bench-cable-fly" to p7(
                setOf(EquipmentTag.CABLE_MACHINE, EquipmentTag.BENCH),
                MovementPattern.CHEST_FLY,
                mapOf(MuscleGroup.CHEST_UPPER to 0.7, MuscleGroup.CHEST_LOWER to 0.7)
            ),
            "single-arm-cable-crossover" to p7(
                setOf(EquipmentTag.CABLE_MACHINE),
                MovementPattern.CHEST_FLY,
                mapOf(MuscleGroup.CHEST_UPPER to 0.7, MuscleGroup.CHEST_LOWER to 0.7),
                unilateral = true
            ),
            "seated-single-arm-cable-row" to p7(
                setOf(EquipmentTag.CABLE_MACHINE),
                MovementPattern.HORIZONTAL_PULL,
                mapOf(
                    MuscleGroup.UPPER_BACK to 0.7,
                    MuscleGroup.LATS to 0.5,
                    MuscleGroup.BICEPS to 0.5
                ),
                unilateral = true
            ),
            "dumbbell-floor-press" to p7(
                setOf(EquipmentTag.DUMBBELL),
                MovementPattern.HORIZONTAL_PUSH,
                mapOf(
                    MuscleGroup.TRICEPS to 0.7,
                    MuscleGroup.CHEST_UPPER to 0.5,
                    MuscleGroup.CHEST_LOWER to 0.5,
                    MuscleGroup.FRONT_DELTS to 0.5
                )
            ),
            "seated-arnold-dumbbell-press" to p7(
                setOf(EquipmentTag.DUMBBELL, EquipmentTag.BENCH),
                MovementPattern.VERTICAL_PUSH,
                mapOf(
                    MuscleGroup.FRONT_DELTS to 0.7,
                    MuscleGroup.SIDE_DELTS to 0.7,
                    MuscleGroup.TRICEPS to 0.5
                )
            ),
            "single-arm-kettlebell-row" to p7(
                setOf(EquipmentTag.KETTLEBELL),
                MovementPattern.HORIZONTAL_PULL,
                mapOf(
                    MuscleGroup.UPPER_BACK to 0.7,
                    MuscleGroup.LATS to 0.5,
                    MuscleGroup.BICEPS to 0.5
                ),
                unilateral = true
            ),
            "single-leg-kettlebell-deadlift" to p7(
                setOf(EquipmentTag.KETTLEBELL),
                MovementPattern.HINGE,
                mapOf(
                    MuscleGroup.HAMSTRINGS to 1.0,
                    MuscleGroup.GLUTES to 0.5,
                    MuscleGroup.LOWER_BACK to 0.3
                ),
                unilateral = true
            ),
            "incline-dumbbell-curl" to p7(
                setOf(EquipmentTag.DUMBBELL, EquipmentTag.BENCH),
                MovementPattern.BICEPS_ISOLATION,
                mapOf(MuscleGroup.BICEPS to 1.0)
            ),
            "single-leg-cable-kickback" to p7(
                setOf(EquipmentTag.CABLE_MACHINE),
                MovementPattern.LEG_ISOLATION,
                mapOf(MuscleGroup.GLUTES to 1.0, MuscleGroup.HAMSTRINGS to 0.5),
                unilateral = true
            ),
            "bicycle-crunch" to p7(
                setOf(EquipmentTag.BODYWEIGHT),
                MovementPattern.CORE,
                mapOf(MuscleGroup.ABS to 1.0, MuscleGroup.OBLIQUES to 0.5),
                loadCapability = ExerciseLoadCapability.BODYWEIGHT_ONLY
            )
        )
        val actual = p7Batch.associate { exercise ->
            exercise.id to p7(
                exercise.requiredEquipment,
                exercise.movementPattern,
                exercise.involvements,
                exercise.isUnilateral,
                exercise.loadCapability
            )
        }
        assertEquals(expected, actual)
        assertEquals(
            mapOf(
                "flat-bench-cable-fly" to "Flat Bench Cable Fly",
                "single-arm-cable-crossover" to "Single-Arm Cable Crossover",
                "seated-single-arm-cable-row" to "Seated Single-Arm Cable Row",
                "dumbbell-floor-press" to "Dumbbell Floor Press",
                "seated-arnold-dumbbell-press" to "Seated Arnold Dumbbell Press",
                "single-arm-kettlebell-row" to "Single-Arm Kettlebell Row",
                "single-leg-kettlebell-deadlift" to "Single-Leg Kettlebell Deadlift",
                "incline-dumbbell-curl" to "Incline Dumbbell Curl",
                "single-leg-cable-kickback" to "Single-Leg Cable Kickback",
                "bicycle-crunch" to "Bicycle Crunch"
            ),
            p7Batch.associate { it.id to it.name }
        )
        p7Batch.forEach { exercise ->
            assertTrue(exercise.involvements.isNotEmpty(), "${exercise.id} has no explicit map")
            assertTrue(
                exercise.involvements.values.all { it in tierScale },
                "${exercise.id} has off-tier weights"
            )
            assertTrue(
                exercise.requiredEquipment.all { it.id in builtInEquipmentIds },
                "${exercise.id} has unresolved equipment"
            )
        }
    }

    @Test
    fun p7AddsNoMovementPatternGuardrailConflicts() {
        val conflicts = p7Batch.filter { exercise ->
            MovementPatternGuardrail.conflicts(
                exercise.movementPattern,
                exercise.effectiveInvolvements
            )
        }
        assertTrue(conflicts.isEmpty(), "P7 pattern conflicts: ${conflicts.map { it.id }}")
    }

    private fun p7(
        equipment: Set<EquipmentTag>,
        pattern: MovementPattern,
        involvements: Map<MuscleGroup, Double>,
        unilateral: Boolean = false,
        loadCapability: ExerciseLoadCapability = ExerciseLoadCapability.EXTERNAL
    ) = ExpectedP7Row(equipment, pattern, unilateral, loadCapability, involvements)

    private data class ExpectedP7Row(
        val equipment: Set<EquipmentTag>,
        val pattern: MovementPattern,
        val unilateral: Boolean,
        val loadCapability: ExerciseLoadCapability,
        val involvements: Map<MuscleGroup, Double>
    )
}
