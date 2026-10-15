package com.hydrafit.app.feature.equipment

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.hydrafit.app.core.domain.equipment.LoadingInventory
import hydrafit.feature.equipment.generated.resources.Res
import hydrafit.feature.equipment.generated.resources.equipment_calculator_calculate
import hydrafit.feature.equipment.generated.resources.equipment_calculator_choose
import hydrafit.feature.equipment.generated.resources.equipment_calculator_exact
import hydrafit.feature.equipment.generated.resources.equipment_calculator_failed
import hydrafit.feature.equipment.generated.resources.equipment_calculator_invalid_target
import hydrafit.feature.equipment.generated.resources.equipment_calculator_kind_dumbbell
import hydrafit.feature.equipment.generated.resources.equipment_calculator_kind_plate
import hydrafit.feature.equipment.generated.resources.equipment_calculator_lower
import hydrafit.feature.equipment.generated.resources.equipment_calculator_no_equipment
import hydrafit.feature.equipment.generated.resources.equipment_calculator_no_plates
import hydrafit.feature.equipment.generated.resources.equipment_calculator_none_lower
import hydrafit.feature.equipment.generated.resources.equipment_calculator_none_upper
import hydrafit.feature.equipment.generated.resources.equipment_calculator_open
import hydrafit.feature.equipment.generated.resources.equipment_calculator_out_of_range
import hydrafit.feature.equipment.generated.resources.equipment_calculator_per_dumbbell
import hydrafit.feature.equipment.generated.resources.equipment_calculator_per_side
import hydrafit.feature.equipment.generated.resources.equipment_calculator_target
import hydrafit.feature.equipment.generated.resources.equipment_calculator_title
import hydrafit.feature.equipment.generated.resources.equipment_calculator_total
import hydrafit.feature.equipment.generated.resources.equipment_calculator_upper
import hydrafit.feature.equipment.generated.resources.equipment_cancel
import hydrafit.feature.equipment.generated.resources.equipment_delete
import hydrafit.feature.equipment.generated.resources.equipment_edit
import hydrafit.feature.equipment.generated.resources.equipment_edit_save
import hydrafit.feature.equipment.generated.resources.equipment_loading_add_bar
import hydrafit.feature.equipment.generated.resources.equipment_loading_add_dumbbell
import hydrafit.feature.equipment.generated.resources.equipment_loading_add_plate
import hydrafit.feature.equipment.generated.resources.equipment_loading_bar_collar
import hydrafit.feature.equipment.generated.resources.equipment_loading_bar_name
import hydrafit.feature.equipment.generated.resources.equipment_loading_bar_weight
import hydrafit.feature.equipment.generated.resources.equipment_loading_bars_section
import hydrafit.feature.equipment.generated.resources.equipment_loading_dumbbell_fixed
import hydrafit.feature.equipment.generated.resources.equipment_loading_dumbbell_fixed_mode
import hydrafit.feature.equipment.generated.resources.equipment_loading_dumbbell_increment
import hydrafit.feature.equipment.generated.resources.equipment_loading_dumbbell_max
import hydrafit.feature.equipment.generated.resources.equipment_loading_dumbbell_min
import hydrafit.feature.equipment.generated.resources.equipment_loading_dumbbell_name
import hydrafit.feature.equipment.generated.resources.equipment_loading_dumbbell_range_mode
import hydrafit.feature.equipment.generated.resources.equipment_loading_dumbbells_section
import hydrafit.feature.equipment.generated.resources.equipment_loading_error_amount_range
import hydrafit.feature.equipment.generated.resources.equipment_loading_error_dumbbell_fixed
import hydrafit.feature.equipment.generated.resources.equipment_loading_error_dumbbell_range
import hydrafit.feature.equipment.generated.resources.equipment_loading_error_duplicate_name
import hydrafit.feature.equipment.generated.resources.equipment_loading_error_duplicate_plate
import hydrafit.feature.equipment.generated.resources.equipment_loading_error_inventory_limit
import hydrafit.feature.equipment.generated.resources.equipment_loading_error_name
import hydrafit.feature.equipment.generated.resources.equipment_loading_error_plate_count
import hydrafit.feature.equipment.generated.resources.equipment_loading_error_plate_limit
import hydrafit.feature.equipment.generated.resources.equipment_loading_error_plate_value
import hydrafit.feature.equipment.generated.resources.equipment_loading_error_save
import hydrafit.feature.equipment.generated.resources.equipment_loading_error_stale
import hydrafit.feature.equipment.generated.resources.equipment_loading_native_note
import hydrafit.feature.equipment.generated.resources.equipment_loading_plate_count
import hydrafit.feature.equipment.generated.resources.equipment_loading_plate_weight
import hydrafit.feature.equipment.generated.resources.equipment_loading_plates_section
import hydrafit.feature.equipment.generated.resources.equipment_loading_section
import hydrafit.feature.equipment.generated.resources.equipment_loading_summary
import hydrafit.feature.equipment.generated.resources.equipment_loading_unit
import hydrafit.feature.equipment.generated.resources.equipment_loading_unit_kg
import hydrafit.feature.equipment.generated.resources.equipment_loading_unit_lb
import hydrafit.feature.equipment.generated.resources.equipment_loading_unit_locked
import hydrafit.feature.equipment.generated.resources.equipment_name_label
import hydrafit.feature.equipment.generated.resources.equipment_profile_active
import hydrafit.feature.equipment.generated.resources.equipment_profile_active_badge
import hydrafit.feature.equipment.generated.resources.equipment_profile_copy
import hydrafit.feature.equipment.generated.resources.equipment_profile_copy_note
import hydrafit.feature.equipment.generated.resources.equipment_profile_copy_title
import hydrafit.feature.equipment.generated.resources.equipment_profile_delete_message
import hydrafit.feature.equipment.generated.resources.equipment_profile_delete_replacement
import hydrafit.feature.equipment.generated.resources.equipment_profile_delete_title
import hydrafit.feature.equipment.generated.resources.equipment_profile_last
import hydrafit.feature.equipment.generated.resources.equipment_profile_name_required
import hydrafit.feature.equipment.generated.resources.equipment_profile_name_too_long
import hydrafit.feature.equipment.generated.resources.equipment_profile_new
import hydrafit.feature.equipment.generated.resources.equipment_profile_new_note
import hydrafit.feature.equipment.generated.resources.equipment_profile_new_title
import hydrafit.feature.equipment.generated.resources.equipment_profile_operation_failed
import hydrafit.feature.equipment.generated.resources.equipment_profile_rename
import hydrafit.feature.equipment.generated.resources.equipment_profile_rename_note
import hydrafit.feature.equipment.generated.resources.equipment_profile_rename_title
import hydrafit.feature.equipment.generated.resources.equipment_profile_replacement_required
import hydrafit.feature.equipment.generated.resources.equipment_profile_stale
import hydrafit.feature.equipment.generated.resources.equipment_profile_use
import hydrafit.feature.equipment.generated.resources.equipment_profiles_section
import hydrafit.feature.equipment.generated.resources.equipment_remove
import org.jetbrains.compose.resources.stringResource

