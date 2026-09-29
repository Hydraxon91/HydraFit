package com.hydrafit.app.core.domain.engine

import kotlin.math.roundToInt

/**
 * Tunable bounds for volume-aware reps. The floor prevents absurdly low reps when many sets are
 * chosen; the ceiling prevents absurdly high reps when very few sets are chosen.
 */
data class VolumeAwareRepsConfig(
    val minReps: Int = DEFAULT_MIN_REPS,
    val maxReps: Int = DEFAULT_MAX_REPS
) {
    init {
        require(minReps >= 1) { "minReps must be at least 1" }
        require(maxReps >= minReps) { "maxReps must be at least minReps" }
    }

    companion object {
        const val DEFAULT_MIN_REPS: Int = 3
        const val DEFAULT_MAX_REPS: Int = 20
    }
}

/**
 * Keeps an exercise's total volume roughly constant as the user changes the set count: fewer sets
 * earn more reps per set, more sets earn fewer, so `sets × reps` stays near the goal's intended
 * volume (`defaultSets × defaultReps`). Reps are clamped to [minReps, maxReps].
 */
class VolumeAwareReps(private val config: VolumeAwareRepsConfig = VolumeAwareRepsConfig()) {

    fun repsFor(goal: TrainingGoal, isCompound: Boolean, sets: Int): Int {
        require(sets >= 1) { "sets must be at least 1" }
        val defaultSets = if (isCompound) goal.defaultSets else goal.accessorySets
        val defaultReps = if (isCompound) goal.compoundReps else goal.isolationReps
        val intendedVolume = defaultSets * defaultReps
        return (intendedVolume.toDouble() / sets).roundToInt()
            .coerceIn(config.minReps, config.maxReps)
    }
}
