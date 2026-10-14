package com.hydrafit.app.core.domain.settings

import kotlinx.coroutines.flow.Flow

/** Quiet-hours defaults, 21:00-07:00 local, used until the user chooses their own interval. */
const val DEFAULT_QUIET_START_MINUTE_OF_DAY: Int = 21 * 60
const val DEFAULT_QUIET_END_MINUTE_OF_DAY: Int = 7 * 60

/**
 * Opt-in local workout reminders for dated activity in the active block.
 *
 * [reminderMinuteOfDay] is null until the user picks a time, so enabling reminders cannot silently
 * adopt an invented time. A reminder inside the enabled quiet interval is skipped rather than
 * postponed. Reminders are a portable preference; whether they can actually be posted is device
 * permission state and is not stored here.
 */
data class WorkoutReminderPreferences(
    val remindersEnabled: Boolean = false,
    val reminderMinuteOfDay: Int? = null,
    val quietHoursEnabled: Boolean = false,
    val quietStartMinuteOfDay: Int = DEFAULT_QUIET_START_MINUTE_OF_DAY,
    val quietEndMinuteOfDay: Int = DEFAULT_QUIET_END_MINUTE_OF_DAY
)

interface WorkoutReminderPreferenceRepository {
    fun observe(): Flow<WorkoutReminderPreferences>

    suspend fun get(): WorkoutReminderPreferences

    suspend fun setEnabled(enabled: Boolean)

    suspend fun setReminderMinuteOfDay(minuteOfDay: Int?)

    suspend fun setQuietHoursEnabled(enabled: Boolean)

    suspend fun setQuietHours(startMinuteOfDay: Int, endMinuteOfDay: Int)
}
