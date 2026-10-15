package com.hydrafit.app.feature.logger

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.hydrafit.app.core.domain.unit.WeightUnit
import com.hydrafit.app.core.domain.workout.LoadKind
import com.hydrafit.app.core.domain.workout.ProgressRecordType
import hydrafit.feature.logger.generated.resources.Res
import hydrafit.feature.logger.generated.resources.logger_history_exercise_missing
import hydrafit.feature.logger.generated.resources.logger_summary_records_empty
import hydrafit.feature.logger.generated.resources.logger_summary_records_estimate
import hydrafit.feature.logger.generated.resources.logger_summary_records_estimate_from
import hydrafit.feature.logger.generated.resources.logger_summary_records_failed
import hydrafit.feature.logger.generated.resources.logger_summary_records_load_at_reps
import hydrafit.feature.logger.generated.resources.logger_summary_records_loading
import hydrafit.feature.logger.generated.resources.logger_summary_records_partial
import hydrafit.feature.logger.generated.resources.logger_summary_records_reps_at_load
import hydrafit.feature.logger.generated.resources.logger_summary_records_retry
import hydrafit.feature.logger.generated.resources.logger_summary_records_title
import org.jetbrains.compose.resources.stringResource

/**
 * The post-finish summary's records section: the chronological improvements the workout achieved,
 * named by metric, load kind and qualifying reps/load, in the display unit. It is descriptive — it
 * never claims a prescription was fulfilled or technique verified. A [partial] workout still shows
 * its improvements, with a disclaimer that they describe only the recorded sets.
 *
 * The section is independent of the summary read: loading, an empty result and a failure are
 * distinct, and a failure offers a retry without touching the finished workout.
 */
@Composable
internal fun WorkoutSummaryRecordsSection(
    state: WorkoutSummaryRecordsUiState,
    partial: Boolean,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    when (val screen = state.screen) {
        WorkoutSummaryRecordsScreen.Hidden -> Unit
        else -> Column(
            modifier = modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = stringResource(Res.string.logger_summary_records_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() }
            )
            when (screen) {
                WorkoutSummaryRecordsScreen.Hidden -> Unit
                WorkoutSummaryRecordsScreen.Loading ->
                    Text(stringResource(Res.string.logger_summary_records_loading))
                WorkoutSummaryRecordsScreen.Failed -> {
                    Text(stringResource(Res.string.logger_summary_records_failed))
                    TextButton(onClick = onRetry) {
                        Text(stringResource(Res.string.logger_summary_records_retry))
                    }
                }
                WorkoutSummaryRecordsScreen.Empty ->
                    Text(stringResource(Res.string.logger_summary_records_empty))
                is WorkoutSummaryRecordsScreen.Ready -> {
                    screen.records.forEach { row -> SummaryRecordLine(row, state.unit) }
                    if (partial) {
                        Text(
                            text = stringResource(Res.string.logger_summary_records_partial),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryRecordLine(row: SummaryRecordRow, unit: WeightUnit) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.fillMaxWidth()) {
        Text(
            text = row.exerciseName ?: stringResource(Res.string.logger_history_exercise_missing),
            style = MaterialTheme.typography.bodyLarge
        )
        Text(text = recordMetricText(row, unit), style = MaterialTheme.typography.bodySmall)
        if (row.type == ProgressRecordType.ESTIMATED_ONE_REP_MAX && row.reps != null) {
            row.weightKg?.let { load ->
                Text(
                    text = stringResource(
                        Res.string.logger_summary_records_estimate_from,
                        row.reps,
                        loadWeightText(row.loadKind, load, unit)
                    ),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun recordMetricText(row: SummaryRecordRow, unit: WeightUnit): String = when (row.type) {
    ProgressRecordType.LOAD_AT_REPS -> stringResource(
        Res.string.logger_summary_records_load_at_reps,
        loadWeightText(row.loadKind, row.value, unit),
        row.reps ?: 0
    )
    ProgressRecordType.REPS_AT_LOAD -> stringResource(
        Res.string.logger_summary_records_reps_at_load,
        row.value.toInt(),
        loadWeightText(row.loadKind, row.weightKg, unit)
    )
    ProgressRecordType.ESTIMATED_ONE_REP_MAX -> stringResource(
        Res.string.logger_summary_records_estimate,
        loadWeightText(LoadKind.EXTERNAL, row.value, unit)
    )
}
