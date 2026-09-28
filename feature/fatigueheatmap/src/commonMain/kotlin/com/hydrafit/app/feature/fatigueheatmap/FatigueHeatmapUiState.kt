package com.hydrafit.app.feature.fatigueheatmap

import com.hydrafit.app.core.domain.fatigue.MuscleGroup

data class MuscleFatigueEntry(val muscle: MuscleGroup, val score: Double)

data class FatigueHeatmapUiState(
    val entries: List<MuscleFatigueEntry> = emptyList(),
    val isLoading: Boolean = true
)
