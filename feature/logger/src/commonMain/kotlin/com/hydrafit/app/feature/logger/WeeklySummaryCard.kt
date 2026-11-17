package com.hydrafit.app.feature.logger

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hydrafit.app.core.domain.workout.WeeklyTrainingSummary
import hydrafit.feature.logger.generated.resources.Res
import hydrafit.feature.logger.generated.resources.weekly_summary_bars_description
import hydrafit.feature.logger.generated.resources.weekly_summary_bars_label
import hydrafit.feature.logger.generated.resources.weekly_summary_deload
import hydrafit.feature.logger.generated.resources.weekly_summary_plan
import hydrafit.feature.logger.generated.resources.weekly_summary_sessions
import hydrafit.feature.logger.generated.resources.weekly_summary_title
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * Sessions this week and a quiet four-week view. There is no streak, target or warning colour: a
 * week without a session is an empty bar, so resting, being ill or recovering never reads as a miss.
 */
@Composable
fun WeeklySummaryCard(modifier: Modifier = Modifier) {
    val viewModel = koinViewModel<WeeklySummaryViewModel>()
    val summary by viewModel.summary.collectAsStateWithLifecycle()
    summary?.let { WeeklySummaryContent(it, modifier) }
}

@Composable
internal fun WeeklySummaryContent(summary: WeeklyTrainingSummary, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = stringResource(Res.string.weekly_summary_title),
            style = MaterialTheme.typography.titleSmall
        )
        Text(
            text = stringResource(Res.string.weekly_summary_sessions, summary.sessionsThisWeek),
            style = MaterialTheme.typography.bodyMedium
        )
        summary.plannedDaysPerWeek?.let { planned ->
            Text(
                text = stringResource(Res.string.weekly_summary_plan, planned),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (summary.isDeloadWeek) {
            Text(
                text = stringResource(Res.string.weekly_summary_deload),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        WeeklyBars(summary.sessionsPerWeek)
        Text(
            text = stringResource(Res.string.weekly_summary_bars_label),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun WeeklyBars(counts: List<Int>) {
    val description = stringResource(
        Res.string.weekly_summary_bars_description,
        counts.joinToString(", ")
    )
    val peak = (counts.maxOrNull() ?: 0).coerceAtLeast(1)
    Row(
        modifier = Modifier
            .padding(top = 4.dp)
            .clearAndSetSemantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        counts.forEach { count ->
            // An empty week keeps a thin baseline so the row always reads as four weeks.
            val barHeight = if (count == 0) BASELINE_HEIGHT else BAR_MAX_HEIGHT * count / peak
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = count.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Box(
                    modifier = Modifier
                        .width(20.dp)
                        .height(barHeight)
                        .background(
                            color = if (count == 0) {
                                MaterialTheme.colorScheme.outlineVariant
                            } else {
                                MaterialTheme.colorScheme.primary
                            },
                            shape = RoundedCornerShape(3.dp)
                        )
                )
            }
        }
    }
}

private val BAR_MAX_HEIGHT = 40.dp
private val BASELINE_HEIGHT = 3.dp
