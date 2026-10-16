package com.hydrafit.app.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hydrafit.app.core.domain.transfer.WorkoutCsvFailure
import com.hydrafit.app.core.domain.transfer.WorkoutCsvSource
import com.hydrafit.app.core.domain.transfer.WorkoutImportCommitFailure
import com.hydrafit.app.core.domain.transfer.WorkoutImportIssue
import com.hydrafit.app.core.domain.workout.LoadKind
import hydrafit.feature.settings.generated.resources.Res
import hydrafit.feature.settings.generated.resources.settings_workout_import_acknowledge_history
import hydrafit.feature.settings.generated.resources.settings_workout_import_acknowledge_metadata
import hydrafit.feature.settings.generated.resources.settings_workout_import_apply
import hydrafit.feature.settings.generated.resources.settings_workout_import_commit_not_importable
import hydrafit.feature.settings.generated.resources.settings_workout_import_commit_nothing_to_import
import hydrafit.feature.settings.generated.resources.settings_workout_import_commit_session_overlap
import hydrafit.feature.settings.generated.resources.settings_workout_import_commit_source_changed
import hydrafit.feature.settings.generated.resources.settings_workout_import_confirm_load
import hydrafit.feature.settings.generated.resources.settings_workout_import_description
import hydrafit.feature.settings.generated.resources.settings_workout_import_error_duplicate_header
import hydrafit.feature.settings.generated.resources.settings_workout_import_error_empty
import hydrafit.feature.settings.generated.resources.settings_workout_import_error_field_too_long
import hydrafit.feature.settings.generated.resources.settings_workout_import_error_malformed
import hydrafit.feature.settings.generated.resources.settings_workout_import_error_too_large
import hydrafit.feature.settings.generated.resources.settings_workout_import_error_too_many_columns
import hydrafit.feature.settings.generated.resources.settings_workout_import_error_too_many_rows
import hydrafit.feature.settings.generated.resources.settings_workout_import_error_unknown
import hydrafit.feature.settings.generated.resources.settings_workout_import_error_unsupported_header
import hydrafit.feature.settings.generated.resources.settings_workout_import_exclude_row
import hydrafit.feature.settings.generated.resources.settings_workout_import_excluded
import hydrafit.feature.settings.generated.resources.settings_workout_import_load_added
import hydrafit.feature.settings.generated.resources.settings_workout_import_load_bodyweight
import hydrafit.feature.settings.generated.resources.settings_workout_import_load_confirmed
import hydrafit.feature.settings.generated.resources.settings_workout_import_load_external
import hydrafit.feature.settings.generated.resources.settings_workout_import_load_kind
import hydrafit.feature.settings.generated.resources.settings_workout_import_load_legacy
import hydrafit.feature.settings.generated.resources.settings_workout_import_mapping_ambiguous
import hydrafit.feature.settings.generated.resources.settings_workout_import_mapping_none
import hydrafit.feature.settings.generated.resources.settings_workout_import_mapping_rows
import hydrafit.feature.settings.generated.resources.settings_workout_import_mapping_suggested
import hydrafit.feature.settings.generated.resources.settings_workout_import_mapping_title
import hydrafit.feature.settings.generated.resources.settings_workout_import_mapping_unmatched
import hydrafit.feature.settings.generated.resources.settings_workout_import_no_time
import hydrafit.feature.settings.generated.resources.settings_workout_import_no_weight
import hydrafit.feature.settings.generated.resources.settings_workout_import_notice_removed_history
import hydrafit.feature.settings.generated.resources.settings_workout_import_notice_unchanged_source
import hydrafit.feature.settings.generated.resources.settings_workout_import_notices_title
import hydrafit.feature.settings.generated.resources.settings_workout_import_problem_changed_or_ambiguous_source
import hydrafit.feature.settings.generated.resources.settings_workout_import_problem_invalid_load
import hydrafit.feature.settings.generated.resources.settings_workout_import_problem_invalid_offset
import hydrafit.feature.settings.generated.resources.settings_workout_import_problem_invalid_session_membership
import hydrafit.feature.settings.generated.resources.settings_workout_import_problem_invalid_time
import hydrafit.feature.settings.generated.resources.settings_workout_import_problem_missing_exercise
import hydrafit.feature.settings.generated.resources.settings_workout_import_problem_row
import hydrafit.feature.settings.generated.resources.settings_workout_import_problem_session_overlap
import hydrafit.feature.settings.generated.resources.settings_workout_import_problem_unacknowledged_history_effects
import hydrafit.feature.settings.generated.resources.settings_workout_import_problem_unacknowledged_metadata
import hydrafit.feature.settings.generated.resources.settings_workout_import_problem_unconfirmed_load
import hydrafit.feature.settings.generated.resources.settings_workout_import_problem_unconfirmed_session
import hydrafit.feature.settings.generated.resources.settings_workout_import_problem_unknown_warmup
import hydrafit.feature.settings.generated.resources.settings_workout_import_problem_unresolved_exclusion
import hydrafit.feature.settings.generated.resources.settings_workout_import_problems_title
import hydrafit.feature.settings.generated.resources.settings_workout_import_provider_fitnotes
import hydrafit.feature.settings.generated.resources.settings_workout_import_provider_hevy
import hydrafit.feature.settings.generated.resources.settings_workout_import_provider_section
import hydrafit.feature.settings.generated.resources.settings_workout_import_provider_strong
import hydrafit.feature.settings.generated.resources.settings_workout_import_rejected_title
import hydrafit.feature.settings.generated.resources.settings_workout_import_reset
import hydrafit.feature.settings.generated.resources.settings_workout_import_review_title
import hydrafit.feature.settings.generated.resources.settings_workout_import_row_session
import hydrafit.feature.settings.generated.resources.settings_workout_import_section
import hydrafit.feature.settings.generated.resources.settings_workout_import_session_confirm
import hydrafit.feature.settings.generated.resources.settings_workout_import_session_confirmed
import hydrafit.feature.settings.generated.resources.settings_workout_import_session_label
import hydrafit.feature.settings.generated.resources.settings_workout_import_session_new
import hydrafit.feature.settings.generated.resources.settings_workout_import_session_offset
import hydrafit.feature.settings.generated.resources.settings_workout_import_session_rows
import hydrafit.feature.settings.generated.resources.settings_workout_import_session_time
import hydrafit.feature.settings.generated.resources.settings_workout_import_session_title
import hydrafit.feature.settings.generated.resources.settings_workout_import_session_untitled
import hydrafit.feature.settings.generated.resources.settings_workout_import_session_workout
import hydrafit.feature.settings.generated.resources.settings_workout_import_summary
import hydrafit.feature.settings.generated.resources.settings_workout_import_unknown_set_type
import hydrafit.feature.settings.generated.resources.settings_workout_import_unknown_warmup
import hydrafit.feature.settings.generated.resources.settings_workout_import_unsupported
import hydrafit.feature.settings.generated.resources.settings_workout_import_use_source_weight
import hydrafit.feature.settings.generated.resources.settings_workout_import_warmup
import hydrafit.feature.settings.generated.resources.settings_workout_import_working
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun WorkoutImportRoute(
    modifier: Modifier = Modifier,
    viewModel: WorkoutImportViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    WorkoutImportScreen(
        state = state,
        onProviderSelected = viewModel::selectProvider,
        onFileChosen = viewModel::chooseFile,
        onMappingSelected = viewModel::setMapping,
        onLoadKindSelected = viewModel::setLoadKind,
        onUseSourceWeightChanged = viewModel::setUseSourceWeight,
        onConfirmLoadMeaning = viewModel::confirmLoadMeaning,
        onUnknownWarmupChanged = viewModel::setUnknownWarmupIsWorking,
        onSessionOffsetChanged = viewModel::setSessionOffset,
        onSessionTimeChanged = viewModel::setSessionTime,
        onConfirmSession = viewModel::confirmSession,
        onRowSessionSelected = viewModel::setRowSession,
        onToggleRowIncluded = viewModel::toggleRowIncluded,
        onAcknowledgeUnsupportedFields = viewModel::acknowledgeUnsupportedFields,
        onAcknowledgeHistoryEffects = viewModel::acknowledgeHistoryEffects,
        onApply = viewModel::apply,
        onReset = viewModel::reset,
        modifier = modifier
    )
}

