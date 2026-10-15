package com.hydrafit.app.feature.progress

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.hydrafit.app.core.domain.unit.WeightUnit
import com.hydrafit.app.core.userdata.measurements.BodyWeightMeasurement
import hydrafit.feature.progress.generated.resources.Res
import hydrafit.feature.progress.generated.resources.bodyweight_chart_caption
import hydrafit.feature.progress.generated.resources.bodyweight_chart_legend
import hydrafit.feature.progress.generated.resources.bodyweight_goal_line
import hydrafit.feature.progress.generated.resources.bodyweight_list_empty_in_range
import hydrafit.feature.progress.generated.resources.bodyweight_list_row
import hydrafit.feature.progress.generated.resources.bodyweight_list_title
import org.jetbrains.compose.resources.stringResource

/**
 * The vertical centre for a mark at [value] on a chart whose recorded range is [min]..[max], inset
 * from the top and bottom by [radius]. The range is the recorded minimum and maximum only: there is
 * no invented zero baseline, and a flat range places its single value at the vertical centre.
 * A value outside the range (for example a goal) clamps to the nearest edge.
 */
internal fun bodyWeightMarkY(
    value: Double,
    min: Double,
    max: Double,
    height: Float,
    radius: Float
): Float {
    val span = max - min
    val fraction = if (span <= 0.0) 0.5 else ((value - min) / span).coerceIn(0.0, 1.0)
    val usable = (height - 2f * radius).coerceAtLeast(0f)
    return radius + ((1.0 - fraction) * usable).toFloat()
}

/**
 * One mark per recorded measurement, positioned at its actual civil day and its display-unit value.
 * Days without a measurement are simply absent: nothing is interpolated or connected across them and
 * no zero baseline is drawn. When a goal exists, a dashed line marks it, clamped into the recorded
 * window so it stays visible. The canvas is decorative (`clearAndSetSemantics`), so assistive tech is
 * not announced twice; [BodyWeightMeasurementList] is the accessible numeric equivalent.
 */
@Composable
internal fun BodyWeightChart(
    measurements: List<BodyWeightMeasurement>,
    weightUnit: WeightUnit,
    goalKg: Double?,
    dayFrom: Long,
    dayTo: Long,
    modifier: Modifier = Modifier
) {
    val points = measurements.map { it.localEpochDay to weightUnit.kilogramsToDisplay(it.weightKg) }
    if (points.isEmpty()) return
    val min = points.minOf { it.second }
    val max = points.maxOf { it.second }
    val color = MaterialTheme.colorScheme.primary
    val goalColor = MaterialTheme.colorScheme.tertiary
    val goalDisplay = goalKg?.let { weightUnit.kilogramsToDisplay(it) }
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(160.dp)
            .clearAndSetSemantics {}
    ) {
        val radius = 3.dp.toPx()
        if (goalDisplay != null) {
            val y = bodyWeightMarkY(goalDisplay, min, max, size.height, radius)
            drawLine(
                color = goalColor,
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f))
            )
        }
        points.forEach { (day, value) ->
            val x = progressMarkX(day, dayFrom, dayTo, size.width, radius)
            val y = bodyWeightMarkY(value, min, max, size.height, radius)
            drawCircle(color = color, radius = radius, center = Offset(x, y))
        }
    }
}

/**
 * The accessible numeric equivalent of the chart: one row per measurement, newest first. Each row is
 * the edit affordance for that measurement, so the same list carries the value and the way into the
 * editor without a second control per entry.
 */
@Composable
internal fun BodyWeightMeasurementList(
    measurements: List<BodyWeightMeasurement>,
    weightUnit: WeightUnit,
    onEdit: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val ordered = measurements.sortedByDescending { it.localEpochDay }
    Column(modifier = modifier) {
        Text(
            text = stringResource(Res.string.bodyweight_list_title),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(vertical = 4.dp)
        )
        if (ordered.isEmpty()) {
            Text(
                text = stringResource(Res.string.bodyweight_list_empty_in_range),
                style = MaterialTheme.typography.bodySmall
            )
            return
        }
        ordered.forEach { measurement ->
            TextButton(
                onClick = { onEdit(measurement.localEpochDay) },
                modifier = Modifier.testTag(
                    BodyWeightTestTags.measurement(measurement.localEpochDay)
                )
            ) {
                Text(
                    text = stringResource(
                        Res.string.bodyweight_list_row,
                        progressDayLabel(measurement.localEpochDay),
                        displayWeight(measurement.weightKg, weightUnit)
                    ),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

/** The chart's explanatory caption and goal legend, shown above the canvas. */
@Composable
internal fun BodyWeightChartCaption(
    goalKg: Double?,
    weightUnit: WeightUnit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(Res.string.bodyweight_chart_caption),
            style = MaterialTheme.typography.bodySmall
        )
        Text(
            text = stringResource(Res.string.bodyweight_chart_legend),
            style = MaterialTheme.typography.bodySmall
        )
        if (goalKg != null) {
            Text(
                text = stringResource(
                    Res.string.bodyweight_goal_line,
                    displayWeight(goalKg, weightUnit)
                ),
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
