package com.hydrafit.app.feature.equipment

import com.hydrafit.app.core.domain.equipment.Equipment
import com.hydrafit.app.core.domain.equipment.EquipmentTag
import com.hydrafit.app.core.domain.equipment.Exercise
import com.hydrafit.app.core.domain.equipment.MovementPattern
import com.hydrafit.app.core.domain.equipment.MovementPatternGuardrail
import com.hydrafit.app.core.domain.fatigue.MuscleGroup

/** The exercise currently open in the editor dialog, and its in-progress edits. */
data class ExerciseEditorState(
    val exerciseId: String? = null,
    val isCustom: Boolean = false,
    val isNew: Boolean = false,
    val name: String = "",
    val movementPattern: MovementPattern = MovementPattern.CORE,
    val equipment: Set<EquipmentTag> = emptySet(),
    /** Per-muscle involvement weights in (0.0, 1.0]; a muscle absent here is not involved. */
    val involvements: Map<MuscleGroup, Double> = emptyMap(),
    val isUnilateral: Boolean = false,
    val error: String? = null
) {
    val isOpen: Boolean
        get() = isNew || exerciseId != null

    val canSave: Boolean
        get() = name.isNotBlank() && involvements.isNotEmpty()

    /** Advisory: the chosen pattern does not match the involvement profile. */
    val patternMismatch: Boolean
        get() = MovementPatternGuardrail.conflicts(movementPattern, involvements)

    /** Muscles at the "primary" tier or above, kept in sync for the legacy tag columns. */
    val primaryMuscles: Set<MuscleGroup>
        get() = involvements.filterValues { it >= PRIMARY_THRESHOLD }.keys

    val secondaryMuscles: Set<MuscleGroup>
        get() = involvements.filterValues { it < PRIMARY_THRESHOLD }.keys

    companion object {
        const val PRIMARY_THRESHOLD = 0.7
    }
}

/** A saved best set shown in the Personal records list (name resolved for display). */
data class PersonalRecordRow(
    val exerciseId: String,
    val exerciseName: String,
    val weightKg: Double,
    val reps: Int
)

/** The personal-record dialog: which exercise, and the entered best set. */
data class PersonalRecordEditorState(
    val open: Boolean = false,
    val exerciseId: String? = null,
    val weightInput: String = "",
    val repsInput: String = "",
    val error: String? = null
) {
    val isOpen: Boolean
        get() = open
}

/** The equipment item currently open in the manage dialog (rename/delete + max weight). */
data class EquipmentEditorState(
    val tag: EquipmentTag? = null,
    val name: String = "",
    val isBuiltIn: Boolean = false,
    val maxWeightInput: String = "",
    val error: String? = null
) {
    val isOpen: Boolean
        get() = tag != null
}

data class EquipmentProfilerUiState(
    val equipment: List<Equipment> = emptyList(),
    val selectedTags: Set<EquipmentTag> = emptySet(),
    val newEquipmentName: String = "",
    val newEquipmentError: String? = null,
    val exercises: List<Exercise> = emptyList(),
    val search: String = "",
    val exerciseEditor: ExerciseEditorState = ExerciseEditorState(),
    val equipmentEditor: EquipmentEditorState = EquipmentEditorState(),
    val personalRecords: List<PersonalRecordRow> = emptyList(),
    val personalRecordEditor: PersonalRecordEditorState = PersonalRecordEditorState(),
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
