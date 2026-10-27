package com.hydrafit.app.feature.cardio

import androidx.compose.foundation.horizontalScroll
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
import com.hydrafit.app.core.domain.cardio.CardioActivity
import com.hydrafit.app.core.domain.cardio.CardioActivityType
import com.hydrafit.app.core.domain.time.isoLocalDateTime
import com.hydrafit.app.core.domain.unit.formatDistance
import hydrafit.feature.cardio.generated.resources.Res
import hydrafit.feature.cardio.generated.resources.cardio_activity_line
import hydrafit.feature.cardio.generated.resources.cardio_activity_note
import hydrafit.feature.cardio.generated.resources.cardio_add_title
import hydrafit.feature.cardio.generated.resources.cardio_back
import hydrafit.feature.cardio.generated.resources.cardio_cancel
import hydrafit.feature.cardio.generated.resources.cardio_delete
import hydrafit.feature.cardio.generated.resources.cardio_delete_confirm
import hydrafit.feature.cardio.generated.resources.cardio_delete_confirm_message
import hydrafit.feature.cardio.generated.resources.cardio_delete_confirm_message_unknown
import hydrafit.feature.cardio.generated.resources.cardio_delete_confirm_title
import hydrafit.feature.cardio.generated.resources.cardio_distance_label
import hydrafit.feature.cardio.generated.resources.cardio_duration_label
import hydrafit.feature.cardio.generated.resources.cardio_edit
import hydrafit.feature.cardio.generated.resources.cardio_edit_title
import hydrafit.feature.cardio.generated.resources.cardio_empty
import hydrafit.feature.cardio.generated.resources.cardio_error_invalid_activity
import hydrafit.feature.cardio.generated.resources.cardio_error_invalid_distance
import hydrafit.feature.cardio.generated.resources.cardio_error_invalid_duration
import hydrafit.feature.cardio.generated.resources.cardio_error_invalid_note
import hydrafit.feature.cardio.generated.resources.cardio_error_invalid_start
import hydrafit.feature.cardio.generated.resources.cardio_error_save_failed
import hydrafit.feature.cardio.generated.resources.cardio_history_note
import hydrafit.feature.cardio.generated.resources.cardio_history_title
import hydrafit.feature.cardio.generated.resources.cardio_load_failed
import hydrafit.feature.cardio.generated.resources.cardio_loading
import hydrafit.feature.cardio.generated.resources.cardio_log_activity
import hydrafit.feature.cardio.generated.resources.cardio_minutes_label
import hydrafit.feature.cardio.generated.resources.cardio_note_label
import hydrafit.feature.cardio.generated.resources.cardio_range_28
import hydrafit.feature.cardio.generated.resources.cardio_range_365
import hydrafit.feature.cardio.generated.resources.cardio_range_84
import hydrafit.feature.cardio.generated.resources.cardio_retry
import hydrafit.feature.cardio.generated.resources.cardio_save
import hydrafit.feature.cardio.generated.resources.cardio_seconds_label
import hydrafit.feature.cardio.generated.resources.cardio_start_label
import hydrafit.feature.cardio.generated.resources.cardio_title
import hydrafit.feature.cardio.generated.resources.cardio_type_distance
import hydrafit.feature.cardio.generated.resources.cardio_type_duration
import hydrafit.feature.cardio.generated.resources.cardio_type_label
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/** Stable external identities for the Cardio controls; technical metadata, never user copy. */
internal object CardioTestTags {
    const val ROOT = "cardio_root"
    const val ENTRY = "cardio_entry"
    const val ADD = "cardio_add"
    const val RETRY = "cardio_retry"
    const val EDITOR_TYPE = "cardio_editor_type"
    const val EDITOR_START = "cardio_editor_start"
    const val EDITOR_MINUTES = "cardio_editor_minutes"
    const val EDITOR_SECONDS = "cardio_editor_seconds"
    const val EDITOR_DISTANCE = "cardio_editor_distance"
    const val EDITOR_NOTE = "cardio_editor_note"
    const val EDITOR_SAVE = "cardio_editor_save"
    const val EDITOR_CANCEL = "cardio_editor_cancel"
    const val EDITOR_DELETE = "cardio_editor_delete"
    const val DELETE_CONFIRM = "cardio_delete_confirm"
    fun range(range: CardioRange) = "cardio_range_${range.days}"
    fun type(type: CardioActivityType) = "cardio_type_${type.name.lowercase()}"
    fun activity(id: Long) = "cardio_activity_$id"
}

