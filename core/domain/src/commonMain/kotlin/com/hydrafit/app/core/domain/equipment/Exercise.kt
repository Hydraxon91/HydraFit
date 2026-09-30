package com.hydrafit.app.core.domain.equipment

import com.hydrafit.app.core.domain.fatigue.MuscleGroup

data class Exercise(
    val id: String,
    val name: String,
    val requiredEquipment: Set<EquipmentTag>,
    val primaryMuscles: Set<MuscleGroup>,
    val secondaryMuscles: Set<MuscleGroup> = emptySet(),
    val movementPattern: MovementPattern = MovementPattern.CORE,
    val isCustom: Boolean = false,
    /** True for one-side-at-a-time exercises (dumbbell curl, single-arm row, …); weight is per hand. */
    val isUnilateral: Boolean = false,
    /** Per-muscle involvement weights in (0.0, 1.0]; empty means derive from the tag sets. */
    val involvements: Map<MuscleGroup, Double> = emptyMap()
) {
    fun isAvailableWith(availableEquipment: Set<EquipmentTag>): Boolean = requiredEquipment
        .filterNot { it == EquipmentTag.BODYWEIGHT }
        .all { it in availableEquipment }

    /**
     * The involvement weights fatigue uses: the explicit [involvements] when present, otherwise the
     * legacy primary (1.0) / secondary (0.5) tags.
     */
    val effectiveInvolvements: Map<MuscleGroup, Double>
        get() = involvements.ifEmpty {
            primaryMuscles.associateWith { 1.0 } + secondaryMuscles.associateWith { 0.5 }
        }
}