@Composable
fun WorkoutImportScreen(
    state: WorkoutImportUiState,
    onProviderSelected: (WorkoutCsvSource) -> Unit,
    onFileChosen: (String) -> Unit,
    onMappingSelected: (String, String?) -> Unit,
    onLoadKindSelected: (String, LoadKind) -> Unit,
    onUseSourceWeightChanged: (String, Boolean) -> Unit,
    onConfirmLoadMeaning: (String) -> Unit,
    onUnknownWarmupChanged: (String, Boolean) -> Unit,
    onSessionOffsetChanged: (Int, String) -> Unit,
    onSessionTimeChanged: (Int, Int?) -> Unit,
    onConfirmSession: (Int, Boolean) -> Unit,
    onRowSessionSelected: (Int, Int?) -> Unit,
    onToggleRowIncluded: (Int) -> Unit,
    onAcknowledgeUnsupportedFields: (Boolean) -> Unit,
    onAcknowledgeHistoryEffects: (Boolean) -> Unit,
    onApply: () -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth().guidanceTestTagsAsResourceId(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = stringResource(Res.string.settings_workout_import_section),
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            text = stringResource(Res.string.settings_workout_import_description),
            style = MaterialTheme.typography.bodySmall
        )
        Text(
            text = stringResource(Res.string.settings_workout_import_provider_section),
            style = MaterialTheme.typography.titleSmall
        )
        WorkoutCsvSource.entries.forEach { provider ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(provider.testTag())
                    .selectable(
                        selected = state.provider == provider,
                        onClick = { onProviderSelected(provider) }
                    ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = state.provider == provider,
                    onClick = { onProviderSelected(provider) }
                )
                Text(stringResource(provider.labelResource()))
            }
        }
        if (state.isSupported) {
            WorkoutImportSection(
                isSupported = state.isSupported,
                busy = state.inProgress,
                onFileChosen = onFileChosen
            )
        } else {
            Text(
                text = stringResource(Res.string.settings_workout_import_unsupported),
                style = MaterialTheme.typography.bodySmall
            )
        }
        when (val phase = state.phase) {
            WorkoutImportPhase.Idle -> Unit
            is WorkoutImportPhase.Reviewing -> WorkoutImportReviewSection(
                review = phase.review,
                inProgress = state.inProgress,
                onMappingSelected = onMappingSelected,
                onLoadKindSelected = onLoadKindSelected,
                onUseSourceWeightChanged = onUseSourceWeightChanged,
                onConfirmLoadMeaning = onConfirmLoadMeaning,
                onUnknownWarmupChanged = onUnknownWarmupChanged,
                onSessionOffsetChanged = onSessionOffsetChanged,
                onSessionTimeChanged = onSessionTimeChanged,
                onConfirmSession = onConfirmSession,
                onRowSessionSelected = onRowSessionSelected,
                onToggleRowIncluded = onToggleRowIncluded,
                onAcknowledgeUnsupportedFields = onAcknowledgeUnsupportedFields,
                onAcknowledgeHistoryEffects = onAcknowledgeHistoryEffects,
                onApply = onApply
            )

            is WorkoutImportPhase.Imported -> Column(
                modifier = Modifier.testTag(WorkoutImportTestTags.SUMMARY),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = stringResource(
                        Res.string.settings_workout_import_summary,
                        phase.result.importedSessions,
                        phase.result.importedSets,
                        phase.result.skippedSessions
                    )
                )
                OutlinedButton(
                    onClick = onReset,
                    modifier = Modifier.testTag(WorkoutImportTestTags.RESET)
                ) {
                    Text(stringResource(Res.string.settings_workout_import_reset))
                }
            }

            is WorkoutImportPhase.Failed -> Text(
                text = stringResource(phase.failure.messageResource()),
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun WorkoutImportReviewSection(
    review: WorkoutImportReview,
    inProgress: Boolean,
    onMappingSelected: (String, String?) -> Unit,
    onLoadKindSelected: (String, LoadKind) -> Unit,
    onUseSourceWeightChanged: (String, Boolean) -> Unit,
    onConfirmLoadMeaning: (String) -> Unit,
    onUnknownWarmupChanged: (String, Boolean) -> Unit,
    onSessionOffsetChanged: (Int, String) -> Unit,
    onSessionTimeChanged: (Int, Int?) -> Unit,
    onConfirmSession: (Int, Boolean) -> Unit,
    onRowSessionSelected: (Int, Int?) -> Unit,
    onToggleRowIncluded: (Int) -> Unit,
    onAcknowledgeUnsupportedFields: (Boolean) -> Unit,
    onAcknowledgeHistoryEffects: (Boolean) -> Unit,
    onApply: () -> Unit
) {
    review.commitFailure?.let { failure ->
        Text(
            text = stringResource(failure.messageResource()),
            style = MaterialTheme.typography.bodySmall
        )
    }
    Text(
        text = stringResource(Res.string.settings_workout_import_review_title),
        style = MaterialTheme.typography.titleSmall
    )
    review.rows.forEach { row ->
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .toggleable(
                        value = row.included,
                        role = Role.Checkbox,
                        onValueChange = { onToggleRowIncluded(row.sourceRowIndex) }
                    ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(checked = row.included, onCheckedChange = null)
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text("${row.exerciseName} — ${row.dayLabel}")
                    Text(
                        text = row.detailText(),
                        style = MaterialTheme.typography.bodySmall
                    )
                    row.diagnostics.forEach { diagnostic ->
                        Text(diagnostic.message, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            if (row.included && row.sessionDraftId != null && row.sessionOptions.isNotEmpty()) {
                RowSessionSelector(row = row, onSessionSelected = onRowSessionSelected)
            }
        }
    }
    if (review.rejectedRows.isNotEmpty()) {
        Text(
            text = stringResource(Res.string.settings_workout_import_rejected_title),
            style = MaterialTheme.typography.titleSmall
        )
        review.rejectedRows.forEach { row ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .toggleable(
                        value = row.excluded,
                        role = Role.Checkbox,
                        onValueChange = { onToggleRowIncluded(row.sourceRowIndex) }
                    ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(checked = row.excluded, onCheckedChange = null)
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = if (row.excluded) {
                            stringResource(Res.string.settings_workout_import_excluded)
                        } else {
                            stringResource(
                                Res.string.settings_workout_import_exclude_row,
                                row.sourceRowIndex
                            )
                        }
                    )
                    row.diagnostics.forEach { diagnostic ->
                        Text(diagnostic.message, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
    if (review.mappings.isNotEmpty()) {
        Text(
            text = stringResource(Res.string.settings_workout_import_mapping_title),
            style = MaterialTheme.typography.titleSmall
        )
        review.mappings.forEach { mapping ->
            MappingEditor(
                mapping = mapping,
                catalogOptions = review.catalogOptions,
                onMappingSelected = onMappingSelected,
                onLoadKindSelected = onLoadKindSelected,
                onUseSourceWeightChanged = onUseSourceWeightChanged,
                onConfirmLoadMeaning = onConfirmLoadMeaning,
                onUnknownWarmupChanged = onUnknownWarmupChanged
            )
        }
    }
    if (review.sessions.isNotEmpty()) {
        Text(
            text = stringResource(Res.string.settings_workout_import_session_title),
            style = MaterialTheme.typography.titleSmall
        )
        review.sessions.forEach { session ->
            SessionEditor(
                session = session,
                onSessionOffsetChanged = onSessionOffsetChanged,
                onSessionTimeChanged = onSessionTimeChanged,
                onConfirmSession = onConfirmSession
            )
        }
    }
    CheckRow(
        checked = review.acknowledgedUnsupportedFields,
        label = stringResource(Res.string.settings_workout_import_acknowledge_metadata),
        onCheckedChange = onAcknowledgeUnsupportedFields
    )
    CheckRow(
        checked = review.acknowledgedHistoryEffects,
        label = stringResource(Res.string.settings_workout_import_acknowledge_history),
        onCheckedChange = onAcknowledgeHistoryEffects
    )
    if (review.notices.isNotEmpty()) {
        Text(
            text = stringResource(Res.string.settings_workout_import_notices_title),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.testTag(WorkoutImportTestTags.NOTICES)
        )
        review.notices.forEach { notice ->
            ProblemText(
                label = stringResource(notice.issue.messageResource()),
                row = notice.sourceRow
            )
        }
    }
    if (review.problems.isNotEmpty()) {
        Text(
            text = stringResource(Res.string.settings_workout_import_problems_title),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.testTag(WorkoutImportTestTags.PROBLEMS)
        )
        review.problems.forEach { problem ->
            ProblemLine(problem)
        }
    }
    Button(
        onClick = onApply,
        enabled = review.canImport && !inProgress,
        modifier = Modifier.testTag(WorkoutImportTestTags.APPLY)
    ) {
        Text(stringResource(Res.string.settings_workout_import_apply))
    }
}

@Composable
private fun MappingEditor(
    mapping: MappingUi,
    catalogOptions: List<MappingCandidateUi>,
    onMappingSelected: (String, String?) -> Unit,
    onLoadKindSelected: (String, LoadKind) -> Unit,
    onUseSourceWeightChanged: (String, Boolean) -> Unit,
    onConfirmLoadMeaning: (String) -> Unit,
    onUnknownWarmupChanged: (String, Boolean) -> Unit
) {
    var expanded by remember(mapping.exerciseName) { mutableStateOf(false) }
    val chosenLabel = catalogOptions.firstOrNull {
        it.exerciseId == mapping.chosenExerciseId
    }?.label
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = "${mapping.exerciseName} · " +
                stringResource(
                    Res.string.settings_workout_import_mapping_rows,
                    mapping.occurrenceCount
                )
        )
        Text(
            text = when (val state = mapping.state) {
                is MappingState.Suggested ->
                    stringResource(
                        Res.string.settings_workout_import_mapping_suggested,
                        state.label
                    )

                is MappingState.Ambiguous -> stringResource(
                    Res.string.settings_workout_import_mapping_ambiguous,
                    state.labels.joinToString(", ")
                )

                MappingState.Unmatched ->
                    stringResource(Res.string.settings_workout_import_mapping_unmatched)
            },
            style = MaterialTheme.typography.bodySmall
        )
        Column {
            OutlinedButton(onClick = { expanded = true }) {
                Text(chosenLabel ?: stringResource(Res.string.settings_workout_import_mapping_none))
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                if (mapping.chosenExerciseId != null) {
                    DropdownMenuItem(
                        text = {
                            Text(stringResource(Res.string.settings_workout_import_mapping_none))
                        },
                        onClick = {
                            expanded = false
                            onMappingSelected(mapping.exerciseName, null)
                        }
                    )
                }
                catalogOptions.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option.label) },
                        onClick = {
                            expanded = false
                            onMappingSelected(mapping.exerciseName, option.exerciseId)
                        }
                    )
                }
            }
        }
        if (mapping.chosenExerciseId != null) {
            Text(
                text = stringResource(Res.string.settings_workout_import_load_kind),
                style = MaterialTheme.typography.bodySmall
            )
            LoadKind.entries.forEach { kind ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(
                            selected = mapping.loadKind == kind,
                            onClick = { onLoadKindSelected(mapping.exerciseName, kind) }
                        ),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = mapping.loadKind == kind,
                        onClick = { onLoadKindSelected(mapping.exerciseName, kind) }
                    )
                    Text(stringResource(kind.labelResource()))
                }
            }
            CheckRow(
                checked = mapping.useSourceWeight,
                label = stringResource(Res.string.settings_workout_import_use_source_weight),
                onCheckedChange = { onUseSourceWeightChanged(mapping.exerciseName, it) }
            )
            if (mapping.confirmedLoadMeaning) {
                Text(
                    text = stringResource(Res.string.settings_workout_import_load_confirmed),
                    style = MaterialTheme.typography.bodySmall
                )
            } else {
                OutlinedButton(onClick = { onConfirmLoadMeaning(mapping.exerciseName) }) {
                    Text(stringResource(Res.string.settings_workout_import_confirm_load))
                }
            }
            CheckRow(
                checked = mapping.unknownWarmupIsWorking,
                label = stringResource(Res.string.settings_workout_import_unknown_warmup),
                onCheckedChange = { onUnknownWarmupChanged(mapping.exerciseName, it) }
            )
        }
    }
}

