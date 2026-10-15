package com.hydrafit.app.feature.progress

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hydrafit.app.core.domain.unit.WeightUnit
import hydrafit.feature.progress.generated.resources.Res
import hydrafit.feature.progress.generated.resources.bodyweight_add
import hydrafit.feature.progress.generated.resources.bodyweight_cancel
import hydrafit.feature.progress.generated.resources.bodyweight_date_label
import hydrafit.feature.progress.generated.resources.bodyweight_delete
import hydrafit.feature.progress.generated.resources.bodyweight_delete_confirm
import hydrafit.feature.progress.generated.resources.bodyweight_delete_confirm_message
import hydrafit.feature.progress.generated.resources.bodyweight_delete_confirm_title
import hydrafit.feature.progress.generated.resources.bodyweight_duplicate_message
import hydrafit.feature.progress.generated.resources.bodyweight_duplicate_replace
import hydrafit.feature.progress.generated.resources.bodyweight_duplicate_title
import hydrafit.feature.progress.generated.resources.bodyweight_editor_edit_title
import hydrafit.feature.progress.generated.resources.bodyweight_empty
import hydrafit.feature.progress.generated.resources.bodyweight_error_date_occupied
import hydrafit.feature.progress.generated.resources.bodyweight_error_future_date
import hydrafit.feature.progress.generated.resources.bodyweight_error_invalid_date
import hydrafit.feature.progress.generated.resources.bodyweight_error_invalid_weight
import hydrafit.feature.progress.generated.resources.bodyweight_error_missing_measurement
import hydrafit.feature.progress.generated.resources.bodyweight_goal_clear
import hydrafit.feature.progress.generated.resources.bodyweight_goal_current
import hydrafit.feature.progress.generated.resources.bodyweight_goal_input_label
import hydrafit.feature.progress.generated.resources.bodyweight_goal_invalid
import hydrafit.feature.progress.generated.resources.bodyweight_goal_none
import hydrafit.feature.progress.generated.resources.bodyweight_goal_save
import hydrafit.feature.progress.generated.resources.bodyweight_goal_title
import hydrafit.feature.progress.generated.resources.bodyweight_load_failed
import hydrafit.feature.progress.generated.resources.bodyweight_loading
import hydrafit.feature.progress.generated.resources.bodyweight_retry
import hydrafit.feature.progress.generated.resources.bodyweight_save
import hydrafit.feature.progress.generated.resources.bodyweight_title
import hydrafit.feature.progress.generated.resources.bodyweight_weight_label
import hydrafit.feature.progress.generated.resources.progress_back
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/** Stable external identities for the Body weight controls; technical metadata, never user copy. */
internal object BodyWeightTestTags {
    const val ROOT = "bodyweight_root"
    const val ADD = "bodyweight_add"
    const val RETRY = "bodyweight_retry"
    const val GOAL_INPUT = "bodyweight_goal_input"
    const val GOAL_SAVE = "bodyweight_goal_save"
    const val GOAL_CLEAR = "bodyweight_goal_clear"
    const val EDITOR_DATE = "bodyweight_editor_date"
    const val EDITOR_WEIGHT = "bodyweight_editor_weight"
    const val EDITOR_SAVE = "bodyweight_editor_save"
    const val EDITOR_CANCEL = "bodyweight_editor_cancel"
    const val EDITOR_DELETE = "bodyweight_editor_delete"
    const val DUPLICATE_REPLACE = "bodyweight_duplicate_replace"
    const val DELETE_CONFIRM = "bodyweight_delete_confirm"
    fun range(range: ProgressRange) = "bodyweight_range_${range.days}"
    fun measurement(localEpochDay: Long) = "bodyweight_measurement_$localEpochDay"
}

@Composable
fun BodyWeightRoute(viewModel: BodyWeightViewModel = koinViewModel(), onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Re-sample the local day when the destination returns to the foreground, so a window that
    // crossed midnight, and the "future date" check, use the current instant.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onResume() }
    BodyWeightScreen(
        state = state,
        onBack = onBack,
        onRangeSelected = viewModel::onRangeSelected,
        onRetryLoad = viewModel::onRetryLoad,
        onAddMeasurement = viewModel::onAddMeasurement,
        onEditMeasurement = viewModel::onEditMeasurement,
        onEditorDateChanged = viewModel::onEditorDateChanged,
        onEditorWeightChanged = viewModel::onEditorWeightChanged,
        onEditorSubmit = viewModel::onEditorSubmit,
        onEditorCancel = viewModel::onEditorCancel,
        onDeleteRequested = viewModel::onDeleteRequested,
        onReplaceConfirmed = viewModel::onReplaceConfirmed,
        onDuplicateDismissed = viewModel::onDuplicateDismissed,
        onDeleteConfirmed = viewModel::onDeleteConfirmed,
        onDeleteDismissed = viewModel::onDeleteDismissed,
        onGoalInputChanged = viewModel::onGoalInputChanged,
        onSaveGoal = viewModel::onSaveGoal,
        onClearGoal = viewModel::onClearGoal
    )
}

