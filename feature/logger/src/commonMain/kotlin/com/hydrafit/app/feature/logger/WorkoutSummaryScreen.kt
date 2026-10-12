package com.hydrafit.app.feature.logger

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.hydrafit.app.core.domain.schedule.OccurrenceStatus
import com.hydrafit.app.core.domain.schedule.RemainingDisposition
import com.hydrafit.app.core.domain.unit.WeightUnit
import com.hydrafit.app.core.domain.workout.LoadKind
import com.hydrafit.app.core.domain.workout.WorkoutHistoryKey
import com.hydrafit.app.core.domain.workout.WorkoutSummary
import com.hydrafit.app.core.domain.workout.WorkoutSummaryExercise
import com.hydrafit.app.core.domain.workout.WorkoutSummarySet
import hydrafit.feature.logger.generated.resources.Res
import hydrafit.feature.logger.generated.resources.logger_edit_set
import hydrafit.feature.logger.generated.resources.logger_history_exercise_missing
import hydrafit.feature.logger.generated.resources.logger_history_set_line
import hydrafit.feature.logger.generated.resources.logger_summary_close
import hydrafit.feature.logger.generated.resources.logger_summary_done
import hydrafit.feature.logger.generated.resources.logger_summary_edit_set_description
import hydrafit.feature.logger.generated.resources.logger_summary_extra_sets
import hydrafit.feature.logger.generated.resources.logger_summary_failed
import hydrafit.feature.logger.generated.resources.logger_summary_loading
import hydrafit.feature.logger.generated.resources.logger_summary_not_performed
import hydrafit.feature.logger.generated.resources.logger_summary_note_none
import hydrafit.feature.logger.generated.resources.logger_summary_remaining
import hydrafit.feature.logger.generated.resources.logger_summary_replaced
import hydrafit.feature.logger.generated.resources.logger_summary_resolved
import hydrafit.feature.logger.generated.resources.logger_summary_retry
import hydrafit.feature.logger.generated.resources.logger_summary_session_line
import hydrafit.feature.logger.generated.resources.logger_summary_session_open
import hydrafit.feature.logger.generated.resources.logger_summary_sessions_title
import hydrafit.feature.logger.generated.resources.logger_summary_skipped
import hydrafit.feature.logger.generated.resources.logger_summary_started
import hydrafit.feature.logger.generated.resources.logger_summary_status_finished
import hydrafit.feature.logger.generated.resources.logger_summary_status_partial
import hydrafit.feature.logger.generated.resources.logger_summary_target_sets
import hydrafit.feature.logger.generated.resources.logger_summary_target_sets_none
import hydrafit.feature.logger.generated.resources.logger_summary_title
import hydrafit.feature.logger.generated.resources.logger_summary_totals
import hydrafit.feature.logger.generated.resources.logger_summary_unavailable
import hydrafit.feature.logger.generated.resources.logger_summary_view_in_history
import hydrafit.feature.logger.generated.resources.logger_summary_warmup_sets
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * The identity "View in history" opens: the workout's single recorded session, or null when the
 * workout spans several sessions or only sets with no session. Null sends the user to the history
 * browser instead, because the detail is keyed per session and inventing one grouping for a
 * multi-session workout would misrepresent it.
 */
internal fun workoutSummaryHistoryKey(summary: WorkoutSummary): WorkoutHistoryKey? =
    summary.sessions.singleOrNull()?.let { WorkoutHistoryKey.Session(it.sessionId) }

/**
 * What the summary route shows. [screen] is null only while the route is being dismissed, so the
 * route renders nothing rather than a stale workout.
 */