@Composable
private fun SessionEditor(
    session: SessionDraftUi,
    onSessionOffsetChanged: (Int, String) -> Unit,
    onSessionTimeChanged: (Int, Int?) -> Unit,
    onConfirmSession: (Int, Boolean) -> Unit
) {
    var timeText by remember(session.draftId) {
        mutableStateOf(session.dateOnlySecondOfDay?.let(::clockText) ?: "")
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (session.split) {
            Text(
                text = stringResource(
                    Res.string.settings_workout_import_session_label,
                    session.ordinal
                )
            )
        }
        Text(session.dayLabel)
        if (session.workoutTitle != null) {
            Text(
                text = stringResource(
                    Res.string.settings_workout_import_session_workout,
                    session.workoutTitle
                ),
                style = MaterialTheme.typography.bodySmall
            )
        } else {
            Text(
                text = stringResource(Res.string.settings_workout_import_session_untitled),
                style = MaterialTheme.typography.bodySmall
            )
        }
        Text(
            text = stringResource(
                Res.string.settings_workout_import_session_rows,
                session.rowIndexes.joinToString(", ")
            ),
            style = MaterialTheme.typography.bodySmall
        )
        OutlinedTextField(
            value = session.utcOffsetText,
            onValueChange = { text -> onSessionOffsetChanged(session.draftId, text) },
            label = { Text(stringResource(Res.string.settings_workout_import_session_offset)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = timeText,
            onValueChange = { text ->
                timeText = text
                onSessionTimeChanged(session.draftId, parseClockText(text))
            },
            label = { Text(stringResource(Res.string.settings_workout_import_session_time)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        CheckRow(
            checked = session.confirmedSession && session.confirmedInferredSetTimes,
            label = if (session.confirmedSession) {
                stringResource(Res.string.settings_workout_import_session_confirmed)
            } else {
                stringResource(Res.string.settings_workout_import_session_confirm)
            },
            onCheckedChange = { onConfirmSession(session.draftId, it) }
        )
    }
}

@Composable
private fun RowSessionSelector(row: WorkoutImportRowUi, onSessionSelected: (Int, Int?) -> Unit) {
    var expanded by remember(row.sourceRowIndex) { mutableStateOf(false) }
    val current = row.sessionOptions.firstOrNull { it.draftId == row.sessionDraftId }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = stringResource(Res.string.settings_workout_import_row_session),
            style = MaterialTheme.typography.bodySmall
        )
        Box {
            OutlinedButton(onClick = { expanded = true }) {
                Text(
                    current?.let {
                        stringResource(Res.string.settings_workout_import_session_label, it.ordinal)
                    } ?: stringResource(Res.string.settings_workout_import_session_new)
                )
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                row.sessionOptions.forEach { option ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                stringResource(
                                    Res.string.settings_workout_import_session_label,
                                    option.ordinal
                                )
                            )
                        },
                        enabled = option.draftId != row.sessionDraftId,
                        onClick = {
                            expanded = false
                            onSessionSelected(row.sourceRowIndex, option.draftId)
                        }
                    )
                }
                DropdownMenuItem(
                    text = { Text(stringResource(Res.string.settings_workout_import_session_new)) },
                    onClick = {
                        expanded = false
                        onSessionSelected(row.sourceRowIndex, null)
                    }
                )
            }
        }
    }
}

