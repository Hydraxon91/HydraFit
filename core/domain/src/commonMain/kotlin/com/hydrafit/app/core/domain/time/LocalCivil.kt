package com.hydrafit.app.core.domain.time

private const val MILLIS_PER_DAY = 24L * 60L * 60L * 1000L
private const val MILLIS_PER_HOUR = 60L * 60L * 1000L
private const val MILLIS_PER_MINUTE = 60L * 1000L

/**
 * Converts a local wall-clock civil date/time to a UTC epoch-millis instant.
 *
 * The whole conversion uses the single supplied [utcOffsetMillis] (the offset at the current
 * instant); there is no historical zone or DST lookup. A local hour that is skipped or repeated
 * across a DST transition therefore maps to the same fixed-offset instant, and a chosen date on the
 * far side of a transition from now can be off the true wall time by about an hour. The local
 * calendar day still round-trips exactly through [localEpochDay], because that applies the same
 * offset.
 *
 * [month] must be 1..12 and [day] 1..31 (validated in [daysFromCivil]); [hour] must be 0..23 and
 * [minute] 0..59. An impossible day for its month (e.g. 2024-02-30) is not rejected and normalizes
 * forward.
 */
fun localCivilToEpochMillis(
    year: Int,
    month: Int,
    day: Int,
    hour: Int,
    minute: Int,
    utcOffsetMillis: Long
): Long {
    require(hour in 0..23) { "hour must be in 0..23, was $hour" }
    require(minute in 0..59) { "minute must be in 0..59, was $minute" }
    val localMillis = daysFromCivil(year, month, day) * MILLIS_PER_DAY +
        hour * MILLIS_PER_HOUR +
        minute * MILLIS_PER_MINUTE
    return localMillis - utcOffsetMillis
}
