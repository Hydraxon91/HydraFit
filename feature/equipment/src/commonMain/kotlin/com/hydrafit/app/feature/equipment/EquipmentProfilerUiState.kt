package com.hydrafit.app.feature.equipment

import com.hydrafit.app.core.domain.equipment.Equipment
import com.hydrafit.app.core.domain.equipment.EquipmentTag
import com.hydrafit.app.core.domain.equipment.Exercise
import com.hydrafit.app.core.domain.equipment.MovementPattern
import com.hydrafit.app.core.domain.fatigue.MuscleGroup

/** The exercise currently open in the editor dialog, and its in-progress edits. */
data class ExerciseEditorState(
    val exerciseId: String? = null,
    val isCustom: Boolean = false,
    val isNew: Boolean = false,
    val name: String = "",
    val movementPattern: MovementPattern = MovementPattern.CORE,
    val equipment: Set<EquipmentTag> = emptySet(),
    val primary: Set<MuscleGroup> = emptySet(),
    val secondary: Set<MuscleGroup> = emptySet(),
    val isUnilateral: Boolean = false,
    val error: String? = null
) {
    val isOpen: Boolean
        get() = isNew || exerciseId != null

    val canSave: Boolean
        get() = name.isNotBlank() && primary.isNotEmpty()
}

/** The equipment item currently open in the manage dialog (rename/delete + max weight). */
data class EquipmentEditorState(
    val tag: EquipmentTag? = null,
    val name: String = "",
    val isBuiltIn: Boolean = false,
    val maxWeightInput: String = ""
) {
    val isOpen: Boolean
        get() = tag != null
}

data class EquipmentProfilerUiState(
    val equipment: List<Equipment> = emptyList(),
    val selectedTags: Set<EquipmentTag> = emptySet(),
    val newEquipmentName: String = "",
    val exercises: List<Exercise> = emptyList(),
    val search: String = "",
    val exerciseEditor: ExerciseEditorState = ExerciseEditorState(),
    val equipmentEditor: EquipmentEditorState = EquipmentEditorState(),
    val isLoading: Boolean = true
) {
    val canAdd: Boolean
        get() = newEquipmentName.isNotBlank()

    val visibleExercises: List<Exercise>
        get() = if (search.isBlank()) {
            exercises
        } else {
            exercises.filter { it.name.contains(search.trim(), ignoreCase = true) }
        }

    val builtInExercises: List<Exercise>
        get() = visibleExercises.filterNot { it.isCustom }

    val customExercises: List<Exercise>
        get() = visibleExercises.filter { it.isCustom }
}
