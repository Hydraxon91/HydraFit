package com.hydrafit.app.core.domain.fatigue

import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
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
    val fallbackHalfLife: Duration = DEFAULT_HALF_LIFE,
    val isolationHalfLifeScale: Double = DEFAULT_ISOLATION_HALF_LIFE_SCALE,
    val compoundHalfLifeScale: Double = DEFAULT_COMPOUND_HALF_LIFE_SCALE,
    val relativeLoadDivisor: Double = DEFAULT_RELATIVE_LOAD_DIVISOR,
    val relativeLoadMin: Double = DEFAULT_RELATIVE_LOAD_MIN,
    val relativeLoadMax: Double = DEFAULT_RELATIVE_LOAD_MAX,
    val referenceWindow: Duration = DEFAULT_REFERENCE_WINDOW,
    val maxReferenceReps: Int = DEFAULT_MAX_REFERENCE_REPS,
    val defaultRir: Double = DEFAULT_RIR,
    val effortNeutralRir: Double = DEFAULT_EFFORT_NEUTRAL_RIR,
    val effortRirDivisor: Double = DEFAULT_EFFORT_RIR_DIVISOR,
    val minRir: Double = DEFAULT_MIN_RIR,
    val maxRir: Double = DEFAULT_MAX_RIR
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
        require(isolationHalfLifeScale.isFinite() && isolationHalfLifeScale > 0.0) {
            "isolationHalfLifeScale must be positive and finite"
        }
        require(
            compoundHalfLifeScale.isFinite() && compoundHalfLifeScale >= isolationHalfLifeScale
        ) {
            "compoundHalfLifeScale must be finite and at least isolationHalfLifeScale"
        }
        require(relativeLoadDivisor.isFinite() && relativeLoadDivisor > 0.0) {
            "relativeLoadDivisor must be positive and finite"
        }
        require(relativeLoadMin.isFinite() && relativeLoadMin > 0.0) {
            "relativeLoadMin must be positive and finite"
        }
        require(relativeLoadMax.isFinite() && relativeLoadMax >= relativeLoadMin) {
            "relativeLoadMax must be finite and at least relativeLoadMin"
        }
        require(referenceWindow.isFinite() && referenceWindow.inWholeMilliseconds > 0L) {
            "referenceWindow must be positive and finite"
        }
        require(maxReferenceReps > 0) { "maxReferenceReps must be positive" }
        require(defaultRir.isFinite() && defaultRir in minRir..maxRir) {
            "defaultRir must be finite and within minRir..maxRir"
        }
        require(effortNeutralRir.isFinite() && effortNeutralRir in minRir..maxRir) {
            "effortNeutralRir must be finite and within minRir..maxRir"
        }
        require(effortRirDivisor.isFinite() && effortRirDivisor > 0.0) {
            "effortRirDivisor must be positive and finite"
        }
        require(minRir.isFinite() && maxRir.isFinite() && maxRir > minRir) {
            "invalid RIR range"
        }
    }

    fun halfLifeFor(muscle: MuscleGroup): Duration = halfLives[muscle] ?: fallbackHalfLife

    /** Effective recovery half-life for isolation work: the base half-life, unscaled by default. */
    fun isolationHalfLifeFor(muscle: MuscleGroup): Duration =
        halfLifeFor(muscle) * isolationHalfLifeScale

    /** Effective recovery half-life for compound work: the base half-life scaled up (slower recovery). */
    fun compoundHalfLifeFor(muscle: MuscleGroup): Duration =
        halfLifeFor(muscle) * compoundHalfLifeScale

    companion object {
        const val DEFAULT_REFERENCE_REPS: Int = 8
        const val DEFAULT_ISOLATION_HALF_LIFE_SCALE: Double = 1.0
        const val DEFAULT_COMPOUND_HALF_LIFE_SCALE: Double = 1.25
        const val DEFAULT_RELATIVE_LOAD_DIVISOR: Double = 0.70
        const val DEFAULT_RELATIVE_LOAD_MIN: Double = 0.75
        const val DEFAULT_RELATIVE_LOAD_MAX: Double = 1.25
        const val DEFAULT_MAX_REFERENCE_REPS: Int = 15
        const val DEFAULT_RIR: Double = 2.0
        const val DEFAULT_EFFORT_NEUTRAL_RIR: Double = 2.0
        const val DEFAULT_EFFORT_RIR_DIVISOR: Double = 4.0
        const val DEFAULT_MIN_RIR: Double = 0.0
        const val DEFAULT_MAX_RIR: Double = 10.0

        val DEFAULT_REFERENCE_WINDOW: Duration = 90.days
        val DEFAULT_HALF_LIFE: Duration = 24.hours

        val DEFAULT_HALF_LIVES: Map<MuscleGroup, Duration> = mapOf(
            MuscleGroup.CHEST_UPPER to 24.hours,
            MuscleGroup.CHEST_LOWER to 24.hours,
            MuscleGroup.LATS to 24.hours,
            MuscleGroup.UPPER_BACK to 24.hours,
            MuscleGroup.LOWER_BACK to 24.hours,
            MuscleGroup.QUADS to 24.hours,
            MuscleGroup.HAMSTRINGS to 24.hours,
            MuscleGroup.GLUTES to 24.hours,
            MuscleGroup.FRONT_DELTS to 21.hours,
            MuscleGroup.SIDE_DELTS to 21.hours,
            MuscleGroup.REAR_DELTS to 21.hours,
            MuscleGroup.BICEPS to 18.hours,
            MuscleGroup.TRICEPS to 18.hours,
            MuscleGroup.FOREARMS to 18.hours,
            MuscleGroup.CALVES to 18.hours,
            MuscleGroup.ABS to 18.hours,
            MuscleGroup.OBLIQUES to 18.hours
        )
    }
}
