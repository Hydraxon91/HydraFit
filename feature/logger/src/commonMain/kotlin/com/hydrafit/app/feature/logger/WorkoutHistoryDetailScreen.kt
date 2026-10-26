package com.hydrafit.app.feature.logger

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hydrafit.app.core.domain.unit.DistanceUnit
import com.hydrafit.app.core.domain.unit.formatDistance
import com.hydrafit.app.core.domain.unit.formatWeight
import com.hydrafit.app.core.domain.workout.WorkoutHistoryKey
import com.hydrafit.app.core.domain.workout.WorkoutHistorySet
import com.hydrafit.app.core.domain.workout.WorkoutTimingProvenance
import hydrafit.feature.logger.generated.resources.Res
import hydrafit.feature.logger.generated.resources.logger_edit_set
import hydrafit.feature.logger.generated.resources.logger_force_value
import hydrafit.feature.logger.generated.resources.logger_history_back
import hydrafit.feature.logger.generated.resources.logger_history_detail_session
import hydrafit.feature.logger.generated.resources.logger_history_detail_unassigned
import hydrafit.feature.logger.generated.resources.logger_history_detail_unavailable
import hydrafit.feature.logger.generated.resources.logger_history_exercise_missing
import hydrafit.feature.logger.generated.resources.logger_history_failed
import hydrafit.feature.logger.generated.resources.logger_history_load_more
import hydrafit.feature.logger.generated.resources.logger_history_load_more_failed
import hydrafit.feature.logger.generated.resources.logger_history_note_absent
import hydrafit.feature.logger.generated.resources.logger_history_note_cleared
import hydrafit.feature.logger.generated.resources.logger_history_rir
import hydrafit.feature.logger.generated.resources.logger_history_session_note_title
import hydrafit.feature.logger.generated.resources.logger_history_set_count
import hydrafit.feature.logger.generated.resources.logger_history_set_distance_line
import hydrafit.feature.logger.generated.resources.logger_history_set_duration_line
import hydrafit.feature.logger.generated.resources.logger_history_set_force_line
import hydrafit.feature.logger.generated.resources.logger_history_set_line
import hydrafit.feature.logger.generated.resources.logger_history_sets_failed
import hydrafit.feature.logger.generated.resources.logger_history_sets_loading
import hydrafit.feature.logger.generated.resources.logger_history_sets_none
import hydrafit.feature.logger.generated.resources.logger_history_sets_title
import hydrafit.feature.logger.generated.resources.logger_history_setup_note_cleared
import hydrafit.feature.logger.generated.resources.logger_history_setup_note_current
import hydrafit.feature.logger.generated.resources.logger_history_setup_note_failed
import hydrafit.feature.logger.generated.resources.logger_history_setup_note_none
import hydrafit.feature.logger.generated.resources.logger_history_setup_note_title
import hydrafit.feature.logger.generated.resources.logger_history_timing_catch_up
import hydrafit.feature.logger.generated.resources.logger_history_timing_live
import hydrafit.feature.logger.generated.resources.logger_history_timing_unknown
import hydrafit.feature.logger.generated.resources.logger_history_warmup
import hydrafit.feature.logger.generated.resources.logger_history_working
import hydrafit.feature.logger.generated.resources.logger_note_retry
import hydrafit.feature.logger.generated.resources.logger_recorded_at
import hydrafit.feature.logger.generated.resources.logger_station_attributed
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutHistoryDetailRoute(
    onBack: () -> Unit,
    viewModel: WorkoutHistoryViewModel,
    loggerViewModel: WorkoutLoggerViewModel
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val loggerState by loggerViewModel.state.collectAsStateWithLifecycle()
    WorkoutHistoryDetailScreen(
        state = state,
        loggerState = loggerState,
        onEditSet = { set ->
            state.selectedKey?.let { key ->
                loggerViewModel.editLoggedSet(set.toLoggedSetRow(key))
            }
        },
        onLoggedSetRepsChanged = loggerViewModel::onLoggedSetRepsChanged,
        onLoggedSetDurationChanged = loggerViewModel::onLoggedSetDurationChanged,
        onLoggedSetDistanceChanged = loggerViewModel::onLoggedSetDistanceChanged,
        onLoggedSetForceChanged = loggerViewModel::onLoggedSetForceChanged,
        onLoggedSetDeviceNameChanged = loggerViewModel::onLoggedSetDeviceNameChanged,
        onLoggedSetDeviceRatingChanged = loggerViewModel::onLoggedSetDeviceRatingChanged,
        onLoggedSetMetricChanged = loggerViewModel::onLoggedSetMetricChanged,
        onLoggedSetWeightChanged = loggerViewModel::onLoggedSetWeightChanged,
        onLoggedSetRirChanged = loggerViewModel::onLoggedSetRirChanged,
        onLoggedSetTimeChanged = loggerViewModel::onLoggedSetTimeChanged,
        onCancelLoggedSetEdit = loggerViewModel::cancelLoggedSetEdit,
        onSaveLoggedSetEdit = loggerViewModel::saveLoggedSetEdit,
        onRetryDetail = viewModel::onRetryDetail,
        onLoadMoreSets = viewModel::onLoadMoreSets,
        onRetryExerciseNote = viewModel::onRetryExerciseNote,
        onBack = onBack,
        nowMillis = loggerViewModel::currentTimeMillis
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutHistoryDetailScreen(
    state: WorkoutHistoryUiState,
    loggerState: WorkoutLoggerUiState,
    onEditSet: (WorkoutHistorySet) -> Unit,
    onLoggedSetRepsChanged: (String) -> Unit,
    onLoggedSetDurationChanged: (String) -> Unit,
    onLoggedSetDistanceChanged: (String) -> Unit,
    onLoggedSetForceChanged: (String) -> Unit,
    onLoggedSetDeviceNameChanged: (String) -> Unit,
    onLoggedSetDeviceRatingChanged: (String) -> Unit,
    onLoggedSetMetricChanged: (SetMetric) -> Unit,
    onLoggedSetWeightChanged: (String) -> Unit,
    onLoggedSetRirChanged: (String) -> Unit,
    onLoggedSetTimeChanged: (Long) -> Boolean,
    onCancelLoggedSetEdit: () -> Unit,
    onSaveLoggedSetEdit: () -> Unit,
    onRetryDetail: () -> Unit,
    onLoadMoreSets: () -> Unit,
    onRetryExerciseNote: (String) -> Unit,
    onBack: () -> Unit,
    nowMillis: () -> Long,
    modifier: Modifier = Modifier
) {
    val entry = state.detailEntry
    // One setup note per exercise actually present in the loaded page, so the read stays bounded.
    val noteExerciseIds = state.sets.map { it.exerciseId }.distinct()

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Text(
                text = stringResource(
                    if (state.selectedKey is WorkoutHistoryKey.UnassignedSet) {
                        Res.string.logger_history_detail_unassigned
                    } else {
                        Res.string.logger_history_detail_session
                    }
                ),
                style = MaterialTheme.typography.headlineSmall
            )
        }
        if (state.showDetailUnavailable) {
            item {
                Text(
                    text = stringResource(Res.string.logger_history_detail_unavailable),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        } else if (state.detailFailed) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = stringResource(Res.string.logger_history_failed),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    TextButton(onClick = onRetryDetail) {
                        Text(stringResource(Res.string.logger_note_retry))
                    }
                }
            }
        } else if (entry != null) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(text = entry.kindLabel(), style = MaterialTheme.typography.titleMedium)
                    val parts = localDateTimeParts(entry.timestampMillis, state.utcOffsetMillis)
                    Text(
                        text = stringResource(
                            Res.string.logger_recorded_at,
                            localDateLabel(parts),
                            localTimeLabel(parts)
                        ),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = stringResource(
                            Res.string.logger_history_set_count,
                            entry.recordedSetCount
                        ),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            if (state.selectedKey is WorkoutHistoryKey.Session) {
                val sessionNote = entry.note
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = stringResource(Res.string.logger_history_session_note_title),
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = when {
                                sessionNote == null ->
                                    stringResource(Res.string.logger_history_note_absent)

                                sessionNote.isEmpty() ->
                                    stringResource(Res.string.logger_history_note_cleared)

                                else -> sessionNote
                            },
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
            item {
                Text(
                    text = stringResource(Res.string.logger_history_sets_title),
                    style = MaterialTheme.typography.titleMedium
                )
            }
            if (state.setsLoading) {
                item {
                    Text(
                        text = stringResource(Res.string.logger_history_sets_loading),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            } else if (state.setsFailed) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = stringResource(Res.string.logger_history_sets_failed),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        TextButton(onClick = onRetryDetail) {
                            Text(stringResource(Res.string.logger_note_retry))
                        }
                    }
                }
            } else if (state.showNoRecordedSets) {
                item {
                    Text(
                        text = stringResource(Res.string.logger_history_sets_none),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            } else {
                items(items = state.sets, key = { "history-set:${it.id}" }) { set ->
                    WorkoutHistorySetRow(
                        set = set,
                        utcOffsetMillis = state.utcOffsetMillis,
                        loadText = loadWeightText(set.loadKind, set.weightKg, state.weightUnit),
                        distanceUnit = state.distanceUnit,
                        editing = loggerState.loggedSetEdit?.row?.id == set.id,
                        onEdit = { onEditSet(set) }
                    )
                }
            }
            if (state.hasMoreSets || state.loadMoreSetsFailed) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (state.loadMoreSetsFailed) {
                            Text(
                                text = stringResource(Res.string.logger_history_load_more_failed),
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        TextButton(onClick = onLoadMoreSets, enabled = !state.loadingMoreSets) {
                            Text(
                                stringResource(
                                    if (state.loadMoreSetsFailed) {
                                        Res.string.logger_note_retry
                                    } else {
                                        Res.string.logger_history_load_more
                                    }
                                )
                            )
                        }
                    }
                }
            }
            if (noteExerciseIds.isNotEmpty()) {
                item {
                    Text(
                        text = stringResource(Res.string.logger_history_setup_note_title),
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                item {
                    Text(
                        text = stringResource(Res.string.logger_history_setup_note_current),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                items(items = noteExerciseIds, key = { "setup-note:$it" }) { exerciseId ->
                    val name = state.sets.firstOrNull { it.exerciseId == exerciseId }
                        ?.exerciseName
                        ?: stringResource(Res.string.logger_history_exercise_missing)
                    val note = state.exerciseNotes[exerciseId]
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(text = name, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            text = when {
                                note == null -> stringResource(
                                    Res.string.logger_history_sets_loading
                                )
                                note.loadFailed ->
                                    stringResource(Res.string.logger_history_setup_note_failed)

                                note.note == null ->
                                    stringResource(Res.string.logger_history_setup_note_none)

                                note.note.isEmpty() ->
                                    stringResource(Res.string.logger_history_setup_note_cleared)

                                else -> note.note
                            },
                            style = MaterialTheme.typography.bodyMedium
                        )
                        if (note?.loadFailed == true) {
                            TextButton(onClick = { onRetryExerciseNote(exerciseId) }) {
                                Text(stringResource(Res.string.logger_note_retry))
                            }
                        }
                    }
                }
            }
        }
        item {
            OutlinedButton(onClick = onBack) {
                Text(stringResource(Res.string.logger_history_back))
            }
        }
    }

    loggerState.loggedSetEdit?.let { edit ->
        LoggedSetEditDialog(
            edit = edit,
            saving = loggerState.savingLoggedSet,
            failed = loggerState.loggedSetEditFailed,
            utcOffsetMillis = state.utcOffsetMillis,
            nowMillis = nowMillis,
            onRepsChanged = onLoggedSetRepsChanged,
            onDurationChanged = onLoggedSetDurationChanged,
            onDistanceChanged = onLoggedSetDistanceChanged,
            onForceChanged = onLoggedSetForceChanged,
            onDeviceNameChanged = onLoggedSetDeviceNameChanged,
            onDeviceRatingChanged = onLoggedSetDeviceRatingChanged,
            onMetricChanged = onLoggedSetMetricChanged,
            onWeightChanged = onLoggedSetWeightChanged,
            onRirChanged = onLoggedSetRirChanged,
            onTimeChanged = onLoggedSetTimeChanged,
            onCancel = onCancelLoggedSetEdit,
            onSave = onSaveLoggedSetEdit
        )
    }
}

@Composable
private fun WorkoutHistorySetRow(
    set: WorkoutHistorySet,
    utcOffsetMillis: Long,
    loadText: String,
    distanceUnit: DistanceUnit,
    editing: Boolean,
    onEdit: () -> Unit
) {
    val parts = localDateTimeParts(set.performedAtMillis, utcOffsetMillis)
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = set.exerciseName
                    ?: stringResource(Res.string.logger_history_exercise_missing),
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = stringResource(
                    if (set.isWarmup) {
                        Res.string.logger_history_warmup
                    } else {
                        Res.string.logger_history_working
                    }
                ),
                style = MaterialTheme.typography.bodySmall
            )
        }
        val durationSeconds = set.durationSeconds
        val reps = set.reps
        val distanceMeters = set.distanceMeters
        val forceNewtons = set.forceNewtons
        Text(
            text = when {
                // A force reading is its own measure: it is never shown as a load or a lifted mass.
                forceNewtons != null -> stringResource(
                    Res.string.logger_history_set_force_line,
                    stringResource(
                        Res.string.logger_force_value,
                        formatWeight(forceNewtons)
                    )
                )

                distanceMeters != null -> stringResource(
                    Res.string.logger_history_set_distance_line,
                    formatDistance(distanceMeters, distanceUnit),
                    loadText
                )

                durationSeconds != null -> stringResource(
                    Res.string.logger_history_set_duration_line,
                    durationSeconds,
                    loadText
                )

                reps != null -> stringResource(Res.string.logger_history_set_line, reps, loadText)

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
        set.station?.let { station ->
            Text(
                text = stringResource(Res.string.logger_station_attributed, station.name),
                style = MaterialTheme.typography.bodySmall
            )
        }
        set.rir?.let { rir ->
            Text(
                text = stringResource(Res.string.logger_history_rir, rir),
                style = MaterialTheme.typography.bodyMedium
            )
        }
        TextButton(onClick = onEdit, enabled = !editing) {
            Text(stringResource(Res.string.logger_edit_set))
        }
        Text(
            text = stringResource(
                Res.string.logger_recorded_at,
                localDateLabel(parts),
                localTimeLabel(parts)
            ),
            style = MaterialTheme.typography.bodySmall
        )
        Text(
            text = stringResource(set.timingProvenance.labelResource()),
            style = MaterialTheme.typography.bodySmall
        )
    }
}

internal fun WorkoutTimingProvenance.labelResource(): StringResource = when (this) {
    WorkoutTimingProvenance.LIVE -> Res.string.logger_history_timing_live
    WorkoutTimingProvenance.CATCH_UP -> Res.string.logger_history_timing_catch_up
    WorkoutTimingProvenance.UNKNOWN -> Res.string.logger_history_timing_unknown
}
