package com.hydrafit.app.core.domain.cardio

import com.hydrafit.app.core.domain.time.localEpochDay

/** One civil day of recorded cardio, aggregated per activity type. */
data class CardioDay(
    val epochDay: Long,
    val activities: Int,
    val durationSeconds: Long,
    val distanceMeters: Long
)

/** One activity type's totals across the requested window. */
data class CardioTypeTotal(
    val type: CardioActivityType,
    val activities: Int,
    val durationSeconds: Long,
    val distanceMeters: Long,
    /** Duration of only activities with recorded distance, for comparable speed. */
    val distanceDurationSeconds: Long
) {
    /**
     * Metres per second from the totals, or null when the window recorded no distance at all. It is
     * deliberately computed from matching totals rather than by averaging per-activity pace, which
     * would weight a short walk as heavily as a long run.
     */
    val metresPerSecond: Double?
        get() = if (distanceMeters > 0 && distanceDurationSeconds > 0) {
            distanceMeters.toDouble() / distanceDurationSeconds
        } else {
            null
        }
}

/** The chart input for one inclusive civil-day window. */
data class CardioProgress(
    val days: List<CardioDay>,
    val totalsByType: List<CardioTypeTotal>,
    val fromEpochDay: Long,
    val throughEpochDay: Long
)

/**
 * Aggregates recorded activities into per-day and per-type totals. Pure: it reads no repository or
 * clock. Activities outside the window are excluded from both, and a future activity is never
 * projected into an earlier day.
 */
fun buildCardioProgress(
    activities: List<CardioActivity>,
    fromEpochDay: Long,
    throughEpochDay: Long,
    utcOffsetMillis: Long
): CardioProgress {
    require(fromEpochDay <= throughEpochDay) { "A cardio window must not be inverted" }
    val inWindow = activities.filter { activity ->
        val day = localEpochDay(activity.startedAtMillis, utcOffsetMillis)
        day in fromEpochDay..throughEpochDay
    }
    val days = inWindow
        .groupBy { localEpochDay(it.startedAtMillis, utcOffsetMillis) }
        .toList()
        .sortedBy { it.first }
        .map { (day, recorded) ->
            CardioDay(
                epochDay = day,
                activities = recorded.size,
                durationSeconds = recorded.sumOf { it.durationSeconds.toLong() },
                distanceMeters = recorded.sumOf { it.distanceMeters?.toLong() ?: 0L }
            )
        }
    val totals = inWindow
        .groupBy { it.type }
        .map { (type, recorded) ->
            CardioTypeTotal(
                type = type,
                activities = recorded.size,
                durationSeconds = recorded.sumOf { it.durationSeconds.toLong() },
                distanceMeters = recorded.sumOf { it.distanceMeters?.toLong() ?: 0L },
                distanceDurationSeconds = recorded.filter { it.distanceMeters != null }
                    .sumOf { it.durationSeconds.toLong() }
            )
        }
        .sortedBy { it.type.ordinal }
    return CardioProgress(
        days = days,
        totalsByType = totals,
        fromEpochDay = fromEpochDay,
        throughEpochDay = throughEpochDay
    )
}
