package com.hydrafit.app.feature.equipment

import com.hydrafit.app.core.domain.equipment.Equipment
import com.hydrafit.app.core.domain.equipment.EquipmentTag
import com.hydrafit.app.core.domain.equipment.Exercise
import com.hydrafit.app.core.domain.fatigue.MuscleGroup

data class EquipmentProfilerUiState(
    val equipment: List<Equipment> = emptyList(),
    val selectedTags: Set<EquipmentTag> = emptySet(),
    val newEquipmentName: String = "",
    val exercises: List<Exercise> = emptyList(),
    val editingExerciseId: String? = null,
    val editingEquipment: Set<EquipmentTag> = emptySet(),
    val editingPrimary: Set<MuscleGroup> = emptySet(),
    val editingSecondary: Set<MuscleGroup> = emptySet(),
    val isLoading: Boolean = true
) {
    val canAdd: Boolean
        get() = newEquipmentName.isNotBlank()

    val canSaveEdit: Boolean
        get() = editingPrimary.isNotEmpty()
}
