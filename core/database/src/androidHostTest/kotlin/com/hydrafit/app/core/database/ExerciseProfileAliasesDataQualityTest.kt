package com.hydrafit.app.core.database

import com.hydrafit.app.core.domain.equipment.CatalogExerciseProfile
import com.hydrafit.app.core.domain.equipment.CatalogProfileMatch
import com.hydrafit.app.core.domain.equipment.CatalogProfileMatcher
import com.hydrafit.app.core.domain.equipment.ExerciseProfile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ExerciseProfileAliasesDataQualityTest {
    @Test
    fun pilotHasExactlyTheApprovedMappingsAndProvenance() {
        assertEquals(
            setOf(
                Triple("en", "Pullup", "pull-up"),
                Triple("en", "Chinup", "chin-up"),
                Triple("de", "Langhantel-Bankdrücken", "barbell-bench-press"),
                Triple("de", "Kurzhantel-Bankdrücken", "dumbbell-bench-press")
            ),
            ExerciseProfileAliases.all.map { Triple(it.language, it.label, it.exerciseId) }.toSet()
        )
        assertTrue(ExerciseProfileAliases.all.all { it.provenance.contains("2026-10-09") })
    }

    @Test
    fun targetsExistAndEntriesAndStableIdsAreUnique() {
        val aliases = ExerciseProfileAliases.all
        val targets = DefaultExercises.all.map { it.id }.toSet()
        assertTrue(aliases.all { it.exerciseId in targets })
        assertEquals(aliases.size, aliases.map { it.id }.toSet().size)
        assertEquals(
            aliases.size,
            aliases.map { Triple(it.language, it.label, it.exerciseId) }.toSet().size
        )
        aliases.forEachIndexed { index, alias ->
            val otherLabels = aliases.filterIndexed { otherIndex, other ->
                otherIndex != index &&
                    other.exerciseId == alias.exerciseId &&
                    other.language == alias.language
            }.map { it.label }
            val duplicate = CatalogProfileMatcher.match(
                alias.label,
                listOf(profile(alias.exerciseId, otherLabels))
            )
            assertEquals(CatalogProfileMatch.Unknown, duplicate)
        }
    }

    @Test
    fun crossIdentityLabelCollisionsMustBeAcknowledged() {
        val snapshot = DefaultExercises.all.map { exercise ->
            profile(
                exercise.id,
                ExerciseProfileAliases.all.filter { it.exerciseId == exercise.id }.map { it.label }
            )
        }
        val collisions = snapshot.flatMap { it.matchingLabels }.mapNotNull { label ->
            val match = CatalogProfileMatcher.match(label, snapshot)
            (match as? CatalogProfileMatch.Ambiguous)?.candidates?.map { it.catalogId }?.toSet()
        }.toSet()
        assertEquals(ExerciseProfileAliases.acknowledgedCollisions, collisions)
    }

    private fun profile(id: String, aliases: List<String>): CatalogExerciseProfile {
        val exercise = DefaultExercises.all.single { it.id == id }
        return CatalogExerciseProfile(
            catalogId = id,
            canonicalName = exercise.name,
            displayName = exercise.name,
            aliases = aliases,
            profile = ExerciseProfile(
                exercise.requiredEquipment,
                exercise.movementPattern,
                exercise.effectiveInvolvements,
                exercise.loadCapability,
                exercise.isUnilateral
            )
        )
    }
}
