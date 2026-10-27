package com.hydrafit.app.feature.cardio

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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.hydrafit.app.core.domain.cardio.CardioActivityType
import com.hydrafit.app.core.domain.cardio.CardioDay
import com.hydrafit.app.core.domain.cardio.CardioTypeTotal
import com.hydrafit.app.core.domain.time.isoDateUtc
import com.hydrafit.app.core.domain.unit.DistanceUnit
import com.hydrafit.app.core.domain.unit.formatDistance
import hydrafit.feature.cardio.generated.resources.Res
import hydrafit.feature.cardio.generated.resources.cardio_chart_caption
import hydrafit.feature.cardio.generated.resources.cardio_chart_title
import hydrafit.feature.cardio.generated.resources.cardio_day_row
import hydrafit.feature.cardio.generated.resources.cardio_duration_hours
import hydrafit.feature.cardio.generated.resources.cardio_duration_minutes
import hydrafit.feature.cardio.generated.resources.cardio_totals_empty
import hydrafit.feature.cardio.generated.resources.cardio_totals_title
import hydrafit.feature.cardio.generated.resources.cardio_type_cycling
import hydrafit.feature.cardio.generated.resources.cardio_type_distance
import hydrafit.feature.cardio.generated.resources.cardio_type_duration
import hydrafit.feature.cardio.generated.resources.cardio_type_other
import hydrafit.feature.cardio.generated.resources.cardio_type_row
import hydrafit.feature.cardio.generated.resources.cardio_type_rowing
import hydrafit.feature.cardio.generated.resources.cardio_type_running
import hydrafit.feature.cardio.generated.resources.cardio_type_speed
import hydrafit.feature.cardio.generated.resources.cardio_type_walking
import kotlin.math.round
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

private const val CARDIO_MILLIS_PER_DAY = 24L * 60L * 60L * 1000L

/** Local civil-day label from an epoch day; the same fixed-offset reading the backends use. */
internal fun cardioDayLabel(localEpochDay: Long): String =
    isoDateUtc(localEpochDay * CARDIO_MILLIS_PER_DAY)

internal fun CardioActivityType.labelResource(): StringResource = when (this) {
    CardioActivityType.WALKING -> Res.string.cardio_type_walking
    CardioActivityType.RUNNING -> Res.string.cardio_type_running
    CardioActivityType.CYCLING -> Res.string.cardio_type_cycling
    CardioActivityType.ROWING -> Res.string.cardio_type_rowing
    CardioActivityType.OTHER -> Res.string.cardio_type_other
}

/** A recorded duration as hours and minutes, or minutes and seconds when it is under an hour. */
@Composable
internal fun cardioDurationLabel(totalSeconds: Long): String {
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) {
        stringResource(Res.string.cardio_duration_hours, hours, minutes)
    } else {
        stringResource(Res.string.cardio_duration_minutes, minutes, seconds)
    }
}

/**
 * One decimal place for a derived metres-per-second speed. Integer arithmetic keeps the format
 * independent of the platform's locale and avoids inventing more precision than the totals carry.
 */
internal fun cardioSpeedValue(metresPerSecond: Double): String {
    val tenths = round(metresPerSecond * 10.0).toLong()
    val whole = tenths / 10L
    val fraction = (tenths % 10L).let { if (it < 0L) -it else it }
    return "$whole.$fraction"
}

/** The per-type totals for the selected window; one block per type that recorded an activity. */
@Composable
internal fun CardioTypeTotals(
    totals: List<CardioTypeTotal>,
    distanceUnit: DistanceUnit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = stringResource(Res.string.cardio_totals_title),
            style = MaterialTheme.typography.titleMedium
        )
        if (totals.isEmpty()) {
            Text(stringResource(Res.string.cardio_totals_empty))
            return
        }
        totals.forEach { total ->
            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                Text(
                    text = stringResource(
                        Res.string.cardio_type_row,
                        stringResource(total.type.labelResource()),
                        total.activities
                    ),
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = stringResource(
                        Res.string.cardio_type_duration,
                        cardioDurationLabel(total.durationSeconds)
                    ),
                    style = MaterialTheme.typography.bodySmall
                )
                if (total.distanceMeters > 0L) {
                    Text(
                        text = stringResource(
                            Res.string.cardio_type_distance,
                            formatDistance(total.distanceMeters, distanceUnit)
                        ),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                total.metresPerSecond?.let { speed ->
                    Text(
                        text = stringResource(
                            Res.string.cardio_type_speed,
                            cardioSpeedValue(speed)
                        ),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

/**
 * One bar per recorded day at its actual civil day, scaled to the window's longest recorded day.
 * Days without an activity are simply absent: nothing is interpolated or carried forward, and the
 * canvas is decorative (`clearAndSetSemantics`), so [CardioDayTable] is the accessible numeric
 * equivalent.
 */
@Composable
internal fun CardioDailyChart(
    days: List<CardioDay>,
    dayFrom: Long,
    dayTo: Long,
    modifier: Modifier = Modifier
) {
    if (days.isEmpty()) return
    val max = days.maxOf { it.durationSeconds }.coerceAtLeast(1L)
    val color = MaterialTheme.colorScheme.primary
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(160.dp)
            .clearAndSetSemantics {}
    ) {
        val span = (dayTo - dayFrom).coerceAtLeast(1L)
        val slot = size.width / (span + 1L).toFloat()
        val barWidth = (slot * 0.6f).coerceAtLeast(1.dp.toPx())
        days.forEach { day ->
            val index = (day.epochDay - dayFrom).coerceIn(0L, span)
            val left = index * slot + (slot - barWidth) / 2f
            val fraction = day.durationSeconds.toFloat() / max.toFloat()
            val barHeight = (size.height * fraction).coerceAtLeast(1.dp.toPx())
            drawRect(
                color = color,
                topLeft = Offset(left, size.height - barHeight),
                size = Size(barWidth, barHeight)
            )
        }
    }
}

/** The accessible numeric equivalent of the chart: one row per recorded day, newest first. */
@Composable
internal fun CardioDayTable(
    days: List<CardioDay>,
    distanceUnit: DistanceUnit,
    modifier: Modifier = Modifier
) {
    val ordered = days.sortedByDescending { it.epochDay }
    Column(modifier = modifier) {
        Text(
            text = stringResource(Res.string.cardio_chart_title),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(vertical = 4.dp)
        )
        ordered.forEach { day ->
            Text(
                text = stringResource(
                    Res.string.cardio_day_row,
                    cardioDayLabel(day.epochDay),
                    day.activities,
                    cardioDurationLabel(day.durationSeconds)
                ),
                style = MaterialTheme.typography.bodySmall
            )
            if (day.distanceMeters > 0L) {
                Text(
                    text = stringResource(
                        Res.string.cardio_type_distance,
                        formatDistance(day.distanceMeters, distanceUnit)
                    ),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

/** The chart's explanatory caption, shown above the canvas. */
@Composable
internal fun CardioChartCaption(modifier: Modifier = Modifier) {
    Text(
        text = stringResource(Res.string.cardio_chart_caption),
        style = MaterialTheme.typography.bodySmall,
        modifier = modifier.fillMaxWidth()
    )
}
