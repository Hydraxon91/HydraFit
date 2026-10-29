package com.hydrafit.app

import com.hydrafit.app.core.domain.engine.EngineAvailability
import com.hydrafit.app.core.domain.engine.PlannerEngineId
import com.hydrafit.app.core.llm.OnDeviceTextGenerator
import com.hydrafit.app.core.network.GroqApiKeyProvider

class DefaultEngineAvailability(
    private val apiKeyProvider: GroqApiKeyProvider,
    private val onDeviceGenerator: OnDeviceTextGenerator
) : EngineAvailability {

    override fun availableEngines(): List<PlannerEngineId> = buildList {
        add(PlannerEngineId.DETERMINISTIC)
        if (apiKeyProvider.groqApiKey().isNotBlank()) {
            add(PlannerEngineId.GROQ_API)
        }
        if (onDeviceGenerator.isAvailable()) {
            add(PlannerEngineId.LOCAL_LLM)
        }
    }
}
