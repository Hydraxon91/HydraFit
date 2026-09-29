package com.hydrafit.app.feature.equipment

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hydrafit.app.core.domain.equipment.Equipment
import com.hydrafit.app.core.domain.equipment.EquipmentTag
import com.hydrafit.app.core.domain.equipment.Exercise
import com.hydrafit.app.core.domain.equipment.MovementPattern
import com.hydrafit.app.core.domain.fatigue.MuscleGroup
import hydrafit.feature.equipment.generated.resources.Res
import hydrafit.feature.equipment.generated.resources.equipment_add_button
import hydrafit.feature.equipment.generated.resources.equipment_add_exercise
import hydrafit.feature.equipment.generated.resources.equipment_add_label
import hydrafit.feature.equipment.generated.resources.equipment_bodyweight
import hydrafit.feature.equipment.generated.resources.equipment_custom_section
import hydrafit.feature.equipment.generated.resources.equipment_delete
import hydrafit.feature.equipment.generated.resources.equipment_edit
import hydrafit.feature.equipment.generated.resources.equipment_edit_reset
import hydrafit.feature.equipment.generated.resources.equipment_edit_save
import hydrafit.feature.equipment.generated.resources.equipment_exercise_section
import hydrafit.feature.equipment.generated.resources.equipment_manage
import hydrafit.feature.equipment.generated.resources.equipment_movement_pattern
import hydrafit.feature.equipment.generated.resources.equipment_name_label
import hydrafit.feature.equipment.generated.resources.equipment_primary_muscles
import hydrafit.feature.equipment.generated.resources.equipment_profiler_title
import hydrafit.feature.equipment.generated.resources.equipment_remove
import hydrafit.feature.equipment.generated.resources.equipment_search_label
import hydrafit.feature.equipment.generated.resources.equipment_secondary_muscles
import hydrafit.feature.equipment.generated.resources.muscle_back
import hydrafit.feature.equipment.generated.resources.muscle_biceps
import hydrafit.feature.equipment.generated.resources.muscle_calves
import hydrafit.feature.equipment.generated.resources.muscle_chest
import hydrafit.feature.equipment.generated.resources.muscle_core
import hydrafit.feature.equipment.generated.resources.muscle_glutes
import hydrafit.feature.equipment.generated.resources.muscle_hamstrings
import hydrafit.feature.equipment.generated.resources.muscle_quads
import hydrafit.feature.equipment.generated.resources.muscle_shoulders
import hydrafit.feature.equipment.generated.resources.muscle_triceps
import hydrafit.feature.equipment.generated.resources.pattern_biceps_isolation
import hydrafit.feature.equipment.generated.resources.pattern_calf_raise
import hydrafit.feature.equipment.generated.resources.pattern_chest_fly
import hydrafit.feature.equipment.generated.resources.pattern_core
import hydrafit.feature.equipment.generated.resources.pattern_hinge
import hydrafit.feature.equipment.generated.resources.pattern_horizontal_pull
import hydrafit.feature.equipment.generated.resources.pattern_horizontal_push
import hydrafit.feature.equipment.generated.resources.pattern_leg_isolation
import hydrafit.feature.equipment.generated.resources.pattern_lunge
import hydrafit.feature.equipment.generated.resources.pattern_shoulder_isolation
import hydrafit.feature.equipment.generated.resources.pattern_squat
import hydrafit.feature.equipment.generated.resources.pattern_triceps_isolation
import hydrafit.feature.equipment.generated.resources.pattern_vertical_pull
import hydrafit.feature.equipment.generated.resources.pattern_vertical_push
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun EquipmentProfilerRoute(
    modifier: Modifier = Modifier,
    viewModel: EquipmentProfilerViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    EquipmentProfilerScreen(
        state = state,
        onTagToggled = viewModel::onTagToggled,
        onNewEquipmentNameChanged = viewModel::onNewEquipmentNameChanged,
        onAddEquipment = viewModel::onAddEquipment,
        onManageEquipment = viewModel::onManageEquipment,
        onRenameEquipmentNameChanged = viewModel::onRenameEquipmentNameChanged,
        onSaveEquipmentRenamed = viewModel::onSaveEquipmentRenamed,
        onDeleteEquipment = viewModel::onDeleteEquipment,
        onDismissEquipmentEditor = viewModel::onDismissEquipmentEditor,
        onSearchChanged = viewModel::onSearchChanged,
        onEditExercise = viewModel::onEditExercise,
        onNewCustomExercise = viewModel::onNewCustomExercise,
        onEditorNameChanged = viewModel::onEditorNameChanged,
        onEditorPatternChanged = viewModel::onEditorPatternChanged,
        onEditorEquipmentToggled = viewModel::onEditorEquipmentToggled,
        onEditorMuscleToggled = viewModel::onEditorMuscleToggled,
        onSaveExercise = viewModel::onSaveExercise,
        onResetExercise = viewModel::onResetExercise,
        onDeleteCustomExercise = viewModel::onDeleteCustomExercise,
        onDismissExerciseEditor = viewModel::onDismissExerciseEditor,
        modifier = modifier
    )
}

