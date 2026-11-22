package com.hydrafit.app.feature.settings

import com.hydrafit.app.core.userdata.settings.AppLanguage
import com.hydrafit.app.core.userdata.settings.InMemoryAppLanguageStore
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
class LanguageSettingsViewModelTest {
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
    fun startsOnTheDeviceLanguage() = runTest(dispatcher) {
        val viewModel = LanguageSettingsViewModel(InMemoryAppLanguageStore())
        assertEquals(AppLanguage.SYSTEM, viewModel.language.value)
    }

    @Test
    fun selectingALanguageReachesTheStoreAndCanBeUndone() = runTest(dispatcher) {
        val store = InMemoryAppLanguageStore()
        val viewModel = LanguageSettingsViewModel(store)

        viewModel.onLanguageSelected(AppLanguage.HUNGARIAN)
        advanceUntilIdle()
        assertEquals(AppLanguage.HUNGARIAN, store.language.value)
        assertEquals(store.language.value, viewModel.language.value)

        viewModel.onLanguageSelected(AppLanguage.SYSTEM)
        advanceUntilIdle()
        assertEquals(AppLanguage.SYSTEM, store.language.value)
    }

    @Test
    fun everyLanguageHasAUserFacingLabel() {
        AppLanguage.entries.forEach { it.labelResource() }
    }
}
