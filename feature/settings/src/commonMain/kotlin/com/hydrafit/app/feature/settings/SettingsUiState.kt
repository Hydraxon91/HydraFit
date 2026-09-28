package com.hydrafit.app.feature.settings

import com.hydrafit.app.core.domain.engine.PlannerEngineId

data class SettingsUiState(
    val availableEngines: List<PlannerEngineId> = emptyList(),
    val selectedEngine: PlannerEngineId? = null,
    val apiKeyConfigured: Boolean = false,
    val apiKeyInput: String = ""
) {
    val isGeminiAvailable: Boolean
        get() = PlannerEngineId.GEMINI_API in availableEngines
}