@Composable
fun EquipmentProfilerScreen(
    state: EquipmentProfilerUiState,
    onTagToggled: (EquipmentTag) -> Unit,
    onNewEquipmentNameChanged: (String) -> Unit,
    onAddEquipment: () -> Unit,
    onManageEquipment: (EquipmentTag) -> Unit,
    onRenameEquipmentNameChanged: (String) -> Unit,
    onSaveEquipmentRenamed: () -> Unit,
    onDeleteEquipment: () -> Unit,
    onDismissEquipmentEditor: () -> Unit,
    onSearchChanged: (String) -> Unit,
    onEditExercise: (String) -> Unit,
    onNewCustomExercise: () -> Unit,
    onEditorNameChanged: (String) -> Unit,
    onEditorPatternChanged: (MovementPattern) -> Unit,
    onEditorEquipmentToggled: (EquipmentTag) -> Unit,
    onEditorMuscleToggled: (MuscleGroup, Boolean) -> Unit,
    onSaveExercise: () -> Unit,
    onResetExercise: () -> Unit,
    onDeleteCustomExercise: () -> Unit,
    onDismissExerciseEditor: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .safeContentPadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = stringResource(Res.string.equipment_profiler_title),
            style = MaterialTheme.typography.headlineSmall
        )
        InventorySection(
            state = state,
            onTagToggled = onTagToggled,
            onManageEquipment = onManageEquipment,
            onNewEquipmentNameChanged = onNewEquipmentNameChanged,
            onAddEquipment = onAddEquipment
        )
        OutlinedTextField(
            value = state.search,
            onValueChange = onSearchChanged,
            label = { Text(stringResource(Res.string.equipment_search_label)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        if (state.customExercises.isNotEmpty()) {
            Text(
                text = stringResource(Res.string.equipment_custom_section),
                style = MaterialTheme.typography.titleMedium
            )
            state.customExercises.forEach { exercise ->
                ExerciseSummaryRow(exercise, onEditExercise)
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = stringResource(Res.string.equipment_exercise_section),
                style = MaterialTheme.typography.titleMedium
            )
            TextButton(onClick = onNewCustomExercise) {
                Text(stringResource(Res.string.equipment_add_exercise))
            }
        }
        state.builtInExercises.forEach { exercise ->
            ExerciseSummaryRow(exercise, onEditExercise)
        }
    }

    if (state.equipmentEditor.isOpen) {
        EquipmentEditorDialog(
            state = state.equipmentEditor,
            onNameChanged = onRenameEquipmentNameChanged,
            onSave = onSaveEquipmentRenamed,
            onDelete = onDeleteEquipment,
            onDismiss = onDismissEquipmentEditor
        )
    }
    if (state.exerciseEditor.isOpen) {
        ExerciseEditorDialog(
            state = state.exerciseEditor,
            equipment = state.equipment,
            onNameChanged = onEditorNameChanged,
            onPatternChanged = onEditorPatternChanged,
            onEquipmentToggled = onEditorEquipmentToggled,
            onMuscleToggled = onEditorMuscleToggled,
            onSave = onSaveExercise,
            onReset = onResetExercise,
            onDelete = onDeleteCustomExercise,
            onDismiss = onDismissExerciseEditor
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InventorySection(
    state: EquipmentProfilerUiState,
    onTagToggled: (EquipmentTag) -> Unit,
    onManageEquipment: (EquipmentTag) -> Unit,
    onNewEquipmentNameChanged: (String) -> Unit,
    onAddEquipment: () -> Unit
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        state.equipment.forEach { equipment ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                FilterChip(
                    selected = equipment.id in state.selectedTags,
                    onClick = { onTagToggled(equipment.id) },
                    label = { Text(equipment.name) }
                )
                if (!equipment.isBuiltIn) {
                    TextButton(onClick = { onManageEquipment(equipment.id) }) {
                        Text(stringResource(Res.string.equipment_manage))
                    }
                }
            }
        }
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedTextField(
            value = state.newEquipmentName,
            onValueChange = onNewEquipmentNameChanged,
            label = { Text(stringResource(Res.string.equipment_add_label)) },
            singleLine = true,
            modifier = Modifier.weight(1f)
        )
        Button(onClick = onAddEquipment, enabled = state.canAdd) {
            Text(stringResource(Res.string.equipment_add_button))
        }
    }
}

@Composable
private fun ExerciseSummaryRow(exercise: Exercise, onEdit: (String) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = exercise.name, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = equipmentSummary(exercise) + " · " + muscleNames(exercise.primaryMuscles),
                style = MaterialTheme.typography.bodySmall
            )
        }
        TextButton(onClick = { onEdit(exercise.id) }) {
            Text(stringResource(Res.string.equipment_edit))
        }
    }
}

