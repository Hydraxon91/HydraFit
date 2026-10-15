package com.hydrafit.app.core.database

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.hydrafit.app.core.domain.settings.WorkoutReminderPreferences
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest

class SqlDelightWorkoutReminderPreferenceRepositoryTest {
    @Test
    fun defaultsOffWithNoTimeChosenAndUpdatesEachDecisionIndependently() = runTest {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        try {
            HydraFitDatabase.Schema.create(driver)
            val repository = SqlDelightWorkoutReminderPreferenceRepository(HydraFitDatabase(driver))
            assertEquals(WorkoutReminderPreferences(), repository.observe().first())

            // Enabling before a time is chosen must not invent one.
            repository.setEnabled(true)
            assertEquals(true, repository.get().remindersEnabled)
            assertNull(repository.get().reminderMinuteOfDay)

            repository.setReminderMinuteOfDay(6 * 60 + 30)
            repository.setQuietHoursEnabled(true)
            repository.setQuietHours(22 * 60, 6 * 60)
            assertEquals(
                WorkoutReminderPreferences(
                    remindersEnabled = true,
                    reminderMinuteOfDay = 390,
                    quietHoursEnabled = true,
                    quietStartMinuteOfDay = 1320,
                    quietEndMinuteOfDay = 360
                ),
                repository.observe().first()
            )

            // Clearing the time keeps the other decisions.
            repository.setReminderMinuteOfDay(null)
            assertNull(repository.get().reminderMinuteOfDay)
            assertEquals(true, repository.get().remindersEnabled)
            assertEquals(1320, repository.get().quietStartMinuteOfDay)

            repository.setEnabled(false)
            repository.setQuietHoursEnabled(false)
            assertEquals(
                WorkoutReminderPreferences(
                    reminderMinuteOfDay = null,
                    quietStartMinuteOfDay = 1320,
                    quietEndMinuteOfDay = 360
                ),
                repository.get()
            )
        } finally {
            driver.close()
        }
    }
}