@Composable
fun BodyWeightScreen(
    state: BodyWeightUiState,
    onBack: () -> Unit,
    onRangeSelected: (ProgressRange) -> Unit,
    onRetryLoad: () -> Unit,
    onAddMeasurement: () -> Unit,
    onEditMeasurement: (Long) -> Unit,
    onEditorDateChanged: (String) -> Unit,
    onEditorWeightChanged: (String) -> Unit,
    onEditorSubmit: () -> Unit,
    onEditorCancel: () -> Unit,
    onDeleteRequested: () -> Unit,
    onReplaceConfirmed: () -> Unit,
    onDuplicateDismissed: () -> Unit,
    onDeleteConfirmed: () -> Unit,
    onDeleteDismissed: () -> Unit,
    onGoalInputChanged: (String) -> Unit,
    onSaveGoal: () -> Unit,
    onClearGoal: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .testTag(BodyWeightTestTags.ROOT),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = stringResource(Res.string.bodyweight_title),
            style = MaterialTheme.typography.headlineSmall
        )
        OutlinedButton(onClick = onBack) {
            Text(stringResource(Res.string.progress_back))
        }
        GoalSection(
            state = state,
            onGoalInputChanged = onGoalInputChanged,
            onSaveGoal = onSaveGoal,
            onClearGoal = onClearGoal
        )
        Button(
            onClick = onAddMeasurement,
            modifier = Modifier.testTag(BodyWeightTestTags.ADD)
        ) {
            Text(stringResource(Res.string.bodyweight_add))
        }
        when {
            state.loading -> {
                CircularProgressIndicator()
                Text(stringResource(Res.string.bodyweight_loading))
            }
            state.loadFailed -> {
                Text(stringResource(Res.string.bodyweight_load_failed))
                TextButton(
                    onClick = onRetryLoad,
                    modifier = Modifier.testTag(BodyWeightTestTags.RETRY)
                ) {
                    Text(stringResource(Res.string.bodyweight_retry))
                }
            }
            state.isEmpty -> Text(stringResource(Res.string.bodyweight_empty))
            else -> {
                BodyWeightRangeSelector(state.range, onRangeSelected)
                if (state.measurementsInRange.isNotEmpty()) {
                    BodyWeightChartCaption(state.goalKg, state.weightUnit)
                    BodyWeightChart(
                        measurements = state.measurementsInRange,
                        weightUnit = state.weightUnit,
                        goalKg = state.goalKg,
                        dayFrom = state.chartDayFrom,
                        dayTo = state.chartDayTo
                    )
                }
                BodyWeightMeasurementList(
                    measurements = state.measurementsInRange,
                    weightUnit = state.weightUnit,
                    onEdit = onEditMeasurement
                )
            }
        }
    }

    state.editor?.let { editor ->
        BodyWeightEditorDialog(
            editor = editor,
            weightUnit = state.weightUnit,
            submitting = state.submitting,
            onDateChanged = onEditorDateChanged,
            onWeightChanged = onEditorWeightChanged,
            onSubmit = onEditorSubmit,
            onCancel = onEditorCancel,
            onDelete = onDeleteRequested
        )
    }

    state.pendingDuplicate?.let { pending ->
        AlertDialog(
            onDismissRequest = onDuplicateDismissed,
            confirmButton = {
                Button(
                    onClick = onReplaceConfirmed,
                    enabled = !state.submitting,
                    modifier = Modifier.testTag(BodyWeightTestTags.DUPLICATE_REPLACE)
                ) {
                    Text(stringResource(Res.string.bodyweight_duplicate_replace))
                }
            },
            dismissButton = {
                TextButton(onClick = onDuplicateDismissed) {
                    Text(stringResource(Res.string.bodyweight_cancel))
                }
            },
            title = { Text(stringResource(Res.string.bodyweight_duplicate_title)) },
            text = {
                Text(
                    stringResource(
                        Res.string.bodyweight_duplicate_message,
                        progressDayLabel(pending.localEpochDay)
                    )
                )
            }
        )
    }

    state.pendingDeleteDay?.let { day ->
        AlertDialog(
            onDismissRequest = onDeleteDismissed,
            confirmButton = {
                Button(
                    onClick = onDeleteConfirmed,
                    enabled = !state.submitting,
                    modifier = Modifier.testTag(BodyWeightTestTags.DELETE_CONFIRM)
                ) {
                    Text(stringResource(Res.string.bodyweight_delete_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = onDeleteDismissed) {
                    Text(stringResource(Res.string.bodyweight_cancel))
                }
            },
            title = { Text(stringResource(Res.string.bodyweight_delete_confirm_title)) },
            text = {
                Text(
                    stringResource(
                        Res.string.bodyweight_delete_confirm_message,
                        progressDayLabel(day)
                    )
                )
            }
        )
    }
}

@Composable
private fun GoalSection(
    state: BodyWeightUiState,
    onGoalInputChanged: (String) -> Unit,
    onSaveGoal: () -> Unit,
    onClearGoal: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = stringResource(Res.string.bodyweight_goal_title),
            style = MaterialTheme.typography.titleMedium
        )
        val goalKg = state.goalKg
        val goal = goalKg?.let { displayWeight(it, state.weightUnit) }
        Text(
            text = if (goal == null) {
                stringResource(Res.string.bodyweight_goal_none)
            } else {
                stringResource(Res.string.bodyweight_goal_current, goal)
            },
            style = MaterialTheme.typography.bodyMedium
        )
        OutlinedTextField(
            value = state.goalInput,
            onValueChange = onGoalInputChanged,
            label = {
                Text(stringResource(Res.string.bodyweight_goal_input_label, state.weightUnit.label))
            },
            singleLine = true,
            enabled = !state.submitting,
            isError = state.goalError,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier
                .fillMaxWidth()
                .testTag(BodyWeightTestTags.GOAL_INPUT)
        )
        if (state.goalError) {
            Text(
                text = stringResource(Res.string.bodyweight_goal_invalid),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = onSaveGoal,
                enabled = !state.submitting,
                modifier = Modifier.testTag(BodyWeightTestTags.GOAL_SAVE)
            ) {
                Text(stringResource(Res.string.bodyweight_goal_save))
            }
            TextButton(
                onClick = onClearGoal,
                enabled = !state.submitting,
                modifier = Modifier.testTag(BodyWeightTestTags.GOAL_CLEAR)
            ) {
                Text(stringResource(Res.string.bodyweight_goal_clear))
            }
        }
    }
}

