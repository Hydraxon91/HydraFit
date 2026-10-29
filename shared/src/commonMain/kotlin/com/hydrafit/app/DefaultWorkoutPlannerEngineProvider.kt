package com.hydrafit.app

import com.hydrafit.app.core.domain.engine.PlanFailureReason
import com.hydrafit.app.core.domain.engine.PlanGenerationException
import com.hydrafit.app.core.domain.engine.PlannerEngineId
import com.hydrafit.app.core.domain.engine.WorkoutPlannerEngine
import com.hydrafit.app.core.domain.engine.WorkoutPlannerEngineProvider
import com.hydrafit.app.core.network.GroqApiKeyProvider
import com.hydrafit.app.core.userdata.settings.EnginePreferenceRepository

class DefaultWorkoutPlannerEngineProvider(
    private val preference: EnginePreferenceRepository,
    private val deterministic: WorkoutPlannerEngine,
    private val groq: WorkoutPlannerEngine,
    private val apiKeyProvider: GroqApiKeyProvider
) : WorkoutPlannerEngineProvider {

    override suspend fun get(): WorkoutPlannerEngine = when (preference.selectedEngine()) {
        // Retired cloud and on-device engines stay readable in history but are never selected.
        PlannerEngineId.GEMINI_API, PlannerEngineId.LOCAL_LLM -> deterministic
        // A saved cloud selection without its key is reported as such: substituting the built-in
        // planner here made an absent key look like a provider response the app could not use.
        PlannerEngineId.GROQ_API ->
            if (apiKeyProvider.groqApiKey().isNotBlank()) {
                groq
            } else {
                throw PlanGenerationException(
                    transient = false,
                    reason = PlanFailureReason.MISSING_API_KEY,
                    message = "No Groq API key is saved on this device"
                )
            }
        PlannerEngineId.DETERMINISTIC -> deterministic
    }
}
