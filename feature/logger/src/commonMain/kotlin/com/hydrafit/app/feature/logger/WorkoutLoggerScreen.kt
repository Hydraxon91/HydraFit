package com.hydrafit.app.feature.logger

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
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
import com.hydrafit.app.core.domain.unit.formatWeight
import com.hydrafit.app.core.navigation.FeatureDestination
import hydrafit.feature.logger.generated.resources.Res
import hydrafit.feature.logger.generated.resources.focus_full_body
import hydrafit.feature.logger.generated.resources.focus_legs
import hydrafit.feature.logger.generated.resources.focus_lower
import hydrafit.feature.logger.generated.resources.focus_pull
import hydrafit.feature.logger.generated.resources.focus_push
import hydrafit.feature.logger.generated.resources.focus_upper
import hydrafit.feature.logger.generated.resources.logger_add_weight
import hydrafit.feature.logger.generated.resources.logger_backdated_at
import hydrafit.feature.logger.generated.resources.logger_cancel
import hydrafit.feature.logger.generated.resources.logger_confirm
import hydrafit.feature.logger.generated.resources.logger_confirm_all
import hydrafit.feature.logger.generated.resources.logger_delete_set
import hydrafit.feature.logger.generated.resources.logger_dismiss
import hydrafit.feature.logger.generated.resources.logger_end_session
import hydrafit.feature.logger.generated.resources.logger_future_time_error
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
    graph = { loggerGraph() }
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
        onRevealWeight = viewModel::onRevealWeight,
        onConfirmDraft = viewModel::confirmDraft,
        onConfirmAllDrafts = viewModel::confirmAllDrafts,
        onDismissDraft = viewModel::dismissDraft,
        onEndSession = viewModel::endSession,
        onNewSession = viewModel::newSession,
        onBackdatedDateTimePicked = viewModel::onBackdatedDateTimePicked,
        onClearBackdated = { viewModel.onPerformedAtChanged(null) },
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
    onRevealWeight: () -> Unit,
    onConfirmDraft: (DraftSet) -> Unit,
    onConfirmAllDrafts: () -> Unit,
    onDismissDraft: (DraftSet) -> Unit,
    onEndSession: () -> Unit,
    onNewSession: () -> Unit,
    onBackdatedDateTimePicked: (Long, Int, Int) -> Boolean,
    onClearBackdated: () -> Unit,
    nowMillis: () -> Long,
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current
    val weightFocusRequester = remember { FocusRequester() }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var pendingDateUtcMillis by remember { mutableStateOf<Long?>(null) }
    var futureTimeError by remember { mutableStateOf(false) }

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
                showFutureError = futureTimeError,
                onSetTime = {
                    futureTimeError = false
                    pendingDateUtcMillis = null
                    showDatePicker = true
                },
                onUseNow = {
                    futureTimeError = false
                    onClearBackdated()
                }
            )
        }
        item {
            Button(onClick = onLog, enabled = state.canLog) {
                Text(stringResource(Res.string.logger_log_button))
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
                    TextButton(onClick = onConfirmAllDrafts) {
                        Text(stringResource(Res.string.logger_confirm_all))
                    }
                }
            }
            items(state.draftSets) { draft ->
                val weight = draft.weightKg
                    ?.let {
                        formatWeight(state.weightUnit.kilogramsToDisplay(it)) +
                            " " + state.weightUnit.label
                    }
                    ?: stringResource(Res.string.logger_weight_none)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${draft.name}  ${draft.sets} x ${draft.reps} · ~$weight",
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { onConfirmDraft(draft) }) {
                        Text(stringResource(Res.string.logger_confirm))
                    }
                    TextButton(onClick = { onDismissDraft(draft) }) {
                        Text(stringResource(Res.string.logger_dismiss))
                    }
                }
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
            val weight = row.weightKg
                ?.let { kg ->
                    formatWeight(unit.kilogramsToDisplay(kg)) + " " + unit.label
                }
                ?: stringResource(Res.string.logger_weight_none)
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${row.exerciseName}  ${row.reps} x $weight$warmupSuffix$weekDay",
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = { onDeleteSet(row.id) }) {
                    Text(stringResource(Res.string.logger_delete_set))
                }
            }
        }
    }

    val backdatedAnchor = state.performedAtMillis ?: nowMillis()
    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = pendingDateUtcMillis
                ?: localDateStartOfDayUtcMillis(backdatedAnchor, state.utcOffsetMillis)
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
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
                TextButton(onClick = { showDatePicker = false }) {
                    Text(stringResource(Res.string.logger_cancel))
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
    if (showTimePicker) {
        val anchorParts = localDateTimeParts(backdatedAnchor, state.utcOffsetMillis)
        val timePickerState = rememberTimePickerState(
            initialHour = anchorParts.hour,
            initialMinute = anchorParts.minute,
            is24Hour = true
        )
        TimePickerDialog(
            onDismissRequest = { showTimePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingDateUtcMillis?.let { date ->
                            futureTimeError = !onBackdatedDateTimePicked(
                                date,
                                timePickerState.hour,
                                timePickerState.minute
                            )
                        }
                        showTimePicker = false
                    }
                ) {
                    Text(stringResource(Res.string.logger_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) {
                    Text(stringResource(Res.string.logger_cancel))
                }
            },
            title = { Text(stringResource(Res.string.logger_pick_time_title)) }
        ) {
            TimePicker(state = timePickerState)
        }
    }
}

@Composable
private fun BackdatedTimeControl(
    isBackdated: Boolean,
    performedAtMillis: Long?,
    utcOffsetMillis: Long,
    showFutureError: Boolean,
    onSetTime: () -> Unit,
    onUseNow: () -> Unit
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
