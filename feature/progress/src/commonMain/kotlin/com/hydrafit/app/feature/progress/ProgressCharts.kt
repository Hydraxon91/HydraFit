package com.hydrafit.app.feature.progress

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.hydrafit.app.core.domain.time.isoDateUtc
import com.hydrafit.app.core.domain.unit.WeightUnit
import com.hydrafit.app.core.domain.unit.formatWeight
import com.hydrafit.app.core.domain.workout.ProgressDay
import hydrafit.feature.progress.generated.resources.Res
import hydrafit.feature.progress.generated.resources.progress_chart_caption
import hydrafit.feature.progress.generated.resources.progress_table_row
import hydrafit.feature.progress.generated.resources.progress_table_row_no_volume
import hydrafit.feature.progress.generated.resources.progress_table_title
import org.jetbrains.compose.resources.stringResource

internal const val PROGRESS_MILLIS_PER_DAY = 24L * 60L * 60L * 1000L

/** Local civil-day label from a day number; the same fixed-offset reading the backends use. */
internal fun progressDayLabel(localEpochDay: Long): String =
    isoDateUtc(localEpochDay * PROGRESS_MILLIS_PER_DAY)

internal fun displayWeight(kilograms: Double, unit: WeightUnit): String =
    "${formatWeight(unit.kilogramsToDisplay(kilograms))} ${unit.label}"

/**
 * One mark per recorded day that carries external load-volume, positioned at its actual civil day.
 * Days without a recorded value are simply absent: nothing is interpolated, smoothed or connected
 * across them, and no fake zero day is drawn. The canvas is decorative (`clearAndSetSemantics`), so
 * assistive tech is not announced twice; [ProgressDayTable] is the accessible numeric equivalent.
 */
@Composable
internal fun ProgressVolumeChart(
    days: List<ProgressDay>,
    dayFrom: Long,
    dayTo: Long,
    modifier: Modifier = Modifier
) {
    val points = days.filter { it.externalVolumeKg != null }
    if (points.isEmpty()) return
    val max = points.maxOf { it.externalVolumeKg ?: 0.0 }.coerceAtLeast(1.0)
    val color = MaterialTheme.colorScheme.primary
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(160.dp)
            .clearAndSetSemantics {}
    ) {
        val span = (dayTo - dayFrom).coerceAtLeast(1L).toFloat()
        val radius = 3.dp.toPx()
        points.forEach { day ->
            val x = ((day.localEpochDay - dayFrom).toFloat() / span) * size.width
            val y = size.height - ((day.externalVolumeKg ?: 0.0) / max * size.height).toFloat()
            drawCircle(color = color, radius = radius, center = Offset(x, y))
        }
    }
}

/** The accessible numeric equivalent of the chart: one row per recorded day, newest first. */
@Composable
internal fun ProgressDayTable(
    days: List<ProgressDay>,
    weightUnit: WeightUnit,
    modifier: Modifier = Modifier
) {
    val ordered = days.sortedByDescending { it.localEpochDay }
    Column(modifier = modifier) {
        Text(
            text = stringResource(Res.string.progress_table_title),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(vertical = 4.dp)
        )
        ordered.forEach { day ->
            val date = progressDayLabel(day.localEpochDay)
            val volume = day.externalVolumeKg?.let { displayWeight(it, weightUnit) }
            Text(
                text = if (volume == null) {
                    stringResource(
                        Res.string.progress_table_row_no_volume,
                        date,
                        day.workingSets,
                        day.totalReps.toInt()
                    )
                } else {
                    stringResource(
                        Res.string.progress_table_row,
                        date,
                        day.workingSets,
                        day.totalReps.toInt(),
                        volume
                    )
                },
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

/** The chart's explanatory caption, shown above the canvas. */
@Composable
internal fun ProgressChartCaption(modifier: Modifier = Modifier) {
    Text(
        text = stringResource(Res.string.progress_chart_caption),
        style = MaterialTheme.typography.bodySmall,
        modifier = modifier.fillMaxWidth()
    )
}
