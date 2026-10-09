package com.hydrafit.app.feature.logger

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDialog
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.hydrafit.app.core.domain.engine.SplitFocus
import com.hydrafit.app.core.domain.equipment.ExerciseLoadCapability
import com.hydrafit.app.core.domain.unit.WeightUnit
import com.hydrafit.app.core.domain.unit.formatWeight
import com.hydrafit.app.core.domain.workout.LoadKind
import com.hydrafit.app.core.navigation.FeatureDestination
import hydrafit.feature.logger.generated.resources.Res
import hydrafit.feature.logger.generated.resources.focus_full_body
import hydrafit.feature.logger.generated.resources.focus_legs
import hydrafit.feature.logger.generated.resources.focus_lower
import hydrafit.feature.logger.generated.resources.focus_pull
import hydrafit.feature.logger.generated.resources.focus_push
import hydrafit.feature.logger.generated.resources.focus_upper
import hydrafit.feature.logger.generated.resources.logger_active_progress
import hydrafit.feature.logger.generated.resources.logger_active_workout
import hydrafit.feature.logger.generated.resources.logger_add_weight
import hydrafit.feature.logger.generated.resources.logger_backdated_at
import hydrafit.feature.logger.generated.resources.logger_cancel
import hydrafit.feature.logger.generated.resources.logger_confirm
import hydrafit.feature.logger.generated.resources.logger_confirm_all
import hydrafit.feature.logger.generated.resources.logger_delete_set
import hydrafit.feature.logger.generated.resources.logger_dismiss
import hydrafit.feature.logger.generated.resources.logger_draft_edit
import hydrafit.feature.logger.generated.resources.logger_draft_edit_title
import hydrafit.feature.logger.generated.resources.logger_draft_load_enter
import hydrafit.feature.logger.generated.resources.logger_draft_load_prompt_body
import hydrafit.feature.logger.generated.resources.logger_draft_load_prompt_title
import hydrafit.feature.logger.generated.resources.logger_draft_load_use_last
import hydrafit.feature.logger.generated.resources.logger_draft_load_without
import hydrafit.feature.logger.generated.resources.logger_draft_more_options
import hydrafit.feature.logger.generated.resources.logger_draft_reset
import hydrafit.feature.logger.generated.resources.logger_draft_write_none
import hydrafit.feature.logger.generated.resources.logger_draft_write_partial
import hydrafit.feature.logger.generated.resources.logger_edit_time
import hydrafit.feature.logger.generated.resources.logger_end_session
import hydrafit.feature.logger.generated.resources.logger_finish
import hydrafit.feature.logger.generated.resources.logger_finish_partial
import hydrafit.feature.logger.generated.resources.logger_future_time_error
import hydrafit.feature.logger.generated.resources.logger_load_added
import hydrafit.feature.logger.generated.resources.logger_load_added_none
import hydrafit.feature.logger.generated.resources.logger_load_bodyweight
import hydrafit.feature.logger.generated.resources.logger_load_confirm_body
import hydrafit.feature.logger.generated.resources.logger_load_confirm_title
import hydrafit.feature.logger.generated.resources.logger_load_legacy
import hydrafit.feature.logger.generated.resources.logger_load_use_bodyweight
import hydrafit.feature.logger.generated.resources.logger_load_use_external
import hydrafit.feature.logger.generated.resources.logger_log_button
import hydrafit.feature.logger.generated.resources.logger_new_session
import hydrafit.feature.logger.generated.resources.logger_per_hand
import hydrafit.feature.logger.generated.resources.logger_pick_time_title
import hydrafit.feature.logger.generated.resources.logger_planned_today
import hydrafit.feature.logger.generated.resources.logger_recent
import hydrafit.feature.logger.generated.resources.logger_reps_label
import hydrafit.feature.logger.generated.resources.logger_rir_label
import hydrafit.feature.logger.generated.resources.logger_search_label
import hydrafit.feature.logger.generated.resources.logger_session_active
import hydrafit.feature.logger.generated.resources.logger_session_none
import hydrafit.feature.logger.generated.resources.logger_set_time
import hydrafit.feature.logger.generated.resources.logger_set_week_day
import hydrafit.feature.logger.generated.resources.logger_skip
import hydrafit.feature.logger.generated.resources.logger_target_current_session
import hydrafit.feature.logger.generated.resources.logger_target_label
import hydrafit.feature.logger.generated.resources.logger_time_live
import hydrafit.feature.logger.generated.resources.logger_title
import hydrafit.feature.logger.generated.resources.logger_today
import hydrafit.feature.logger.generated.resources.logger_use_now
import hydrafit.feature.logger.generated.resources.logger_warmup
import hydrafit.feature.logger.generated.resources.logger_warmup_suffix
import hydrafit.feature.logger.generated.resources.logger_weight_label
import hydrafit.feature.logger.generated.resources.logger_weight_none
import hydrafit.feature.logger.generated.resources.nav_label
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

