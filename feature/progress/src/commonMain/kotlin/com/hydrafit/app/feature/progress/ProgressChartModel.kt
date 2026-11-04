package com.hydrafit.app.feature.progress

import com.hydrafit.app.core.domain.unit.WeightUnit
import com.hydrafit.app.core.domain.workout.ProgressDay
import com.hydrafit.app.core.userdata.presentation.PresentationNumberFormat
import com.hydrafit.app.core.userdata.presentation.formatLocalizedDecimal
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.round

/**
 * One plotted daily observation in canonical units: external load-volume is stored as kg·reps and
 * estimated one-rep max as kg. Display units are applied only when a label is rendered.
 */
internal data class ProgressChartPoint(val localEpochDay: Long, val value: Double)

/** Two consecutive recorded observations joined by a straight chart line. */
internal data class ProgressChartSegment(val from: ProgressChartPoint, val to: ProgressChartPoint)

/** A chart value range and its deterministic ticks, all in the same canonical unit. */
internal data class ProgressAxis(
    val min: Double,
    val max: Double,
    val step: Double,
    val ticks: List<Double>
)

/** The selected metric's stored value for this day, or null when the day has no observation. */
internal fun ProgressDay.progressChartValue(metric: ProgressMetric): Double? = when (metric) {
    ProgressMetric.EXTERNAL_VOLUME -> externalVolumeKg
    ProgressMetric.ESTIMATED_ONE_REP_MAX -> estimatedOneRepMax?.value
}

/**
 * Existing daily values projected for one metric, oldest first. Missing values stay absent: nothing
 * is estimated, interpolated or replaced with a zero, and a metric switch never changes the days.
 */
internal fun progressChartPoints(
    days: List<ProgressDay>,
    metric: ProgressMetric
): List<ProgressChartPoint> = days
    .mapNotNull { day ->
        day.progressChartValue(metric)?.let { ProgressChartPoint(day.localEpochDay, it) }
    }
    .sortedBy { it.localEpochDay }

/**
 * Straight line segments joining consecutive recorded observations, oldest first. A segment spans
 * the actual interval between its endpoints rather than pretending the days between them were
 * measured. No segment is invented for a single observation, and no value is interpolated into the
 * numeric table.
 */
internal fun progressChartSegments(points: List<ProgressChartPoint>): List<ProgressChartSegment> =
    points
        .sortedBy { it.localEpochDay }
        .zipWithNext { earlier, later -> ProgressChartSegment(earlier, later) }

/** The axis for [metric]: volume keeps a zero baseline, estimates use their padded observed bounds. */
internal fun progressAxis(
    points: List<ProgressChartPoint>,
    metric: ProgressMetric,
    weightUnit: WeightUnit = WeightUnit.KG
): ProgressAxis = when (metric) {
    ProgressMetric.EXTERNAL_VOLUME -> progressVolumeAxis(points)
    ProgressMetric.ESTIMATED_ONE_REP_MAX -> progressEstimateAxis(points, weightUnit)
}

/** Volume's y-axis starts at zero and grows to a nice bound that contains every observation. */
internal fun progressVolumeAxis(points: List<ProgressChartPoint>): ProgressAxis {
    val observedMax = points.maxOfOrNull { it.value }?.coerceAtLeast(0.0) ?: 0.0
    if (observedMax <= 0.0) return ProgressAxis(0.0, 1.0, 1.0, listOf(0.0, 1.0))
    val step = progressNiceStep(observedMax, VOLUME_TARGET_TICKS)
    val upper = ceil(observedMax / step) * step
    return buildProgressAxis(0.0, upper, step)
}

/**
 * Estimated one-rep max's y-axis pads the observed bounds by 5% of their span. A flat or single
 * observation has no span, so it uses the larger of 5% of its magnitude and one display unit.
 * The lower bound never goes below zero, and the nice bounds still contain every observation.
 */
