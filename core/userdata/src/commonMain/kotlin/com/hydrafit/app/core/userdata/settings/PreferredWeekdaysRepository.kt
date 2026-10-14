package com.hydrafit.app.core.userdata.settings

import com.hydrafit.app.core.domain.time.DayOfWeek
import kotlinx.coroutines.flow.Flow

interface PreferredWeekdaysRepository {
    suspend fun current(): Set<DayOfWeek>?

    fun observe(): Flow<Set<DayOfWeek>?>

    /** Saves preferences and their 2–6-day planning frequency together; null preserves frequency. */
    suspend fun set(weekdays: Set<DayOfWeek>?)
}
