package com.hydrafit.app.core.llm

import com.hydrafit.app.core.domain.engine.OnDevicePlanProgress
import com.hydrafit.app.core.domain.engine.PlannerEngineId
import com.hydrafit.app.core.userdata.settings.EnginePreferenceRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest

class OnDeviceEngineLifecycleTest {

    @Test
    fun releasesOnlyForNonLocalSelections() = runTest {
        val generator = FakeGenerator()
        val lifecycle = OnDeviceEngineLifecycle(
            generator = generator,
            preference = FakePreference(PlannerEngineId.LOCAL_LLM),
            scope = backgroundScope
        )

        lifecycle.onEngineSelected(PlannerEngineId.DETERMINISTIC)
        lifecycle.onEngineSelected(PlannerEngineId.GEMINI_API)
        lifecycle.onEngineSelected(PlannerEngineId.LOCAL_LLM)
        lifecycle.onEngineSelected(PlannerEngineId.LOCAL_LLM)

        assertEquals(2, generator.releaseCalls)
    }

    @Test
    fun startReleasesWhenTheStoredEngineIsNotLocal() = runTest {
        val generator = FakeGenerator()
        val lifecycle = OnDeviceEngineLifecycle(
            generator = generator,
            preference = FakePreference(PlannerEngineId.DETERMINISTIC),
            scope = this
        )

        lifecycle.start()
        advanceUntilIdle()

        assertEquals(1, generator.releaseCalls)
    }

    private class FakeGenerator : OnDeviceTextGenerator {
        var releaseCalls: Int = 0
            private set

        override fun isAvailable(): Boolean = true

        override fun generate(
            prompt: String,
            jsonSchema: String?,
            onProgress: (OnDevicePlanProgress) -> Unit
        ): String = ""

        override fun release() {
            releaseCalls++
        }
    }

    private class FakePreference(private val engine: PlannerEngineId) : EnginePreferenceRepository {
        override suspend fun selectedEngine(): PlannerEngineId = engine

        override fun engineFlow(): Flow<PlannerEngineId> = flowOf(engine)

        override suspend fun setEngine(engine: PlannerEngineId) = Unit

        override suspend fun selectedDaysPerWeek(): Int = 4

        override fun daysPerWeekFlow(): Flow<Int> = flowOf(4)

        override suspend fun setDaysPerWeek(daysPerWeek: Int) = Unit

        override suspend fun isWorkoutDataSharingEnabled(): Boolean = false

        override fun workoutDataSharingFlow(): Flow<Boolean> = flowOf(false)

        override suspend fun setWorkoutDataSharingEnabled(enabled: Boolean) = Unit
    }
}