@Composable
fun CardioRoute(viewModel: CardioViewModel = koinViewModel(), onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Re-sample the local civil day when the destination returns to the foreground, so a window that
    // crossed midnight charts against the current day.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onResume() }
    CardioScreen(
        state = state,
        onBack = onBack,
        onRangeSelected = viewModel::onRangeSelected,
        onRetryLoad = viewModel::onRetryLoad,
        onAddActivity = viewModel::onAddActivity,
        onEditActivity = viewModel::onEditActivity,
        onEditorTypeChanged = viewModel::onEditorTypeChanged,
        onEditorStartChanged = viewModel::onEditorStartChanged,
        onEditorMinutesChanged = viewModel::onEditorMinutesChanged,
        onEditorSecondsChanged = viewModel::onEditorSecondsChanged,
        onEditorDistanceChanged = viewModel::onEditorDistanceChanged,
        onEditorNoteChanged = viewModel::onEditorNoteChanged,
        onEditorSubmit = viewModel::onEditorSubmit,
        onEditorCancel = viewModel::onEditorCancel,
        onDeleteRequested = viewModel::onEditorDeleteRequested,
        onDeleteConfirmed = viewModel::onEditorDeleteConfirmed,
        onDeleteDismissed = viewModel::onEditorDeleteDismissed
    )
}

@Composable
fun CardioScreen(
    state: CardioUiState,
    onBack: () -> Unit,
    onRangeSelected: (CardioRange) -> Unit,
    onRetryLoad: () -> Unit,
    onAddActivity: () -> Unit,
    onEditActivity: (Long) -> Unit,
    onEditorTypeChanged: (CardioActivityType) -> Unit,
    onEditorStartChanged: (String) -> Unit,
    onEditorMinutesChanged: (String) -> Unit,
    onEditorSecondsChanged: (String) -> Unit,
    onEditorDistanceChanged: (String) -> Unit,
    onEditorNoteChanged: (String) -> Unit,
    onEditorSubmit: () -> Unit,
    onEditorCancel: () -> Unit,
    onDeleteRequested: () -> Unit,
    onDeleteConfirmed: () -> Unit,
    onDeleteDismissed: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .testTag(CardioTestTags.ROOT),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = stringResource(Res.string.cardio_title),
            style = MaterialTheme.typography.headlineSmall
        )
        OutlinedButton(onClick = onBack) {
            Text(stringResource(Res.string.cardio_back))
        }
        Button(
            onClick = onAddActivity,
            modifier = Modifier.testTag(CardioTestTags.ADD)
        ) {
            Text(stringResource(Res.string.cardio_log_activity))
        }
        when {
            state.loading -> {
                CircularProgressIndicator()
                Text(stringResource(Res.string.cardio_loading))
            }
            state.loadFailed -> {
                Text(stringResource(Res.string.cardio_load_failed))
                TextButton(
                    onClick = onRetryLoad,
                    modifier = Modifier.testTag(CardioTestTags.RETRY)
                ) {
                    Text(stringResource(Res.string.cardio_retry))
                }
            }
            state.isEmpty -> Text(stringResource(Res.string.cardio_empty))
            else -> {
                RangeSelector(state.range, onRangeSelected)
                CardioTypeTotals(state.progress.totalsByType, state.distanceUnit)
                if (!state.rangeIsEmpty) {
                    CardioChartCaption()
                    CardioDailyChart(
                        days = state.progress.days,
                        dayFrom = state.chartDayFrom,
                        dayTo = state.chartDayTo
                    )
                    CardioDayTable(state.progress.days, state.distanceUnit)
                }
                HistorySection(state, onEditActivity)
            }
        }
    }

    state.editor?.let { editor ->
        EditorDialog(
            editor = editor,
            distanceUnitLabel = state.distanceUnit.label,
            submitting = state.submitting,
            onTypeChanged = onEditorTypeChanged,
            onStartChanged = onEditorStartChanged,
            onMinutesChanged = onEditorMinutesChanged,
            onSecondsChanged = onEditorSecondsChanged,
            onDistanceChanged = onEditorDistanceChanged,
            onNoteChanged = onEditorNoteChanged,
            onSubmit = onEditorSubmit,
            onCancel = onEditorCancel,
            onDelete = onDeleteRequested
        )
    }

    val pendingDelete = state.editor?.takeIf { it.pendingDelete }
    if (pendingDelete != null) {
        val stored = state.activities.firstOrNull { it.id == pendingDelete.previousId }
        AlertDialog(
            onDismissRequest = onDeleteDismissed,
            confirmButton = {
                Button(
                    onClick = onDeleteConfirmed,
                    enabled = !state.submitting,
                    modifier = Modifier.testTag(CardioTestTags.DELETE_CONFIRM)
                ) {
                    Text(stringResource(Res.string.cardio_delete_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = onDeleteDismissed) {
                    Text(stringResource(Res.string.cardio_cancel))
                }
            },
            title = { Text(stringResource(Res.string.cardio_delete_confirm_title)) },
            text = {
                Text(
                    if (stored == null) {
                        stringResource(Res.string.cardio_delete_confirm_message_unknown)
                    } else {
                        stringResource(
                            Res.string.cardio_delete_confirm_message,
                            stringResource(stored.type.labelResource()),
                            isoLocalDateTime(stored.startedAtMillis, state.utcOffsetMillis)
                        )
                    }
                )
            }
        )
    }
}

@Composable
private fun RangeSelector(range: CardioRange, onRangeSelected: (CardioRange) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        CardioRange.entries.forEach { option ->
            FilterChip(
                selected = option == range,
                onClick = { onRangeSelected(option) },
                label = { Text(stringResource(option.labelResource())) },
                modifier = Modifier.testTag(CardioTestTags.range(option))
            )
        }
    }
}