@Composable
private fun CheckRow(checked: Boolean, label: String, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = onCheckedChange),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = checked, onCheckedChange = null)
        Text(label)
    }
}

@Composable
private fun ProblemLine(problem: ReviewProblem) {
    val label = when (problem) {
        is ReviewProblem.Domain -> stringResource(problem.problem.issue.messageResource())
        is ReviewProblem.InvalidOffset ->
            stringResource(Res.string.settings_workout_import_problem_invalid_offset)
    }
    ProblemText(label = label, row = problem.sourceRow)
}

@Composable
private fun ProblemText(label: String, row: Int?) {
    Text(
        text = if (row != null) {
            stringResource(Res.string.settings_workout_import_problem_row, row) + " " + label
        } else {
            label
        },
        style = MaterialTheme.typography.bodySmall
    )
}

@Composable
private fun WorkoutImportRowUi.detailText(): String {
    val time = timeText ?: stringResource(Res.string.settings_workout_import_no_time)
    val weight = weightText?.let { "$it ${unitLabel.orEmpty()}".trim() }
        ?: stringResource(Res.string.settings_workout_import_no_weight)
    val warmupLabel = when (warmup) {
        true -> stringResource(Res.string.settings_workout_import_warmup)
        false -> stringResource(Res.string.settings_workout_import_working)
        null -> stringResource(Res.string.settings_workout_import_unknown_set_type)
    }
    return "$time · $weight · $reps · $warmupLabel"
}

