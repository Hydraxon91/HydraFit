package com.hydrafit.app.core.domain.fatigue

import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours

data class FatigueConfig(
    val capacityScale: Double = 6.0,
    val diminishingScale: Double = 6.0,
    val referenceReps: Int = DEFAULT_REFERENCE_REPS,
    val repExponent: Double = 0.5,
    val minRepMultiplier: Double = 0.5,
    val maxRepMultiplier: Double = 1.5,
    val sessionGap: Duration = 2.hours,
    val reduceThreshold: Double = 0.65,
    val skipThreshold: Double = 0.80,
    val targetedInvolvementCutoff: Double = 0.7,
    val halfLives: Map<MuscleGroup, Duration> = DEFAULT_HALF_LIVES,
    val fallbackHalfLife: Duration = DEFAULT_HALF_LIFE
) {
    init {
        require(capacityScale.isFinite() && capacityScale > 0.0) {
            "capacityScale must be positive and finite"
        }
        require(diminishingScale.isFinite() && diminishingScale > 0.0) {
            "diminishingScale must be positive and finite"
        }
        require(referenceReps > 0) { "referenceReps must be positive" }
        require(repExponent.isFinite() && repExponent > 0.0) {
            "repExponent must be positive and finite"
        }
        require(minRepMultiplier.isFinite() && minRepMultiplier > 0.0) {
            "minRepMultiplier must be positive and finite"
        }
        require(maxRepMultiplier.isFinite() && maxRepMultiplier >= minRepMultiplier) {
            "invalid rep multiplier range"
        }
        require(sessionGap.isFinite() && sessionGap.inWholeMilliseconds > 0L) {
            "sessionGap must be positive and finite"
        }
        require(reduceThreshold in 0.0..1.0 && skipThreshold in reduceThreshold..1.0) {
            "invalid fatigue thresholds"
        }
        require(targetedInvolvementCutoff in 0.0..1.0) { "invalid targeted involvement cutoff" }
        require(
            (halfLives.values + fallbackHalfLife).all {
                it.isFinite() && it.inWholeMilliseconds > 0L
            }
        ) {
            "half-lives must be positive and finite"
        }
    }

    fun halfLifeFor(muscle: MuscleGroup): Duration = halfLives[muscle] ?: fallbackHalfLife

    companion object {
        const val DEFAULT_REFERENCE_REPS: Int = 8

        val DEFAULT_HALF_LIFE: Duration = 24.hours

        val DEFAULT_HALF_LIVES: Map<MuscleGroup, Duration> = mapOf(
            MuscleGroup.CHEST to 24.hours,
            MuscleGroup.BACK to 24.hours,
            MuscleGroup.QUADS to 24.hours,
            MuscleGroup.HAMSTRINGS to 24.hours,
            MuscleGroup.GLUTES to 24.hours,
            MuscleGroup.SHOULDERS to 21.hours,
            MuscleGroup.BICEPS to 18.hours,
            MuscleGroup.TRICEPS to 18.hours,
            MuscleGroup.CALVES to 18.hours,
            MuscleGroup.CORE to 18.hours
        )
    }
}
