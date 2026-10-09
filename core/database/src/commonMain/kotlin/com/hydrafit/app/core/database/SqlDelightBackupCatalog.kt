package com.hydrafit.app.core.database

import com.hydrafit.app.core.domain.backup.BackupCatalog
import com.hydrafit.app.core.domain.equipment.EquipmentTag
import com.hydrafit.app.core.domain.equipment.Exercise

/** The catalog identities this install owns, read from the compiled seed rather than user rows. */
class SqlDelightBackupCatalog : BackupCatalog {
    override fun seedExerciseIds(): Set<String> = DefaultExercises.all.map { it.id }.toSet()

    override fun builtInEquipmentIds(): Set<String> = EquipmentTag.BUILT_IN.map { it.id }.toSet()

    override fun dedupeSeedKeys(): Set<String> {
        val protectedP7Ids = DefaultExercisesCatalogP7.all.map { it.id }.toSet()
        return DefaultExercises.all
            .filterNot { it.id in protectedP7Ids }
            .map { normalizeExerciseName(it.name) }
            .toSet()
    }

    override fun dedupeNameKey(name: String): String = normalizeExerciseName(name)

    override fun seedProfiles(): Map<String, String> =
        DefaultExercises.all.associate { it.id to seedProfile(it) }

    /**
     * Everything about a seed that affects planning or fatigue: equipment, pattern, laterality and
     * resolved involvement weights. A change here under the same id is a compatibility mismatch.
     */
    private fun seedProfile(exercise: Exercise): String = listOf(
        exercise.requiredEquipment.map { it.id }.sorted().joinToString(","),
        exercise.movementPattern.name,
        exercise.isUnilateral.toString(),
        exercise.loadCapability.name,
        exercise.effectiveInvolvements.entries
            .sortedBy { it.key.name }
            .joinToString(",") { "${it.key.name}:${it.value}" }
    ).joinToString("|")
}