/** Stable per-control identities for repeated equipment-profile and loading rows. */
internal object EquipmentTestTags {
    const val PROFILES_SECTION = "equipment_profiles_section"
    const val PROFILE_ROW = "equipment_profile_row"
    const val LOADING_SECTION = "equipment_loading_section"
    const val LOADING_PLATE_ROW = "equipment_loading_plate_row"
    const val LOADING_BAR_ROW = "equipment_loading_bar_row"
    const val LOADING_DUMBBELL_ROW = "equipment_loading_dumbbell_row"
    const val CALCULATOR_SECTION = "equipment_calculator_section"
}

/** The profile-list actions, grouped so the screen signature stays readable. */
data class EquipmentProfileActions(
    val onNew: () -> Unit,
    val onCopy: () -> Unit,
    val onRename: () -> Unit,
    val onActivate: (Long) -> Unit,
    val onDelete: (Long) -> Unit,
    val onOpenLoading: () -> Unit,
    val onOpenCalculator: () -> Unit
)

/** The loading draft's actions; every one of them edits the draft and writes nothing. */
data class LoadingInventoryActions(
    val onUnitSelected: (LoadingUnitOption) -> Unit,
    val onPlateChanged: (Int, OwnedPlateDraft) -> Unit,
    val onPlateAdded: () -> Unit,
    val onPlateRemoved: (Int) -> Unit,
    val onBarChanged: (Int, LoadingBarDraft) -> Unit,
    val onBarAdded: () -> Unit,
    val onBarRemoved: (Int) -> Unit,
    val onDumbbellChanged: (Int, LoadingDumbbellDraft) -> Unit,
    val onDumbbellAdded: () -> Unit,
    val onDumbbellRemoved: (Int) -> Unit,
    val onSave: () -> Unit,
    val onCancel: () -> Unit
)

