package com.hydrafit.app.feature.routines

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hydrafit.app.core.domain.engine.SplitFocus
import com.hydrafit.app.core.domain.equipment.matchesExerciseNameQuery
import com.hydrafit.app.core.domain.routine.RoutineTemplate
import com.hydrafit.app.core.domain.schedule.OccurrenceStatus
import com.hydrafit.app.core.domain.schedule.ScheduleMode
import com.hydrafit.app.core.domain.schedule.WorkoutOccurrence
import com.hydrafit.app.core.domain.time.DayOfWeek
import com.hydrafit.app.core.domain.time.isoDateUtc
import com.hydrafit.app.core.domain.unit.WeightUnit
import hydrafit.feature.routines.generated.resources.Res
import hydrafit.feature.routines.generated.resources.focus_full_body
import hydrafit.feature.routines.generated.resources.focus_legs
import hydrafit.feature.routines.generated.resources.focus_lower
import hydrafit.feature.routines.generated.resources.focus_none
import hydrafit.feature.routines.generated.resources.focus_pull
import hydrafit.feature.routines.generated.resources.focus_push
import hydrafit.feature.routines.generated.resources.focus_upper
import hydrafit.feature.routines.generated.resources.routines_activate
import hydrafit.feature.routines.generated.resources.routines_activation_choose_date
import hydrafit.feature.routines.generated.resources.routines_activation_mode_sequence
import hydrafit.feature.routines.generated.resources.routines_activation_mode_weekday
import hydrafit.feature.routines.generated.resources.routines_activation_no_weekday
import hydrafit.feature.routines.generated.resources.routines_activation_preview
import hydrafit.feature.routines.generated.resources.routines_activation_replace
import hydrafit.feature.routines.generated.resources.routines_activation_start_today
import hydrafit.feature.routines.generated.resources.routines_activation_title
import hydrafit.feature.routines.generated.resources.routines_activation_weekdays_label
import hydrafit.feature.routines.generated.resources.routines_active_progress
import hydrafit.feature.routines.generated.resources.routines_active_title
import hydrafit.feature.routines.generated.resources.routines_add_exercise
import hydrafit.feature.routines.generated.resources.routines_add_workout
import hydrafit.feature.routines.generated.resources.routines_archive
import hydrafit.feature.routines.generated.resources.routines_archived_badge
import hydrafit.feature.routines.generated.resources.routines_cancel
import hydrafit.feature.routines.generated.resources.routines_cancel_block
import hydrafit.feature.routines.generated.resources.routines_confirm
import hydrafit.feature.routines.generated.resources.routines_delete
import hydrafit.feature.routines.generated.resources.routines_delete_message
import hydrafit.feature.routines.generated.resources.routines_delete_title
import hydrafit.feature.routines.generated.resources.routines_duplicate
import hydrafit.feature.routines.generated.resources.routines_edit
import hydrafit.feature.routines.generated.resources.routines_editor_edit_title
import hydrafit.feature.routines.generated.resources.routines_editor_new_title
import hydrafit.feature.routines.generated.resources.routines_empty
import hydrafit.feature.routines.generated.resources.routines_finish_block
import hydrafit.feature.routines.generated.resources.routines_focus_label
import hydrafit.feature.routines.generated.resources.routines_move_down
import hydrafit.feature.routines.generated.resources.routines_move_up
import hydrafit.feature.routines.generated.resources.routines_name_label
import hydrafit.feature.routines.generated.resources.routines_new
import hydrafit.feature.routines.generated.resources.routines_no_active
import hydrafit.feature.routines.generated.resources.routines_occurrence_finished
import hydrafit.feature.routines.generated.resources.routines_occurrence_in_progress
import hydrafit.feature.routines.generated.resources.routines_occurrence_not_before
import hydrafit.feature.routines.generated.resources.routines_occurrence_partial
import hydrafit.feature.routines.generated.resources.routines_occurrence_pending
import hydrafit.feature.routines.generated.resources.routines_occurrence_scheduled
import hydrafit.feature.routines.generated.resources.routines_occurrence_skipped
import hydrafit.feature.routines.generated.resources.routines_occurrence_unscheduled
import hydrafit.feature.routines.generated.resources.routines_picker_empty
import hydrafit.feature.routines.generated.resources.routines_picker_title
import hydrafit.feature.routines.generated.resources.routines_postpone
import hydrafit.feature.routines.generated.resources.routines_postpone_title
import hydrafit.feature.routines.generated.resources.routines_remove
import hydrafit.feature.routines.generated.resources.routines_repeat_block
import hydrafit.feature.routines.generated.resources.routines_reps_label
import hydrafit.feature.routines.generated.resources.routines_save
import hydrafit.feature.routines.generated.resources.routines_search_label
import hydrafit.feature.routines.generated.resources.routines_sets_label
import hydrafit.feature.routines.generated.resources.routines_switch_mode
import hydrafit.feature.routines.generated.resources.routines_title
import hydrafit.feature.routines.generated.resources.routines_unarchive
import hydrafit.feature.routines.generated.resources.routines_weight_label
import hydrafit.feature.routines.generated.resources.routines_weight_optional
import hydrafit.feature.routines.generated.resources.routines_workout_count
import hydrafit.feature.routines.generated.resources.routines_workout_name_label
import hydrafit.feature.routines.generated.resources.weekday_friday
import hydrafit.feature.routines.generated.resources.weekday_monday
import hydrafit.feature.routines.generated.resources.weekday_saturday
import hydrafit.feature.routines.generated.resources.weekday_sunday
import hydrafit.feature.routines.generated.resources.weekday_thursday
import hydrafit.feature.routines.generated.resources.weekday_tuesday
import hydrafit.feature.routines.generated.resources.weekday_wednesday
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

