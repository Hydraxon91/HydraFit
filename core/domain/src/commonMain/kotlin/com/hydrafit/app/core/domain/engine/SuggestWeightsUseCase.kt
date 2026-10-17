package com.hydrafit.app.core.domain.engine

import com.hydrafit.app.core.domain.workout.WorkoutSet

/**
 * Estimates one personal-record 1RM per exercise from the user's logged sets (Epley). Warmups,
 * timed sets and sets without a usable weight or reps are ignored. The working weight is derived
 * from this 1RM at plan time, where the planned rep count is known (see
 * `SuggestedWeightConfig.intensityForReps`).
 */
class SuggestWeightsUseCase(private val config: SuggestedWeightConfig = SuggestedWeightConfig()) {

    operator fun invoke(sets: List<WorkoutSet>): Map<String, Double> = sets.mapNotNull { set ->
        val weight = set.weightKg
        val reps = set.reps
        if (set.isWarmup || reps == null || weight == null || weight <= 0.0) return@mapNotNull null
        if (!config.hasUsableEstimate(reps)) return@mapNotNull null
        set.exerciseId to OneRepMax.estimate(weight, reps)
    }
        .groupBy({ it.first }, { it.second })
        .mapValues { (_, estimates) -> estimates.max() }
}
