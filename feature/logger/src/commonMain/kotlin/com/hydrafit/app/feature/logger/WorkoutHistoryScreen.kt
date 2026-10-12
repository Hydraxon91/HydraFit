package com.hydrafit.app.feature.logger

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hydrafit.app.core.domain.workout.WorkoutHistoryEntry
import com.hydrafit.app.core.domain.workout.WorkoutHistoryKey
import hydrafit.feature.logger.generated.resources.Res
import hydrafit.feature.logger.generated.resources.logger_cancel
import hydrafit.feature.logger.generated.resources.logger_confirm
import hydrafit.feature.logger.generated.resources.logger_history_back
import hydrafit.feature.logger.generated.resources.logger_history_clear_date
import hydrafit.feature.logger.generated.resources.logger_history_clear_filters
import hydrafit.feature.logger.generated.resources.logger_history_date_from
import hydrafit.feature.logger.generated.resources.logger_history_date_from_none
import hydrafit.feature.logger.generated.resources.logger_history_date_to
import hydrafit.feature.logger.generated.resources.logger_history_date_to_none
import hydrafit.feature.logger.generated.resources.logger_history_empty
import hydrafit.feature.logger.generated.resources.logger_history_failed
import hydrafit.feature.logger.generated.resources.logger_history_invalid_range
import hydrafit.feature.logger.generated.resources.logger_history_load_more
import hydrafit.feature.logger.generated.resources.logger_history_load_more_failed
import hydrafit.feature.logger.generated.resources.logger_history_loading
import hydrafit.feature.logger.generated.resources.logger_history_no_matches
import hydrafit.feature.logger.generated.resources.logger_history_no_sets
import hydrafit.feature.logger.generated.resources.logger_history_note_absent
import hydrafit.feature.logger.generated.resources.logger_history_note_cleared
import hydrafit.feature.logger.generated.resources.logger_history_note_present
import hydrafit.feature.logger.generated.resources.logger_history_open_entry
import hydrafit.feature.logger.generated.resources.logger_history_pick_date_title
import hydrafit.feature.logger.generated.resources.logger_history_search_action
import hydrafit.feature.logger.generated.resources.logger_history_search_label
import hydrafit.feature.logger.generated.resources.logger_history_session_missing
import hydrafit.feature.logger.generated.resources.logger_history_set_count
import hydrafit.feature.logger.generated.resources.logger_history_title
import hydrafit.feature.logger.generated.resources.logger_history_unassigned_set
import hydrafit.feature.logger.generated.resources.logger_note_retry
import hydrafit.feature.logger.generated.resources.logger_recorded_at
import hydrafit.feature.logger.generated.resources.logger_session_recorded
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun WorkoutHistoryRoute(
    onEntrySelected: (WorkoutHistoryKey) -> Unit,
    onBack: () -> Unit,
    viewModel: WorkoutHistoryViewModel
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    WorkoutHistoryScreen(
        state = state,
        onSearchDraftChanged = viewModel::onSearchDraftChanged,
        onApplySearch = viewModel::onApplySearch,
        onDayFromChanged = viewModel::onDayFromChanged,
        onDayToChanged = viewModel::onDayToChanged,
        onClearDates = viewModel::onClearDates,
        onClearFilters = viewModel::onClearFilters,
        onRetry = viewModel::onRetry,
        onLoadMore = viewModel::onLoadMore,
        onEntrySelected = { entry -> onEntrySelected(entry.key) },
        onBack = onBack
    )
}