private const val MILLIS_PER_DAY = 24L * 60L * 60L * 1000L

@Composable
fun RoutinesRoute(viewModel: RoutinesViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    RoutinesScreen(state = state, viewModel = viewModel)
}

@Composable
fun RoutinesScreen(state: RoutinesUiState, viewModel: RoutinesViewModel) {
    if (state.editor != null) {
        RoutineEditor(
            editor = state.editor,
            weightUnit = state.weightUnit,
            viewModel = viewModel
        )
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeContentPadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(Res.string.routines_title),
                style = MaterialTheme.typography.headlineSmall
            )
            state.message?.let { message ->
                MessageBanner(message = message, onDismiss = viewModel::onMessageShown)
            }
            ActiveBlockSection(state = state, viewModel = viewModel)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(Res.string.routines_title),
                    style = MaterialTheme.typography.titleMedium
                )
                TextButton(onClick = viewModel::onNewRoutine) {
                    Text(stringResource(Res.string.routines_new))
                }
            }
            when {
                state.isLoading -> CircularProgressIndicator()
                state.templates.isEmpty() -> Text(stringResource(Res.string.routines_empty))
                else -> state.templates.forEach { template ->
                    TemplateRow(template = template, viewModel = viewModel)
                }
            }
        }
    }

    state.picker?.let { picker ->
        ExercisePickerDialog(
            exercises = state.exercises,
            query = picker.query,
            onQueryChanged = viewModel::onPickerQueryChanged,
            onSelected = viewModel::onPickerExerciseSelected,
            onDismiss = viewModel::onPickerDismissed
        )
    }
    state.activation?.let { activation ->
        ActivationDialog(
            activation = activation,
            onModeChanged = viewModel::onActivationModeChanged,
            onWeekdayToggled = viewModel::onActivationWeekdayToggled,
            onStartTodayChanged = viewModel::onActivationStartTodayChanged,
            onStartDateChosen = viewModel::onActivationStartDateChosen,
            onReplaceActiveChanged = viewModel::onActivationReplaceActiveChanged,
            onConfirm = viewModel::onConfirmActivation,
            onDismiss = viewModel::onActivationDismissed
        )
    }
}

