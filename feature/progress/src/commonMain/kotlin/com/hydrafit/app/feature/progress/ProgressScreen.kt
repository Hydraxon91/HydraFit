package com.hydrafit.app.feature.progress

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hydrafit.app.core.domain.time.localEpochDay
import com.hydrafit.app.core.domain.unit.DistanceUnit
import com.hydrafit.app.core.domain.unit.WeightUnit
import com.hydrafit.app.core.domain.unit.formatDistance
import com.hydrafit.app.core.domain.unit.formatWeight
import com.hydrafit.app.core.domain.workout.DeviceAttribution
import com.hydrafit.app.core.domain.workout.LoadKind
import com.hydrafit.app.core.domain.workout.ProgressRecord
import com.hydrafit.app.core.domain.workout.ProgressRecordKey
import com.hydrafit.app.core.domain.workout.ProgressRecordType
import com.hydrafit.app.core.domain.workout.StationFilter
import com.hydrafit.app.core.domain.workout.WorkoutHistorySet
import hydrafit.feature.progress.generated.resources.Res
import hydrafit.feature.progress.generated.resources.progress_back
import hydrafit.feature.progress.generated.resources.progress_body_weight
import hydrafit.feature.progress.generated.resources.progress_chart_volume_legend
import hydrafit.feature.progress.generated.resources.progress_charts_failed
import hydrafit.feature.progress.generated.resources.progress_charts_no_eligible
import hydrafit.feature.progress.generated.resources.progress_charts_retry
import hydrafit.feature.progress.generated.resources.progress_charts_title
import hydrafit.feature.progress.generated.resources.progress_device_attributed
import hydrafit.feature.progress.generated.resources.progress_device_attributed_rated
import hydrafit.feature.progress.generated.resources.progress_exercise_empty
import hydrafit.feature.progress.generated.resources.progress_exercise_missing_name
import hydrafit.feature.progress.generated.resources.progress_exercise_none_selected
import hydrafit.feature.progress.generated.resources.progress_exercise_search_label
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
import hydrafit.feature.progress.generated.resources.progress_records_none
import hydrafit.feature.progress.generated.resources.progress_records_title
import hydrafit.feature.progress.generated.resources.progress_station_all
import hydrafit.feature.progress.generated.resources.progress_station_station
import hydrafit.feature.progress.generated.resources.progress_station_unattributed
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
    fun record(index: Int) = "progress_record_$index"
}

@Composable
fun ProgressRoute(
    viewModel: ProgressViewModel = koinViewModel(),
    onBack: () -> Unit,
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
        onStationFilterSelected = viewModel::onStationFilterSelected,
        onRetryCharts = viewModel::onRetryCharts,
        onRetryHistory = viewModel::onRetryHistory,
        onLoadMoreHistory = viewModel::onLoadMoreHistory,
        onShowSupportingSet = viewModel::onShowSupportingSet,
        onCloseSupportingSet = viewModel::onSupportingSetClosed,
        onBack = onBack,
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
    onStationFilterSelected: (StationFilter) -> Unit,
    onRetryCharts: () -> Unit,
    onRetryHistory: () -> Unit,
    onLoadMoreHistory: () -> Unit,
    onShowSupportingSet: (ProgressRecordKey, Long) -> Unit,
    onCloseSupportingSet: () -> Unit,
    onBack: () -> Unit,
    onOpenBodyWeight: () -> Unit,
    onOpenFatigue: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .testTag(ProgressTestTags.ROOT),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = stringResource(Res.string.progress_title),
            style = MaterialTheme.typography.headlineSmall
        )
        OutlinedButton(onClick = onBack) {
            Text(stringResource(Res.string.progress_back))
        }
        OutlinedButton(
            onClick = onOpenBodyWeight,
            modifier = Modifier.testTag(ProgressTestTags.BODY_WEIGHT_ENTRY)
        ) {
            Text(stringResource(Res.string.progress_body_weight))
        }
        OutlinedButton(
            onClick = onOpenFatigue,
            modifier = Modifier.testTag(ProgressTestTags.FATIGUE_ENTRY)
        ) {
            Text(stringResource(Res.string.progress_fatigue))
        }
        ExerciseSelector(state, onExerciseQueryChanged, onExerciseSelected)
        if (state.hasSelectedExercise) {
            RangeSelector(state.range, onRangeSelected)
            StationFilterSelector(state, onStationFilterSelected)
            ChartsSection(state, onRetryCharts, onShowSupportingSet, onCloseSupportingSet)
            HistorySection(state, onRetryHistory, onLoadMoreHistory)
        } else {
            Text(stringResource(Res.string.progress_exercise_none_selected))
        }
    }
}

@Composable
private fun ExerciseSelector(
    state: ProgressUiState,
    onQueryChanged: (String) -> Unit,
    onSelected: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
        } else {
            state.matchingExercises.forEach { option ->
                TextButton(
                    onClick = { onSelected(option.id) },
                    modifier = Modifier.testTag(ProgressTestTags.exercise(option.id))
                ) {
                    Text(option.name)
                }
            }
        }
        if (state.selectedExerciseNameMissing) {
            Text(
                text = stringResource(Res.string.progress_exercise_missing_name),
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
private fun RangeSelector(range: ProgressRange, onRangeSelected: (ProgressRange) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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

@Composable
private fun StationFilterSelector(state: ProgressUiState, onSelected: (StationFilter) -> Unit) {
    if (!state.hasStationOptions) return
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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

@Composable
private fun ChartsSection(
    state: ProgressUiState,
    onRetryCharts: () -> Unit,
    onShowSupportingSet: (ProgressRecordKey, Long) -> Unit,
    onCloseSupportingSet: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = stringResource(Res.string.progress_charts_title, state.range.days.toString()),
            style = MaterialTheme.typography.titleMedium
        )
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
                    text = stringResource(Res.string.progress_chart_volume_legend),
                    style = MaterialTheme.typography.bodySmall
                )
                val dayFrom = state.chartDayFrom
                val dayTo = state.chartDayTo
                if (dayFrom != null && dayTo != null) {
                    ProgressVolumeChart(state.days, dayFrom, dayTo)
                }
                ProgressDayTable(state.days, state.weightUnit, state.distanceUnit)
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
                formatWeight(forceNewtons)
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
                formatWeight(forceNewtons)
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
            formatWeight(ratingKg)
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
