package com.hydrafit.app.core.database

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToOneOrNull
import com.hydrafit.app.core.domain.settings.WorkoutReminderPreferenceRepository
import com.hydrafit.app.core.domain.settings.WorkoutReminderPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map

class SqlDelightWorkoutReminderPreferenceRepository(database: HydraFitDatabase) :
    WorkoutReminderPreferenceRepository {
    private val queries = database.workoutReminderPreferenceQueries

    override fun observe() = queries.selectPreferences().asFlow()
        .mapToOneOrNull(Dispatchers.Default)
        .map { it.toPreferences() }

    override suspend fun get() = queries.selectPreferences().executeAsOneOrNull().toPreferences()

    override suspend fun setEnabled(enabled: Boolean) {
        queries.setEnabled(if (enabled) 1L else 0L)
    }

    override suspend fun setReminderMinuteOfDay(minuteOfDay: Int?) {
        queries.setReminderMinuteOfDay(minuteOfDay?.toLong())
    }

    override suspend fun setQuietHoursEnabled(enabled: Boolean) {
        queries.setQuietHoursEnabled(if (enabled) 1L else 0L)
    }

    override suspend fun setQuietHours(startMinuteOfDay: Int, endMinuteOfDay: Int) {
        queries.setQuietHours(startMinuteOfDay.toLong(), endMinuteOfDay.toLong())
    }

    private fun WorkoutReminderPreference?.toPreferences(): WorkoutReminderPreferences =
        if (this == null) {
            WorkoutReminderPreferences()
        } else {
            WorkoutReminderPreferences(
                remindersEnabled = remindersEnabled == 1L,
                reminderMinuteOfDay = reminderMinuteOfDay?.toInt(),
                quietHoursEnabled = quietHoursEnabled == 1L,
                quietStartMinuteOfDay = quietStartMinuteOfDay.toInt(),
                quietEndMinuteOfDay = quietEndMinuteOfDay.toInt()
            )
        }
}
