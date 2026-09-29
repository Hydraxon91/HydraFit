package com.hydrafit.app.core.domain.engine

/**
 * Tunable defaults for progressive overload. A streak of completed sessions earns an increment; a
 * streak of missed sessions gives one back, but the suggestion never drops below the 1RM baseline.
 * The thresholds are starting points, not physiological facts, and are expected to be adjusted.
 */
data class ProgressionConfig(
    val successStreak: Int = DEFAULT_SUCCESS_STREAK,
    val failureStreak: Int = DEFAULT_FAILURE_STREAK,
    val roundToKg: Double = DEFAULT_ROUND_TO_KG,
    val maxIncrements: Int = DEFAULT_MAX_INCREMENTS
) {
    init {
        require(successStreak >= 1) { "successStreak must be at least 1" }
        require(failureStreak >= 1) { "failureStreak must be at least 1" }
        require(roundToKg > 0.0) { "roundToKg must be positive" }
        require(maxIncrements >= 0) { "maxIncrements must not be negative" }
    }

    companion object {
        const val DEFAULT_SUCCESS_STREAK: Int = 3
        const val DEFAULT_FAILURE_STREAK: Int = 3
        const val DEFAULT_ROUND_TO_KG: Double = 2.5
        const val DEFAULT_MAX_INCREMENTS: Int = 5
    }
}