internal fun progressEstimateAxis(
    points: List<ProgressChartPoint>,
    weightUnit: WeightUnit = WeightUnit.KG
): ProgressAxis {
    if (points.isEmpty()) return ProgressAxis(0.0, 1.0, 1.0, listOf(0.0, 1.0))
    val observedMin = points.minOf { it.value }
    val observedMax = points.maxOf { it.value }
    val span = observedMax - observedMin
    val padding = if (span > 0.0) {
        span * ESTIMATE_PADDING_FRACTION
    } else {
        max(
            abs(observedMax) * ESTIMATE_PADDING_FRACTION,
            weightUnit.displayToKilograms(1.0)
        )
    }
    val lower = max(0.0, observedMin - padding)
    val upper = observedMax + padding
    val step = progressNiceStep(upper - lower, ESTIMATE_TARGET_TICKS)
    val axisMin = max(0.0, floor(lower / step) * step)
    val axisMax = max(axisMin + step, ceil(upper / step) * step)
    return buildProgressAxis(axisMin, axisMax, step)
}

/**
 * Deterministic 1/2/5 × 10^n tick spacing for [span], aiming at roughly [targetTicks] ticks. The
 * displayed values are never rounded into the canonical bounds; only the tick step is quantized.
 */
internal fun progressNiceStep(span: Double, targetTicks: Double): Double {
    if (!span.isFinite() || span <= 0.0 || targetTicks <= 0.0) return 1.0
    val raw = span / targetTicks
    val magnitude = 10.0.pow(floor(log10(raw)))
    val normalized = raw / magnitude
    val nice = when {
        normalized <= 1.0 -> 1.0
        normalized <= 2.0 -> 2.0
        normalized <= 5.0 -> 5.0
        else -> 10.0
    }
    return nice * magnitude
}

/**
 * The y position of [value] between [top] and [bottom], inset by [radius] at both edges so a mark
 * stays inside the plot. A degenerate axis places its value at the vertical centre.
 */
internal fun progressAxisY(
    value: Double,
    axis: ProgressAxis,
    top: Float,
    bottom: Float,
    radius: Float
): Float {
    val span = axis.max - axis.min
    val fraction = if (span <= 0.0) 0.5 else ((value - axis.min) / span).coerceIn(0.0, 1.0)
    val usable = (bottom - top - 2f * radius).coerceAtLeast(0f)
    return top + radius + ((1.0 - fraction) * usable).toFloat()
}

/**
 * The horizontal centre for a day inside a plot that starts at [left] and ends at [right], inset by
 * [radius]. This keeps the existing [progressMarkX] contract intact for the body-weight chart.
 */
internal fun progressChartMarkX(
    day: Long,
    from: Long,
    to: Long,
    left: Float,
    right: Float,
    radius: Float
): Float {
    val width = (right - left).coerceAtLeast(0f)
    return left + progressMarkX(day, from, to, width, radius)
}

/**
 * Deterministic date labels for the selected window. The first and last day are always included, and
 * [maxLabels] caps how many fit before the caller's measured width would overlap. A shorter window
 * lists every day it has.
 */
internal fun progressDateTickDays(from: Long, to: Long, maxLabels: Int): List<Long> {
    if (to < from || maxLabels <= 0) return emptyList()
    if (from == to) return listOf(from)
    val limit = maxLabels.coerceAtLeast(2)
    if (to - from + 1 <= limit) return (from..to).toList()
    val step = (to - from).toDouble() / (limit - 1)
    return (0 until limit)
        .map { index -> (from + round(index * step).toLong()).coerceIn(from, to) }
        .distinct()
}

/** One placed date label. [left] and [row] are the final values the canvas draws with. */
internal data class ProgressDateLabelPlacement(
    val day: Long,
    val left: Float,
    val width: Float,
    val row: Int
) {
    val right: Float get() = left + width
}

/**
 * Chooses and places date labels from their actual measured widths and final mark positions.
 *
 * The first label is left-aligned to its mark and the last right-aligned, so an endpoint reserves a
 * full label width toward the neighbouring label rather than half of one. Candidate counts are
 * reduced until every same-row interval is at least [gap], keeping both civil-date endpoints. If
 * even those two cannot share a row, they are stacked on separate rows instead of overlapping,
 * shrinking, truncating or dropping a date.
 */