@Composable
private fun HistorySection(state: CardioUiState, onEditActivity: (Long) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = stringResource(Res.string.cardio_history_title),
            style = MaterialTheme.typography.titleMedium
        )
        Text(stringResource(Res.string.cardio_history_note))
        state.sortedActivities.forEach { activity ->
            HistoryRow(activity, state, onEditActivity)
        }
    }
}

@Composable
private fun HistoryRow(
    activity: CardioActivity,
    state: CardioUiState,
    onEditActivity: (Long) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = stringResource(
                Res.string.cardio_activity_line,
                stringResource(activity.type.labelResource()),
                isoLocalDateTime(activity.startedAtMillis, state.utcOffsetMillis)
            ),
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            text = stringResource(
                Res.string.cardio_type_duration,
                cardioDurationLabel(activity.durationSeconds.toLong())
            ),
            style = MaterialTheme.typography.bodySmall
        )
        activity.distanceMeters?.let { distance ->
            Text(
                text = stringResource(
                    Res.string.cardio_type_distance,
                    formatDistance(distance, state.distanceUnit)
                ),
                style = MaterialTheme.typography.bodySmall
            )
        }
        activity.note?.let { note ->
            Text(
                text = stringResource(Res.string.cardio_activity_note, note),
                style = MaterialTheme.typography.bodySmall
            )
        }
        TextButton(
            onClick = { onEditActivity(activity.id) },
            modifier = Modifier.testTag(CardioTestTags.activity(activity.id))
        ) {
            Text(stringResource(Res.string.cardio_edit))
        }
    }
}

