package com.hydrafit.app

import com.hydrafit.app.core.domain.engine.PlannerEngineId
import com.hydrafit.app.core.domain.engine.WorkoutPlannerEngine
import com.hydrafit.app.core.domain.engine.WorkoutPlannerEngineProvider
import com.hydrafit.app.core.network.GroqApiKeyProvider
import com.hydrafit.app.core.userdata.settings.EnginePreferenceRepository

class DefaultWorkoutPlannerEngineProvider(
    private val preference: EnginePreferenceRepository,
    private val deterministic: WorkoutPlannerEngine,
    private val groq: WorkoutPlannerEngine,
    private val localLlm: WorkoutPlannerEngine,
    private val apiKeyProvider: GroqApiKeyProvider
) : WorkoutPlannerEngineProvider {

    override suspend fun get(): WorkoutPlannerEngine = when (preference.selectedEngine()) {
        PlannerEngineId.GEMINI_API -> deterministic
        PlannerEngineId.GROQ_API ->
            if (apiKeyProvider.groqApiKey().isNotBlank()) groq else deterministic
        PlannerEngineId.LOCAL_LLM -> localLlm
        PlannerEngineId.DETERMINISTIC -> deterministic
    }
}
