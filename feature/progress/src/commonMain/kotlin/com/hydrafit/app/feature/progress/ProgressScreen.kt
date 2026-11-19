package com.hydrafit.app.feature.progress

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hydrafit.app.core.domain.time.localEpochDay
import com.hydrafit.app.core.domain.unit.DistanceUnit
import com.hydrafit.app.core.domain.unit.WeightUnit
import com.hydrafit.app.core.domain.unit.formatDistance
import com.hydrafit.app.core.domain.workout.DeviceAttribution
import com.hydrafit.app.core.domain.workout.LoadKind
import com.hydrafit.app.core.domain.workout.ProgressRecord
import com.hydrafit.app.core.domain.workout.ProgressRecordKey
import com.hydrafit.app.core.domain.workout.ProgressRecordType
import com.hydrafit.app.core.domain.workout.StationFilter
import com.hydrafit.app.core.domain.workout.WorkoutHistorySet
import com.hydrafit.app.core.ui.ScreenHeader
import com.hydrafit.app.core.userdata.presentation.currentPresentationNumberFormat
import com.hydrafit.app.core.userdata.presentation.formatLocalizedDecimal
import hydrafit.feature.progress.generated.resources.Res
import hydrafit.feature.progress.generated.resources.bodyweight_cancel
import hydrafit.feature.progress.generated.resources.progress_body_weight
import hydrafit.feature.progress.generated.resources.progress_chart_estimate_legend
import hydrafit.feature.progress.generated.resources.progress_chart_metric_empty_estimate
import hydrafit.feature.progress.generated.resources.progress_chart_metric_empty_volume
import hydrafit.feature.progress.generated.resources.progress_chart_volume_legend
import hydrafit.feature.progress.generated.resources.progress_charts_failed
import hydrafit.feature.progress.generated.resources.progress_charts_no_eligible
import hydrafit.feature.progress.generated.resources.progress_charts_retry
import hydrafit.feature.progress.generated.resources.progress_charts_title
import hydrafit.feature.progress.generated.resources.progress_days_hide
import hydrafit.feature.progress.generated.resources.progress_days_show
import hydrafit.feature.progress.generated.resources.progress_device_attributed
import hydrafit.feature.progress.generated.resources.progress_device_attributed_rated
import hydrafit.feature.progress.generated.resources.progress_exercise_change
import hydrafit.feature.progress.generated.resources.progress_exercise_empty
import hydrafit.feature.progress.generated.resources.progress_exercise_missing_name
import hydrafit.feature.progress.generated.resources.progress_exercise_none_selected
import hydrafit.feature.progress.generated.resources.progress_exercise_row_sessions
import hydrafit.feature.progress.generated.resources.progress_exercise_search_label
import hydrafit.feature.progress.generated.resources.progress_exercises_hide_untouched
import hydrafit.feature.progress.generated.resources.progress_exercises_logged_earlier
import hydrafit.feature.progress.generated.resources.progress_exercises_most_trained
import hydrafit.feature.progress.generated.resources.progress_exercises_show_untouched
import hydrafit.feature.progress.generated.resources.progress_fatigue
import hydrafit.feature.progress.generated.resources.progress_history_distance_line
import hydrafit.feature.progress.generated.resources.progress_history_duration_line
import hydrafit.feature.progress.generated.resources.progress_history_empty
import hydrafit.feature.progress.generated.resources.progress_history_failed
import hydrafit.feature.progress.generated.resources.progress_history_force_line
import hydrafit.feature.progress.generated.resources.progress_history_line
import hydrafit.feature.progress.generated.resources.progress_history_load_more
import hydrafit.feature.progress.generated.resources.progress_history_load_more_failed
import hydrafit.feature.progress.generated.resources.progress_history_loading
import hydrafit.feature.progress.generated.resources.progress_history_note
import hydrafit.feature.progress.generated.resources.progress_history_title
import hydrafit.feature.progress.generated.resources.progress_history_warmup
import hydrafit.feature.progress.generated.resources.progress_history_working
import hydrafit.feature.progress.generated.resources.progress_last_days_ago
import hydrafit.feature.progress.generated.resources.progress_last_months_ago
import hydrafit.feature.progress.generated.resources.progress_last_today
import hydrafit.feature.progress.generated.resources.progress_last_weeks_ago
import hydrafit.feature.progress.generated.resources.progress_last_yesterday
import hydrafit.feature.progress.generated.resources.progress_metric_estimate
import hydrafit.feature.progress.generated.resources.progress_metric_label
import hydrafit.feature.progress.generated.resources.progress_metric_volume
import hydrafit.feature.progress.generated.resources.progress_range_28
import hydrafit.feature.progress.generated.resources.progress_range_365
import hydrafit.feature.progress.generated.resources.progress_range_84
import hydrafit.feature.progress.generated.resources.progress_record_estimate
import hydrafit.feature.progress.generated.resources.progress_record_kind_added
import hydrafit.feature.progress.generated.resources.progress_record_kind_bodyweight
import hydrafit.feature.progress.generated.resources.progress_record_kind_external
import hydrafit.feature.progress.generated.resources.progress_record_kind_legacy
import hydrafit.feature.progress.generated.resources.progress_record_load_at_reps
import hydrafit.feature.progress.generated.resources.progress_record_reps_at_load
import hydrafit.feature.progress.generated.resources.progress_record_show_supporting
import hydrafit.feature.progress.generated.resources.progress_record_show_supporting_of
import hydrafit.feature.progress.generated.resources.progress_record_supporting_hide
import hydrafit.feature.progress.generated.resources.progress_record_supporting_loading
import hydrafit.feature.progress.generated.resources.progress_record_supporting_missing
import hydrafit.feature.progress.generated.resources.progress_record_supporting_next
import hydrafit.feature.progress.generated.resources.progress_record_supporting_note
import hydrafit.feature.progress.generated.resources.progress_record_supporting_previous
import hydrafit.feature.progress.generated.resources.progress_records_all_time_note
import hydrafit.feature.progress.generated.resources.progress_records_hide_all
import hydrafit.feature.progress.generated.resources.progress_records_none
import hydrafit.feature.progress.generated.resources.progress_records_show_all
import hydrafit.feature.progress.generated.resources.progress_records_title
import hydrafit.feature.progress.generated.resources.progress_station_all
import hydrafit.feature.progress.generated.resources.progress_station_label
import hydrafit.feature.progress.generated.resources.progress_station_station
import hydrafit.feature.progress.generated.resources.progress_station_unattributed
import hydrafit.feature.progress.generated.resources.progress_summary_best_estimate
import hydrafit.feature.progress.generated.resources.progress_summary_heaviest
import hydrafit.feature.progress.generated.resources.progress_summary_heaviest_value
import hydrafit.feature.progress.generated.resources.progress_title
import hydrafit.feature.progress.generated.resources.progress_weight_none
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/** Stable external identities for the Progress controls; technical metadata, never user copy. */
internal object ProgressTestTags {
    const val ROOT = "progress_root"
    const val EXERCISE_SEARCH = "progress_exercise_search"
    const val HISTORY_LIST = "progress_history_list"
    const val HISTORY_LOAD_MORE = "progress_history_load_more"
    const val BODY_WEIGHT_ENTRY = "progress_body_weight_entry"
    const val FATIGUE_ENTRY = "progress_fatigue_entry"
    fun exercise(exerciseId: String) = "progress_exercise_$exerciseId"
    fun range(range: ProgressRange) = "progress_range_${range.days}"
    fun metric(metric: ProgressMetric) = "progress_metric_${metric.name.lowercase()}"
    fun record(index: Int) = "progress_record_$index"
}

