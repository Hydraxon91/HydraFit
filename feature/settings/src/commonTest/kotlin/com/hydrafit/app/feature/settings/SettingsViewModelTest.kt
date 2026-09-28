package com.hydrafit.app.feature.settings

import com.hydrafit.app.core.domain.engine.EngineAvailability
import com.hydrafit.app.core.domain.engine.PlannerEngineId
import com.hydrafit.app.core.userdata.settings.EnginePreferenceRepository
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

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
    fun listsAvailableEnginesAndSelectsTheStoredOne() = runTest(dispatcher) {
        val viewModel = viewModel(
            available = listOf(PlannerEngineId.DETERMINISTIC, PlannerEngineId.GEMINI_API),
            stored = PlannerEngineId.GEMINI_API
        )
        advanceUntilIdle()

        assertEquals(
            listOf(PlannerEngineId.DETERMINISTIC, PlannerEngineId.GEMINI_API),
            viewModel.state.value.availableEngines
        )
        assertEquals(PlannerEngineId.GEMINI_API, viewModel.state.value.selectedEngine)
        assertTrue(viewModel.state.value.isGeminiAvailable)
    }

    @Test
    fun hidesGeminiWhenNoApiKeyIsConfigured() = runTest(dispatcher) {
        val viewModel = viewModel(
            available = listOf(PlannerEngineId.DETERMINISTIC),
            stored = PlannerEngineId.DETERMINISTIC
        )
        advanceUntilIdle()

        assertFalse(viewModel.state.value.isGeminiAvailable)
    }

    @Test
    fun selectingAnEnginePersistsIt() = runTest(dispatcher) {
        val repository = FakeEnginePreferenceRepository(PlannerEngineId.DETERMINISTIC)
        val viewModel = SettingsViewModel(
            preference = repository,
            availability = FakeEngineAvailability(
                listOf(PlannerEngineId.DETERMINISTIC, PlannerEngineId.GEMINI_API)
            )
        )
        advanceUntilIdle()

        viewModel.onEngineSelected(PlannerEngineId.GEMINI_API)
        advanceUntilIdle()

        assertEquals(PlannerEngineId.GEMINI_API, repository.stored)
        assertEquals(PlannerEngineId.GEMINI_API, viewModel.state.value.selectedEngine)
    }

    @Test
    fun fallsBackToAnAvailableEngineWhenTheStoredOneIsUnavailable() = runTest(dispatcher) {
        val viewModel = viewModel(
            available = listOf(PlannerEngineId.DETERMINISTIC),
            stored = PlannerEngineId.GEMINI_API
        )
        advanceUntilIdle()

        assertEquals(PlannerEngineId.DETERMINISTIC, viewModel.state.value.selectedEngine)
    }

    private fun viewModel(available: List<PlannerEngineId>, stored: PlannerEngineId) =
        SettingsViewModel(
            preference = FakeEnginePreferenceRepository(stored),
            availability = FakeEngineAvailability(available)
        )

    private class FakeEnginePreferenceRepository(var stored: PlannerEngineId) :
        EnginePreferenceRepository {
        override suspend fun selectedEngine(): PlannerEngineId = stored

        override suspend fun setEngine(engine: PlannerEngineId) {
            stored = engine
        }
    }

    private class FakeEngineAvailability(private val engines: List<PlannerEngineId>) :
        EngineAvailability {
        override fun availableEngines(): List<PlannerEngineId> = engines
    }
}