private fun WorkoutCsvSource.testTag(): String = when (this) {
    WorkoutCsvSource.FITNOTES -> WorkoutImportTestTags.PROVIDER_FITNOTES
    WorkoutCsvSource.STRONG -> WorkoutImportTestTags.PROVIDER_STRONG
    WorkoutCsvSource.HEVY -> WorkoutImportTestTags.PROVIDER_HEVY
}

private fun WorkoutCsvSource.labelResource(): StringResource = when (this) {
    WorkoutCsvSource.FITNOTES -> Res.string.settings_workout_import_provider_fitnotes
    WorkoutCsvSource.STRONG -> Res.string.settings_workout_import_provider_strong
    WorkoutCsvSource.HEVY -> Res.string.settings_workout_import_provider_hevy
}

private fun LoadKind.labelResource(): StringResource = when (this) {
    LoadKind.EXTERNAL -> Res.string.settings_workout_import_load_external
    LoadKind.BODYWEIGHT -> Res.string.settings_workout_import_load_bodyweight
    LoadKind.ADDED -> Res.string.settings_workout_import_load_added
    LoadKind.LEGACY_UNSPECIFIED -> Res.string.settings_workout_import_load_legacy
}

private fun WorkoutImportFailure.messageResource(): StringResource = when (this) {
    is WorkoutImportFailure.Csv -> failure.messageResource()
    WorkoutImportFailure.Unknown -> Res.string.settings_workout_import_error_unknown
}

