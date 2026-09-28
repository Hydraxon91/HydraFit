package com.hydrafit.app.feature.splitbuilder

import com.hydrafit.app.core.domain.engine.PlannerEngineId
import com.hydrafit.app.core.domain.engine.WeeklyPlan

data class SplitBuilderUiState(
    val daysPerWeek: Int = 4,
    val setsPerExercise: Int = 3,
    val plan: WeeklyPlan? = null,
    val exerciseNames: Map<String, String> = emptyMap(),
    val requestedEngine: PlannerEngineId? = null,
    val isLoading: Boolean = true,
    val hasError: Boolean = false,
    val isTransientError: Boolean = false,
    val errorDetail: String? = null
) {
    val usedFallbackEngine: Boolean
        get() = plan != null && requestedEngine != null && plan.engine != requestedEngine
}
