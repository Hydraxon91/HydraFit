package com.hydrafit.app.core.database

import com.hydrafit.app.core.domain.equipment.EquipmentTag
import com.hydrafit.app.core.domain.equipment.MovementPattern
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
}