val loggerRoute: String = "log"

val loggerDestination: FeatureDestination = FeatureDestination(
    route = loggerRoute,
    label = Res.string.nav_label,
    graph = { { loggerGraph() } }
)

fun NavGraphBuilder.loggerGraph() {
    composable(loggerRoute) { WorkoutLoggerRoute() }
}

@Composable
fun WorkoutLoggerRoute(
    modifier: Modifier = Modifier,
    viewModel: WorkoutLoggerViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onResume() }
    WorkoutLoggerScreen(
        state = state,
        onExerciseSelected = viewModel::onExerciseSelected,
        onExerciseSearchChanged = viewModel::onExerciseSearchChanged,
        onRepsChanged = viewModel::onRepsChanged,
        onWeightChanged = viewModel::onWeightChanged,
        onRirChanged = viewModel::onRirChanged,
        onWarmupToggled = viewModel::onWarmupToggled,
        onLog = viewModel::log,
        onDeleteSet = viewModel::deleteSet,
        onRecentSetSelected = viewModel::onRecentSetSelected,
        onRevealWeight = viewModel::onRevealWeight,
        onConfirmDraft = viewModel::confirmDraft,
        onEditDraft = viewModel::editDraft,
        onDraftRepsChanged = viewModel::onDraftRepsChanged,
        onDraftWeightChanged = viewModel::onDraftWeightChanged,
        onDraftRirChanged = viewModel::onDraftRirChanged,
        onDraftWeightRevealed = viewModel::onDraftWeightRevealed,
        onDraftPerformedAtChanged = viewModel::onDraftPerformedAtChanged,
        onCancelDraftEdit = viewModel::cancelDraftEdit,
        onResetDraftEdit = viewModel::resetDraftEdit,
        onConfirmDraftEdit = viewModel::confirmDraftEdit,
        onCancelMissingLoadPrompt = viewModel::cancelMissingLoadPrompt,
        onUseLastLoggedLoad = viewModel::useLastLoggedLoad,
        onEnterMissingLoad = viewModel::enterMissingLoad,
        onLogMissingLoadWithoutWeight = viewModel::logMissingLoadWithoutWeight,
        onConfirmAllDrafts = viewModel::confirmAllDrafts,
        onDismissDraft = viewModel::dismissDraft,
        onResolveLegacyAsBodyweight = viewModel::resolveLegacyAsBodyweight,
        onResolveLegacyAsExternal = viewModel::resolveLegacyAsExternal,
        onDismissLegacyResolution = viewModel::dismissLegacyResolution,
        onEndSession = viewModel::endSession,
        onNewSession = viewModel::newSession,
        onFinishWorkout = viewModel::finishWorkout,
        onFinishWorkoutPartially = viewModel::finishWorkoutPartially,
        onSkipWorkout = viewModel::skipWorkout,
        onOccurrenceMessageShown = viewModel::onOccurrenceMessageShown,
        onBackdatedDateTimePicked = viewModel::onBackdatedDateTimePicked,
        onCorrectSetTime = viewModel::correctSetTime,
        onClearBackdated = { viewModel.onPerformedAtChanged(null) },
        onForceNewSessionChanged = viewModel::onForceNewSessionChanged,
        nowMillis = viewModel::currentTimeMillis,
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutLoggerScreen(
    state: WorkoutLoggerUiState,
    onExerciseSelected: (String) -> Unit,
    onExerciseSearchChanged: (String) -> Unit,
    onRepsChanged: (String) -> Unit,
    onWeightChanged: (String) -> Unit,
    onRirChanged: (String) -> Unit,
    onWarmupToggled: (Boolean) -> Unit,
    onLog: () -> Unit,
    onDeleteSet: (Long) -> Unit,
    onRecentSetSelected: (LoggedSetRow) -> Unit,
    onRevealWeight: () -> Unit,
    onConfirmDraft: (DraftSet) -> Unit,
    onEditDraft: (DraftSet) -> Unit,
    onDraftRepsChanged: (String) -> Unit,
    onDraftWeightChanged: (String) -> Unit,
    onDraftRirChanged: (String) -> Unit,
    onDraftWeightRevealed: () -> Unit,
    onDraftPerformedAtChanged: (Long?) -> Boolean,
    onCancelDraftEdit: () -> Unit,
    onResetDraftEdit: () -> Unit,
    onConfirmDraftEdit: () -> Unit,
    onCancelMissingLoadPrompt: () -> Unit,
    onUseLastLoggedLoad: () -> Unit,
    onEnterMissingLoad: () -> Unit,
    onLogMissingLoadWithoutWeight: () -> Unit,
    onConfirmAllDrafts: () -> Unit,
    onDismissDraft: (DraftSet) -> Unit,
    onResolveLegacyAsBodyweight: () -> Unit,
    onResolveLegacyAsExternal: () -> Unit,
    onDismissLegacyResolution: () -> Unit,
    onEndSession: () -> Unit,
    onNewSession: () -> Unit,
    onFinishWorkout: () -> Unit,
    onFinishWorkoutPartially: () -> Unit,
    onSkipWorkout: () -> Unit,
    onOccurrenceMessageShown: () -> Unit,
    onBackdatedDateTimePicked: (Long, Int, Int) -> Boolean,
    onCorrectSetTime: (Long, Long, Int, Int) -> Boolean,
    onClearBackdated: () -> Unit,
    onForceNewSessionChanged: (Boolean) -> Unit,
    nowMillis: () -> Long,
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current
    val weightFocusRequester = remember { FocusRequester() }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var pendingDateUtcMillis by remember { mutableStateOf<Long?>(null) }
    var futureTimeError by remember { mutableStateOf(false) }
    // When set, the date/time picker corrects that row instead of choosing the new-log time.
    var correctingSetId by remember { mutableStateOf<Long?>(null) }

    // One scroll container for the whole screen: a fixed-height picker inside a non-scrolling
    // parent used to push the form and the recent-sets list off short viewports.
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .safeContentPadding(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Text(
                text = stringResource(Res.string.logger_title),
                style = MaterialTheme.typography.headlineSmall
            )
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(
                        if (state.activeSession != null) {
                            Res.string.logger_session_active
                        } else {
                            Res.string.logger_session_none
                        }
                    )
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onEndSession, enabled = state.activeSession != null) {
                        Text(stringResource(Res.string.logger_end_session))
                    }
                    TextButton(onClick = onNewSession) {
                        Text(stringResource(Res.string.logger_new_session))
                    }
                }
            }
        }
        state.todayFocus?.let { focus ->
            item {
                Text(
                    text = stringResource(
                        Res.string.logger_today,
                        stringResource(focus.labelResource())
                    ),
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }
        item {
            OutlinedTextField(
                value = state.exerciseSearch,
                onValueChange = onExerciseSearchChanged,
                label = { Text(stringResource(Res.string.logger_search_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
        item {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
            ) {
                items(state.visibleExercises, key = { it.id }) { exercise ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onExerciseSelected(exercise.id) },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = state.selectedExerciseId == exercise.id,
                            onClick = { onExerciseSelected(exercise.id) }
                        )
                        Text(exercise.name)
                    }
                }
            }
        }
        item {
            OutlinedTextField(
                value = state.reps,
                onValueChange = onRepsChanged,
                label = { Text(stringResource(Res.string.logger_reps_label)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Next
                ),
                keyboardActions = KeyboardActions(
                    onNext = { focusManager.moveFocus(FocusDirection.Down) }
                ),
                modifier = Modifier.fillMaxWidth()
            )
        }
        item {
            if (state.showWeightField) {
                OutlinedTextField(
                    value = state.weightInput,
                    onValueChange = onWeightChanged,
                    label = {
                        Text(stringResource(Res.string.logger_weight_label, state.weightUnit.label))
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal,
                        imeAction = ImeAction.Next
                    ),
                    keyboardActions = KeyboardActions(
                        onNext = { focusManager.moveFocus(FocusDirection.Down) }
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(weightFocusRequester)
                )
            } else {
                TextButton(onClick = onRevealWeight) {
                    Text(stringResource(Res.string.logger_add_weight))
                }
            }
        }
        item {
            OutlinedTextField(
                value = state.rir,
                onValueChange = onRirChanged,
                label = { Text(stringResource(Res.string.logger_rir_label)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                modifier = Modifier.fillMaxWidth()
            )
        }
        if (state.selectedExerciseIsUnilateral) {
            item {
                Text(
                    text = stringResource(Res.string.logger_per_hand),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Switch(checked = state.isWarmup, onCheckedChange = onWarmupToggled)
                Text(stringResource(Res.string.logger_warmup))
            }
        }
        item {
            BackdatedTimeControl(
                isBackdated = state.isBackdated,
                performedAtMillis = state.performedAtMillis,
                utcOffsetMillis = state.utcOffsetMillis,
                attachesToOpenSession = state.backdatedTargetSession != null,
                forceNewSession = state.forceNewSession,
                showFutureError = futureTimeError,
                onSetTime = {
                    futureTimeError = false
                    pendingDateUtcMillis = null
                    correctingSetId = null
                    showDatePicker = true
                },
                onUseNow = {
                    futureTimeError = false
                    onClearBackdated()
                },
                onForceNewSessionChanged = onForceNewSessionChanged
            )
        }
        item {
            Button(onClick = onLog, enabled = state.canLog) {
                Text(stringResource(Res.string.logger_log_button))
            }
        }
        state.activeOccurrence?.let { occurrence ->
            item {
                ActiveOccurrenceSection(
                    occurrence = occurrence,
                    message = state.occurrenceMessage,
                    onFinish = onFinishWorkout,
                    onFinishPartially = onFinishWorkoutPartially,
                    onSkip = onSkipWorkout,
                    onMessageShown = onOccurrenceMessageShown
                )
            }
        }
        if (state.draftSets.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = stringResource(Res.string.logger_planned_today),
                        style = MaterialTheme.typography.titleMedium
                    )
                    TextButton(
                        onClick = onConfirmAllDrafts,
                        enabled = !state.draftWriteInProgress
                    ) {
                        Text(stringResource(Res.string.logger_confirm_all))
                    }
                }
            }
            items(state.draftSets) { draft ->
                val weight = loadWeightText(draft.loadKind, draft.weightKg, state.weightUnit)
                var expanded by remember(draft) { mutableStateOf(false) }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${draft.name}  ${draft.sets} x ${draft.reps} · ~$weight",
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(
                        onClick = { onConfirmDraft(draft) },
                        enabled = !state.draftWriteInProgress
                    ) {
                        Text(stringResource(Res.string.logger_confirm))
                    }
                    TextButton(onClick = { onDismissDraft(draft) }) {
                        Text(stringResource(Res.string.logger_dismiss))
                    }
                    Box {
                        TextButton(onClick = { expanded = true }) {
                            Text(stringResource(Res.string.logger_draft_more_options))
                        }
                        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(Res.string.logger_draft_edit)) },
                                onClick = {
                                    expanded = false
                                    onEditDraft(draft)
                                }
                            )
                        }
                    }
                }
            }
        }
        state.draftWriteRetry?.let { retry ->
            item {
                Text(
                    text = stringResource(
                        if (retry.anyRecorded) {
                            Res.string.logger_draft_write_partial
                        } else {
                            Res.string.logger_draft_write_none
                        }
                    ),
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
        item {
            Text(
                text = stringResource(Res.string.logger_recent),
                style = MaterialTheme.typography.titleMedium
            )
        }
        items(state.recentSets) { row ->
            val unit = state.weightUnit
            val weight = loadWeightText(row.loadKind, row.weightKg, unit)
            val warmupSuffix = if (row.isWarmup) {
                " " + stringResource(Res.string.logger_warmup_suffix)
            } else {
                ""
            }
            val weekDay = if (row.weekNumber != null && row.dayIndex != null) {
                " · " + stringResource(
                    Res.string.logger_set_week_day,
                    row.weekNumber,
                    row.dayIndex + 1
                )
            } else {
                ""
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable { onRecentSetSelected(row) }
            ) {
                Text(
                    text = "${row.exerciseName}  ${row.reps} x $weight$warmupSuffix$weekDay",
                    modifier = Modifier.weight(1f)
                )
                TextButton(
                    onClick = {
                        futureTimeError = false
                        pendingDateUtcMillis = null
                        correctingSetId = row.id
                        showDatePicker = true
                    }
                ) {
                    Text(stringResource(Res.string.logger_edit_time))
                }
                TextButton(onClick = { onDeleteSet(row.id) }) {
                    Text(stringResource(Res.string.logger_delete_set))
                }
            }
        }
    }

    val backdatedAnchor = state.performedAtMillis ?: nowMillis()
    val pickerAnchor = correctingSetId
        ?.let { id -> state.recentSets.firstOrNull { it.id == id }?.performedAtMillis }
        ?: backdatedAnchor
    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = pendingDateUtcMillis
                ?: localDateStartOfDayUtcMillis(pickerAnchor, state.utcOffsetMillis)
        )
        DatePickerDialog(
            onDismissRequest = {
                showDatePicker = false
                correctingSetId = null
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { selected ->
                            pendingDateUtcMillis = selected
                            showDatePicker = false
                            showTimePicker = true
                        }
                    }
                ) {
                    Text(stringResource(Res.string.logger_confirm))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showDatePicker = false
                        correctingSetId = null
                    }
                ) {
                    Text(stringResource(Res.string.logger_cancel))
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
    if (showTimePicker) {
        val anchorParts = localDateTimeParts(pickerAnchor, state.utcOffsetMillis)
        val timePickerState = rememberTimePickerState(
            initialHour = anchorParts.hour,
            initialMinute = anchorParts.minute,
            is24Hour = true
        )
        TimePickerDialog(
            onDismissRequest = {
                showTimePicker = false
                correctingSetId = null
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingDateUtcMillis?.let { date ->
                            val target = correctingSetId
                            futureTimeError = if (target != null) {
                                !onCorrectSetTime(
                                    target,
                                    date,
                                    timePickerState.hour,
                                    timePickerState.minute
                                )
                            } else {
                                !onBackdatedDateTimePicked(
                                    date,
                                    timePickerState.hour,
                                    timePickerState.minute
                                )
                            }
                        }
                        showTimePicker = false
                        correctingSetId = null
                    }
                ) {
                    Text(stringResource(Res.string.logger_confirm))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showTimePicker = false
                        correctingSetId = null
                    }
                ) {
                    Text(stringResource(Res.string.logger_cancel))
                }
            },
            title = { Text(stringResource(Res.string.logger_pick_time_title)) }
        ) {
            TimePicker(state = timePickerState)
        }
    }
    state.legacyResolution?.let { resolution ->
        val draft = resolution.current.draft
        val recorded = draft.weightKg
            ?.let {
                formatWeight(state.weightUnit.kilogramsToDisplay(it)) +
                    " " + state.weightUnit.label
            }
            ?: stringResource(Res.string.logger_weight_none)
        AlertDialog(
            onDismissRequest = onDismissLegacyResolution,
            title = { Text(stringResource(Res.string.logger_load_confirm_title)) },
            text = {
                Text(stringResource(Res.string.logger_load_confirm_body, draft.name, recorded))
            },
            confirmButton = {
                if (resolution.current.canBeExternal) {
                    TextButton(onClick = onResolveLegacyAsExternal) {
                        Text(stringResource(Res.string.logger_load_use_external))
                    }
                } else {
                    TextButton(onClick = onResolveLegacyAsBodyweight) {
                        Text(stringResource(Res.string.logger_load_use_bodyweight))
                    }
                }
            },
            dismissButton = {
                Row {
                    if (resolution.current.canBeExternal) {
                        TextButton(onClick = onResolveLegacyAsBodyweight) {
                            Text(stringResource(Res.string.logger_load_use_bodyweight))
                        }
                    }
                    TextButton(onClick = onDismissLegacyResolution) {
                        Text(stringResource(Res.string.logger_cancel))
                    }
                }
            }
        )
    }

    if (state.legacyResolution == null) {
        state.draftEdit?.let { edit ->
            val capability = edit.draft.loadCapability
            DraftEditDialog(
                edit = edit,
                weightUnit = state.weightUnit,
                utcOffsetMillis = state.utcOffsetMillis,
                nowMillis = nowMillis(),
                canRevealWeight = capability == ExerciseLoadCapability.BODYWEIGHT_ADDABLE,
                showWeight = when (capability) {
                    ExerciseLoadCapability.BODYWEIGHT_ONLY -> false
                    ExerciseLoadCapability.BODYWEIGHT_ADDABLE -> edit.weightRevealed
                    else -> true
                },
                onRepsChanged = onDraftRepsChanged,
                onWeightChanged = onDraftWeightChanged,
                onRirChanged = onDraftRirChanged,
                onRevealWeight = onDraftWeightRevealed,
                onTimeChanged = onDraftPerformedAtChanged,
                onCancel = onCancelDraftEdit,
                onReset = onResetDraftEdit,
                onConfirm = onConfirmDraftEdit
            )
        }
    }
    state.missingLoadPrompt?.let { draft ->
        AlertDialog(
            onDismissRequest = onCancelMissingLoadPrompt,
            title = { Text(stringResource(Res.string.logger_draft_load_prompt_title)) },
            text = { Text(stringResource(Res.string.logger_draft_load_prompt_body, draft.name)) },
            confirmButton = {
                TextButton(onClick = onUseLastLoggedLoad) {
                    Text(stringResource(Res.string.logger_draft_load_use_last))
                }
            },
            dismissButton = {
                Column {
                    TextButton(onClick = onEnterMissingLoad) {
                        Text(stringResource(Res.string.logger_draft_load_enter))
                    }
                    TextButton(onClick = onLogMissingLoadWithoutWeight) {
                        Text(stringResource(Res.string.logger_draft_load_without))
                    }
                    TextButton(onClick = onCancelMissingLoadPrompt) {
                        Text(stringResource(Res.string.logger_cancel))
                    }
                }
            }
        )
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun DraftEditDialog(
    edit: DraftEdit,
    weightUnit: WeightUnit,
    utcOffsetMillis: Long,
    nowMillis: Long,
    canRevealWeight: Boolean,
    showWeight: Boolean,
    onRepsChanged: (String) -> Unit,
    onWeightChanged: (String) -> Unit,
    onRirChanged: (String) -> Unit,
    onRevealWeight: () -> Unit,
    onTimeChanged: (Long?) -> Boolean,
    onCancel: () -> Unit,
    onReset: () -> Unit,
    onConfirm: () -> Unit
) {
    var showDate by remember { mutableStateOf(false) }
    var showTime by remember { mutableStateOf(false) }
    var date by remember { mutableStateOf<Long?>(null) }
    var timeError by remember { mutableStateOf(false) }
    val anchor = edit.performedAtMillis ?: nowMillis
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(Res.string.logger_draft_edit_title, edit.draft.name)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = edit.reps,
                    onValueChange = onRepsChanged,
                    label = { Text(stringResource(Res.string.logger_reps_label)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )
                if (showWeight) {
                    OutlinedTextField(
                        value = edit.weightInput,
                        onValueChange = onWeightChanged,
                        label = {
                            Text(stringResource(Res.string.logger_weight_label, weightUnit.label))
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true
                    )
                } else {
                    Text(stringResource(Res.string.logger_load_bodyweight))
                    if (canRevealWeight) {
                        TextButton(onClick = onRevealWeight) {
                            Text(stringResource(Res.string.logger_add_weight))
                        }
                    }
                }
                OutlinedTextField(
                    value = edit.rir,
                    onValueChange = onRirChanged,
                    label = { Text(stringResource(Res.string.logger_rir_label)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )
                TextButton(onClick = {
                    showDate = true
                    timeError = false
                }) {
                    Text(stringResource(Res.string.logger_set_time))
                }
                TextButton(onClick = {
                    timeError = false
                    onTimeChanged(null)
                }) {
                    Text(stringResource(Res.string.logger_use_now))
                }
                if (timeError) Text(stringResource(Res.string.logger_future_time_error))
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = (edit.reps.toIntOrNull() ?: 0) > 0) {
                Text(stringResource(Res.string.logger_confirm))
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onReset) {
                    Text(stringResource(Res.string.logger_draft_reset))
                }
                TextButton(onClick = onCancel) { Text(stringResource(Res.string.logger_cancel)) }
            }
        }
    )
    if (showDate) {
        val picker = rememberDatePickerState(
            initialSelectedDateMillis =
            date ?: localDateStartOfDayUtcMillis(anchor, utcOffsetMillis)
        )
        DatePickerDialog(
            onDismissRequest = { showDate = false },
            confirmButton = {
                TextButton(onClick = {
                    date = picker.selectedDateMillis
                    showDate = false
                    showTime =
                        true
                }) {
                    Text(stringResource(Res.string.logger_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showDate = false
                }) { Text(stringResource(Res.string.logger_cancel)) }
            }
        ) { DatePicker(state = picker) }
    }
    if (showTime) {
        val parts = localDateTimeParts(anchor, utcOffsetMillis)
        val picker =
            rememberTimePickerState(
                initialHour = parts.hour,
                initialMinute = parts.minute,
                is24Hour = true
            )
        TimePickerDialog(
            onDismissRequest = { showTime = false },
            confirmButton = {
                TextButton(onClick = {
                    val selectedDate = date
                    if (selectedDate != null) {
                        val picked =
                            pickedLocalDateTimeToEpochMillis(
                                selectedDate,
                                picker.hour,
                                picker.minute,
                                utcOffsetMillis
                            )
                        timeError = !onTimeChanged(picked)
                    }
                    showTime = false
                }) { Text(stringResource(Res.string.logger_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    showTime = false
                }) { Text(stringResource(Res.string.logger_cancel)) }
            },
            title = { Text(stringResource(Res.string.logger_pick_time_title)) }
        ) { TimePicker(state = picker) }
    }
}

/** Renders a recorded weight with honest load semantics (external vs added vs bodyweight vs legacy). */
@Composable
private fun loadWeightText(loadKind: LoadKind, weightKg: Double?, unit: WeightUnit): String =
    when (val display = loadDisplayFor(loadKind, weightKg)) {
        LoadDisplay.None -> stringResource(Res.string.logger_weight_none)
        is LoadDisplay.External ->
            formatWeight(unit.kilogramsToDisplay(display.weightKg)) + " " + unit.label
        is LoadDisplay.Added -> display.weightKg?.let {
            stringResource(
                Res.string.logger_load_added,
                formatWeight(unit.kilogramsToDisplay(it)),
                unit.label
            )
        } ?: stringResource(Res.string.logger_load_added_none)
        LoadDisplay.Bodyweight -> stringResource(Res.string.logger_load_bodyweight)
        is LoadDisplay.LegacyUnconfirmed -> display.weightKg?.let {
            stringResource(
                Res.string.logger_load_legacy,
                formatWeight(unit.kilogramsToDisplay(it)),
                unit.label
            )
        } ?: stringResource(Res.string.logger_weight_none)
    }

@Composable
private fun BackdatedTimeControl(
    isBackdated: Boolean,
    performedAtMillis: Long?,
    utcOffsetMillis: Long,
    attachesToOpenSession: Boolean,
    forceNewSession: Boolean,
    showFutureError: Boolean,
    onSetTime: () -> Unit,
    onUseNow: () -> Unit,
    onForceNewSessionChanged: (Boolean) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = if (isBackdated && performedAtMillis != null) {
                    val parts = localDateTimeParts(performedAtMillis, utcOffsetMillis)
                    stringResource(
                        Res.string.logger_backdated_at,
                        localDateLabel(parts),
                        localTimeLabel(parts)
                    )
                } else {
                    stringResource(Res.string.logger_time_live)
                },
                style = MaterialTheme.typography.bodyMedium
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onSetTime) {
                    Text(stringResource(Res.string.logger_set_time))
                }
                if (isBackdated) {
                    TextButton(onClick = onUseNow) {
                        Text(stringResource(Res.string.logger_use_now))
                    }
                }
            }
        }
        if (isBackdated) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(
                        Res.string.logger_target_label,
                        stringResource(
                            if (attachesToOpenSession) {
                                Res.string.logger_target_current_session
                            } else {
                                Res.string.logger_new_session
                            }
                        )
                    ),
                    style = MaterialTheme.typography.bodyMedium
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(stringResource(Res.string.logger_new_session))
                    Switch(checked = forceNewSession, onCheckedChange = onForceNewSessionChanged)
                }
            }
        }
        if (showFutureError) {
            Text(
                text = stringResource(Res.string.logger_future_time_error),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

private fun localDateLabel(parts: LocalDateTimeParts): String =
    "${parts.year}-${twoDigits(parts.month)}-${twoDigits(parts.day)}"

private fun localTimeLabel(parts: LocalDateTimeParts): String =
    "${twoDigits(parts.hour)}:${twoDigits(parts.minute)}"

private fun twoDigits(value: Int): String = value.toString().padStart(2, '0')

private fun SplitFocus.labelResource(): StringResource = when (this) {
    SplitFocus.PUSH -> Res.string.focus_push
    SplitFocus.PULL -> Res.string.focus_pull
    SplitFocus.LEGS -> Res.string.focus_legs
    SplitFocus.UPPER -> Res.string.focus_upper
    SplitFocus.LOWER -> Res.string.focus_lower
    SplitFocus.FULL_BODY -> Res.string.focus_full_body
}

@Composable
private fun ActiveOccurrenceSection(
    occurrence: ActiveOccurrence,
    message: String?,
    onFinish: () -> Unit,
    onFinishPartially: () -> Unit,
    onSkip: () -> Unit,
    onMessageShown: () -> Unit
) {
    Card {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = stringResource(Res.string.logger_active_workout, occurrence.workoutName),
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = stringResource(
                    Res.string.logger_active_progress,
                    occurrence.performedSets,
                    occurrence.prescribedSets
                ),
                style = MaterialTheme.typography.bodySmall
            )
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onFinish) {
                    Text(stringResource(Res.string.logger_finish))
                }
                TextButton(onClick = onFinishPartially) {
                    Text(stringResource(Res.string.logger_finish_partial))
                }
                TextButton(onClick = onSkip) {
                    Text(stringResource(Res.string.logger_skip))
                }
            }
            message?.let { error ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = onMessageShown) {
                        Text(stringResource(Res.string.logger_dismiss))
                    }
                }
            }
        }
    }
}
