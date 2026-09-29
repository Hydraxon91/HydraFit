package com.hydrafit.app.feature.settings

import com.hydrafit.app.core.domain.engine.PlannerEngineId
import com.hydrafit.app.core.domain.engine.TrainingGoal
import com.hydrafit.app.core.domain.unit.WeightUnit

data class SettingsUiState(
    val availableEngines: List<PlannerEngineId> = emptyList(),
    val selectedEngine: PlannerEngineId? = null,
    val selectedGoal: TrainingGoal = TrainingGoal.BALANCED,
    val weightUnit: WeightUnit = WeightUnit.KG,
    val workoutDataSharingEnabled: Boolean = false,
    val apiKeyConfigured: Boolean = false,
    val apiKeyInput: String = "",
    val isLocalLlmInstalled: Boolean = false
) {
    val isGeminiAvailable: Boolean
        get() = PlannerEngineId.GEMINI_API in availableEngines
}
