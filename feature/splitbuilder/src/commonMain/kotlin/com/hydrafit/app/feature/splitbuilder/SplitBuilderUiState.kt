package com.hydrafit.app.feature.splitbuilder

import com.hydrafit.app.core.domain.engine.WeeklyPlan

data class SplitBuilderUiState(
    val daysPerWeek: Int = 4,
    val plan: WeeklyPlan? = null,
    val exerciseNames: Map<String, String> = emptyMap(),
    val isLoading: Boolean = true
)