internal fun progressDateLabelPlacements(
    from: Long,
    to: Long,
    widthFor: (Long) -> Float,
    xFor: (Long) -> Float,
    plotLeft: Float,
    plotRight: Float,
    gap: Float,
    maxCandidates: Int = DEFAULT_MAX_DATE_LABELS
): List<ProgressDateLabelPlacement> {
    if (to < from) return emptyList()
    if (from == to) {
        return listOf(
            placeDateLabel(
                day = from,
                width = widthFor(from),
                x = xFor(from),
                dateLabelFirst = true,
                plotLeft = plotLeft,
                plotRight = plotRight
            )
        )
    }
    val upper = maxCandidates.coerceIn(2, DEFAULT_MAX_DATE_LABELS)
    for (count in upper downTo 2) {
        val days = progressDateTickDays(from, to, count)
        if (days.size < 2) continue
        val placements = placeDateLabels(days, widthFor, xFor, plotLeft, plotRight)
        if (dateLabelsSeparated(placements, gap)) return placements
    }
    // Two endpoints alone are the last single-row option. If their measured spans still intersect,
    // stack them so neither date is hidden, truncated or shrunk.
    val endpoints = placeDateLabels(
        days = listOf(from, to),
        widthFor = widthFor,
        xFor = xFor,
        plotLeft = plotLeft,
        plotRight = plotRight
    )
    if (dateLabelsSeparated(endpoints, gap)) return endpoints
    return endpoints.mapIndexed { index, placement -> placement.copy(row = index) }
}

private fun placeDateLabels(
    days: List<Long>,
    widthFor: (Long) -> Float,
    xFor: (Long) -> Float,
    plotLeft: Float,
    plotRight: Float
): List<ProgressDateLabelPlacement> = days.mapIndexed { index, day ->
    placeDateLabel(
        day = day,
        width = widthFor(day).coerceAtLeast(0f),
        x = xFor(day),
        dateLabelFirst = index == 0,
        dateLabelLast = index == days.lastIndex,
        plotLeft = plotLeft,
        plotRight = plotRight
    )
}

private fun placeDateLabel(
    day: Long,
    width: Float,
    x: Float,
    dateLabelFirst: Boolean,
    dateLabelLast: Boolean = false,
    plotLeft: Float,
    plotRight: Float
): ProgressDateLabelPlacement {
    val boundedWidth = width.coerceAtLeast(0f)
    val rawLeft = when {
        dateLabelFirst -> x
        dateLabelLast -> x - boundedWidth
        else -> x - boundedWidth / 2f
    }
    val maxLeft = (plotRight - boundedWidth).coerceAtLeast(plotLeft)
    return ProgressDateLabelPlacement(
        day = day,
        left = rawLeft.coerceIn(plotLeft, maxLeft),
        width = boundedWidth,
        row = 0
    )
}

private fun dateLabelsSeparated(placements: List<ProgressDateLabelPlacement>, gap: Float): Boolean =
    placements
        .groupBy { it.row }
        .values
        .all { row ->
            row.sortedBy { it.left }.zipWithNext().all { (earlier, later) ->
                later.left >= earlier.right + gap
            }
        }

/** Enough decimal places for the axis step, so small fractional ticks stay distinguishable. */
internal fun progressAxisLabelDecimals(axis: ProgressAxis): Int = when {
    axis.step >= 1.0 -> 0
    axis.step >= 0.1 -> 1
    else -> 2
}

/** A canonical tick value rendered in the display unit and the current locale's separator. */
internal fun progressAxisTickLabel(
    value: Double,
    weightUnit: WeightUnit,
    format: PresentationNumberFormat,
    decimals: Int
): String = formatLocalizedDecimal(weightUnit.kilogramsToDisplay(value), format, decimals)

private fun buildProgressAxis(min: Double, max: Double, step: Double): ProgressAxis {
    val ticks = mutableListOf<Double>()
    val epsilon = step * 1e-9
    var index = 0
    while (true) {
        val value = min + step * index
        if (value > max + epsilon || index > MAX_TICKS) break
        ticks += value
        index++
    }
    return ProgressAxis(min, max, step, ticks)
}

private const val VOLUME_TARGET_TICKS = 5.0
private const val ESTIMATE_TARGET_TICKS = 4.0
private const val ESTIMATE_PADDING_FRACTION = 0.05
private const val MAX_TICKS = 100
private const val DEFAULT_MAX_DATE_LABELS = 6
