package com.hydrafit.app.core.domain.equipment

import com.hydrafit.app.core.domain.fatigue.MuscleGroup

data class Exercise(
    val id: String,
    val name: String,
    val requiredEquipment: Set<EquipmentTag>,
    val primaryMuscles: Set<MuscleGroup>,
    val secondaryMuscles: Set<MuscleGroup> = emptySet(),
    val movementPattern: MovementPattern = MovementPattern.CORE
) {
    fun isAvailableWith(availableEquipment: Set<EquipmentTag>): Boolean = requiredEquipment
        .filterNot { it == EquipmentTag.BODYWEIGHT }
        .all { it in availableEquipment }
}