@Composable
private fun EditorDialog(
    editor: CardioEditorState,
    distanceUnitLabel: String,
    submitting: Boolean,
    onTypeChanged: (CardioActivityType) -> Unit,
    onStartChanged: (String) -> Unit,
    onMinutesChanged: (String) -> Unit,
    onSecondsChanged: (String) -> Unit,
    onDistanceChanged: (String) -> Unit,
    onNoteChanged: (String) -> Unit,
    onSubmit: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { if (!submitting) onCancel() },
        confirmButton = {
            Button(
                onClick = onSubmit,
                enabled = !submitting,
                modifier = Modifier.testTag(CardioTestTags.EDITOR_SAVE)
            ) {
                Text(stringResource(Res.string.cardio_save))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onCancel,
                enabled = !submitting,
                modifier = Modifier.testTag(CardioTestTags.EDITOR_CANCEL)
            ) {
                Text(stringResource(Res.string.cardio_cancel))
            }
        },
        title = {
            Text(
                if (editor.previousId == null) {
                    stringResource(Res.string.cardio_add_title)
                } else {
                    stringResource(Res.string.cardio_edit_title)
                }
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(Res.string.cardio_type_label),
                    style = MaterialTheme.typography.labelLarge
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CardioActivityType.entries.forEach { type ->
                        FilterChip(
                            selected = type == editor.type,
                            onClick = { onTypeChanged(type) },
                            label = { Text(stringResource(type.labelResource())) },
                            enabled = !submitting,
                            modifier = Modifier.testTag(CardioTestTags.type(type))
                        )
                    }
                }
                OutlinedTextField(
                    value = editor.startText,
                    onValueChange = onStartChanged,
                    label = { Text(stringResource(Res.string.cardio_start_label)) },
                    singleLine = true,
                    enabled = !submitting,
                    modifier = Modifier.testTag(CardioTestTags.EDITOR_START)
                )
                Text(
                    text = stringResource(Res.string.cardio_duration_label),
                    style = MaterialTheme.typography.labelLarge
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = editor.minutesText,
                        onValueChange = onMinutesChanged,
                        label = { Text(stringResource(Res.string.cardio_minutes_label)) },
                        singleLine = true,
                        enabled = !submitting,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier
                            .weight(1f)
                            .testTag(CardioTestTags.EDITOR_MINUTES)
                    )
                    OutlinedTextField(
                        value = editor.secondsText,
                        onValueChange = onSecondsChanged,
                        label = { Text(stringResource(Res.string.cardio_seconds_label)) },
                        singleLine = true,
                        enabled = !submitting,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier
                            .weight(1f)
                            .testTag(CardioTestTags.EDITOR_SECONDS)
                    )
                }
                OutlinedTextField(
                    value = editor.distanceText,
                    onValueChange = onDistanceChanged,
                    label = {
                        Text(stringResource(Res.string.cardio_distance_label, distanceUnitLabel))
                    },
                    singleLine = true,
                    enabled = !submitting,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.testTag(CardioTestTags.EDITOR_DISTANCE)
                )
                OutlinedTextField(
                    value = editor.noteText,
                    onValueChange = onNoteChanged,
                    label = { Text(stringResource(Res.string.cardio_note_label)) },
                    enabled = !submitting,
                    modifier = Modifier.testTag(CardioTestTags.EDITOR_NOTE)
                )
                editor.error?.let { error ->
                    Text(
                        text = editorErrorMessage(error),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                if (editor.previousId != null) {
                    TextButton(
                        onClick = onDelete,
                        enabled = !submitting,
                        modifier = Modifier.testTag(CardioTestTags.EDITOR_DELETE)
                    ) {
                        Text(stringResource(Res.string.cardio_delete))
                    }
                }
            }
        }
    )
}

/** The localized guidance for one editor refusal; each refusal has its own message. */
@Composable
private fun editorErrorMessage(error: CardioEditorError): String = when (error) {
    CardioEditorError.INVALID_START_TIME -> stringResource(Res.string.cardio_error_invalid_start)
    CardioEditorError.INVALID_DURATION -> stringResource(Res.string.cardio_error_invalid_duration)
    CardioEditorError.INVALID_DISTANCE -> stringResource(Res.string.cardio_error_invalid_distance)
    CardioEditorError.INVALID_NOTE -> stringResource(Res.string.cardio_error_invalid_note)
    CardioEditorError.INVALID_ACTIVITY -> stringResource(Res.string.cardio_error_invalid_activity)
    CardioEditorError.SAVE_FAILED -> stringResource(Res.string.cardio_error_save_failed)
}

private fun CardioRange.labelResource(): StringResource = when (this) {
    CardioRange.DAYS_28 -> Res.string.cardio_range_28
    CardioRange.DAYS_84 -> Res.string.cardio_range_84
    CardioRange.DAYS_365 -> Res.string.cardio_range_365
}
