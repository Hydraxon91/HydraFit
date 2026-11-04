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
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.hydrafit.app.core.domain.time.isoDateUtc
import com.hydrafit.app.core.domain.unit.DistanceUnit
import com.hydrafit.app.core.domain.unit.WeightUnit
import com.hydrafit.app.core.domain.unit.formatDistance
import com.hydrafit.app.core.domain.workout.ProgressDay
import com.hydrafit.app.core.userdata.presentation.PresentationNumberFormat
import com.hydrafit.app.core.userdata.presentation.currentPresentationNumberFormat
import com.hydrafit.app.core.userdata.presentation.formatLocalizedDecimal
import hydrafit.feature.progress.generated.resources.Res
import hydrafit.feature.progress.generated.resources.progress_chart_caption
import hydrafit.feature.progress.generated.resources.progress_table_distance
import hydrafit.feature.progress.generated.resources.progress_table_duration
import hydrafit.feature.progress.generated.resources.progress_table_estimate
import hydrafit.feature.progress.generated.resources.progress_table_estimate_unavailable
import hydrafit.feature.progress.generated.resources.progress_table_row
import hydrafit.feature.progress.generated.resources.progress_table_row_no_volume
import hydrafit.feature.progress.generated.resources.progress_table_title
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

internal const val PROGRESS_MILLIS_PER_DAY = 24L * 60L * 60L * 1000L

/** Local civil-day label from a day number; the same fixed-offset reading the backends use. */
internal fun progressDayLabel(localEpochDay: Long): String =
    isoDateUtc(localEpochDay * PROGRESS_MILLIS_PER_DAY)

internal fun displayWeight(
    kilograms: Double,
    unit: WeightUnit,
    format: PresentationNumberFormat = currentPresentationNumberFormat()
): String = "${formatLocalizedDecimal(unit.kilogramsToDisplay(kilograms), format)} ${unit.label}"

/** External load-volume is stored as kg·reps, so only its magnitude is converted for display. */
internal fun displayVolume(
    kilogramReps: Double,
    unit: WeightUnit,
    format: PresentationNumberFormat = currentPresentationNumberFormat()
): String = "${formatLocalizedDecimal(unit.kilogramsToDisplay(kilogramReps), format)} " +
    "${unit.label}·reps"

/**
 * The horizontal centre for a mark at [localEpochDay], inset from each edge by [radiusPx] so no mark
 * is centred on the canvas boundary. [dayFrom] and [dayTo] are inclusive and map to the usable edges.
 */
internal fun progressMarkX(day: Long, from: Long, to: Long, width: Float, radius: Float): Float {
    val span = (to - from).coerceAtLeast(1L).toFloat()
    val usable = (width - 2f * radius).coerceAtLeast(0f)
    return radius + ((day - from).toFloat() / span) * usable
}

/**
 * The vertical centre for a mark at [value] on a chart whose maximum is [max], inset from the top and
 * bottom by [radius]. The maximum sits at the top inset; a zero value would rest on the bottom one.
 */
internal fun progressMarkY(value: Double, max: Double, height: Float, radius: Float): Float {
    val fraction = (value / max).coerceIn(0.0, 1.0)
    val usable = (height - 2f * radius).coerceAtLeast(0f)
    return radius + ((1.0 - fraction) * usable).toFloat()
}

/**
 * One mark per recorded day that carries the selected metric, positioned at its actual civil day.
 * Days without a value are simply absent: nothing is interpolated, smoothed, connected or turned
 * into a fake zero day. The plot reserves measured space for its axis labels, keeps grid, marks and
 * date labels on the same positions, and reduces date labels until they fit the available width.
 * The canvas is decorative (`clearAndSetSemantics`), so assistive tech is not announced twice;
 * [ProgressDayTable] is the accessible numeric equivalent.
 */
