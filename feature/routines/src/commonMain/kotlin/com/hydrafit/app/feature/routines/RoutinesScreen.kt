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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hydrafit.app.core.domain.engine.SlotProgressionPolicy
import com.hydrafit.app.core.domain.engine.SplitFocus
import com.hydrafit.app.core.domain.equipment.matchesExerciseNameQuery
import com.hydrafit.app.core.domain.routine.RoutineTemplate
import com.hydrafit.app.core.domain.schedule.OccurrenceDateChange
import com.hydrafit.app.core.domain.schedule.OccurrenceStatus
import com.hydrafit.app.core.domain.schedule.ScheduleMode
import com.hydrafit.app.core.domain.schedule.WorkoutOccurrence
import com.hydrafit.app.core.domain.time.DayOfWeek
import com.hydrafit.app.core.domain.time.isoDateUtc
import com.hydrafit.app.core.domain.transfer.PrescriptionFailure
import com.hydrafit.app.core.domain.transfer.PrescriptionIdentityChoice
import com.hydrafit.app.core.domain.transfer.PrescriptionIdentityOption
import com.hydrafit.app.core.domain.transfer.PrescriptionImportCommitFailure
import com.hydrafit.app.core.domain.transfer.PrescriptionImportIssue
import com.hydrafit.app.core.domain.transfer.PrescriptionImportProblem
import com.hydrafit.app.core.domain.transfer.PrescriptionResolution
import com.hydrafit.app.core.domain.unit.WeightUnit
import com.hydrafit.app.core.domain.unit.formatWeight
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
import hydrafit.feature.routines.generated.resources.routines_entry_excluded
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
import hydrafit.feature.routines.generated.resources.routines_picker_excluded
import hydrafit.feature.routines.generated.resources.routines_picker_title
import hydrafit.feature.routines.generated.resources.routines_postpone
import hydrafit.feature.routines.generated.resources.routines_postpone_title
import hydrafit.feature.routines.generated.resources.routines_prescription_commit_exists
import hydrafit.feature.routines.generated.resources.routines_prescription_commit_not_importable
import hydrafit.feature.routines.generated.resources.routines_prescription_commit_nothing
import hydrafit.feature.routines.generated.resources.routines_prescription_error_duplicate_id
import hydrafit.feature.routines.generated.resources.routines_prescription_error_invalid_value
import hydrafit.feature.routines.generated.resources.routines_prescription_error_malformed
import hydrafit.feature.routines.generated.resources.routines_prescription_error_over_limit
import hydrafit.feature.routines.generated.resources.routines_prescription_error_read
import hydrafit.feature.routines.generated.resources.routines_prescription_error_too_large
import hydrafit.feature.routines.generated.resources.routines_prescription_error_unsupported_version
import hydrafit.feature.routines.generated.resources.routines_prescription_export_action
import hydrafit.feature.routines.generated.resources.routines_prescription_import_cancel
import hydrafit.feature.routines.generated.resources.routines_prescription_import_choice_accept_equipment
import hydrafit.feature.routines.generated.resources.routines_prescription_import_choice_create
import hydrafit.feature.routines.generated.resources.routines_prescription_import_choice_map
import hydrafit.feature.routines.generated.resources.routines_prescription_import_close
import hydrafit.feature.routines.generated.resources.routines_prescription_import_confirm
import hydrafit.feature.routines.generated.resources.routines_prescription_import_description
import hydrafit.feature.routines.generated.resources.routines_prescription_import_missing_equipment
import hydrafit.feature.routines.generated.resources.routines_prescription_import_problem_equipment
import hydrafit.feature.routines.generated.resources.routines_prescription_import_problem_identity_conflict
import hydrafit.feature.routines.generated.resources.routines_prescription_import_problem_invalid_choice
import hydrafit.feature.routines.generated.resources.routines_prescription_import_problem_invalid_routine
import hydrafit.feature.routines.generated.resources.routines_prescription_import_problem_name_collision
import hydrafit.feature.routines.generated.resources.routines_prescription_import_problem_no_routines
import hydrafit.feature.routines.generated.resources.routines_prescription_import_problem_undecided
import hydrafit.feature.routines.generated.resources.routines_prescription_import_problem_unknown
import hydrafit.feature.routines.generated.resources.routines_prescription_import_problems_title
import hydrafit.feature.routines.generated.resources.routines_prescription_import_resolution_collision
import hydrafit.feature.routines.generated.resources.routines_prescription_import_resolution_create
import hydrafit.feature.routines.generated.resources.routines_prescription_import_resolution_existing
import hydrafit.feature.routines.generated.resources.routines_prescription_import_resolution_id_conflict
import hydrafit.feature.routines.generated.resources.routines_prescription_import_resolution_unknown
import hydrafit.feature.routines.generated.resources.routines_prescription_import_summary
import hydrafit.feature.routines.generated.resources.routines_prescription_import_title
import hydrafit.feature.routines.generated.resources.routines_prescription_import_unsupported
import hydrafit.feature.routines.generated.resources.routines_prescription_import_used_by
import hydrafit.feature.routines.generated.resources.routines_prescription_section
import hydrafit.feature.routines.generated.resources.routines_progression_action
import hydrafit.feature.routines.generated.resources.routines_progression_clear
import hydrafit.feature.routines.generated.resources.routines_progression_equipment_label
import hydrafit.feature.routines.generated.resources.routines_progression_error_equipment
import hydrafit.feature.routines.generated.resources.routines_progression_error_increment
import hydrafit.feature.routines.generated.resources.routines_progression_error_reps_range
import hydrafit.feature.routines.generated.resources.routines_progression_increment_label
import hydrafit.feature.routines.generated.resources.routines_progression_max_reps_label
import hydrafit.feature.routines.generated.resources.routines_progression_min_reps_label
import hydrafit.feature.routines.generated.resources.routines_progression_summary
import hydrafit.feature.routines.generated.resources.routines_progression_summary_equipment
import hydrafit.feature.routines.generated.resources.routines_progression_title
import hydrafit.feature.routines.generated.resources.routines_remove
import hydrafit.feature.routines.generated.resources.routines_repeat_block
import hydrafit.feature.routines.generated.resources.routines_reps_label
import hydrafit.feature.routines.generated.resources.routines_save
import hydrafit.feature.routines.generated.resources.routines_search_label
import hydrafit.feature.routines.generated.resources.routines_seconds_label
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
fun RoutinesRoute(
    viewModel: RoutinesViewModel = koinViewModel(),
    shareViewModel: PrescriptionShareViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val shareState by shareViewModel.state.collectAsStateWithLifecycle()
    // A save made elsewhere (e.g. SplitBuilder) may have landed after the long-lived collection
    // already ran; re-read on resume so the list is never stale.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }
    // An import appends routines through another repository, so re-read once it lands.
    LaunchedEffect(shareState.importPhase) {
        if (shareState.importPhase is PrescriptionImportPhase.Imported) viewModel.refresh()
    }
    RoutinesScreen(
        state = state,
        viewModel = viewModel,
        shareState = shareState,
        shareViewModel = shareViewModel
    )
}

