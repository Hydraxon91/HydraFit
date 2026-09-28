package com.hydrafit.app.core.domain.fatigue

import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours

data class FatigueConfig(
    val referenceVolume: Double = DEFAULT_REFERENCE_VOLUME,
    val halfLives: Map<MuscleGroup, Duration> = DEFAULT_HALF_LIVES,
) {
    init {
        require(referenceVolume > 0.0) { "referenceVolume must be positive" }
        require(halfLives.values.all { it > Duration.ZERO }) { "half-lives must be positive" }
    }

    fun halfLifeFor(muscle: MuscleGroup): Duration = halfLives[muscle] ?: DEFAULT_HALF_LIFE

    companion object {
        const val DEFAULT_REFERENCE_VOLUME: Double = 24.0

        val DEFAULT_HALF_LIFE: Duration = 48.hours

        val DEFAULT_HALF_LIVES: Map<MuscleGroup, Duration> = mapOf(
            MuscleGroup.CHEST to 48.hours,
            MuscleGroup.BACK to 48.hours,
            MuscleGroup.QUADS to 48.hours,
            MuscleGroup.HAMSTRINGS to 48.hours,
            MuscleGroup.GLUTES to 48.hours,
            MuscleGroup.SHOULDERS to 36.hours,
            MuscleGroup.BICEPS to 24.hours,
            MuscleGroup.TRICEPS to 24.hours,
            MuscleGroup.CALVES to 24.hours,
            MuscleGroup.CORE to 24.hours,
        )
    }
}