private fun WorkoutCsvFailure.messageResource(): StringResource = when (this) {
    WorkoutCsvFailure.EMPTY_INPUT -> Res.string.settings_workout_import_error_empty
    WorkoutCsvFailure.TOO_LARGE -> Res.string.settings_workout_import_error_too_large
    WorkoutCsvFailure.TOO_MANY_ROWS -> Res.string.settings_workout_import_error_too_many_rows
    WorkoutCsvFailure.TOO_MANY_COLUMNS -> Res.string.settings_workout_import_error_too_many_columns
    WorkoutCsvFailure.FIELD_TOO_LONG -> Res.string.settings_workout_import_error_field_too_long
    WorkoutCsvFailure.MALFORMED_CSV -> Res.string.settings_workout_import_error_malformed
    WorkoutCsvFailure.DUPLICATE_HEADER -> Res.string.settings_workout_import_error_duplicate_header
    WorkoutCsvFailure.UNSUPPORTED_HEADER ->
        Res.string.settings_workout_import_error_unsupported_header
}

private fun WorkoutImportCommitFailure.messageResource(): StringResource = when (this) {
    WorkoutImportCommitFailure.SOURCE_CHANGED ->
        Res.string.settings_workout_import_commit_source_changed

    WorkoutImportCommitFailure.NOTHING_TO_IMPORT ->
        Res.string.settings_workout_import_commit_nothing_to_import

    WorkoutImportCommitFailure.SESSION_OVERLAP ->
        Res.string.settings_workout_import_commit_session_overlap

    WorkoutImportCommitFailure.NOT_IMPORTABLE ->
        Res.string.settings_workout_import_commit_not_importable
}