@Composable
private fun EquipmentEditorDialog(
    state: EquipmentEditorState,
    onNameChanged: (String) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(onClick = onSave, enabled = state.name.isNotBlank()) {
                Text(stringResource(Res.string.equipment_edit_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDelete) {
                Text(stringResource(Res.string.equipment_delete))
            }
        },
        title = { Text(stringResource(Res.string.equipment_manage)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = state.name,
                    onValueChange = onNameChanged,
                    label = { Text(stringResource(Res.string.equipment_name_label)) },
                    singleLine = true
                )
                TextButton(onClick = onDismiss) {
                    Text(stringResource(Res.string.equipment_remove))
                }
            }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExerciseEditorDialog(
    state: ExerciseEditorState,
    equipment: List<Equipment>,
    onNameChanged: (String) -> Unit,
    onPatternChanged: (MovementPattern) -> Unit,
    onEquipmentToggled: (EquipmentTag) -> Unit,
    onMuscleToggled: (MuscleGroup, Boolean) -> Unit,
    onSave: () -> Unit,
    onReset: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(onClick = onSave, enabled = state.canSave) {
                Text(stringResource(Res.string.equipment_edit_save))
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (state.isCustom && !state.isNew) {
                    TextButton(onClick = onDelete) {
                        Text(stringResource(Res.string.equipment_delete))
                    }
                } else if (!state.isCustom) {
                    TextButton(onClick = onReset) {
                        Text(stringResource(Res.string.equipment_edit_reset))
                    }
                }
            }
        },
        title = {
            Text(
                if (state.isNew) {
                    stringResource(Res.string.equipment_add_exercise)
                } else {
                    state.name
                }
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = state.name,
                    onValueChange = onNameChanged,
                    label = { Text(stringResource(Res.string.equipment_name_label)) },
                    singleLine = true
                )
                MovementPatternPicker(state.movementPattern, onPatternChanged)
                Text(
                    text = stringResource(Res.string.equipment_primary_muscles),
                    style = MaterialTheme.typography.labelMedium
                )
                MuscleChipRow(
                    selected = state.primary,
                    onToggle = { onMuscleToggled(it, true) }
                )
                Text(
                    text = stringResource(Res.string.equipment_secondary_muscles),
                    style = MaterialTheme.typography.labelMedium
                )
                MuscleChipRow(
                    selected = state.secondary,
                    onToggle = { onMuscleToggled(it, false) }
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    equipment.forEach { item ->
                        FilterChip(
                            selected = item.id in state.equipment,
                            onClick = { onEquipmentToggled(item.id) },
                            label = { Text(item.name) }
                        )
                    }
                }
                state.error?.let { message ->
                    Text(
                        text = message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MovementPatternPicker(selected: MovementPattern, onChange: (MovementPattern) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = Modifier.fillMaxWidth()
    ) {
        OutlinedTextField(
            value = stringResource(selected.labelResource()),
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(Res.string.equipment_movement_pattern)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            MovementPattern.entries.forEach { pattern ->
                DropdownMenuItem(
                    text = { Text(stringResource(pattern.labelResource())) },
                    onClick = {
                        onChange(pattern)
                        expanded = false
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MuscleChipRow(selected: Set<MuscleGroup>, onToggle: (MuscleGroup) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        MuscleGroup.entries.forEach { muscle ->
            FilterChip(
                selected = muscle in selected,
                onClick = { onToggle(muscle) },
                label = { Text(stringResource(muscle.labelResource())) }
            )
        }
    }
}

@Composable
private fun equipmentSummary(exercise: Exercise): String =
    exercise.requiredEquipment.joinToString { it.displayName }
        .ifEmpty { stringResource(Res.string.equipment_bodyweight) }

@Composable
private fun muscleNames(muscles: Set<MuscleGroup>): String {
    val labels = mutableListOf<String>()
    muscles.forEach { muscle -> labels += stringResource(muscle.labelResource()) }
    return labels.joinToString(", ")
}

private fun MuscleGroup.labelResource(): StringResource = when (this) {
    MuscleGroup.CHEST -> Res.string.muscle_chest
    MuscleGroup.BACK -> Res.string.muscle_back
    MuscleGroup.SHOULDERS -> Res.string.muscle_shoulders
    MuscleGroup.BICEPS -> Res.string.muscle_biceps
    MuscleGroup.TRICEPS -> Res.string.muscle_triceps
    MuscleGroup.QUADS -> Res.string.muscle_quads
    MuscleGroup.HAMSTRINGS -> Res.string.muscle_hamstrings
    MuscleGroup.GLUTES -> Res.string.muscle_glutes
    MuscleGroup.CALVES -> Res.string.muscle_calves
    MuscleGroup.CORE -> Res.string.muscle_core
}

private fun MovementPattern.labelResource(): StringResource = when (this) {
    MovementPattern.HORIZONTAL_PUSH -> Res.string.pattern_horizontal_push
    MovementPattern.VERTICAL_PUSH -> Res.string.pattern_vertical_push
    MovementPattern.HORIZONTAL_PULL -> Res.string.pattern_horizontal_pull
    MovementPattern.VERTICAL_PULL -> Res.string.pattern_vertical_pull
    MovementPattern.SQUAT -> Res.string.pattern_squat
    MovementPattern.HINGE -> Res.string.pattern_hinge
    MovementPattern.LUNGE -> Res.string.pattern_lunge
    MovementPattern.CALF_RAISE -> Res.string.pattern_calf_raise
    MovementPattern.CHEST_FLY -> Res.string.pattern_chest_fly
    MovementPattern.BICEPS_ISOLATION -> Res.string.pattern_biceps_isolation
    MovementPattern.TRICEPS_ISOLATION -> Res.string.pattern_triceps_isolation
    MovementPattern.SHOULDER_ISOLATION -> Res.string.pattern_shoulder_isolation
    MovementPattern.LEG_ISOLATION -> Res.string.pattern_leg_isolation
    MovementPattern.CORE -> Res.string.pattern_core
}