/** The calculator's actions; changing any of them invalidates a result still in flight. */
data class LoadingCalculatorActions(
    val onKindChanged: (LoadingCalculatorKind) -> Unit,
    val onEquipmentSelected: (String) -> Unit,
    val onTargetChanged: (String) -> Unit,
    val onCalculate: () -> Unit,
    val onDismiss: () -> Unit
)

/**
 * The equipment-profile section: the active profile, its configuration summary and the actions that
 * create, copy, rename, switch and delete profiles.
 */
@Composable
internal fun EquipmentProfilesSection(
    summary: EquipmentProfileSummary?,
    failure: EquipmentProfileFailure?,
    actions: EquipmentProfileActions,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag(EquipmentTestTags.PROFILES_SECTION),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = stringResource(Res.string.equipment_profiles_section),
            style = MaterialTheme.typography.titleMedium
        )
        if (summary == null) return@Column
        Text(
            text = stringResource(Res.string.equipment_profile_active, summary.activeName),
            style = MaterialTheme.typography.bodyMedium
        )
        summary.rows.forEach { row ->
            ProfileRow(
                row = row,
                canDelete = summary.canDelete,
                onActivate = actions.onActivate,
                onDelete = actions.onDelete
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = actions.onNew) {
                Text(stringResource(Res.string.equipment_profile_new))
            }
            TextButton(onClick = actions.onCopy) {
                Text(stringResource(Res.string.equipment_profile_copy))
            }
            TextButton(onClick = actions.onRename) {
                Text(stringResource(Res.string.equipment_profile_rename))
            }
        }
        LoadingConfigurationRow(
            summary = summary,
            onOpenLoading = actions.onOpenLoading,
            onOpenCalculator = actions.onOpenCalculator
        )
        failure?.let { ProfileFailureText(it) }
    }
}

@Composable
private fun ProfileRow(
    row: EquipmentProfileRow,
    canDelete: Boolean,
    onActivate: (Long) -> Unit,
    onDelete: (Long) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(EquipmentTestTags.PROFILE_ROW),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = row.name, style = MaterialTheme.typography.bodyLarge)
            if (row.isActive) {
                Text(
                    text = stringResource(Res.string.equipment_profile_active_badge),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
        if (!row.isActive) {
            TextButton(onClick = { onActivate(row.id) }) {
                Text(stringResource(Res.string.equipment_profile_use))
            }
        }
        if (canDelete) {
            TextButton(onClick = { onDelete(row.id) }) {
                Text(stringResource(Res.string.equipment_delete))
            }
        }
    }
}