@Composable
private fun MessageBanner(message: String, onDismiss: () -> Unit) {
    Card {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(text = message, modifier = Modifier.weight(1f))
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.routines_cancel))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ActiveBlockSection(state: RoutinesUiState, viewModel: RoutinesViewModel) {
    Card {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stringResource(Res.string.routines_active_title),
                style = MaterialTheme.typography.titleMedium
            )
            val activation = state.activeActivation
            if (activation == null) {
                Text(stringResource(Res.string.routines_no_active))
                return@Column
            }
            Text(
                text = activation.name,
                style = MaterialTheme.typography.bodyLarge
            )
            val resolved = state.occurrences.count { it.isResolved }
            Text(
                text = stringResource(
                    Res.string.routines_active_progress,
                    resolved,
                    state.occurrences.size
                )
            )
            val workoutNames = activation.workouts.associate { it.id to it.name }
            state.occurrences.sortedBy { it.queuePosition }.forEach { occurrence ->
                OccurrenceRow(
                    occurrence = occurrence,
                    workoutName = workoutNames[occurrence.activationWorkoutId].orEmpty(),
                    onPostpone = viewModel::onPostpone
                )
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = viewModel::onSwitchScheduleMode) {
                    Text(stringResource(Res.string.routines_switch_mode))
                }
                TextButton(onClick = viewModel::onRepeatBlock) {
                    Text(stringResource(Res.string.routines_repeat_block))
                }
                TextButton(onClick = viewModel::onFinishBlock) {
                    Text(stringResource(Res.string.routines_finish_block))
                }
                TextButton(onClick = viewModel::onCancelBlock) {
                    Text(stringResource(Res.string.routines_cancel_block))
                }
            }
        }
    }
}

@Composable
private fun OccurrenceRow(
    occurrence: WorkoutOccurrence,
    workoutName: String,
    onPostpone: (Long, Long) -> Unit
) {
    var showPostpone by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = workoutName, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = occurrenceScheduleText(occurrence),
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                text = occurrenceStatusText(occurrence.status),
                style = MaterialTheme.typography.bodySmall
            )
        }
        if (!occurrence.isResolved) {
            TextButton(onClick = { showPostpone = true }) {
                Text(stringResource(Res.string.routines_postpone))
            }
        }
    }
    if (showPostpone) {
        DatePickerSheet(
            title = stringResource(Res.string.routines_postpone_title),
            initialEpochDay = occurrence.scheduledEpochDay,
            onConfirm = { epochDay ->
                showPostpone = false
                onPostpone(occurrence.id, epochDay)
            },
            onDismiss = { showPostpone = false }
        )
    }
}

@Composable
private fun occurrenceStatusText(status: OccurrenceStatus): String = when (status) {
    OccurrenceStatus.PENDING -> stringResource(Res.string.routines_occurrence_pending)
    OccurrenceStatus.IN_PROGRESS -> stringResource(Res.string.routines_occurrence_in_progress)
    OccurrenceStatus.FINISHED -> stringResource(Res.string.routines_occurrence_finished)
    OccurrenceStatus.FINISHED_PARTIAL -> stringResource(Res.string.routines_occurrence_partial)
    OccurrenceStatus.SKIPPED -> stringResource(Res.string.routines_occurrence_skipped)
}

@Composable
private fun occurrenceScheduleText(occurrence: WorkoutOccurrence): String {
    val scheduled = occurrence.scheduledEpochDay
    val notBefore = occurrence.notBeforeEpochDay
    return when {
        scheduled != null ->
            stringResource(Res.string.routines_occurrence_scheduled, epochDayLabel(scheduled))
        notBefore != null ->
            stringResource(Res.string.routines_occurrence_not_before, epochDayLabel(notBefore))
        else -> stringResource(Res.string.routines_occurrence_unscheduled)
    }
}