@Composable
internal fun WorkoutSummaryRoute(
    screen: WorkoutSummaryScreen?,
    weightUnit: WeightUnit,
    utcOffsetMillis: Long,
    onRetry: () -> Unit,
    onDone: () -> Unit,
    onViewInHistory: (WorkoutSummary) -> Unit,
    onEditSet: (Long, WorkoutSummarySet) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        when (screen) {
            null -> Unit
            WorkoutSummaryScreen.Loading -> Text(stringResource(Res.string.logger_summary_loading))
            WorkoutSummaryScreen.Failed -> {
                Text(stringResource(Res.string.logger_summary_failed))
                Button(onClick = onRetry) { Text(stringResource(Res.string.logger_summary_retry)) }
            }
            WorkoutSummaryScreen.Unavailable ->
                Text(stringResource(Res.string.logger_summary_unavailable))
            is WorkoutSummaryScreen.Ready -> WorkoutSummaryContent(
                summary = screen.summary,
                weightUnit = weightUnit,
                utcOffsetMillis = utcOffsetMillis,
                onViewInHistory = onViewInHistory,
                onEditSet = { set -> onEditSet(screen.summary.occurrenceId, set) }
            )
        }
        // Every settled state can be left in-app; only the in-flight read cannot.
        when (screen) {
            null, WorkoutSummaryScreen.Loading -> Unit
            is WorkoutSummaryScreen.Ready -> Button(onClick = onDone) {
                Text(stringResource(Res.string.logger_summary_done))
            }
            WorkoutSummaryScreen.Failed, WorkoutSummaryScreen.Unavailable ->
                TextButton(onClick = onDone) {
                    Text(stringResource(Res.string.logger_summary_close))
                }
        }
    }
}

@Composable
private fun WorkoutSummaryContent(
    summary: WorkoutSummary,
    weightUnit: WeightUnit,
    utcOffsetMillis: Long,
    onViewInHistory: (WorkoutSummary) -> Unit,
    onEditSet: (WorkoutSummarySet) -> Unit
) {
    Text(
        text = summary.workoutName ?: stringResource(Res.string.logger_summary_title),
        style = MaterialTheme.typography.titleLarge,
        modifier = Modifier.semantics { heading() }
    )
    Text(
        text = stringResource(
            if (summary.status == OccurrenceStatus.FINISHED) {
                Res.string.logger_summary_status_finished
            } else {
                Res.string.logger_summary_status_partial
            }
        ),
        style = MaterialTheme.typography.bodyMedium
    )
    summary.startedAtMillis?.let {
        Text(
            text = stringResource(
                Res.string.logger_summary_started,
                instantLabel(it, utcOffsetMillis)
            )
        )
    }
    summary.resolvedAtMillis?.let {
        Text(
            text = stringResource(
                Res.string.logger_summary_resolved,
                instantLabel(it, utcOffsetMillis)
            )
        )
    }
    Text(
        text = stringResource(
            Res.string.logger_summary_totals,
            summary.recordedWorkingSets,
            summary.prescribedSets
        )
    )

    summary.exercises.forEach { exercise ->
        SummaryExerciseSection(exercise, weightUnit, onEditSet)
    }
    SummarySetGroup(
        Res.string.logger_summary_extra_sets,
        summary.extraSets,
        weightUnit,
        onEditSet
    )
    SummarySetGroup(
        Res.string.logger_summary_warmup_sets,
        summary.warmupSets,
        weightUnit,
        onEditSet
    )

    if (summary.sessions.isNotEmpty()) {
        Text(
            text = stringResource(Res.string.logger_summary_sessions_title),
            style = MaterialTheme.typography.titleMedium
        )
        summary.sessions.forEach { session ->
            Text(
                text = session.endedAtMillis?.let {
                    stringResource(
                        Res.string.logger_summary_session_line,
                        instantLabel(session.startedAtMillis, utcOffsetMillis),
                        instantLabel(it, utcOffsetMillis)
                    )
                } ?: stringResource(
                    Res.string.logger_summary_session_open,
                    instantLabel(session.startedAtMillis, utcOffsetMillis)
                )
            )
            Text(
                text = if (session.hasNote) {
                    session.note.orEmpty()
                } else {
                    stringResource(Res.string.logger_summary_note_none)
                },
                style = MaterialTheme.typography.bodySmall
            )
        }
    }

    TextButton(onClick = { onViewInHistory(summary) }) {
        Text(stringResource(Res.string.logger_summary_view_in_history))
    }
}