@Composable
private fun BodyWeightRangeSelector(
    range: ProgressRange,
    onRangeSelected: (ProgressRange) -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ProgressRange.entries.forEach { option ->
            FilterChip(
                selected = option == range,
                onClick = { onRangeSelected(option) },
                label = { Text(stringResource(option.labelResource())) },
                modifier = Modifier.testTag(BodyWeightTestTags.range(option))
            )
        }
    }
}

@Composable
private fun BodyWeightEditorDialog(
    editor: BodyWeightEditorState,
    weightUnit: WeightUnit,
    submitting: Boolean,
    onDateChanged: (String) -> Unit,
    onWeightChanged: (String) -> Unit,
    onSubmit: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onCancel,
        confirmButton = {
            Button(
                onClick = onSubmit,
                enabled = !submitting,
                modifier = Modifier.testTag(BodyWeightTestTags.EDITOR_SAVE)
            ) {
                Text(stringResource(Res.string.bodyweight_save))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onCancel,
                modifier = Modifier.testTag(BodyWeightTestTags.EDITOR_CANCEL)
            ) {
                Text(stringResource(Res.string.bodyweight_cancel))
            }
        },
        title = {
            Text(
                text = if (editor.previousLocalEpochDay == null) {
                    stringResource(Res.string.bodyweight_add)
                } else {
                    stringResource(Res.string.bodyweight_editor_edit_title)
                }
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = editor.dateText,
                    onValueChange = onDateChanged,
                    label = { Text(stringResource(Res.string.bodyweight_date_label)) },
                    singleLine = true,
                    enabled = !submitting,
                    isError = editor.error == BodyWeightEditorError.INVALID_DATE ||
                        editor.error == BodyWeightEditorError.FUTURE_DATE,
                    modifier = Modifier.testTag(BodyWeightTestTags.EDITOR_DATE)
                )
                OutlinedTextField(
                    value = editor.weightText,
                    onValueChange = onWeightChanged,
                    label = {
                        Text(stringResource(Res.string.bodyweight_weight_label, weightUnit.label))
                    },
                    singleLine = true,
                    enabled = !submitting,
                    isError = editor.error == BodyWeightEditorError.INVALID_WEIGHT,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.testTag(BodyWeightTestTags.EDITOR_WEIGHT)
                )
                editor.error?.let { error ->
                    Text(
                        text = editorErrorMessage(error),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                if (editor.previousLocalEpochDay != null) {
                    TextButton(
                        onClick = onDelete,
                        enabled = !submitting,
                        modifier = Modifier.testTag(BodyWeightTestTags.EDITOR_DELETE)
                    ) {
                        Text(stringResource(Res.string.bodyweight_delete))
                    }
                }
            }
        }
    )
}

@Composable
private fun editorErrorMessage(error: BodyWeightEditorError): String = when (error) {
    BodyWeightEditorError.INVALID_WEIGHT ->
        stringResource(Res.string.bodyweight_error_invalid_weight)
    BodyWeightEditorError.INVALID_DATE ->
        stringResource(Res.string.bodyweight_error_invalid_date)
    BodyWeightEditorError.FUTURE_DATE ->
        stringResource(Res.string.bodyweight_error_future_date)
    BodyWeightEditorError.DATE_OCCUPIED ->
        stringResource(Res.string.bodyweight_error_date_occupied)
    BodyWeightEditorError.MISSING_MEASUREMENT ->
        stringResource(Res.string.bodyweight_error_missing_measurement)
}