private fun epochDayLabel(epochDay: Long): String = isoDateUtc(epochDay * MILLIS_PER_DAY)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TemplateRow(template: RoutineTemplate, viewModel: RoutinesViewModel) {
    var confirmDelete by remember { mutableStateOf(false) }
    Card {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = template.name, style = MaterialTheme.typography.bodyLarge)
                if (template.isArchived) {
                    Text(
                        text = stringResource(Res.string.routines_archived_badge),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
            Text(
                text = stringResource(Res.string.routines_workout_count, template.workouts.size),
                style = MaterialTheme.typography.bodySmall
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = { viewModel.onEditRoutine(template) }) {
                    Text(stringResource(Res.string.routines_edit))
                }
                TextButton(onClick = { viewModel.onDuplicate(template) }) {
                    Text(stringResource(Res.string.routines_duplicate))
                }
                TextButton(onClick = { viewModel.onActivateRequested(template) }) {
                    Text(stringResource(Res.string.routines_activate))
                }
                TextButton(onClick = { viewModel.onToggleArchive(template) }) {
                    val label = if (template.isArchived) {
                        Res.string.routines_unarchive
                    } else {
                        Res.string.routines_archive
                    }
                    Text(stringResource(label))
                }
                TextButton(onClick = { confirmDelete = true }) {
                    Text(stringResource(Res.string.routines_delete))
                }
            }
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(Res.string.routines_delete_title)) },
            text = { Text(stringResource(Res.string.routines_delete_message, template.name)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        viewModel.onDelete(template)
                    }
                ) {
                    Text(stringResource(Res.string.routines_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text(stringResource(Res.string.routines_cancel))
                }
            }
        )
    }
}

@Composable
private fun RoutineEditor(
    editor: RoutineEditorState,
    weightUnit: WeightUnit,
    viewModel: RoutinesViewModel
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeContentPadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = stringResource(
                if (editor.id ==
                    0L
                ) {
                    Res.string.routines_editor_new_title
                } else {
                    Res.string.routines_editor_edit_title
                }
            ),
            style = MaterialTheme.typography.headlineSmall
        )
        OutlinedTextField(
            value = editor.name,
            onValueChange = viewModel::onEditorNameChanged,
            label = { Text(stringResource(Res.string.routines_name_label)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        editor.workouts.forEachIndexed { index, workout ->
            WorkoutCard(
                index = index,
                workout = workout,
                weightUnit = weightUnit,
                viewModel = viewModel
            )
        }
        OutlinedButton(onClick = viewModel::onAddWorkout) {
            Text(stringResource(Res.string.routines_add_workout))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = viewModel::onSaveEditor) {
                Text(stringResource(Res.string.routines_save))
            }
            OutlinedButton(onClick = viewModel::onEditorDismissed) {
                Text(stringResource(Res.string.routines_cancel))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WorkoutCard(
    index: Int,
    workout: EditorWorkout,
    weightUnit: WeightUnit,
    viewModel: RoutinesViewModel
) {
    Card {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = workout.name,
                onValueChange = { viewModel.onWorkoutNameChanged(index, it) },
                label = { Text(stringResource(Res.string.routines_workout_name_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(stringResource(Res.string.routines_focus_label))
                FocusSelector(
                    focus = workout.focus,
                    onSelected = { viewModel.onWorkoutFocusChanged(index, it) }
                )
            }
            workout.entries.forEachIndexed { entryIndex, entry ->
                EntryRow(
                    workoutIndex = index,
                    entryIndex = entryIndex,
                    entry = entry,
                    weightUnit = weightUnit,
                    viewModel = viewModel
                )
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = { viewModel.onAddExercise(index) }) {
                    Text(stringResource(Res.string.routines_add_exercise))
                }
                TextButton(onClick = { viewModel.onMoveWorkout(index, -1) }) {
                    Text(stringResource(Res.string.routines_move_up))
                }
                TextButton(onClick = { viewModel.onMoveWorkout(index, 1) }) {
                    Text(stringResource(Res.string.routines_move_down))
                }
                TextButton(onClick = { viewModel.onRemoveWorkout(index) }) {
                    Text(stringResource(Res.string.routines_remove))
                }
            }
        }
    }
}

@Composable
private fun EntryRow(
    workoutIndex: Int,
    entryIndex: Int,
    entry: EditorEntry,
    weightUnit: WeightUnit,
    viewModel: RoutinesViewModel
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = entry.name, modifier = Modifier.weight(1f))
            TextButton(onClick = { viewModel.onReplaceExercise(workoutIndex, entry.id) }) {
                Text(stringResource(Res.string.routines_edit))
            }
            TextButton(onClick = { viewModel.onRemoveEntry(workoutIndex, entryIndex) }) {
                Text(stringResource(Res.string.routines_remove))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = entry.sets,
                onValueChange = { viewModel.onEntrySetsChanged(workoutIndex, entryIndex, it) },
                label = { Text(stringResource(Res.string.routines_sets_label)) },
                singleLine = true,
                modifier = Modifier.width(88.dp)
            )
            OutlinedTextField(
                value = entry.reps,
                onValueChange = { viewModel.onEntryRepsChanged(workoutIndex, entryIndex, it) },
                label = { Text(stringResource(Res.string.routines_reps_label)) },
                singleLine = true,
                modifier = Modifier.width(88.dp)
            )
            OutlinedTextField(
                value = entry.weight,
                onValueChange = { viewModel.onEntryWeightChanged(workoutIndex, entryIndex, it) },
                label = {
                    Text(stringResource(Res.string.routines_weight_label, weightUnit.label))
                },
                placeholder = { Text(stringResource(Res.string.routines_weight_optional)) },
                singleLine = true,
                modifier = Modifier.width(120.dp)
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(onClick = { viewModel.onMoveEntry(workoutIndex, entryIndex, -1) }) {
                Text(stringResource(Res.string.routines_move_up))
            }
            TextButton(onClick = { viewModel.onMoveEntry(workoutIndex, entryIndex, 1) }) {
                Text(stringResource(Res.string.routines_move_down))
            }
        }
    }
}

@Composable
private fun FocusSelector(focus: SplitFocus?, onSelected: (SplitFocus?) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }) {
            Text(focusLabel(focus))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.focus_none)) },
                onClick = {
                    expanded = false
                    onSelected(null)
                }
            )
            SplitFocus.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(focusLabel(option)) },
                    onClick = {
                        expanded = false
                        onSelected(option)
                    }
                )
            }
        }
    }
}

