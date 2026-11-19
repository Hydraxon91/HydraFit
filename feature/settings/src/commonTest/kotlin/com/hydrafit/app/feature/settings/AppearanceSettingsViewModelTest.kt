package com.hydrafit.app.feature.settings

import com.hydrafit.app.core.userdata.settings.AppColorMode
import com.hydrafit.app.core.userdata.settings.AppThemeId
import com.hydrafit.app.core.userdata.settings.AppThemePreference
import com.hydrafit.app.core.userdata.settings.InMemoryAppThemePreferenceStore
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
class AppearanceSettingsViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun startsOnTheDefaultThemeFollowingTheDevice() = runTest(dispatcher) {
        val viewModel = AppearanceSettingsViewModel(InMemoryAppThemePreferenceStore())
        assertEquals(
            AppThemePreference(AppThemeId.QUIET_STRENGTH, AppColorMode.SYSTEM),
            viewModel.preference.value
        )
    }

    @Test
    fun selectingAThemeAndAModeReachesTheStoreWithoutDroppingTheOther() = runTest(dispatcher) {
        val store = InMemoryAppThemePreferenceStore()
        val viewModel = AppearanceSettingsViewModel(store)

        viewModel.onThemeSelected(AppThemeId.GOLD_NUVA)
        advanceUntilIdle()
        viewModel.onColorModeSelected(AppColorMode.DARK)
        advanceUntilIdle()

        assertEquals(
            AppThemePreference(AppThemeId.GOLD_NUVA, AppColorMode.DARK),
            store.preference.value
        )
        assertEquals(store.preference.value, viewModel.preference.value)
    }

    @Test
    fun everyThemeAndModeHasAUserFacingLabel() {
        AppThemeId.entries.forEach { it.labelResource() }
        AppColorMode.entries.forEach { it.labelResource() }
    }
}
