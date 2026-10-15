package com.hydrafit.app.feature.settings

import com.hydrafit.app.core.domain.schedule.WorkoutReminderScheduler
import com.hydrafit.app.core.domain.settings.WorkoutReminderPreferenceRepository
import com.hydrafit.app.core.domain.settings.WorkoutReminderPreferences
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
class WorkoutReminderSettingsViewModelTest {
    @Test
    fun deniedPermissionKeepsRemindersOffAndExplainsWhy() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val preferences = FakeReminderPreferences()
            val viewModel = WorkoutReminderSettingsViewModel(preferences, FakeReminderScheduler())

            viewModel.setEnabled(true, false)
            runCurrent()
            assertFalse(viewModel.state.value.preferences.remindersEnabled)
            assertTrue(viewModel.state.value.permissionDenied)

            viewModel.setEnabled(true, true)
            runCurrent()
            assertTrue(viewModel.state.value.preferences.remindersEnabled)
            assertFalse(viewModel.state.value.permissionDenied)

            viewModel.setEnabled(false, true)
            runCurrent()
            assertFalse(viewModel.state.value.preferences.remindersEnabled)
            assertFalse(viewModel.state.value.permissionDenied)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun timeAndQuietHoursPersistIndependentlyAndPlatformRefreshIsReported() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val preferences = FakeReminderPreferences()
            val scheduler = FakeReminderScheduler()
            val viewModel = WorkoutReminderSettingsViewModel(preferences, scheduler)

            viewModel.setEnabled(true, true)
            viewModel.setReminderMinuteOfDay(minutes(7, 30))
            viewModel.setQuietHoursEnabled(true)
            viewModel.setQuietHours(minutes(21), minutes(6))
            runCurrent()

            assertEquals(
                WorkoutReminderPreferences(
                    remindersEnabled = true,
                    reminderMinuteOfDay = minutes(7, 30),
                    quietHoursEnabled = true,
                    quietStartMinuteOfDay = minutes(21),
                    quietEndMinuteOfDay = minutes(6)
                ),
                preferences.state.value
            )
            // An interval edit is a separate choice, not a reset of the other end.
            viewModel.setQuietHours(minutes(22), minutes(6))
            runCurrent()
            assertEquals(minutes(22), preferences.state.value.quietStartMinuteOfDay)
            assertEquals(minutes(6), preferences.state.value.quietEndMinuteOfDay)

            viewModel.refreshDelivery()
            assertEquals(1, scheduler.reports)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun chosenMinutesAreShownAsATwentyFourHourClock() {
        assertEquals("00:00", clockLabel(0))
        assertEquals("07:30", clockLabel(minutes(7, 30)))
        assertEquals("21:05", clockLabel(minutes(21) + 5))
        assertEquals("23:59", clockLabel(minutes(23, 59)))
    }

    private fun minutes(hours: Int, minutes: Int = 0) = hours * 60 + minutes

    private class FakeReminderPreferences(
        initial: WorkoutReminderPreferences = WorkoutReminderPreferences()
    ) : WorkoutReminderPreferenceRepository {
        val state = MutableStateFlow(initial)
        override fun observe(): Flow<WorkoutReminderPreferences> = state
        override suspend fun get() = state.value
        override suspend fun setEnabled(enabled: Boolean) {
            state.value = state.value.copy(remindersEnabled = enabled)
        }

        override suspend fun setReminderMinuteOfDay(minuteOfDay: Int?) {
            state.value = state.value.copy(reminderMinuteOfDay = minuteOfDay)
        }

        override suspend fun setQuietHoursEnabled(enabled: Boolean) {
            state.value = state.value.copy(quietHoursEnabled = enabled)
        }

        override suspend fun setQuietHours(startMinuteOfDay: Int, endMinuteOfDay: Int) {
            state.value = state.value.copy(
                quietStartMinuteOfDay = startMinuteOfDay,
                quietEndMinuteOfDay = endMinuteOfDay
            )
        }
    }

    private class FakeReminderScheduler : WorkoutReminderScheduler {
        var reports = 0
        private val changes = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

        override val deliveryChanges: Flow<Unit> = changes.asSharedFlow()

        override fun reportDeliveryChange() {
            reports++
            changes.tryEmit(Unit)
        }

        override fun canNotify() = false
        override suspend fun schedule(atEpochMillis: Long) = Unit
        override fun cancel() = Unit
        override suspend fun notify(workoutNames: List<String>) = false
    }
}