@Composable
private fun ExercisePickerDialog(
    exercises: List<RoutineExerciseOption>,
    query: String,
    onQueryChanged: (String) -> Unit,
    onSelected: (String, String) -> Unit,
    onDismiss: () -> Unit
) {
    val matches = exercises.filter { matchesExerciseNameQuery(it.name, query) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.routines_picker_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = onQueryChanged,
                    label = { Text(stringResource(Res.string.routines_search_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (matches.isEmpty()) {
                    Text(stringResource(Res.string.routines_picker_empty))
                } else {
                    matches.forEach { exercise ->
                        TextButton(onClick = { onSelected(exercise.id, exercise.name) }) {
                            Text(exercise.name)
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.routines_cancel))
            }
        }
    )
}

@Composable
private fun ActivationDialog(
    activation: ActivationUiState,
    onModeChanged: (ScheduleMode) -> Unit,
    onWeekdayToggled: (DayOfWeek) -> Unit,
    onStartTodayChanged: (Boolean) -> Unit,
    onStartDateChosen: (Long) -> Unit,
    onReplaceActiveChanged: (Boolean) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    var showStartDatePicker by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.routines_activation_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(activation.templateName, style = MaterialTheme.typography.bodyLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = activation.mode == ScheduleMode.WEEKDAY,
                        onClick = { onModeChanged(ScheduleMode.WEEKDAY) },
                        label = {
                            Text(stringResource(Res.string.routines_activation_mode_weekday))
                        }
                    )
                    FilterChip(
                        selected = activation.mode == ScheduleMode.SEQUENCE,
                        onClick = { onModeChanged(ScheduleMode.SEQUENCE) },
                        label = {
                            Text(stringResource(Res.string.routines_activation_mode_sequence))
                        }
                    )
                }
                if (activation.mode == ScheduleMode.WEEKDAY) {
                    Text(stringResource(Res.string.routines_activation_weekdays_label))
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        DayOfWeek.entries.forEach { day ->
                            FilterChip(
                                selected = day in activation.weekdays,
                                onClick = { onWeekdayToggled(day) },
                                label = { Text(weekdayLabel(day)) }
                            )
                        }
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(stringResource(Res.string.routines_activation_start_today))
                        Switch(
                            checked = activation.startToday,
                            onCheckedChange = onStartTodayChanged
                        )
                        TextButton(onClick = { showStartDatePicker = true }) {
                            Text(stringResource(Res.string.routines_activation_choose_date))
                        }
                    }
                    Text(
                        stringResource(
                            Res.string.routines_occurrence_scheduled,
                            epochDayLabel(activation.startEpochDay)
                        ),
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        stringResource(Res.string.routines_activation_preview),
                        style = MaterialTheme.typography.labelMedium
                    )
                    activation.preview.forEach { day ->
                        Text(
                            text =
                            day?.let(::epochDayLabel)
                                ?: stringResource(Res.string.routines_occurrence_unscheduled),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                if (activation.replaceActive) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(stringResource(Res.string.routines_activation_replace))
                        Switch(
                            checked = activation.replaceActive,
                            onCheckedChange = onReplaceActiveChanged
                        )
                    }
                }
                activation.error?.let { error ->
                    Text(
                        text = if (error == "weekdays") {
                            stringResource(Res.string.routines_activation_no_weekday)
                        } else {
                            error
                        },
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(Res.string.routines_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.routines_cancel))
            }
        }
    )
    if (showStartDatePicker) {
        DatePickerSheet(
            title = stringResource(Res.string.routines_activation_choose_date),
            initialEpochDay = activation.startEpochDay,
            onConfirm = { epochDay ->
                showStartDatePicker = false
                onStartDateChosen(epochDay)
            },
            onDismiss = { showStartDatePicker = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DatePickerSheet(
    title: String,
    initialEpochDay: Long?,
    onConfirm: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    val state = rememberDatePickerState(
        initialSelectedDateMillis = (initialEpochDay ?: 0L) * MILLIS_PER_DAY
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    state.selectedDateMillis?.let { millis ->
                        onConfirm(millis / MILLIS_PER_DAY)
                    }
                }
            ) {
                Text(stringResource(Res.string.routines_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.routines_cancel))
            }
        }
    ) {
        Text(title)
        DatePicker(state = state)
    }
}

