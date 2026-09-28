package com.hydrafit.app.feature.settings

import com.hydrafit.app.core.domain.engine.EngineAvailability
import com.hydrafit.app.core.domain.engine.PlannerEngineId
import com.hydrafit.app.core.userdata.settings.ApiKeyStore
import com.hydrafit.app.core.userdata.settings.EnginePreferenceRepository
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
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
            ),
            apiKeyStore = FakeApiKeyStore()
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

    @Test
    fun savingAnApiKeyStoresItAndClearsTheInput() = runTest(dispatcher) {
        val store = FakeApiKeyStore()
        val viewModel = SettingsViewModel(
            preference = FakeEnginePreferenceRepository(PlannerEngineId.DETERMINISTIC),
            availability = FakeEngineAvailability(listOf(PlannerEngineId.DETERMINISTIC)),
            apiKeyStore = store
        )
        advanceUntilIdle()

        viewModel.onApiKeyChanged("  secret-key  ")
        viewModel.saveApiKey()
        advanceUntilIdle()

        assertEquals("secret-key", store.value)
        assertEquals("", viewModel.state.value.apiKeyInput)
        assertTrue(viewModel.state.value.apiKeyConfigured)
    }

    @Test
    fun blankApiKeyIsNotSaved() = runTest(dispatcher) {
        val store = FakeApiKeyStore()
        val viewModel = SettingsViewModel(
            preference = FakeEnginePreferenceRepository(PlannerEngineId.DETERMINISTIC),
            availability = FakeEngineAvailability(listOf(PlannerEngineId.DETERMINISTIC)),
            apiKeyStore = store
        )
        advanceUntilIdle()

        viewModel.onApiKeyChanged("   ")
        viewModel.saveApiKey()
        advanceUntilIdle()

        assertEquals(null, store.value)
        assertFalse(viewModel.state.value.apiKeyConfigured)
    }

    @Test
    fun clearingTheApiKeyRemovesIt() = runTest(dispatcher) {
        val store = FakeApiKeyStore(value = "existing")
        val viewModel = SettingsViewModel(
            preference = FakeEnginePreferenceRepository(PlannerEngineId.DETERMINISTIC),
            availability = FakeEngineAvailability(listOf(PlannerEngineId.DETERMINISTIC)),
            apiKeyStore = store
        )
        advanceUntilIdle()
        assertTrue(viewModel.state.value.apiKeyConfigured)

        viewModel.clearApiKey()
        advanceUntilIdle()

        assertEquals(null, store.value)
        assertFalse(viewModel.state.value.apiKeyConfigured)
    }

    private fun viewModel(available: List<PlannerEngineId>, stored: PlannerEngineId) =
        SettingsViewModel(
            preference = FakeEnginePreferenceRepository(stored),
            availability = FakeEngineAvailability(available),
            apiKeyStore = FakeApiKeyStore()
        )

    private class FakeApiKeyStore(var value: String? = null) : ApiKeyStore {
        override fun load(): String? = value

        override fun save(apiKey: String) {
            value = apiKey
        }

        override fun clear() {
            value = null
        }
    }

    private class FakeEnginePreferenceRepository(
        var stored: PlannerEngineId,
        var storedDaysPerWeek: Int = 4
    ) : EnginePreferenceRepository {
        override suspend fun selectedEngine(): PlannerEngineId = stored

        override fun engineFlow(): Flow<PlannerEngineId> = flowOf(stored)

        override suspend fun setEngine(engine: PlannerEngineId) {
            stored = engine
        }

        override suspend fun selectedDaysPerWeek(): Int = storedDaysPerWeek

        override fun daysPerWeekFlow(): Flow<Int> = flowOf(storedDaysPerWeek)

        override suspend fun setDaysPerWeek(daysPerWeek: Int) {
            storedDaysPerWeek = daysPerWeek
        }
    }

    private class FakeEngineAvailability(private val engines: List<PlannerEngineId>) :
        EngineAvailability {
        override fun availableEngines(): List<PlannerEngineId> = engines
    }
}