private fun WorkoutImportIssue.messageResource(): StringResource = when (this) {
    WorkoutImportIssue.UNRESOLVED_EXCLUSION ->
        Res.string.settings_workout_import_problem_unresolved_exclusion

    WorkoutImportIssue.UNACKNOWLEDGED_METADATA ->
        Res.string.settings_workout_import_problem_unacknowledged_metadata

    WorkoutImportIssue.UNACKNOWLEDGED_HISTORY_EFFECTS ->
        Res.string.settings_workout_import_problem_unacknowledged_history_effects

    WorkoutImportIssue.INVALID_SESSION_MEMBERSHIP ->
        Res.string.settings_workout_import_problem_invalid_session_membership

    WorkoutImportIssue.UNCONFIRMED_SESSION ->
        Res.string.settings_workout_import_problem_unconfirmed_session

    WorkoutImportIssue.INVALID_TIME -> Res.string.settings_workout_import_problem_invalid_time
    WorkoutImportIssue.MISSING_EXERCISE ->
        Res.string.settings_workout_import_problem_missing_exercise

    WorkoutImportIssue.UNCONFIRMED_LOAD ->
        Res.string.settings_workout_import_problem_unconfirmed_load

    WorkoutImportIssue.INVALID_LOAD -> Res.string.settings_workout_import_problem_invalid_load
    WorkoutImportIssue.UNKNOWN_WARMUP ->
        Res.string.settings_workout_import_problem_unknown_warmup

    WorkoutImportIssue.SESSION_OVERLAP ->
        Res.string.settings_workout_import_problem_session_overlap

    WorkoutImportIssue.CHANGED_OR_AMBIGUOUS_SOURCE ->
        Res.string.settings_workout_import_problem_changed_or_ambiguous_source

    WorkoutImportIssue.UNCHANGED_SOURCE_SKIPPED ->
        Res.string.settings_workout_import_notice_unchanged_source

    WorkoutImportIssue.REMOVED_HISTORY_SKIPPED ->
        Res.string.settings_workout_import_notice_removed_history
}

private fun clockText(secondsOfDay: Int): String {
    val hour = secondsOfDay / 3600
    val minute = (secondsOfDay % 3600) / 60
    return hour.toString().padStart(2, '0') + ":" + minute.toString().padStart(2, '0')
}

private fun parseClockText(text: String): Int? {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) return null
    val parts = trimmed.split(':')
    if (parts.size != 2) return null
    val hour = parts[0].toIntOrNull() ?: return null
    val minute = parts[1].toIntOrNull() ?: return null
    if (hour !in 0..23 || minute !in 0..59) return null
    return hour * 3600 + minute * 60
}
