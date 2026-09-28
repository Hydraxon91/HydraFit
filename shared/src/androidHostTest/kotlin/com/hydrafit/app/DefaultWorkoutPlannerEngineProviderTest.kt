package com.hydrafit.app

import com.hydrafit.app.core.domain.engine.PlanRequest
import com.hydrafit.app.core.domain.engine.PlannerEngineId
import com.hydrafit.app.core.domain.engine.WeeklyPlan
import com.hydrafit.app.core.domain.engine.WorkoutPlannerEngine
import com.hydrafit.app.core.network.ApiKeyProvider
import com.hydrafit.app.core.userdata.settings.EnginePreferenceRepository
import kotlin.test.Test
import kotlin.test.assertSame
import kotlinx.coroutines.test.runTest

class DefaultWorkoutPlannerEngineProviderTest {

    private val deterministic = FakeEngine(PlannerEngineId.DETERMINISTIC)
    private val gemini = FakeEngine(PlannerEngineId.GEMINI_API)

    @Test
    fun usesDeterministicWhenThatIsSelected() = runTest {
        val provider = provider(selected = PlannerEngineId.DETERMINISTIC, apiKey = "key")

        assertSame(deterministic, provider.get())
    }

    @Test
    fun usesGeminiWhenSelectedAndKeyPresent() = runTest {
        val provider = provider(selected = PlannerEngineId.GEMINI_API, apiKey = "key")

        assertSame(gemini, provider.get())
    }

    @Test
    fun fallsBackToDeterministicWhenGeminiSelectedButKeyBlank() = runTest {
        val provider = provider(selected = PlannerEngineId.GEMINI_API, apiKey = "")

        assertSame(deterministic, provider.get())
    }

    private fun provider(selected: PlannerEngineId, apiKey: String) =
        DefaultWorkoutPlannerEngineProvider(
            preference = FakeEnginePreferenceRepository(selected),
            deterministic = deterministic,
            gemini = gemini,
            apiKeyProvider = ApiKeyProvider { apiKey }
        )

    private class FakeEngine(override val id: PlannerEngineId) : WorkoutPlannerEngine {
        override suspend fun generatePlan(request: PlanRequest): WeeklyPlan =
            WeeklyPlan(engine = id, days = emptyList())
    }

    private class FakeEnginePreferenceRepository(private val selected: PlannerEngineId) :
        EnginePreferenceRepository {
        override suspend fun selectedEngine(): PlannerEngineId = selected

        override suspend fun setEngine(engine: PlannerEngineId) = Unit
    }
}
