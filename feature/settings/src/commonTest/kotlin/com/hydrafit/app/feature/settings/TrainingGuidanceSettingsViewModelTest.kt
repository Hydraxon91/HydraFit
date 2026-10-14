package com.hydrafit.app.feature.settings

import com.hydrafit.app.core.domain.settings.TrainingGuidancePreferenceRepository
import com.hydrafit.app.core.domain.settings.TrainingGuidancePreferences
import com.hydrafit.app.core.domain.workout.PersistedRestCountdown
import com.hydrafit.app.core.domain.workout.RestAlertScheduler
import com.hydrafit.app.core.domain.workout.RestAlertTiming
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
class TrainingGuidanceSettingsViewModelTest {
    @Test
    fun timingRefreshAndRevocationDoNotDisableAlertsOrChangeCoaching() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val preferences = FakePreferences()
            val timing = MutableStateFlow(RestAlertTiming.INEXACT)
            var refreshed = 0
            val scheduler = object : RestAlertScheduler {
                override val timing = timing
                override fun refreshTiming() {
                    refreshed++
                    timing.value = RestAlertTiming.PRECISE
                }
                override suspend fun schedule(countdown: PersistedRestCountdown) = Unit
                override fun cancel() = Unit
            }
            val viewModel = TrainingGuidanceSettingsViewModel(preferences, scheduler)
            viewModel.setAlerts(true, true)
            viewModel.setCoaching(true)
            runCurrent()
            assertEquals(RestAlertTiming.INEXACT, viewModel.state.value.alertTiming)
            viewModel.refreshAlertTiming()
            runCurrent()
            assertEquals(1, refreshed)
            assertEquals(RestAlertTiming.PRECISE, viewModel.state.value.alertTiming)
            timing.value = RestAlertTiming.INEXACT
            runCurrent()
            assertEquals(RestAlertTiming.INEXACT, viewModel.state.value.alertTiming)
            assertEquals(TrainingGuidancePreferences(true, true), preferences.state.value)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun deniedPermissionKeepsAlertsOffAndCoachingIsIndependent() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val preferences = FakePreferences()
            var cancellations = 0
            val scheduler = object : RestAlertScheduler {
                override suspend fun schedule(countdown: PersistedRestCountdown) = Unit
                override fun cancel() {
                    cancellations++
                }
            }
            val viewModel = TrainingGuidanceSettingsViewModel(preferences, scheduler)
            runCurrent()
            viewModel.setAlerts(true, false)
            runCurrent()
            assertFalse(viewModel.state.value.preferences.restAlertsEnabled)
            assertTrue(viewModel.state.value.permissionDenied)
            assertEquals(1, cancellations)
            viewModel.setAlerts(true, true)
            viewModel.setCoaching(true)
            runCurrent()
            assertEquals(TrainingGuidancePreferences(true, true), viewModel.state.value.preferences)
            assertFalse(viewModel.state.value.permissionDenied)
            viewModel.setAlerts(false, true)
            runCurrent()
            assertEquals(TrainingGuidancePreferences(false, true), preferences.state.value)
            assertEquals(2, cancellations)
        } finally {
            Dispatchers.resetMain()
        }
    }

    private class FakePreferences : TrainingGuidancePreferenceRepository {
        val state = MutableStateFlow(TrainingGuidancePreferences())
        override fun observe() = state
        override suspend fun get() = state.value
        override suspend fun setRestAlertsEnabled(enabled: Boolean) {
            state.value = state.value.copy(restAlertsEnabled = enabled)
        }
        override suspend fun setShortRestCoachingEnabled(enabled: Boolean) {
            state.value = state.value.copy(shortRestCoachingEnabled = enabled)
        }
        override suspend fun setRestCountdownEnabled(enabled: Boolean) {
            state.value = state.value.copy(restCountdownEnabled = enabled)
        }
        override suspend fun setRestCompletionSignalEnabled(enabled: Boolean) {
            state.value = state.value.copy(restCompletionSignalEnabled = enabled)
        }
    }
}