@Composable
private fun focusLabel(focus: SplitFocus?): String = when (focus) {
    SplitFocus.PUSH -> stringResource(Res.string.focus_push)
    SplitFocus.PULL -> stringResource(Res.string.focus_pull)
    SplitFocus.LEGS -> stringResource(Res.string.focus_legs)
    SplitFocus.UPPER -> stringResource(Res.string.focus_upper)
    SplitFocus.LOWER -> stringResource(Res.string.focus_lower)
    SplitFocus.FULL_BODY -> stringResource(Res.string.focus_full_body)
    null -> stringResource(Res.string.focus_none)
}

@Composable
private fun weekdayLabel(day: DayOfWeek): String = when (day) {
    DayOfWeek.MONDAY -> stringResource(Res.string.weekday_monday)
    DayOfWeek.TUESDAY -> stringResource(Res.string.weekday_tuesday)
    DayOfWeek.WEDNESDAY -> stringResource(Res.string.weekday_wednesday)
    DayOfWeek.THURSDAY -> stringResource(Res.string.weekday_thursday)
    DayOfWeek.FRIDAY -> stringResource(Res.string.weekday_friday)
    DayOfWeek.SATURDAY -> stringResource(Res.string.weekday_saturday)
    DayOfWeek.SUNDAY -> stringResource(Res.string.weekday_sunday)
}