@Composable
private fun LoadingConfigurationRow(
    summary: EquipmentProfileSummary,
    onOpenLoading: () -> Unit,
    onOpenCalculator: () -> Unit
) {
    Text(
        text = stringResource(Res.string.equipment_loading_section),
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.testTag(EquipmentTestTags.LOADING_SECTION)
    )
    Text(
        text = stringResource(
            Res.string.equipment_loading_summary,
            summary.loadingInventory.plates.size,
            summary.loadingInventory.bars.size,
            summary.loadingInventory.dumbbells.size
        ),
        style = MaterialTheme.typography.bodySmall
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(onClick = onOpenLoading) {
            Text(stringResource(Res.string.equipment_edit))
        }
        TextButton(onClick = onOpenCalculator) {
            Text(
                text = stringResource(Res.string.equipment_calculator_open),
                modifier = Modifier.testTag(EquipmentTestTags.CALCULATOR_SECTION)
            )
        }
    }
}

@Composable
private fun ProfileFailureText(failure: EquipmentProfileFailure) {
    val message = when (failure) {
        EquipmentProfileFailure.NAME_REQUIRED ->
            stringResource(Res.string.equipment_profile_name_required)

        EquipmentProfileFailure.NAME_TOO_LONG ->
            stringResource(Res.string.equipment_profile_name_too_long)

        EquipmentProfileFailure.LAST_PROFILE ->
            stringResource(Res.string.equipment_profile_last)

        EquipmentProfileFailure.REPLACEMENT_REQUIRED ->
            stringResource(Res.string.equipment_profile_replacement_required)

        EquipmentProfileFailure.STALE -> stringResource(Res.string.equipment_profile_stale)
        EquipmentProfileFailure.FAILED ->
            stringResource(Res.string.equipment_profile_operation_failed)
    }
    Text(
        text = message,
        color = MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.bodySmall
    )
}

