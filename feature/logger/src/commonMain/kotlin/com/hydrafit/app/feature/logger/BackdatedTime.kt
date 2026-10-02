package com.hydrafit.app.feature.logger

import com.hydrafit.app.core.domain.time.civilFromDays
import com.hydrafit.app.core.domain.time.localCivilToEpochMillis
import com.hydrafit.app.core.domain.time.localEpochDay

internal const val MILLIS_PER_DAY = 24L * 60L * 60L * 1000L
private const val MILLIS_PER_HOUR = 60L * 60L * 1000L
private const val MILLIS_PER_MINUTE = 60L * 1000L

/** The local civil date and time of an instant, in the supplied offset. */
internal data class LocalDateTimeParts(
    val year: Int,
    val month: Int,
    val day: Int,
    val hour: Int,
    val minute: Int
)

/**
 * Combines the date picker's start-of-day UTC millis with a local wall-clock time into a UTC
 * epoch-millis instant. The picker's date millis is a whole number of days, so it maps back to the
 * civil date and then through [localCivilToEpochMillis] (the single fixed-offset conversion).
 */
internal fun pickedLocalDateTimeToEpochMillis(
    dateStartOfDayUtcMillis: Long,
    hour: Int,
    minute: Int,
    utcOffsetMillis: Long
): Long {
    val (year, month, day) = civilFromDays(dateStartOfDayUtcMillis / MILLIS_PER_DAY)
    return localCivilToEpochMillis(year, month, day, hour, minute, utcOffsetMillis)
}

/** The start-of-day UTC millis of [epochMillis]'s local date, matching the date picker's input. */
internal fun localDateStartOfDayUtcMillis(epochMillis: Long, utcOffsetMillis: Long): Long =
    localEpochDay(epochMillis, utcOffsetMillis) * MILLIS_PER_DAY

/** Splits [epochMillis] into its local civil date and time of day. */
internal fun localDateTimeParts(epochMillis: Long, utcOffsetMillis: Long): LocalDateTimeParts {
    val localDay = localEpochDay(epochMillis, utcOffsetMillis)
    val (year, month, day) = civilFromDays(localDay)
    val millisIntoDay = epochMillis + utcOffsetMillis - localDay * MILLIS_PER_DAY
    return LocalDateTimeParts(
        year = year,
        month = month,
        day = day,
        hour = (millisIntoDay / MILLIS_PER_HOUR).toInt(),
        minute = ((millisIntoDay % MILLIS_PER_HOUR) / MILLIS_PER_MINUTE).toInt()
    )
}
