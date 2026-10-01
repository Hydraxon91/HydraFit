package com.hydrafit.app.core.domain.fatigue

import kotlin.math.expm1
import kotlin.math.ln1p
import kotlin.math.nextDown
import kotlin.math.pow
import kotlin.time.Duration

class FatigueCalculator(private val config: FatigueConfig = FatigueConfig()) {
    fun calculate(sets: List<LoggedSet>, nowMillis: Long): Map<MuscleGroup, Double> {
        val batches = sets.filterNot { it.isWarmup }
            .sortedBy { it.timestampMillis }
            .groupBy { it.timestampMillis }
        return MuscleGroup.entries.associateWith { muscle -> scoreFor(muscle, batches, nowMillis) }
    }

    private fun scoreFor(
        muscle: MuscleGroup,
        batches: Map<Long, List<LoggedSet>>,
        nowMillis: Long
    ): Double {
        if (batches.isEmpty()) return 0.0

        val isolationHalfLife = config.isolationHalfLifeFor(muscle)
        val compoundHalfLife = config.compoundHalfLifeFor(muscle)
        // Compound and isolation fatigue are one bounded state (shared headroom); only their decay
        // channels differ, so a compound set recovers on a longer half-life than an isolation set.
        var isolationFatigue = 0.0
        var compoundFatigue = 0.0
        var sessionStimulus = 0.0
        var previousMillis = batches.keys.first()
        for ((timestampMillis, sets) in batches) {
            val elapsed = timestampMillis - previousMillis
            isolationFatigue *= decayFactor(elapsed, isolationHalfLife)
            compoundFatigue *= decayFactor(elapsed, compoundHalfLife)
            if (elapsed >= config.sessionGap.inWholeMilliseconds) sessionStimulus = 0.0

            // The set's addition goes to the component matching its type. Equal-timestamp sets of the
            // same type share one dose (canonical order), preserving the Phase B path bit-for-bit
            // when every set is isolation.
            for (isCompound in COMPONENT_ORDER) {
                val typeSets = sets.filter { it.isCompound == isCompound }
                if (typeSets.isEmpty()) continue
                // The logarithmic doses telescope at a shared timestamp. Summing in canonical order
                // avoids insertion-order-dependent floating-point differences as well.
                val stimulus = typeSets.map { set ->
                    val involvement = set.targets.filter { it.muscle == muscle }
                        .map { it.weight }.sorted().sum()
                    val repsFactor = (set.reps.toDouble() / config.referenceReps)
                        .pow(config.repExponent)
                        .coerceIn(config.minRepMultiplier, config.maxRepMultiplier)
                    involvement * repsFactor
                }.sorted().sum()
                val dose = config.diminishingScale *
                    ln1p(stimulus / (config.diminishingScale + sessionStimulus))
                val increment = (1.0 - isolationFatigue - compoundFatigue) *
                    -expm1(-dose / config.capacityScale)
                if (isCompound) compoundFatigue += increment else isolationFatigue += increment
                // Keep the mathematical open upper bound even if floating-point rounding reaches 1.
                val overflow = isolationFatigue + compoundFatigue - 1.0.nextDown()
                if (overflow > 0.0) {
                    if (isCompound) compoundFatigue -= overflow else isolationFatigue -= overflow
                }
                sessionStimulus += stimulus
            }
            previousMillis = timestampMillis
        }

        val finalIsolation = isolationFatigue *
            decayFactor(nowMillis - previousMillis, isolationHalfLife)
        val finalCompound = compoundFatigue *
            decayFactor(nowMillis - previousMillis, compoundHalfLife)
        return (finalIsolation + finalCompound).coerceAtMost(1.0.nextDown())
    }

    private companion object {
        /** Deterministic order for routing mixed-type same-timestamp batches; isolation (-) first. */
        val COMPONENT_ORDER = listOf(false, true)
    }

    private fun decayFactor(elapsedMillis: Long, halfLife: Duration): Double {
        val elapsed = elapsedMillis.coerceAtLeast(0L)
        val halfLifeMillis = halfLife.inWholeMilliseconds.toDouble()
        return 2.0.pow(-elapsed / halfLifeMillis)
    }
}
