package com.hydrafit.app.feature.routines

import android.icu.util.Calendar
import android.icu.util.TimeZone
import com.hydrafit.app.core.domain.time.civilFromDays

/**
 * Resolves one local civil minute to a UTC instant in the device's current zone.
 *
 * The zone's own rules decide a wall time a DST transition skips or repeats: a skipped time resolves
 * forward to the next valid wall time, and a repeated time resolves to its first occurrence. Only the
 * device's current zone is consulted, so an instant stored earlier is not reinterpreted after a zone
 * change.
 */
fun localMinuteEpochMillis(localEpochDay: Long, minuteOfDay: Int): Long =
    localMinuteEpochMillis(localEpochDay, minuteOfDay, TimeZone.getDefault())

/** The zone-taking seam [localMinuteEpochMillis] and its tests resolve against. */
internal fun localMinuteEpochMillis(localEpochDay: Long, minuteOfDay: Int, zone: TimeZone): Long {
    require(minuteOfDay in 0..1439) { "minuteOfDay must be in 0..1439, was $minuteOfDay" }
    val (year, month, day) = civilFromDays(localEpochDay)
    return Calendar.getInstance(zone).apply {
        isLenient = true
        setRepeatedWallTimeOption(Calendar.WALLTIME_FIRST)
        setSkippedWallTimeOption(Calendar.WALLTIME_NEXT_VALID)
        clear()
        set(year, month - 1, day, minuteOfDay / MINUTES_PER_HOUR, minuteOfDay % MINUTES_PER_HOUR)
    }.timeInMillis
}

private const val MINUTES_PER_HOUR = 60
