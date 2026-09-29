package com.hydrafit.app.core.domain.engine

import kotlin.math.round

/**
 * Tunable defaults for the deterministic weight suggestion. The intensities are starting points for
 * different training goals, not physiological facts, and are expected to be adjusted.
 */
data class SuggestedWeightConfig(
    val goalIntensity: Map<TrainingGoal, Double> = DEFAULT_GOAL_INTENSITY,
    val roundToKg: Double = DEFAULT_ROUND_TO_KG,
    val maxRepsForEstimate: Int = DEFAULT_MAX_REPS
) {
    init {
        require(roundToKg > 0.0) { "roundToKg must be positive" }
        require(maxRepsForEstimate >= 1) { "maxRepsForEstimate must be at least 1" }
        require(goalIntensity.values.all { it > 0.0 && it <= 1.0 }) {
            "intensities must be in (0.0, 1.0]"
        }
    }

    fun intensityFor(goal: TrainingGoal): Double = goalIntensity[goal] ?: DEFAULT_INTENSITY

    fun roundToIncrement(weightKg: Double): Double = round(weightKg / roundToKg) * roundToKg

    companion object {
        const val DEFAULT_ROUND_TO_KG: Double = 2.5
        const val DEFAULT_MAX_REPS: Int = 15
        const val DEFAULT_INTENSITY: Double = 0.7

        val DEFAULT_GOAL_INTENSITY: Map<TrainingGoal, Double> = mapOf(
            TrainingGoal.BALANCED to 0.70,
            TrainingGoal.STRENGTH to 0.875,
            TrainingGoal.HYPERTROPHY to 0.725,
            TrainingGoal.ENDURANCE to 0.575
        )
    }
}