@Composable
fun ProgressRoute(
    viewModel: ProgressViewModel = koinViewModel(),
    onOpenBodyWeight: () -> Unit,
    onOpenFatigue: () -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Re-sample the clock and offset when the destination returns to the foreground, so a charting
    // read that crossed midnight is re-issued against the current instant and day window.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onResume() }
    ProgressScreen(
        state = state,
        onExerciseQueryChanged = viewModel::onExerciseQueryChanged,
        onExerciseSelected = viewModel::onExerciseSelected,
        onRangeSelected = viewModel::onRangeSelected,
        onMetricSelected = viewModel::onMetricSelected,
        onStationFilterSelected = viewModel::onStationFilterSelected,
        onRetryCharts = viewModel::onRetryCharts,
        onRetryHistory = viewModel::onRetryHistory,
        onLoadMoreHistory = viewModel::onLoadMoreHistory,
        onShowSupportingSet = viewModel::onShowSupportingSet,
        onCloseSupportingSet = viewModel::onSupportingSetClosed,
        onOpenBodyWeight = onOpenBodyWeight,
        onOpenFatigue = onOpenFatigue
    )
}

@Composable
fun ProgressScreen(
    state: ProgressUiState,
    onExerciseQueryChanged: (String) -> Unit,
    onExerciseSelected: (String) -> Unit,
    onRangeSelected: (ProgressRange) -> Unit,
    onMetricSelected: (ProgressMetric) -> Unit,
    onStationFilterSelected: (StationFilter) -> Unit,
    onRetryCharts: () -> Unit,
    onRetryHistory: () -> Unit,
    onLoadMoreHistory: () -> Unit,
    onShowSupportingSet: (ProgressRecordKey, Long) -> Unit,
    onCloseSupportingSet: () -> Unit,
    onOpenBodyWeight: () -> Unit,
    onOpenFatigue: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Query, selection, range and filter live in the ViewModel. The dialog intent and both scroll
    // offsets are hoisted above the adaptive branch so rotating or resizing keeps them; no layout
    // transition selects an exercise or writes state on its own.
    var showExercisePicker by rememberSaveable { mutableStateOf(false) }
    val detailScrollState = rememberScrollState()
    val pickerScrollState = rememberScrollState()

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .testTag(ProgressTestTags.ROOT)
    ) {
        if (progressUsesTwoPane(maxWidth, maxHeight)) {
            // The search/list stays visible beside the selected exercise instead of behind the
            // Change exercise dialog. Narrow windows keep the compact workflow below.
            Row(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .weight(0.4f)
                        .fillMaxHeight()
                        .verticalScroll(pickerScrollState)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ProgressHeader(onOpenBodyWeight, onOpenFatigue)
                    ExerciseSearch(state, onExerciseQueryChanged, onExerciseSelected)
                }
                Column(
                    modifier = Modifier
                        .weight(0.6f)
                        .fillMaxHeight()
                        .verticalScroll(detailScrollState)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (state.hasSelectedExercise) {
                        ProgressDetail(
                            state = state,
                            onRangeSelected = onRangeSelected,
                            onMetricSelected = onMetricSelected,
                            onStationFilterSelected = onStationFilterSelected,
                            onRetryCharts = onRetryCharts,
                            onRetryHistory = onRetryHistory,
                            onLoadMoreHistory = onLoadMoreHistory,
                            onShowSupportingSet = onShowSupportingSet,
                            onCloseSupportingSet = onCloseSupportingSet,
                            onChangeExercise = null
                        )
                    } else {
                        Text(stringResource(Res.string.progress_exercise_none_selected))
                    }
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(detailScrollState)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ProgressHeader(onOpenBodyWeight, onOpenFatigue)
                if (state.hasSelectedExercise) {
                    ProgressDetail(
                        state = state,
                        onRangeSelected = onRangeSelected,
                        onMetricSelected = onMetricSelected,
                        onStationFilterSelected = onStationFilterSelected,
                        onRetryCharts = onRetryCharts,
                        onRetryHistory = onRetryHistory,
                        onLoadMoreHistory = onLoadMoreHistory,
                        onShowSupportingSet = onShowSupportingSet,
                        onCloseSupportingSet = onCloseSupportingSet,
                        onChangeExercise = { showExercisePicker = true }
                    )
                } else {
                    ExerciseSearch(state, onExerciseQueryChanged, onExerciseSelected)
                    Text(stringResource(Res.string.progress_exercise_none_selected))
                }
            }

            if (showExercisePicker) {
                AlertDialog(
                    onDismissRequest = { showExercisePicker = false },
                    title = { Text(stringResource(Res.string.progress_exercise_change)) },
                    text = {
                        ExerciseSearch(
                            state = state,
                            onQueryChanged = onExerciseQueryChanged,
                            onSelected = { exerciseId ->
                                onExerciseSelected(exerciseId)
                                showExercisePicker = false
                            },
                            modifier = Modifier.verticalScroll(pickerScrollState)
                        )
                    },
                    confirmButton = {},
                    dismissButton = {
                        TextButton(onClick = { showExercisePicker = false }) {
                            Text(stringResource(Res.string.bodyweight_cancel))
                        }
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProgressHeader(onOpenBodyWeight: () -> Unit, onOpenFatigue: () -> Unit) {
    // A bottom-bar destination: the bar is its way out, so the title carries no back action.
    ScreenHeader(title = stringResource(Res.string.progress_title))
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        TextButton(
            onClick = onOpenBodyWeight,
            modifier = Modifier.testTag(ProgressTestTags.BODY_WEIGHT_ENTRY)
        ) {
            Text(stringResource(Res.string.progress_body_weight))
        }
        TextButton(
            onClick = onOpenFatigue,
            modifier = Modifier.testTag(ProgressTestTags.FATIGUE_ENTRY)
        ) {
            Text(stringResource(Res.string.progress_fatigue))
        }
    }
}

/**
 * The exercise-scoped detail sections, shared by both layouts so selection, range, filters and the
 * chart/record/history state have one rendering path. [onChangeExercise] is null in the two-pane
 * layout, where the always-visible list is the selection control.
 */
@Composable
private fun ProgressDetail(
    state: ProgressUiState,
    onRangeSelected: (ProgressRange) -> Unit,
    onMetricSelected: (ProgressMetric) -> Unit,
    onStationFilterSelected: (StationFilter) -> Unit,
    onRetryCharts: () -> Unit,
    onRetryHistory: () -> Unit,
    onLoadMoreHistory: () -> Unit,
    onShowSupportingSet: (ProgressRecordKey, Long) -> Unit,
    onCloseSupportingSet: () -> Unit,
    onChangeExercise: (() -> Unit)?
) {
    SelectedExercise(state, onChangeExercise)
    RangeSelector(state.range, onRangeSelected)
    StationFilterSelector(state, onStationFilterSelected)
    ChartsSection(state, onMetricSelected, onRetryCharts, onShowSupportingSet, onCloseSupportingSet)
    HistorySection(state, onRetryHistory, onLoadMoreHistory)
}

@Composable
private fun SelectedExercise(state: ProgressUiState, onChangeExercise: (() -> Unit)?) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        val name = state.selectedExerciseName
        Text(
            text = name ?: stringResource(Res.string.progress_exercise_missing_name),
            style = MaterialTheme.typography.titleLarge,
            color = if (name == null) MaterialTheme.colorScheme.error else Color.Unspecified,
            modifier = Modifier.weight(1f)
        )
        if (onChangeExercise != null) {
            TextButton(onClick = onChangeExercise) {
                Text(stringResource(Res.string.progress_exercise_change))
            }
        }
    }
}

@Composable
private fun ExerciseSearch(
    state: ProgressUiState,
    onQueryChanged: (String) -> Unit,
    onSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        OutlinedTextField(
            value = state.exerciseQuery,
            onValueChange = onQueryChanged,
            label = { Text(stringResource(Res.string.progress_exercise_search_label)) },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(ProgressTestTags.EXERCISE_SEARCH)
        )
        if (state.exercises.isEmpty()) {
            Text(stringResource(Res.string.progress_exercise_empty))
        } else if (state.exerciseQuery.isNotBlank()) {
            // A search is a flat list across everything, ordered the same way.
            state.matchingExercises.forEach { option -> ExerciseRow(option, onSelected) }
        } else {
            val trained = state.exercises.filter { it.recentSessions > 0 }
            val earlier = state.exercises.filter {
                it.recentSessions == 0 &&
                    it.daysSinceLast != null
            }
            val untouched = state.exercises.filter { it.daysSinceLast == null }
            var showUntouched by rememberSaveable { mutableStateOf(false) }
            if (trained.isNotEmpty()) {
                SectionLabel(stringResource(Res.string.progress_exercises_most_trained))
                trained.forEach { option -> ExerciseRow(option, onSelected) }
            }
            if (earlier.isNotEmpty()) {
                SectionLabel(stringResource(Res.string.progress_exercises_logged_earlier))
                earlier.forEach { option -> ExerciseRow(option, onSelected) }
            }
            if (untouched.isNotEmpty()) {
                TextButton(onClick = { showUntouched = !showUntouched }) {
                    Text(
                        stringResource(
                            if (showUntouched) {
                                Res.string.progress_exercises_hide_untouched
                            } else {
                                Res.string.progress_exercises_show_untouched
                            },
                            untouched.size
                        )
                    )
                }
                if (showUntouched) {
                    untouched.forEach { option -> ExerciseRow(option, onSelected) }
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 12.dp).semantics { heading() }
    )
}

/** One exercise: its name, then how often and how recently it was trained when it has been. */
@Composable
private fun ExerciseRow(option: ProgressExerciseOption, onSelected: (String) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(role = Role.Button) { onSelected(option.id) }
            .testTag(ProgressTestTags.exercise(option.id))
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(option.name, style = MaterialTheme.typography.bodyLarge)
        val daysAgo = option.daysSinceLast
        if (daysAgo != null) {
            val lastLogged = lastLoggedText(daysAgo)
            Text(
                text = if (option.recentSessions > 0) {
                    stringResource(
                        Res.string.progress_exercise_row_sessions,
                        option.recentSessions,
                        lastLogged
                    )
                } else {
                    lastLogged
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
private fun lastLoggedText(daysAgo: Int): String = when {
    daysAgo == 0 -> stringResource(Res.string.progress_last_today)
    daysAgo == 1 -> stringResource(Res.string.progress_last_yesterday)
    daysAgo < 14 -> stringResource(Res.string.progress_last_days_ago, daysAgo)
    daysAgo < 63 -> stringResource(Res.string.progress_last_weeks_ago, daysAgo / 7)
    else -> stringResource(Res.string.progress_last_months_ago, daysAgo / 30)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RangeSelector(range: ProgressRange, onRangeSelected: (ProgressRange) -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ProgressRange.entries.forEach { option ->
            FilterChip(
                selected = option == range,
                onClick = { onRangeSelected(option) },
                label = { Text(stringResource(option.labelResource())) },
                modifier = Modifier.testTag(ProgressTestTags.range(option))
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MetricSelector(metric: ProgressMetric, onSelected: (ProgressMetric) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = stringResource(Res.string.progress_metric_label),
            style = MaterialTheme.typography.labelLarge
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ProgressMetric.entries.forEach { option ->
                FilterChip(
                    selected = option == metric,
                    onClick = { onSelected(option) },
                    label = { Text(stringResource(option.labelResource())) },
                    modifier = Modifier.testTag(ProgressTestTags.metric(option))
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StationFilterSelector(state: ProgressUiState, onSelected: (StationFilter) -> Unit) {
    if (!state.hasStationOptions) return
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = stringResource(Res.string.progress_station_label),
            style = MaterialTheme.typography.labelLarge
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = state.stationFilter == StationFilter.All,
                onClick = { onSelected(StationFilter.All) },
                label = { Text(stringResource(Res.string.progress_station_all)) }
            )
            FilterChip(
                selected = state.stationFilter == StationFilter.Unattributed,
                onClick = { onSelected(StationFilter.Unattributed) },
                label = { Text(stringResource(Res.string.progress_station_unattributed)) }
            )
            state.stationOptions.forEach { station ->
                FilterChip(
                    selected = state.stationFilter == StationFilter.Station(station.id),
                    onClick = {
                        if (station.id > 0L) onSelected(StationFilter.Station(station.id))
                    },
                    label = {
                        Text(stringResource(Res.string.progress_station_station, station.name))
                    }
                )
            }
        }
    }
}

@Composable
private fun ChartsSection(
    state: ProgressUiState,
    onMetricSelected: (ProgressMetric) -> Unit,
    onRetryCharts: () -> Unit,
    onShowSupportingSet: (ProgressRecordKey, Long) -> Unit,
    onCloseSupportingSet: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = stringResource(Res.string.progress_charts_title, state.range.days.toString()),
            style = MaterialTheme.typography.titleMedium
        )
        MetricSelector(state.metric, onMetricSelected)
        when {
            state.chartsLoading -> CircularProgressIndicator()
            state.chartsFailed -> {
                Text(stringResource(Res.string.progress_charts_failed))
                TextButton(onClick = onRetryCharts) {
                    Text(stringResource(Res.string.progress_charts_retry))
                }
            }
            state.chartsHaveNoEligibleDays ->
                Text(stringResource(Res.string.progress_charts_no_eligible))
            else -> {
                ProgressChartCaption()
                Text(
                    text = stringResource(
                        state.metric.legendResource(),
                        state.weightUnit.label
                    ),
                    style = MaterialTheme.typography.bodySmall
                )
                val dayFrom = state.chartDayFrom
                val dayTo = state.chartDayTo
                if (state.chartsHaveNoSelectedMetricPoints) {
                    Text(stringResource(state.metric.emptyResource()))
                } else if (dayFrom != null && dayTo != null) {
                    ProgressMetricChart(
                        points = state.chartPoints,
                        metric = state.metric,
                        weightUnit = state.weightUnit,
                        dayFrom = dayFrom,
                        dayTo = dayTo
                    )
                }
                var showDays by rememberSaveable { mutableStateOf(false) }
                TextButton(onClick = { showDays = !showDays }) {
                    Text(
                        stringResource(
                            if (showDays) {
                                Res.string.progress_days_hide
                            } else {
                                Res.string.progress_days_show
                            }
                        )
                    )
                }
                if (showDays) {
                    ProgressDayTable(state.days, state.weightUnit, state.distanceUnit)
                }
            }
        }
        RecordsSection(state, onShowSupportingSet, onCloseSupportingSet)
    }
}

@Composable
private fun RecordsSection(
    state: ProgressUiState,
    onShowSupportingSet: (ProgressRecordKey, Long) -> Unit,
    onCloseSupportingSet: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = stringResource(Res.string.progress_records_title),
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            text = stringResource(Res.string.progress_records_all_time_note),
            style = MaterialTheme.typography.bodySmall
        )
        if (state.records.isEmpty()) {
            Text(stringResource(Res.string.progress_records_none))
            return
        }
        RecordSummary(state)
        var showAll by rememberSaveable { mutableStateOf(false) }
        TextButton(onClick = { showAll = !showAll }) {
            Text(
                if (showAll) {
                    stringResource(Res.string.progress_records_hide_all)
                } else {
                    stringResource(Res.string.progress_records_show_all, state.records.size)
                }
            )
        }
        if (!showAll) return
        state.records.forEachIndexed { index, record ->
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    recordLine(record, state.weightUnit),
                    style = MaterialTheme.typography.bodyMedium
                )
                if (record.setIds.isNotEmpty()) {
                    RecordSupportingSet(
                        record = record,
                        state = state,
                        index = index,
                        onShowSupportingSet = onShowSupportingSet,
                        onCloseSupportingSet = onCloseSupportingSet
                    )
                }
            }
        }
    }
}

/** The two records worth reading at a glance: the best estimated one-rep max and the heaviest load. */
@Composable
private fun RecordSummary(state: ProgressUiState) {
    val estimate = state.records
        .filter { it.key.type == ProgressRecordType.ESTIMATED_ONE_REP_MAX }
        .maxByOrNull { it.value }
    val heaviest = state.records
        .filter { it.key.type == ProgressRecordType.LOAD_AT_REPS }
        .maxByOrNull { it.value }
    if (estimate == null && heaviest == null) return
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        estimate?.let {
            SummaryCard(
                label = stringResource(Res.string.progress_summary_best_estimate),
                value = displayWeight(it.value, state.weightUnit),
                modifier = Modifier.weight(1f)
            )
        }
        heaviest?.let {
            SummaryCard(
                label = stringResource(Res.string.progress_summary_heaviest),
                value = stringResource(
                    Res.string.progress_summary_heaviest_value,
                    displayWeight(it.value, state.weightUnit),
                    it.key.reps ?: 0
                ),
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun SummaryCard(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.medium)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(text = value, style = MaterialTheme.typography.titleMedium)
    }
}

/**
 * One record's single supporting-set action plus its inline detail. The same set can hold several
 * records, so the placement is the record's key *and* one of its holder ids; the detail renders here,
 * directly under the record, rather than at the end of the list.
 */
@Composable
private fun RecordSupportingSet(
    record: ProgressRecord,
    state: ProgressUiState,
    index: Int,
    onShowSupportingSet: (ProgressRecordKey, Long) -> Unit,
    onCloseSupportingSet: () -> Unit
) {
    val open = state.selectedRecordKey == record.key && state.selectedSetId in record.setIds
    val position = if (open) {
        record.setIds.indexOf(state.selectedSetId) + 1
    } else {
        1
    }
    TextButton(
        onClick = { onShowSupportingSet(record.key, record.setIds[position - 1]) },
        modifier = Modifier.testTag(ProgressTestTags.record(index))
    ) {
        Text(
            if (record.setIds.size > 1) {
                stringResource(
                    Res.string.progress_record_show_supporting_of,
                    position,
                    record.setIds.size
                )
            } else {
                stringResource(Res.string.progress_record_show_supporting)
            }
        )
    }
    if (!open) return
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        when {
            state.supportingSetLoading ->
                Text(stringResource(Res.string.progress_record_supporting_loading))
            state.supportingSetFailed ->
                Text(stringResource(Res.string.progress_record_supporting_missing))
            state.supportingSetMissing ->
                Text(stringResource(Res.string.progress_record_supporting_missing))
            state.supportingSet != null -> SupportingSetDetail(
                state.supportingSet,
                state.weightUnit,
                state.distanceUnit,
                state.utcOffsetMillis
            )
        }
        if (record.setIds.size > 1) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    onClick = { onShowSupportingSet(record.key, record.setIds[position - 2]) },
                    enabled = position > 1
                ) {
                    Text(stringResource(Res.string.progress_record_supporting_previous))
                }
                TextButton(
                    onClick = { onShowSupportingSet(record.key, record.setIds[position]) },
                    enabled = position < record.setIds.size
                ) {
                    Text(stringResource(Res.string.progress_record_supporting_next))
                }
            }
        }
        TextButton(onClick = onCloseSupportingSet) {
            Text(stringResource(Res.string.progress_record_supporting_hide))
        }
    }
}

@Composable
private fun SupportingSetDetail(
    set: WorkoutHistorySet,
    weightUnit: WeightUnit,
    distanceUnit: DistanceUnit,
    utcOffsetMillis: Long
) {
    Text(
        text = stringResource(
            Res.string.progress_record_supporting_note,
            progressDayLabel(localEpochDay(set.performedAtMillis, utcOffsetMillis))
        ),
        style = MaterialTheme.typography.bodySmall
    )
    val load = set.weightKg
    val loadText = if (load == null) {
        stringResource(Res.string.progress_weight_none)
    } else {
        "${loadKindLabel(set.loadKind)} ${displayWeight(load, weightUnit)}"
    }
    val durationSeconds = set.durationSeconds
    val reps = set.reps
    val distanceMeters = set.distanceMeters
    val forceNewtons = set.forceNewtons
    Text(
        text = when {
            forceNewtons != null -> stringResource(
                Res.string.progress_history_force_line,
                formatLocalizedDecimal(forceNewtons, currentPresentationNumberFormat())
            )

            distanceMeters != null -> stringResource(
                Res.string.progress_history_distance_line,
                formatDistance(distanceMeters, distanceUnit),
                loadText
            )

            durationSeconds != null -> stringResource(
                Res.string.progress_history_duration_line,
                durationSeconds,
                loadText
            )

            reps != null -> stringResource(Res.string.progress_history_line, reps, loadText)

            else -> loadText
        },
        style = MaterialTheme.typography.bodyMedium
    )
    set.device?.takeIf { forceNewtons != null }?.let { device ->
        Text(
            text = deviceAttributionText(device),
            style = MaterialTheme.typography.bodySmall
        )
    }
    if (set.isWarmup) {
        Text(
            stringResource(Res.string.progress_history_warmup),
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun HistorySection(
    state: ProgressUiState,
    onRetryHistory: () -> Unit,
    onLoadMoreHistory: () -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.testTag(ProgressTestTags.HISTORY_LIST)
    ) {
        Text(
            text = stringResource(Res.string.progress_history_title),
            style = MaterialTheme.typography.titleMedium
        )
        Text(stringResource(Res.string.progress_history_note))
        when {
            state.historyLoading -> Text(stringResource(Res.string.progress_history_loading))
            state.historyFailed -> {
                Text(stringResource(Res.string.progress_history_failed))
                TextButton(onClick = onRetryHistory) {
                    Text(stringResource(Res.string.progress_charts_retry))
                }
            }
            state.historyIsEmpty -> Text(stringResource(Res.string.progress_history_empty))
            else -> {
                state.historySets.forEach { set ->
                    HistorySetRow(set, state.weightUnit, state.distanceUnit)
                }
                if (state.historyHasMore || state.loadMoreHistoryFailed) {
                    if (state.loadMoreHistoryFailed) {
                        Text(stringResource(Res.string.progress_history_load_more_failed))
                    }
                    TextButton(
                        onClick = onLoadMoreHistory,
                        enabled = !state.loadingMoreHistory,
                        modifier = Modifier.testTag(ProgressTestTags.HISTORY_LOAD_MORE)
                    ) {
                        Text(stringResource(Res.string.progress_history_load_more))
                    }
                }
            }
        }
    }
}

@Composable
private fun HistorySetRow(
    set: WorkoutHistorySet,
    weightUnit: WeightUnit,
    distanceUnit: DistanceUnit
) {
    val role = stringResource(
        if (set.isWarmup) {
            Res.string.progress_history_warmup
        } else {
            Res.string.progress_history_working
        }
    )
    val loadText = set.weightKg?.let { displayWeight(it, weightUnit) }
        ?: stringResource(Res.string.progress_weight_none)
    val durationSeconds = set.durationSeconds
    val reps = set.reps
    val distanceMeters = set.distanceMeters
    val forceNewtons = set.forceNewtons
    Text(
        text = "$role · " + when {
            forceNewtons != null -> stringResource(
                Res.string.progress_history_force_line,
                formatLocalizedDecimal(forceNewtons, currentPresentationNumberFormat())
            )

            distanceMeters != null -> stringResource(
                Res.string.progress_history_distance_line,
                formatDistance(distanceMeters, distanceUnit),
                loadText
            )

            durationSeconds != null -> stringResource(
                Res.string.progress_history_duration_line,
                durationSeconds,
                loadText
            )

            reps != null -> stringResource(Res.string.progress_history_line, reps, loadText)

            else -> loadText
        },
        style = MaterialTheme.typography.bodySmall
    )
    set.device?.takeIf { forceNewtons != null }?.let { device ->
        Text(
            text = deviceAttributionText(device),
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun deviceAttributionText(device: DeviceAttribution): String {
    val ratingKg = device.ratingKg
    return if (ratingKg == null) {
        stringResource(Res.string.progress_device_attributed, device.name)
    } else {
        stringResource(
            Res.string.progress_device_attributed_rated,
            device.name,
            formatLocalizedDecimal(ratingKg, currentPresentationNumberFormat())
        )
    }
}

@Composable
private fun recordLine(record: ProgressRecord, weightUnit: WeightUnit): String =
    when (record.key.type) {
        ProgressRecordType.LOAD_AT_REPS -> stringResource(
            Res.string.progress_record_load_at_reps,
            record.key.reps ?: 0,
            "${displayWeight(record.value, weightUnit)} (${loadKindLabel(record.key.loadKind)})"
        )
        ProgressRecordType.REPS_AT_LOAD -> {
            val load = record.key.weightKg
            stringResource(
                Res.string.progress_record_reps_at_load,
                load?.let { displayWeight(it, weightUnit) } ?: loadKindLabel(record.key.loadKind),
                record.value.toInt()
            )
        }
        ProgressRecordType.ESTIMATED_ONE_REP_MAX -> stringResource(
            Res.string.progress_record_estimate,
            displayWeight(record.value, weightUnit)
        )
    }

@Composable
private fun loadKindLabel(kind: LoadKind): String = when (kind) {
    LoadKind.EXTERNAL -> stringResource(Res.string.progress_record_kind_external)
    LoadKind.BODYWEIGHT -> stringResource(Res.string.progress_record_kind_bodyweight)
    LoadKind.ADDED -> stringResource(Res.string.progress_record_kind_added)
    LoadKind.LEGACY_UNSPECIFIED -> stringResource(Res.string.progress_record_kind_legacy)
}

internal fun ProgressRange.labelResource(): StringResource = when (this) {
    ProgressRange.DAYS_28 -> Res.string.progress_range_28
    ProgressRange.DAYS_84 -> Res.string.progress_range_84
    ProgressRange.DAYS_365 -> Res.string.progress_range_365
}

internal fun ProgressMetric.labelResource(): StringResource = when (this) {
    ProgressMetric.EXTERNAL_VOLUME -> Res.string.progress_metric_volume
    ProgressMetric.ESTIMATED_ONE_REP_MAX -> Res.string.progress_metric_estimate
}

internal fun ProgressMetric.legendResource(): StringResource = when (this) {
    ProgressMetric.EXTERNAL_VOLUME -> Res.string.progress_chart_volume_legend
    ProgressMetric.ESTIMATED_ONE_REP_MAX -> Res.string.progress_chart_estimate_legend
}

internal fun ProgressMetric.emptyResource(): StringResource = when (this) {
    ProgressMetric.EXTERNAL_VOLUME -> Res.string.progress_chart_metric_empty_volume
    ProgressMetric.ESTIMATED_ONE_REP_MAX -> Res.string.progress_chart_metric_empty_estimate
}