@Composable
internal fun ProgressMetricChart(
    points: List<ProgressChartPoint>,
    metric: ProgressMetric,
    weightUnit: WeightUnit,
    dayFrom: Long,
    dayTo: Long,
    modifier: Modifier = Modifier
) {
    if (points.isEmpty()) return
    val axis = progressAxis(points, metric, weightUnit)
    val format = currentPresentationNumberFormat()
    val decimals = progressAxisLabelDecimals(axis)
    val markColor = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val baselineColor = MaterialTheme.colorScheme.outline
    val labelStyle = MaterialTheme.typography.labelSmall.copy(
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    val textMeasurer = rememberTextMeasurer()
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(200.dp)
            .clearAndSetSemantics {}
    ) {
        val markRadius = 3.dp.toPx()
        val gap = 6.dp.toPx()
        val strokeWidth = 1.dp.toPx()
        val lineWidth = 2.dp.toPx()
        val yLabels = axis.ticks.mapIndexed { index, tick ->
            val layout = textMeasurer.measure(
                text = progressAxisTickLabel(tick, weightUnit, format, decimals),
                style = labelStyle,
                softWrap = false
            )
            index to layout
        }
        val gutter = (yLabels.maxOfOrNull { it.second.size.width } ?: 0) + gap
        val labelHeight = textMeasurer.measure(
            text = "0000-00-00",
            style = labelStyle,
            softWrap = false
        ).size.height
        val plotLeft = gutter
        val plotRight = size.width
        val plotTop = gap + labelHeight / 2f
        val datePlacements = progressDateLabelPlacements(
            from = dayFrom,
            to = dayTo,
            widthFor = { day ->
                textMeasurer.measure(
                    text = progressDayLabel(day),
                    style = labelStyle,
                    softWrap = false
                ).size.width.toFloat()
            },
            xFor = { day ->
                progressChartMarkX(day, dayFrom, dayTo, plotLeft, plotRight, markRadius)
            },
            plotLeft = plotLeft,
            plotRight = plotRight,
            gap = gap
        )
        val dateRows = (datePlacements.maxOfOrNull { it.row } ?: 0) + 1
        val bottomInset = gap + dateRows * labelHeight + (dateRows - 1) * (gap / 2f)
        val plotBottom = (size.height - bottomInset).coerceAtLeast(plotTop + 1f)

        // Restrained horizontal grid; the date ticks below carry the horizontal positions.
        yLabels.forEach { (index, layout) ->
            val tick = axis.ticks[index]
            val y = progressAxisY(tick, axis, plotTop, plotBottom, markRadius)
            drawLine(
                color = if (index == 0 && axis.min == 0.0) baselineColor else gridColor,
                start = Offset(plotLeft, y),
                end = Offset(plotRight, y),
                strokeWidth = strokeWidth
            )
            drawText(
                textLayoutResult = layout,
                topLeft = Offset(plotLeft - gap - layout.size.width, y - layout.size.height / 2f)
            )
        }

        fun xForPoint(point: ProgressChartPoint): Float = progressChartMarkX(
            day = point.localEpochDay,
            from = dayFrom,
            to = dayTo,
            left = plotLeft,
            right = plotRight,
            radius = markRadius
        )

        fun yForPoint(point: ProgressChartPoint): Float =
            progressAxisY(point.value, axis, plotTop, plotBottom, markRadius)

        // Straight segments join observed values across unlogged dates; dots still mark the only
        // measured values, so the line is a visual connection rather than inferred daily data.
        progressChartSegments(points).forEach { segment ->
            drawLine(
                color = markColor,
                start = Offset(xForPoint(segment.from), yForPoint(segment.from)),
                end = Offset(xForPoint(segment.to), yForPoint(segment.to)),
                strokeWidth = lineWidth
            )
        }
        points.forEach { point ->
            drawCircle(
                color = markColor,
                radius = markRadius,
                center = Offset(xForPoint(point), yForPoint(point))
            )
        }

        datePlacements.forEach { placement ->
            val layout = textMeasurer.measure(
                text = progressDayLabel(placement.day),
                style = labelStyle,
                softWrap = false
            )
            drawText(
                textLayoutResult = layout,
                topLeft = Offset(
                    placement.left,
                    plotBottom + gap / 2f + placement.row * (labelHeight + gap / 2f)
                )
            )
        }
    }
}

/**
 * The duration line for a day's table row, or null when the day recorded no timed work. Recorded
 * duration is descriptive only: it never feeds a record, progression or estimated one-rep max, and
 * a day without timed work shows no duration total rather than a misleading zero.
 */
internal fun progressDayDurationResource(day: ProgressDay): StringResource? =
    if (day.totalDurationSeconds > 0L) Res.string.progress_table_duration else null

/**
 * The distance line for a day's table row, or null when the day recorded no distance work. Recorded
 * distance is descriptive only: it never feeds a record, progression or estimated one-rep max, and a
 * day without distance work shows no distance total rather than a misleading zero.
 */
internal fun progressDayDistanceResource(day: ProgressDay): StringResource? =
    if (day.totalDistanceMeters > 0L) Res.string.progress_table_distance else null

/**
 * The accessible numeric equivalent of the chart: one row per recorded day, newest first, with the
 * estimated one-rep max either stated or explicitly unavailable. Timed and distance totals stay
 * visible even when the selected metric has no value for the day.
 */
@Composable
internal fun ProgressDayTable(
    days: List<ProgressDay>,
    weightUnit: WeightUnit,
    distanceUnit: DistanceUnit,
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
            val volume = day.externalVolumeKg?.let { displayVolume(it, weightUnit) }
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
            val estimate = day.estimatedOneRepMax?.value
            Text(
                text = if (estimate == null) {
                    stringResource(Res.string.progress_table_estimate_unavailable)
                } else {
                    stringResource(
                        Res.string.progress_table_estimate,
                        displayWeight(estimate, weightUnit)
                    )
                },
                style = MaterialTheme.typography.bodySmall
            )
            val durationResource = progressDayDurationResource(day)
            if (durationResource != null) {
                Text(
                    text = stringResource(durationResource, day.totalDurationSeconds),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            val distanceResource = progressDayDistanceResource(day)
            if (distanceResource != null) {
                Text(
                    text = stringResource(
                        distanceResource,
                        formatDistance(day.totalDistanceMeters, distanceUnit)
                    ),
                    style = MaterialTheme.typography.bodySmall
                )
            }
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