/** A stable list key from the entry's explicit identity; identities are never inferred. */
internal fun WorkoutHistoryEntry.historyRowKey(): String = when (val identity = key) {
    is WorkoutHistoryKey.Session -> "session:${identity.sessionId}"
    is WorkoutHistoryKey.UnassignedSet -> "set:${identity.setId}"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutHistoryScreen(
    state: WorkoutHistoryUiState,
    onSearchDraftChanged: (String) -> Unit,
    onApplySearch: () -> Unit,
    onDayFromChanged: (Long?) -> Unit,
    onDayToChanged: (Long?) -> Unit,
    onClearDates: () -> Unit,
    onClearFilters: () -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    onEntrySelected: (WorkoutHistoryEntry) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var pickingDayFrom by remember { mutableStateOf(false) }
    var pickingDayTo by remember { mutableStateOf(false) }
    val openEntryLabel = stringResource(Res.string.logger_history_open_entry)

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Text(
                text = stringResource(Res.string.logger_history_title),
                style = MaterialTheme.typography.headlineSmall
            )
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                OutlinedTextField(
                    value = state.searchDraft,
                    onValueChange = onSearchDraftChanged,
                    label = { Text(stringResource(Res.string.logger_history_search_label)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { onApplySearch() }),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onApplySearch) {
                        Text(stringResource(Res.string.logger_history_search_action))
                    }
                    TextButton(
                        onClick = onClearFilters,
                        enabled = state.filter.hasFilters || state.searchDraft.isNotEmpty()
                    ) {
                        Text(stringResource(Res.string.logger_history_clear_filters))
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { pickingDayFrom = true }) {
                        Text(dayBoundLabel(state.filter.dayFrom, from = true))
                    }
                    TextButton(onClick = { pickingDayTo = true }) {
                        Text(dayBoundLabel(state.filter.dayTo, from = false))
                    }
                    if (state.filter.dayFrom != null || state.filter.dayTo != null) {
                        TextButton(onClick = onClearDates) {
                            Text(stringResource(Res.string.logger_history_clear_date))
                        }
                    }
                }
            }
        }
        if (state.filter.hasInvalidDayRange) {
            item {
                Text(
                    text = stringResource(Res.string.logger_history_invalid_range),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
        when {
            state.loading -> item {
                Text(
                    text = stringResource(Res.string.logger_history_loading),
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            state.failed -> item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = stringResource(Res.string.logger_history_failed),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    TextButton(onClick = onRetry) {
                        Text(stringResource(Res.string.logger_note_retry))
                    }
                }
            }

            state.showEmptyHistory -> item {
                Text(
                    text = stringResource(Res.string.logger_history_empty),
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            state.showNoMatches -> item {
                Text(
                    text = stringResource(Res.string.logger_history_no_matches),
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            else -> items(items = state.entries, key = { it.historyRowKey() }) { entry ->
                WorkoutHistoryRow(
                    entry = entry,
                    utcOffsetMillis = state.utcOffsetMillis,
                    openLabel = openEntryLabel,
                    onClick = { onEntrySelected(entry) }
                )
            }
        }
        if (state.hasMore || state.loadMoreFailed) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (state.loadMoreFailed) {
                        Text(
                            text = stringResource(Res.string.logger_history_load_more_failed),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    TextButton(onClick = onLoadMore, enabled = !state.loadingMore) {
                        Text(
                            stringResource(
                                if (state.loadMoreFailed) {
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
        item {
            OutlinedButton(onClick = onBack) {
                Text(stringResource(Res.string.logger_history_back))
            }
        }
    }

    if (pickingDayFrom) {
        HistoryDatePickerDialog(
            initialEpochDay = state.filter.dayFrom,
            onDismiss = { pickingDayFrom = false },
            onPicked = { epochDay ->
                pickingDayFrom = false
                onDayFromChanged(epochDay)
            }
        )
    }
    if (pickingDayTo) {
        HistoryDatePickerDialog(
            initialEpochDay = state.filter.dayTo,
            onDismiss = { pickingDayTo = false },
            onPicked = { epochDay ->
                pickingDayTo = false
                onDayToChanged(epochDay)
            }
        )
    }
}

@Composable
private fun dayBoundLabel(epochDay: Long?, from: Boolean): String {
    if (epochDay == null) {
        return stringResource(
            if (from) {
                Res.string.logger_history_date_from_none
            } else {
                Res.string.logger_history_date_to_none
            }
        )
    }
    val parts = localDateTimeParts(epochDay * MILLIS_PER_DAY, utcOffsetMillis = 0L)
    val date = localDateLabel(parts)
    return stringResource(
        if (from) Res.string.logger_history_date_from else Res.string.logger_history_date_to,
        date
    )
}

@Composable
private fun WorkoutHistoryRow(
    entry: WorkoutHistoryEntry,
    utcOffsetMillis: Long,
    openLabel: String,
    onClick: () -> Unit
) {
    val parts = localDateTimeParts(entry.timestampMillis, utcOffsetMillis)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = openLabel, onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = entry.kindLabel(),
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            text = stringResource(
                Res.string.logger_recorded_at,
                localDateLabel(parts),
                localTimeLabel(parts)
            ),
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            text = if (entry.recordedSetCount > 0) {
                stringResource(Res.string.logger_history_set_count, entry.recordedSetCount)
            } else {
                stringResource(Res.string.logger_history_no_sets)
            },
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            text = stringResource(entry.noteState().labelResource()),
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
internal fun WorkoutHistoryEntry.kindLabel(): String = when (key) {
    is WorkoutHistoryKey.UnassignedSet -> stringResource(Res.string.logger_history_unassigned_set)
    is WorkoutHistoryKey.Session -> if (sessionMissing) {
        stringResource(Res.string.logger_history_session_missing)
    } else {
        stringResource(Res.string.logger_session_recorded)
    }
}

/** The list shows the note's stored state; the full text belongs to the entry's details. */
internal fun WorkoutHistoryEntry.noteState(): HistoryNoteState = noteStateOf(note)

internal fun HistoryNoteState.labelResource(): StringResource = when (this) {
    HistoryNoteState.ABSENT -> Res.string.logger_history_note_absent
    HistoryNoteState.CLEARED -> Res.string.logger_history_note_cleared
    HistoryNoteState.PRESENT -> Res.string.logger_history_note_present
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HistoryDatePickerDialog(
    initialEpochDay: Long?,
    onDismiss: () -> Unit,
    onPicked: (Long) -> Unit
) {
    val picker = rememberDatePickerState(
        initialSelectedDateMillis = initialEpochDay?.let { it * MILLIS_PER_DAY }
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    picker.selectedDateMillis?.let { picked -> onPicked(picked / MILLIS_PER_DAY) }
                },
                enabled = picker.selectedDateMillis != null
            ) {
                Text(stringResource(Res.string.logger_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.logger_cancel))
            }
        }
    ) {
        DatePicker(state = picker, title = {
            Text(stringResource(Res.string.logger_history_pick_date_title))
        })
    }
}
