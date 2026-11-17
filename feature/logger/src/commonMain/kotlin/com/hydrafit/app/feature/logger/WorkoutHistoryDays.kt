package com.hydrafit.app.feature.logger

import com.hydrafit.app.core.domain.time.localEpochDay
import com.hydrafit.app.core.domain.workout.WorkoutHistoryEntry

/**
 * The row keys that begin a new local day in an already ordered history list, so the screen can put
 * one date heading above each day's entries. Consecutive entries on the same local day share one
 * heading, including when a later page continues a day the earlier page started.
 */
internal fun historyDayStartKeys(
    entries: List<WorkoutHistoryEntry>,
    utcOffsetMillis: Long
): Set<String> {
    val starts = mutableSetOf<String>()
    var previousDay: Long? = null
    entries.forEach { entry ->
        val day = localEpochDay(entry.timestampMillis, utcOffsetMillis)
        if (day != previousDay) starts += entry.historyRowKey()
        previousDay = day
    }
    return starts
}