@Composable
internal fun EquipmentProfileEditorDialog(
    state: EquipmentProfileEditorState,
    onNameChanged: (String) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit
) {
    val mode = state.mode ?: return
    val title = when (mode) {
        EquipmentProfileEditorMode.CREATE -> stringResource(Res.string.equipment_profile_new_title)
        EquipmentProfileEditorMode.COPY -> stringResource(Res.string.equipment_profile_copy_title)
        EquipmentProfileEditorMode.RENAME ->
            stringResource(Res.string.equipment_profile_rename_title)
    }
    AlertDialog(
        onDismissRequest = onCancel,
        confirmButton = {
            Button(onClick = onSave, enabled = state.canSave) {
                Text(stringResource(Res.string.equipment_edit_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) {
                Text(stringResource(Res.string.equipment_cancel))
            }
        },
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = state.name,
                    onValueChange = onNameChanged,
                    label = { Text(stringResource(Res.string.equipment_name_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = when (mode) {
                        EquipmentProfileEditorMode.CREATE ->
                            stringResource(Res.string.equipment_profile_new_note)

                        EquipmentProfileEditorMode.COPY ->
                            stringResource(Res.string.equipment_profile_copy_note)

                        EquipmentProfileEditorMode.RENAME ->
                            stringResource(Res.string.equipment_profile_rename_note)
                    },
                    style = MaterialTheme.typography.bodySmall
                )
                state.failure?.let { ProfileFailureText(it) }
            }
        }
    )
}

/** The deletion confirmation, including the explicit replacement of an active profile. */
@Composable
internal fun EquipmentProfileDeleteDialog(
    state: EquipmentProfileDeleteState,
    onSelectReplacement: (Long) -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onCancel,
        confirmButton = {
            Button(onClick = onConfirm, enabled = state.canConfirm) {
                Text(stringResource(Res.string.equipment_delete))
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) {
                Text(stringResource(Res.string.equipment_cancel))
            }
        },
        title = { Text(stringResource(Res.string.equipment_profile_delete_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(Res.string.equipment_profile_delete_message, state.name))
                if (state.requiresReplacement) {
                    Text(
                        text = stringResource(Res.string.equipment_profile_delete_replacement),
                        style = MaterialTheme.typography.bodySmall
                    )
                    state.candidates.forEach { candidate ->
                        FilterChip(
                            selected = candidate.id == state.replacementId,
                            onClick = { onSelectReplacement(candidate.id) },
                            label = { Text(candidate.name) }
                        )
                    }
                }
                state.failure?.let { ProfileFailureText(it) }
            }
        }
    )
}

/**
 * The loading-configuration draft. Every field edits the draft; Save is the only write and Cancel
 * discards it without touching the stored configuration.
 */
@Composable
internal fun LoadingInventoryDialog(
    state: LoadingInventoryEditorState,
    actions: LoadingInventoryActions
) {
    AlertDialog(
        onDismissRequest = actions.onCancel,
        confirmButton = {
            Button(onClick = actions.onSave, enabled = state.canSave) {
                Text(stringResource(Res.string.equipment_edit_save))
            }
        },
        dismissButton = {
            TextButton(onClick = actions.onCancel) {
                Text(stringResource(Res.string.equipment_cancel))
            }
        },
        title = { Text(stringResource(Res.string.equipment_loading_section)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                UnitSelector(state, actions)
                Text(
                    text = stringResource(
                        Res.string.equipment_loading_native_note,
                        state.unit.storageId
                    ),
                    style = MaterialTheme.typography.bodySmall
                )
                PlateDrafts(state, actions)
                BarDrafts(state, actions)
                DumbbellDrafts(state, actions)
                LoadingFailureText(state, actions)
            }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun UnitSelector(state: LoadingInventoryEditorState, actions: LoadingInventoryActions) {
    Text(
        text = stringResource(Res.string.equipment_loading_unit),
        style = MaterialTheme.typography.titleSmall
    )
    if (state.unitLocked) {
        Text(
            text = stringResource(
                Res.string.equipment_loading_unit_locked,
                unitLabel(state.unit)
            ),
            style = MaterialTheme.typography.bodySmall
        )
    } else {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LoadingUnitOption.entries.forEach { option ->
                FilterChip(
                    selected = option == state.unit,
                    onClick = { actions.onUnitSelected(option) },
                    label = { Text(unitLabel(option)) }
                )
            }
        }
    }
}

@Composable
private fun PlateDrafts(state: LoadingInventoryEditorState, actions: LoadingInventoryActions) {
    Text(
        text = stringResource(Res.string.equipment_loading_plates_section),
        style = MaterialTheme.typography.titleSmall
    )
    state.plates.forEachIndexed { index, plate ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .testTag(EquipmentTestTags.LOADING_PLATE_ROW)
        ) {
            OutlinedTextField(
                value = plate.weightInput,
                onValueChange = {
                    actions.onPlateChanged(index, plate.copy(weightInput = it))
                },
                label = {
                    Text(
                        stringResource(
                            Res.string.equipment_loading_plate_weight,
                            state.unit.storageId
                        )
                    )
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = plate.countInput,
                onValueChange = {
                    actions.onPlateChanged(index, plate.copy(countInput = it))
                },
                label = { Text(stringResource(Res.string.equipment_loading_plate_count)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            TextButton(onClick = { actions.onPlateRemoved(index) }) {
                Text(stringResource(Res.string.equipment_remove))
            }
        }
    }
    TextButton(onClick = actions.onPlateAdded) {
        Text(stringResource(Res.string.equipment_loading_add_plate))
    }
}

@Composable
private fun BarDrafts(state: LoadingInventoryEditorState, actions: LoadingInventoryActions) {
    Text(
        text = stringResource(Res.string.equipment_loading_bars_section),
        style = MaterialTheme.typography.titleSmall
    )
    state.bars.forEachIndexed { index, bar ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .testTag(EquipmentTestTags.LOADING_BAR_ROW)
        ) {
            OutlinedTextField(
                value = bar.name,
                onValueChange = { actions.onBarChanged(index, bar.copy(name = it)) },
                label = { Text(stringResource(Res.string.equipment_loading_bar_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = bar.weightInput,
                onValueChange = { actions.onBarChanged(index, bar.copy(weightInput = it)) },
                label = {
                    Text(
                        stringResource(
                            Res.string.equipment_loading_bar_weight,
                            state.unit.storageId
                        )
                    )
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = bar.collarInput,
                onValueChange = { actions.onBarChanged(index, bar.copy(collarInput = it)) },
                label = {
                    Text(
                        stringResource(
                            Res.string.equipment_loading_bar_collar,
                            state.unit.storageId
                        )
                    )
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            TextButton(onClick = { actions.onBarRemoved(index) }) {
                Text(stringResource(Res.string.equipment_remove))
            }
        }
    }
    TextButton(onClick = actions.onBarAdded) {
        Text(stringResource(Res.string.equipment_loading_add_bar))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DumbbellDrafts(state: LoadingInventoryEditorState, actions: LoadingInventoryActions) {
    Text(
        text = stringResource(Res.string.equipment_loading_dumbbells_section),
        style = MaterialTheme.typography.titleSmall
    )
    state.dumbbells.forEachIndexed { index, dumbbell ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .testTag(EquipmentTestTags.LOADING_DUMBBELL_ROW)
        ) {
            OutlinedTextField(
                value = dumbbell.name,
                onValueChange = { actions.onDumbbellChanged(index, dumbbell.copy(name = it)) },
                label = { Text(stringResource(Res.string.equipment_loading_dumbbell_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = dumbbell.mode == DumbbellDraftMode.FIXED_VALUES,
                    onClick = {
                        actions.onDumbbellChanged(
                            index,
                            dumbbell.copy(mode = DumbbellDraftMode.FIXED_VALUES)
                        )
                    },
                    label = {
                        Text(stringResource(Res.string.equipment_loading_dumbbell_fixed_mode))
                    }
                )
                FilterChip(
                    selected = dumbbell.mode == DumbbellDraftMode.ADJUSTABLE_RANGE,
                    onClick = {
                        actions.onDumbbellChanged(
                            index,
                            dumbbell.copy(mode = DumbbellDraftMode.ADJUSTABLE_RANGE)
                        )
                    },
                    label = {
                        Text(stringResource(Res.string.equipment_loading_dumbbell_range_mode))
                    }
                )
            }
            if (dumbbell.mode == DumbbellDraftMode.FIXED_VALUES) {
                OutlinedTextField(
                    value = dumbbell.fixedInput,
                    onValueChange = {
                        actions.onDumbbellChanged(index, dumbbell.copy(fixedInput = it))
                    },
                    label = {
                        Text(
                            stringResource(
                                Res.string.equipment_loading_dumbbell_fixed,
                                state.unit.storageId
                            )
                        )
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                DumbbellRangeFields(state, dumbbell, index, actions)
            }
            TextButton(onClick = { actions.onDumbbellRemoved(index) }) {
                Text(stringResource(Res.string.equipment_remove))
            }
        }
    }
    TextButton(onClick = actions.onDumbbellAdded) {
        Text(stringResource(Res.string.equipment_loading_add_dumbbell))
    }
}

@Composable
private fun DumbbellRangeFields(
    state: LoadingInventoryEditorState,
    dumbbell: LoadingDumbbellDraft,
    index: Int,
    actions: LoadingInventoryActions
) {
    OutlinedTextField(
        value = dumbbell.minimumInput,
        onValueChange = { actions.onDumbbellChanged(index, dumbbell.copy(minimumInput = it)) },
        label = {
            Text(
                stringResource(Res.string.equipment_loading_dumbbell_min, state.unit.storageId)
            )
        },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
    OutlinedTextField(
        value = dumbbell.maximumInput,
        onValueChange = { actions.onDumbbellChanged(index, dumbbell.copy(maximumInput = it)) },
        label = {
            Text(
                stringResource(Res.string.equipment_loading_dumbbell_max, state.unit.storageId)
            )
        },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
    OutlinedTextField(
        value = dumbbell.incrementInput,
        onValueChange = { actions.onDumbbellChanged(index, dumbbell.copy(incrementInput = it)) },
        label = {
            Text(
                stringResource(
                    Res.string.equipment_loading_dumbbell_increment,
                    state.unit.storageId
                )
            )
        },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun LoadingFailureText(
    state: LoadingInventoryEditorState,
    actions: LoadingInventoryActions
) {
    val failure = state.failure ?: return
    Text(
        text = when (failure) {
            LoadingEditorFailure.UNIT_REQUIRED ->
                stringResource(Res.string.equipment_loading_error_dumbbell_range)

            LoadingEditorFailure.PLATE_VALUE ->
                stringResource(Res.string.equipment_loading_error_plate_value)

            LoadingEditorFailure.PLATE_COUNT ->
                stringResource(Res.string.equipment_loading_error_plate_count)

            LoadingEditorFailure.DUPLICATE_PLATE ->
                stringResource(Res.string.equipment_loading_error_duplicate_plate)

            LoadingEditorFailure.PLATE_LIMIT ->
                stringResource(Res.string.equipment_loading_error_plate_limit)

            LoadingEditorFailure.NAME_REQUIRED ->
                stringResource(Res.string.equipment_loading_error_name)

            LoadingEditorFailure.NAME_DUPLICATE ->
                stringResource(Res.string.equipment_loading_error_duplicate_name)

            LoadingEditorFailure.AMOUNT_RANGE ->
                stringResource(Res.string.equipment_loading_error_amount_range)

            LoadingEditorFailure.INVENTORY_LIMIT -> stringResource(
                Res.string.equipment_loading_error_inventory_limit,
                state.unit.storageId
            )

            LoadingEditorFailure.DUMBBELL_FIXED_VALUE ->
                stringResource(Res.string.equipment_loading_error_dumbbell_fixed)

            LoadingEditorFailure.DUMBBELL_RANGE,
            LoadingEditorFailure.DUMBBELL_INCREMENT ->
                stringResource(Res.string.equipment_loading_error_dumbbell_range)

            LoadingEditorFailure.STALE -> stringResource(Res.string.equipment_loading_error_stale)
            LoadingEditorFailure.SAVE_FAILED ->
                stringResource(Res.string.equipment_loading_error_save)
        },
        color = MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.bodySmall
    )
}

/** The manual calculator over one saved bar or dumbbell in the profile's own unit. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun LoadingCalculatorDialog(
    state: LoadingCalculatorState,
    inventory: LoadingInventory,
    unit: LoadingUnitOption,
    actions: LoadingCalculatorActions
) {
    val options = loadingCalculatorOptions(inventory, state.kind)
    val expandedState = remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = actions.onDismiss,
        confirmButton = {
            Button(
                onClick = actions.onCalculate,
                enabled = state.selectedId != null && !state.isRunning
            ) {
                Text(stringResource(Res.string.equipment_calculator_calculate))
            }
        },
        dismissButton = {
            TextButton(onClick = actions.onDismiss) {
                Text(stringResource(Res.string.equipment_cancel))
            }
        },
        title = { Text(stringResource(Res.string.equipment_calculator_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = state.kind == LoadingCalculatorKind.PLATE,
                        onClick = { actions.onKindChanged(LoadingCalculatorKind.PLATE) },
                        label = {
                            Text(stringResource(Res.string.equipment_calculator_kind_plate))
                        }
                    )
                    FilterChip(
                        selected = state.kind == LoadingCalculatorKind.DUMBBELL,
                        onClick = { actions.onKindChanged(LoadingCalculatorKind.DUMBBELL) },
                        label = {
                            Text(stringResource(Res.string.equipment_calculator_kind_dumbbell))
                        }
                    )
                }
                if (options.isEmpty()) {
                    Text(
                        text = stringResource(Res.string.equipment_calculator_no_equipment),
                        style = MaterialTheme.typography.bodySmall
                    )
                    return@Column
                }
                ExposedDropdownMenuBox(
                    expanded = expandedState.value,
                    onExpandedChange = { expandedState.value = it }
                ) {
                    OutlinedTextField(
                        value = options.firstOrNull { it.id == state.selectedId }?.label.orEmpty(),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(Res.string.equipment_calculator_choose)) },
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedState.value)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                    )
                    ExposedDropdownMenu(
                        expanded = expandedState.value,
                        onDismissRequest = { expandedState.value = false }
                    ) {
                        options.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option.label) },
                                onClick = {
                                    actions.onEquipmentSelected(option.id)
                                    expandedState.value = false
                                }
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = state.targetInput,
                    onValueChange = actions.onTargetChanged,
                    label = {
                        Text(stringResource(Res.string.equipment_calculator_target, unit.storageId))
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                CalculatorOutput(state, unit)
            }
        }
    )
}

@Composable
private fun CalculatorOutput(state: LoadingCalculatorState, unit: LoadingUnitOption) {
    state.failure?.let { failure ->
        Text(
            text = when (failure) {
                LoadingCalculatorFailure.NO_EQUIPMENT ->
                    stringResource(Res.string.equipment_calculator_no_equipment)

                LoadingCalculatorFailure.INVALID_TARGET ->
                    stringResource(Res.string.equipment_calculator_invalid_target)

                LoadingCalculatorFailure.OUT_OF_RANGE ->
                    stringResource(Res.string.equipment_calculator_out_of_range)

                LoadingCalculatorFailure.FAILED ->
                    stringResource(Res.string.equipment_calculator_failed)
            },
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall
        )
        return
    }
    val result = state.result ?: return
    result.options.forEach { option ->
        Text(
            text = when (option.label) {
                LoadingResultLabel.EXACT -> stringResource(Res.string.equipment_calculator_exact)
                LoadingResultLabel.LOWER -> stringResource(Res.string.equipment_calculator_lower)
                LoadingResultLabel.UPPER -> stringResource(Res.string.equipment_calculator_upper)
            },
            style = MaterialTheme.typography.titleSmall
        )
        if (option.perDumbbell) {
            Text(
                text = stringResource(
                    Res.string.equipment_calculator_per_dumbbell,
                    option.totalWeight
                ),
                style = MaterialTheme.typography.bodyMedium
            )
        } else {
            Text(
                text = stringResource(Res.string.equipment_calculator_total, option.totalWeight),
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = if (option.perSidePlates.isEmpty()) {
                    stringResource(Res.string.equipment_calculator_no_plates)
                } else {
                    stringResource(
                        Res.string.equipment_calculator_per_side,
                        option.perSidePlates.joinToString(", ") {
                            "${it.denomination} × ${it.perSideCount}"
                        }
                    )
                },
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
    if (!result.isExact && result.missingLower) {
        Text(
            text = stringResource(Res.string.equipment_calculator_none_lower),
            style = MaterialTheme.typography.bodySmall
        )
    }
    if (!result.isExact && result.missingUpper) {
        Text(
            text = stringResource(Res.string.equipment_calculator_none_upper),
            style = MaterialTheme.typography.bodySmall
        )
    }
    Text(
        text = stringResource(Res.string.equipment_loading_native_note, unit.storageId),
        style = MaterialTheme.typography.bodySmall
    )
}

@Composable
private fun unitLabel(option: LoadingUnitOption): String = when (option) {
    LoadingUnitOption.KG -> stringResource(Res.string.equipment_loading_unit_kg)
    LoadingUnitOption.LB -> stringResource(Res.string.equipment_loading_unit_lb)
}