@Composable
fun RoutinesScreen(
    state: RoutinesUiState,
    viewModel: RoutinesViewModel,
    shareState: PrescriptionShareUiState,
    shareViewModel: PrescriptionShareViewModel
) {
    if (state.editor != null) {
        RoutineEditor(
            editor = state.editor,
            message = state.message,
            excludedExerciseIds = state.excludedExerciseIds,
            viewModel = viewModel
        )
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
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
                    TemplateRow(
                        template = template,
                        viewModel = viewModel,
                        canExport = shareState.isSupported,
                        onExportRequested = shareViewModel::onExportRequested
                    )
                }
            }
            PrescriptionTransferSection(
                shareState = shareState,
                shareViewModel = shareViewModel
            )
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
    state.progressionPolicyEditor?.let { policyEditor ->
        val hasExistingPolicy = state.editor
            ?.workouts?.getOrNull(policyEditor.workoutIndex)
            ?.entries?.getOrNull(policyEditor.entryIndex)
            ?.progressionPolicy != null
        ProgressionPolicyDialog(
            state = policyEditor,
            hasExistingPolicy = hasExistingPolicy,
            onMinRepsChanged = viewModel::onProgressionPolicyMinRepsChanged,
            onMaxRepsChanged = viewModel::onProgressionPolicyMaxRepsChanged,
            onIncrementChanged = viewModel::onProgressionPolicyIncrementChanged,
            onEquipmentIdChanged = viewModel::onProgressionPolicyEquipmentIdChanged,
            onSave = viewModel::onProgressionPolicySave,
            onClear = viewModel::onProgressionPolicyClear,
            onDismiss = viewModel::onProgressionPolicyDismissed
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
    val occurrenceNames = occurrenceNames(state)
    state.pendingPostpone?.let { pending ->
        ScheduleChangeDialog(
            title = stringResource(Res.string.routines_postpone_title),
            changes = pending.changes,
            occurrenceNames = occurrenceNames,
            onConfirm = viewModel::onConfirmPostpone,
            onDismiss = viewModel::onPendingDismissed
        )
    }
    state.pendingSwitchMode?.let { pending ->
        ScheduleChangeDialog(
            title = stringResource(Res.string.routines_switch_mode),
            changes = pending.changes,
            occurrenceNames = occurrenceNames,
            onConfirm = viewModel::onConfirmSwitchMode,
            onDismiss = viewModel::onPendingDismissed
        )
    }
    (shareState.importPhase as? PrescriptionImportPhase.Reviewing)?.let { reviewing ->
        PrescriptionImportDialog(
            review = reviewing.review,
            exercises = shareState.exercises,
            busy = shareState.importInProgress,
            onChoiceChanged = shareViewModel::onIdentityChoiceChanged,
            onGapChanged = shareViewModel::onEquipmentGapChanged,
            onConfirm = shareViewModel::onImportConfirmed,
            onDismiss = shareViewModel::onImportDismissed
        )
    }
}

private fun occurrenceNames(state: RoutinesUiState): Map<Long, String> {
    val activation = state.activeActivation ?: return emptyMap()
    val workoutNames = activation.workouts.associate { it.id to it.name }
    return state.occurrences.associate { it.id to workoutNames[it.activationWorkoutId].orEmpty() }
}

@Composable
private fun ScheduleChangeDialog(
    title: String,
    changes: List<OccurrenceDateChange>,
    occurrenceNames: Map<Long, String>,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                changes.forEach { change ->
                    val date = change.scheduledEpochDay ?: change.notBeforeEpochDay
                    Text(
                        text = occurrenceNames[change.occurrenceId].orEmpty() +
                            " → " + (date?.let(::epochDayLabel) ?: ""),
                        style = MaterialTheme.typography.bodySmall
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
private fun TemplateRow(
    template: RoutineTemplate,
    viewModel: RoutinesViewModel,
    canExport: Boolean,
    onExportRequested: (RoutineTemplate) -> Unit
) {
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
                if (canExport) {
                    TextButton(
                        onClick = { onExportRequested(template) },
                        modifier = Modifier.testTag(PrescriptionShareTestTags.EXPORT_ROUTINE)
                    ) {
                        Text(stringResource(Res.string.routines_prescription_export_action))
                    }
                }
                if (!template.isArchived) {
                    TextButton(onClick = { viewModel.onActivateRequested(template) }) {
                        Text(stringResource(Res.string.routines_activate))
                    }
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
    message: String?,
    excludedExerciseIds: Set<String>,
    viewModel: RoutinesViewModel
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = stringResource(
                if (editor.id == 0L) {
                    Res.string.routines_editor_new_title
                } else {
                    Res.string.routines_editor_edit_title
                }
            ),
            style = MaterialTheme.typography.headlineSmall
        )
        message?.let { MessageBanner(message = it, onDismiss = viewModel::onMessageShown) }
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
                weightUnit = editor.unit,
                excludedExerciseIds = excludedExerciseIds,
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
    excludedExerciseIds: Set<String>,
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
                    isExcluded = entry.exerciseId in excludedExerciseIds,
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
    isExcluded: Boolean,
    viewModel: RoutinesViewModel
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = if (isExcluded) {
                    stringResource(Res.string.routines_entry_excluded, entry.name)
                } else {
                    entry.name
                },
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = { viewModel.onReplaceExercise(workoutIndex, entryIndex) }) {
                Text(stringResource(Res.string.routines_edit))
            }
            TextButton(onClick = { viewModel.onRemoveEntry(workoutIndex, entryIndex) }) {
                Text(stringResource(Res.string.routines_remove))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            FilterChip(
                selected = entry.metric == EntryMetric.REPETITIONS,
                onClick = {
                    viewModel.onEntryMetricChanged(
                        workoutIndex,
                        entryIndex,
                        EntryMetric.REPETITIONS
                    )
                },
                label = { Text(stringResource(Res.string.routines_reps_label)) }
            )
            FilterChip(
                selected = entry.metric == EntryMetric.DURATION,
                onClick = {
                    viewModel.onEntryMetricChanged(workoutIndex, entryIndex, EntryMetric.DURATION)
                },
                label = { Text(stringResource(Res.string.routines_seconds_label)) }
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = entry.sets,
                onValueChange = { viewModel.onEntrySetsChanged(workoutIndex, entryIndex, it) },
                label = { Text(stringResource(Res.string.routines_sets_label)) },
                singleLine = true,
                modifier = Modifier.width(88.dp)
            )
            // Only the selected measurement is shown, so a slot can never carry both at once.
            if (entry.metric == EntryMetric.DURATION) {
                OutlinedTextField(
                    value = entry.durationSeconds,
                    onValueChange = {
                        viewModel.onEntryDurationChanged(workoutIndex, entryIndex, it)
                    },
                    label = { Text(stringResource(Res.string.routines_seconds_label)) },
                    singleLine = true,
                    modifier = Modifier.width(88.dp)
                )
            } else {
                OutlinedTextField(
                    value = entry.reps,
                    onValueChange = { viewModel.onEntryRepsChanged(workoutIndex, entryIndex, it) },
                    label = { Text(stringResource(Res.string.routines_reps_label)) },
                    singleLine = true,
                    modifier = Modifier.width(88.dp)
                )
            }
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
            // Progression applies to counted slots; a timed slot works to seconds, not reps.
            if (entry.metric == EntryMetric.REPETITIONS) {
                TextButton(
                    onClick = { viewModel.onEditProgressionPolicy(workoutIndex, entryIndex) }
                ) {
                    Text(stringResource(Res.string.routines_progression_action))
                }
            }
        }
        entry.progressionPolicy?.let { policy ->
            Text(
                text = progressionSummary(policy),
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun progressionSummary(policy: SlotProgressionPolicy): String {
    val equipmentId = policy.equipmentId
    return if (equipmentId == null) {
        stringResource(
            Res.string.routines_progression_summary,
            policy.minReps,
            policy.maxReps,
            formatWeight(policy.incrementKg)
        )
    } else {
        stringResource(
            Res.string.routines_progression_summary_equipment,
            policy.minReps,
            policy.maxReps,
            formatWeight(policy.incrementKg),
            equipmentId
        )
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
                            Text(
                                if (exercise.isExcluded) {
                                    stringResource(
                                        Res.string.routines_picker_excluded,
                                        exercise.name
                                    )
                                } else {
                                    exercise.name
                                }
                            )
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
private fun ProgressionPolicyDialog(
    state: ProgressionPolicyEditorState,
    hasExistingPolicy: Boolean,
    onMinRepsChanged: (String) -> Unit,
    onMaxRepsChanged: (String) -> Unit,
    onIncrementChanged: (String) -> Unit,
    onEquipmentIdChanged: (String) -> Unit,
    onSave: () -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.routines_progression_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = state.minReps,
                    onValueChange = onMinRepsChanged,
                    label = {
                        Text(stringResource(Res.string.routines_progression_min_reps_label))
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = state.maxReps,
                    onValueChange = onMaxRepsChanged,
                    label = {
                        Text(stringResource(Res.string.routines_progression_max_reps_label))
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = state.incrementKg,
                    onValueChange = onIncrementChanged,
                    label = {
                        Text(stringResource(Res.string.routines_progression_increment_label))
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = state.equipmentId,
                    onValueChange = onEquipmentIdChanged,
                    label = {
                        Text(stringResource(Res.string.routines_progression_equipment_label))
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                state.error?.let { error ->
                    Text(
                        text = progressionPolicyErrorText(error),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onSave) {
                Text(stringResource(Res.string.routines_save))
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (hasExistingPolicy) {
                    TextButton(onClick = onClear) {
                        Text(stringResource(Res.string.routines_progression_clear))
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(Res.string.routines_cancel))
                }
            }
        }
    )
}

@Composable
private fun progressionPolicyErrorText(error: ProgressionPolicyError): String = when (error) {
    ProgressionPolicyError.REPS_RANGE -> stringResource(
        Res.string.routines_progression_error_reps_range,
        SlotProgressionPolicy.MIN_REPS,
        SlotProgressionPolicy.MAX_REPS
    )
    ProgressionPolicyError.INCREMENT ->
        stringResource(Res.string.routines_progression_error_increment)
    ProgressionPolicyError.EQUIPMENT_ID -> stringResource(
        Res.string.routines_progression_error_equipment,
        SlotProgressionPolicy.MAX_ID_LENGTH
    )
}

@OptIn(ExperimentalLayoutApi::class)
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
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
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
                if (activation.hasActiveBlock) {
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

/** The routine interchange actions: per-routine export, document import, and their status. */
@Composable
private fun PrescriptionTransferSection(
    shareState: PrescriptionShareUiState,
    shareViewModel: PrescriptionShareViewModel
) {
    Column(
        modifier = Modifier.routinesTestTagsAsResourceId(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = stringResource(Res.string.routines_prescription_section),
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            text = stringResource(Res.string.routines_prescription_import_description),
            style = MaterialTheme.typography.bodySmall
        )
        if (shareState.isSupported) {
            PrescriptionExportSection(
                export = shareState.export,
                busy = shareState.importInProgress,
                onExportWeightsChanged = shareViewModel::onExportWeightsChanged,
                onExportConfirmed = shareViewModel::exportRoutine,
                onExportDismissed = shareViewModel::onExportDismissed,
                onImportChosen = shareViewModel::onImportChosen
            )
        } else {
            Text(
                text = stringResource(Res.string.routines_prescription_import_unsupported),
                style = MaterialTheme.typography.bodySmall
            )
        }
        when (val phase = shareState.importPhase) {
            is PrescriptionImportPhase.Imported -> {
                Text(
                    text = stringResource(
                        Res.string.routines_prescription_import_summary,
                        phase.importedRoutines,
                        phase.createdExercises
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.testTag(PrescriptionShareTestTags.IMPORT_SUMMARY)
                )
                TextButton(onClick = shareViewModel::onImportDismissed) {
                    Text(stringResource(Res.string.routines_prescription_import_close))
                }
            }

            is PrescriptionImportPhase.Failed -> Text(
                text = phase.failure.importMessageResource(),
                style = MaterialTheme.typography.bodySmall
            )

            else -> Unit
        }
    }
}

@Composable
private fun PrescriptionImportDialog(
    review: PrescriptionImportReview,
    exercises: List<PrescriptionExerciseOption>,
    busy: Boolean,
    onChoiceChanged: (String, PrescriptionIdentityChoice?) -> Unit,
    onGapChanged: (String, Boolean) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.routines_prescription_import_title)) },
        text = {
            Column(
                modifier = Modifier
                    .routinesTestTagsAsResourceId()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = stringResource(Res.string.routines_prescription_import_description),
                    style = MaterialTheme.typography.bodySmall
                )
                review.preview.identities.forEach { identity ->
                    PrescriptionIdentityRow(
                        identity = identity,
                        exercises = exercises,
                        busy = busy,
                        onChoiceChanged = onChoiceChanged,
                        onGapChanged = onGapChanged
                    )
                }
                if (review.preview.problems.isNotEmpty()) {
                    Column(
                        modifier = Modifier.testTag(PrescriptionShareTestTags.IMPORT_PROBLEMS),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = stringResource(
                                Res.string.routines_prescription_import_problems_title
                            ),
                            style = MaterialTheme.typography.labelLarge
                        )
                        review.preview.problems.forEach { problem ->
                            Text(
                                text = problem.messageResource(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
                review.commitFailure?.let { failure ->
                    Text(
                        text = failure.messageResource(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = review.preview.canImport && !busy,
                modifier = Modifier.testTag(PrescriptionShareTestTags.IMPORT_CONFIRM)
            ) {
                Text(stringResource(Res.string.routines_prescription_import_confirm))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag(PrescriptionShareTestTags.IMPORT_CANCEL)
            ) {
                Text(stringResource(Res.string.routines_prescription_import_cancel))
            }
        }
    )
}

/**
 * One referenced identity with the state the user is deciding about. A choice is only offered where
 * the resolution allows it: create-or-map for a declared definition, map for a collision or unknown
 * identity. The equipment gap is a separate toggle, so it can be accepted for a definition that is
 * about to be created as well as for one that is already local.
 */
@Composable
private fun PrescriptionIdentityRow(
    identity: PrescriptionIdentityOption,
    exercises: List<PrescriptionExerciseOption>,
    busy: Boolean,
    onChoiceChanged: (String, PrescriptionIdentityChoice?) -> Unit,
    onGapChanged: (String, Boolean) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(identity.sourceName, style = MaterialTheme.typography.bodyMedium)
        Text(
            text = identity.resolution.messageResource(),
            style = MaterialTheme.typography.bodySmall
        )
        Text(
            text = stringResource(
                Res.string.routines_prescription_import_used_by,
                identity.usedByRoutineCount
            ),
            style = MaterialTheme.typography.bodySmall
        )
        if (identity.missingEquipment.isNotEmpty()) {
            Text(
                text = stringResource(
                    Res.string.routines_prescription_import_missing_equipment,
                    identity.missingEquipment.map { it.displayName }.sorted().joinToString(", ")
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
        when (identity.resolution) {
            is PrescriptionResolution.Create -> {
                TextButton(
                    onClick = {
                        onChoiceChanged(
                            identity.sourceExerciseId,
                            PrescriptionIdentityChoice.Create
                        )
                    },
                    enabled = !busy,
                    modifier = Modifier.testTag(PrescriptionShareTestTags.IMPORT_CREATE_CHOICE)
                ) {
                    Text(stringResource(Res.string.routines_prescription_import_choice_create))
                }
                MapTargetButton(
                    sourceExerciseId = identity.sourceExerciseId,
                    exercises = exercises,
                    busy = busy,
                    onChoiceChanged = onChoiceChanged
                )
            }

            is PrescriptionResolution.NameCollision,
            is PrescriptionResolution.IdCollision,
            PrescriptionResolution.Unknown ->
                MapTargetButton(
                    sourceExerciseId = identity.sourceExerciseId,
                    exercises = exercises,
                    busy = busy,
                    onChoiceChanged = onChoiceChanged
                )

            // An identity the catalog already holds needs no decision of its own.
            is PrescriptionResolution.Existing -> Unit
        }
        if (identity.missingEquipment.isNotEmpty()) {
            FilterChip(
                selected = identity.acceptedEquipmentGap,
                onClick = {
                    onGapChanged(identity.sourceExerciseId, !identity.acceptedEquipmentGap)
                },
                enabled = !busy,
                label = {
                    Text(
                        stringResource(
                            Res.string.routines_prescription_import_choice_accept_equipment
                        )
                    )
                },
                modifier = Modifier.testTag(PrescriptionShareTestTags.IMPORT_ACCEPT_EQUIPMENT)
            )
        }
    }
}

/** Offers the catalog as mapping targets for one unresolved identity. */
@Composable
private fun MapTargetButton(
    sourceExerciseId: String,
    exercises: List<PrescriptionExerciseOption>,
    busy: Boolean,
    onChoiceChanged: (String, PrescriptionIdentityChoice?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton(
            onClick = { expanded = true },
            enabled = !busy,
            modifier = Modifier.testTag(PrescriptionShareTestTags.IMPORT_MAP_CHOICE)
        ) {
            Text(stringResource(Res.string.routines_prescription_import_choice_map))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            exercises.forEach { exercise ->
                DropdownMenuItem(
                    text = { Text(exercise.name) },
                    onClick = {
                        expanded = false
                        onChoiceChanged(
                            sourceExerciseId,
                            PrescriptionIdentityChoice.MapTo(exercise.id)
                        )
                    }
                )
            }
        }
    }
}

@Composable
private fun PrescriptionResolution.messageResource(): String = when (this) {
    is PrescriptionResolution.Existing ->
        stringResource(Res.string.routines_prescription_import_resolution_existing)

    is PrescriptionResolution.Create ->
        stringResource(Res.string.routines_prescription_import_resolution_create)

    is PrescriptionResolution.NameCollision ->
        stringResource(
            Res.string.routines_prescription_import_resolution_collision,
            local.name
        )

    is PrescriptionResolution.IdCollision ->
        stringResource(
            Res.string.routines_prescription_import_resolution_id_conflict,
            local.name
        )

    PrescriptionResolution.Unknown ->
        stringResource(Res.string.routines_prescription_import_resolution_unknown)
}

@Composable
private fun PrescriptionImportProblem.messageResource(): String = when (issue) {
    PrescriptionImportIssue.NO_ROUTINES ->
        stringResource(Res.string.routines_prescription_import_problem_no_routines)

    PrescriptionImportIssue.UNDECIDED_IDENTITY ->
        stringResource(Res.string.routines_prescription_import_problem_undecided)

    PrescriptionImportIssue.NAME_COLLISION ->
        stringResource(Res.string.routines_prescription_import_problem_name_collision)

    PrescriptionImportIssue.IDENTITY_CONFLICT ->
        stringResource(Res.string.routines_prescription_import_problem_identity_conflict)

    PrescriptionImportIssue.UNKNOWN_IDENTITY ->
        stringResource(Res.string.routines_prescription_import_problem_unknown)

    PrescriptionImportIssue.EQUIPMENT_GAP ->
        stringResource(Res.string.routines_prescription_import_problem_equipment)

    PrescriptionImportIssue.INVALID_CHOICE ->
        stringResource(Res.string.routines_prescription_import_problem_invalid_choice)

    PrescriptionImportIssue.INVALID_ROUTINE ->
        stringResource(Res.string.routines_prescription_import_problem_invalid_routine)
}

@Composable
private fun PrescriptionImportCommitFailure.messageResource(): String = when (this) {
    PrescriptionImportCommitFailure.NOTHING_TO_IMPORT ->
        stringResource(Res.string.routines_prescription_commit_nothing)

    PrescriptionImportCommitFailure.IDENTITY_ALREADY_EXISTS ->
        stringResource(Res.string.routines_prescription_commit_exists)

    PrescriptionImportCommitFailure.PREVIEW_NOT_IMPORTABLE ->
        stringResource(Res.string.routines_prescription_commit_not_importable)
}

@Composable
private fun PrescriptionFailure?.importMessageResource(): String = when (this) {
    PrescriptionFailure.UNSUPPORTED_VERSION ->
        stringResource(Res.string.routines_prescription_error_unsupported_version)

    PrescriptionFailure.MALFORMED ->
        stringResource(Res.string.routines_prescription_error_malformed)

    PrescriptionFailure.TOO_LARGE ->
        stringResource(Res.string.routines_prescription_error_too_large)

    PrescriptionFailure.OVER_LIMIT ->
        stringResource(Res.string.routines_prescription_error_over_limit)

    PrescriptionFailure.INVALID_VALUE ->
        stringResource(Res.string.routines_prescription_error_invalid_value)

    PrescriptionFailure.DUPLICATE_ID ->
        stringResource(Res.string.routines_prescription_error_duplicate_id)

    null -> stringResource(Res.string.routines_prescription_error_read)
}
