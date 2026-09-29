package com.hydrafit.app.core.domain.engine

import com.hydrafit.app.core.domain.workout.WorkoutSet

/**
 * Suggests one working weight per exercise from the user's personal record (best estimated 1RM) and
 * the selected training goal. Warmups and sets without a usable weight or reps are ignored.
 */
class SuggestWeightsUseCase(private val config: SuggestedWeightConfig = SuggestedWeightConfig()) {

    operator fun invoke(sets: List<WorkoutSet>, goal: TrainingGoal): Map<String, Double> =
        sets.mapNotNull { set ->
            val weight = set.weightKg
            if (set.isWarmup || weight == null || weight <= 0.0) return@mapNotNull null
            if (set.reps !in 1..config.maxRepsForEstimate) return@mapNotNull null
            set.exerciseId to estimatedOneRepMax(weight, set.reps)
        }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, estimates) -> estimates.max() }
            .mapValues { (_, oneRepMax) ->
                config.roundToIncrement(oneRepMax * config.intensityFor(goal))
            }

    private fun estimatedOneRepMax(weightKg: Double, reps: Int): Double =
        weightKg * (1.0 + reps / 30.0)
}
