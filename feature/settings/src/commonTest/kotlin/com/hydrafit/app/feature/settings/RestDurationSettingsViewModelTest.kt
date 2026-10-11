package com.hydrafit.app.feature.settings

import com.hydrafit.app.core.domain.settings.DEFAULT_REST_SECONDS
import com.hydrafit.app.core.domain.settings.ObserveGlobalRestDurationUseCase
import com.hydrafit.app.core.domain.settings.RestPreferenceRepository
import com.hydrafit.app.core.domain.settings.SetGlobalRestDurationUseCase
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
class RestDurationSettingsViewModelTest {
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
    fun observesAndSavesGlobalDefaultWithinSupportedRange() = runTest(dispatcher) {
        val repository = FakeRestPreferenceRepository()
        val viewModel = RestDurationSettingsViewModel(
            ObserveGlobalRestDurationUseCase(repository),
            SetGlobalRestDurationUseCase(repository)
        )
        advanceUntilIdle()
        assertEquals(DEFAULT_REST_SECONDS.toString(), viewModel.state.value.seconds)

        viewModel.onSecondsChanged("180")
        assertTrue(viewModel.state.value.canSave)
        viewModel.save()
        advanceUntilIdle()

        assertEquals(180L, repository.global.value)
        assertEquals("180", viewModel.state.value.seconds)
        viewModel.onSecondsChanged("0")
        assertFalse(viewModel.state.value.canSave)
    }

    private class FakeRestPreferenceRepository : RestPreferenceRepository {
        val global = MutableStateFlow(DEFAULT_REST_SECONDS)

        override fun globalDefaultSecondsFlow(): Flow<Long> = global

        override suspend fun globalDefaultSeconds(): Long = global.value

        override suspend fun setGlobalDefaultSeconds(seconds: Long) {
            global.value = seconds
        }

        override suspend fun exerciseOverrideSeconds(exerciseId: String): Long? = null

        override suspend fun setExerciseOverrideSeconds(exerciseId: String, seconds: Long) = Unit

        override suspend fun clearExerciseOverride(exerciseId: String) = Unit
    }
}
