package com.hydrafit.app

import com.hydrafit.app.core.domain.engine.EngineAvailability
import com.hydrafit.app.core.domain.engine.PlannerEngineId
import com.hydrafit.app.core.llm.OnDeviceTextGenerator
import com.hydrafit.app.core.network.ApiKeyProvider

class DefaultEngineAvailability(
    private val apiKeyProvider: ApiKeyProvider,
    private val onDeviceGenerator: OnDeviceTextGenerator
) : EngineAvailability {

    override fun availableEngines(): List<PlannerEngineId> = buildList {
        add(PlannerEngineId.DETERMINISTIC)
        if (apiKeyProvider.geminiApiKey().isNotBlank()) {
            add(PlannerEngineId.GEMINI_API)
        }
        if (onDeviceGenerator.isAvailable()) {
            add(PlannerEngineId.LOCAL_LLM)
        }
    }
}