@Composable
private fun SummaryExerciseSection(
    exercise: WorkoutSummaryExercise,
    weightUnit: WeightUnit,
    onEditSet: (WorkoutSummarySet) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.fillMaxWidth()) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = exercise.exerciseName, style = MaterialTheme.typography.bodyLarge)
            if (exercise.isReplaced) {
                Text(
                    text = stringResource(Res.string.logger_summary_replaced),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
        Text(
            text = prescribedTargetText(exercise, weightUnit),
            style = MaterialTheme.typography.bodySmall
        )
        exercise.performedSets.forEachIndexed { index, set ->
            SummarySetLine(set, index + 1, weightUnit, onEditSet)
        }
        if (exercise.remainingSets > 0) {
            Text(
                text = stringResource(Res.string.logger_summary_remaining, exercise.remainingSets) +
                    exercise.remainingDisposition.let { disposition ->
                        when (disposition) {
                            RemainingDisposition.OMITTED ->
                                " · " + stringResource(Res.string.logger_summary_not_performed)
                            RemainingDisposition.SKIPPED ->
                                " · " + stringResource(Res.string.logger_summary_skipped)
                            null -> ""
                        }
                    },
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun SummarySetGroup(
    label: StringResource,
    sets: List<WorkoutSummarySet>,
    weightUnit: WeightUnit,
    onEditSet: (WorkoutSummarySet) -> Unit
) {
    if (sets.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.fillMaxWidth()) {
        Text(text = stringResource(label), style = MaterialTheme.typography.titleMedium)
        sets.forEachIndexed { index, set -> SummarySetLine(set, index + 1, weightUnit, onEditSet) }
    }
}

/**
 * One recorded set with its own correction action. [position] is the set's 1-based place within its
 * group, so the repeated "Edit set" control is distinguishable when spoken as well as seen.
 */
@Composable
private fun SummarySetLine(
    set: WorkoutSummarySet,
    position: Int,
    weightUnit: WeightUnit,
    onEditSet: (WorkoutSummarySet) -> Unit
) {
    val editDescription = stringResource(
        Res.string.logger_summary_edit_set_description,
        set.exerciseName ?: stringResource(Res.string.logger_history_exercise_missing),
        position,
        set.reps
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(
                Res.string.logger_history_set_line,
                set.reps,
                loadWeightText(set.loadKind, set.weightKg, weightUnit)
            ),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f)
        )
        TextButton(
            onClick = { onEditSet(set) },
            modifier = Modifier.semantics {
                contentDescription = editDescription
            }
        ) {
            Text(stringResource(Res.string.logger_edit_set))
        }
    }
}

@Composable
private fun prescribedTargetText(exercise: WorkoutSummaryExercise, weightUnit: WeightUnit): String =
    if (exercise.prescribedWeightKg == null && exercise.prescribedLoadKind == LoadKind.BODYWEIGHT) {
        stringResource(
            Res.string.logger_summary_target_sets_none,
            exercise.prescribedSets,
            exercise.prescribedReps
        )
    } else {
        stringResource(
            Res.string.logger_summary_target_sets,
            exercise.prescribedSets,
            exercise.prescribedReps,
            loadWeightText(
                exercise.prescribedLoadKind,
                exercise.prescribedWeightKg,
                weightUnit
            )
        )
    }

private fun instantLabel(epochMillis: Long, utcOffsetMillis: Long): String {
    val parts = localDateTimeParts(epochMillis, utcOffsetMillis)
    return "${localDateLabel(parts)} ${localTimeLabel(parts)}"
}
