package com.hydrafit.app.core.domain.equipment

import com.hydrafit.app.core.domain.fatigue.MuscleGroup

/** Only these five independently selectable groups may be copied into a new custom editor. */
data class ExerciseProfile(
    val equipment: Set<EquipmentTag>,
    val movementPattern: MovementPattern,
    val involvements: Map<MuscleGroup, Double>,
    val loadCapability: ExerciseLoadCapability,
    val isUnilateral: Boolean
)

/** Catalog identity is for matching/preview only; applying a profile never copies this id or name. */
data class CatalogExerciseProfile(
    val catalogId: String,
    val canonicalName: String,
    val displayName: String,
    val aliases: List<String> = emptyList(),
    val profile: ExerciseProfile
) {
    val matchingLabels: List<String>
        get() = listOf(canonicalName, displayName) + aliases
}
