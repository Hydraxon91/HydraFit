package com.hydrafit.app

import com.hydrafit.app.core.domain.engine.PlanRequest
import com.hydrafit.app.core.domain.engine.PlannerEngineId
import com.hydrafit.app.core.domain.engine.WeeklyPlan
import com.hydrafit.app.core.domain.engine.WorkoutPlannerEngine
import com.hydrafit.app.core.network.GroqApiKeyProvider
import com.hydrafit.app.core.userdata.settings.EnginePreferenceRepository
import kotlin.test.Test
import kotlin.test.assertSame
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest

class DefaultWorkoutPlannerEngineProviderTest {

    private val deterministic = FakeEngine(PlannerEngineId.DETERMINISTIC)
    private val groq = FakeEngine(PlannerEngineId.GROQ_API)

    @Test
    fun usesDeterministicWhenThatIsSelected() = runTest {
        val provider = provider(selected = PlannerEngineId.DETERMINISTIC, apiKey = "key")

        assertSame(deterministic, provider.get())
    }

    @Test
    fun usesGroqWhenSelectedAndKeyPresent() = runTest {
        val provider = provider(selected = PlannerEngineId.GROQ_API, apiKey = "key")

        assertSame(groq, provider.get())
    }

    @Test
    fun fallsBackToDeterministicWhenGroqSelectedButKeyBlank() = runTest {
        val provider = provider(selected = PlannerEngineId.GROQ_API, apiKey = "")

        assertSame(deterministic, provider.get())
    }

    @Test
    fun legacyGeminiSelectionUsesDeterministic() = runTest {
        assertSame(deterministic, provider(PlannerEngineId.GEMINI_API, "key").get())
    }

    @Test
    fun legacyOnDeviceSelectionUsesDeterministic() = runTest {
        assertSame(deterministic, provider(PlannerEngineId.LOCAL_LLM, "").get())
    }

    private fun provider(selected: PlannerEngineId, apiKey: String) =
        DefaultWorkoutPlannerEngineProvider(
            preference = FakeEnginePreferenceRepository(selected),
            deterministic = deterministic,
            groq = groq,
            apiKeyProvider = GroqApiKeyProvider { apiKey }
        )

    private class FakeEngine(override val id: PlannerEngineId) : WorkoutPlannerEngine {
        override suspend fun generatePlan(request: PlanRequest): WeeklyPlan =
            WeeklyPlan(engine = id, days = emptyList())
    }

    private class FakeEnginePreferenceRepository(private val selected: PlannerEngineId) :
        EnginePreferenceRepository {
        override suspend fun selectedEngine(): PlannerEngineId = selected

        override fun engineFlow(): Flow<PlannerEngineId> = flowOf(selected)

        override suspend fun setEngine(engine: PlannerEngineId) = Unit

        override suspend fun selectedDaysPerWeek(): Int = 4

        override fun daysPerWeekFlow(): Flow<Int> = flowOf(4)

        override suspend fun setDaysPerWeek(daysPerWeek: Int) = Unit

        override suspend fun isWorkoutDataSharingEnabled(): Boolean = false

        override fun workoutDataSharingFlow(): Flow<Boolean> = flowOf(false)

        override suspend fun setWorkoutDataSharingEnabled(enabled: Boolean) = Unit
    }
}
