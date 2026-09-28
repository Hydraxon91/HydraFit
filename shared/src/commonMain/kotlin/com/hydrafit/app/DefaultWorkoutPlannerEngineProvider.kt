package com.hydrafit.app

import com.hydrafit.app.core.domain.engine.PlannerEngineId
import com.hydrafit.app.core.domain.engine.WorkoutPlannerEngine
import com.hydrafit.app.core.domain.engine.WorkoutPlannerEngineProvider
import com.hydrafit.app.core.network.ApiKeyProvider
import com.hydrafit.app.core.userdata.settings.EnginePreferenceRepository

class DefaultWorkoutPlannerEngineProvider(
    private val preference: EnginePreferenceRepository,
    private val deterministic: WorkoutPlannerEngine,
    private val gemini: WorkoutPlannerEngine,
    private val apiKeyProvider: ApiKeyProvider
) : WorkoutPlannerEngineProvider {

    override suspend fun get(): WorkoutPlannerEngine {
        val selected = preference.selectedEngine()
        val geminiConfigured = apiKeyProvider.geminiApiKey().isNotBlank()
        return if (selected == PlannerEngineId.GEMINI_API &&
            geminiConfigured
        ) {
            gemini
        } else {
            deterministic
        }
    }
}
