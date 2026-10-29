package com.hydrafit.app

import com.hydrafit.app.core.domain.engine.EngineAvailability
import com.hydrafit.app.core.domain.engine.PlannerEngineId
import com.hydrafit.app.core.network.GroqApiKeyProvider

class DefaultEngineAvailability(private val apiKeyProvider: GroqApiKeyProvider) :
    EngineAvailability {

    override fun availableEngines(): List<PlannerEngineId> = listOfNotNull(
        PlannerEngineId.DETERMINISTIC,
        PlannerEngineId.GROQ_API.takeIf { apiKeyProvider.groqApiKey().isNotBlank() }
    )
}
