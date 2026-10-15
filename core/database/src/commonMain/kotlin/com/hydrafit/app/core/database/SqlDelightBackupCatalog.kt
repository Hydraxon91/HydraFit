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

    override fun compatiblePreviousSeedProfiles(): Map<String, Set<String>> = mapOf(
        "upright-barbell-row" to setOf(
            "BARBELL|HORIZONTAL_PULL|false|EXTERNAL|FRONT_DELTS:0.7,SIDE_DELTS:0.7,UPPER_BACK:0.5"
        ),
        "cable-deadlifts" to setOf(
            "CABLE_MACHINE|HINGE|false|EXTERNAL|" +
                "FOREARMS:0.3,GLUTES:0.5,HAMSTRINGS:0.5,LOWER_BACK:0.3,QUADS:1.0"
        )
    )

    /**
     * Everything about a seed that affects planning or fatigue: equipment, pattern, laterality and
     * resolved involvement weights. Only the explicitly listed historical profiles may differ.
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
