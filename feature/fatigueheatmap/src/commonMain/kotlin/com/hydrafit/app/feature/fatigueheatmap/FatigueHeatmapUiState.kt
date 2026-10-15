package com.hydrafit.app.feature.fatigueheatmap

import com.hydrafit.app.core.domain.fatigue.MuscleGroup

/**
 * One row of the screen: the modelled fatigue [score] plus the separate recorded-coverage facts for
 * the same muscle. [workingSets] and [estimatedInvolvementCredits] describe recorded working sets;
 * [lastTrainedAtMillis] spans all available history and is not restricted to the coverage window.
 */
data class MuscleFatigueEntry(
    val muscle: MuscleGroup,
    val score: Double,
    val workingSets: Int = 0,
    val estimatedInvolvementCredits: Double = 0.0,
    val lastTrainedAtMillis: Long? = null
)

data class FatigueHeatmapUiState(
    val entries: List<MuscleFatigueEntry> = emptyList(),
    val utcOffsetMillis: Long = 0L,
    val unclassifiedSetsInWindow: Int = 0,
    val hasUnclassifiedHistory: Boolean = false,
    val coverageComputed: Boolean = false,
    val isLoading: Boolean = true
)
