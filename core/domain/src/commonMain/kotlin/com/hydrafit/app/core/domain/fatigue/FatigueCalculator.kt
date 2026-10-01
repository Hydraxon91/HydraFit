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

        val halfLife = config.halfLifeFor(muscle)
        var fatigue = 0.0
        var sessionStimulus = 0.0
        var previousMillis = batches.keys.first()
        for ((timestampMillis, sets) in batches) {
            val elapsed = timestampMillis - previousMillis
            fatigue *= decayFactor(elapsed, halfLife)
            if (elapsed >= config.sessionGap.inWholeMilliseconds) sessionStimulus = 0.0

            // The logarithmic doses telescope at a shared timestamp. Summing in canonical order
            // avoids insertion-order-dependent floating-point differences as well.
            val stimulus = sets.map { set ->
                val involvement = set.targets.filter { it.muscle == muscle }
                    .map { it.weight }.sorted().sum()
                val repsFactor = (set.reps.toDouble() / config.referenceReps)
                    .pow(config.repExponent)
                    .coerceIn(config.minRepMultiplier, config.maxRepMultiplier)
                involvement * repsFactor
            }.sorted().sum()
            val dose = config.diminishingScale *
                ln1p(stimulus / (config.diminishingScale + sessionStimulus))
            fatigue += (1.0 - fatigue) * -expm1(-dose / config.capacityScale)
            // Keep the mathematical open upper bound even if floating-point rounding reaches 1.
            fatigue = fatigue.coerceAtMost(1.0.nextDown())
            sessionStimulus += stimulus
            previousMillis = timestampMillis
        }

        return fatigue * decayFactor(nowMillis - previousMillis, halfLife)
    }

    private fun decayFactor(elapsedMillis: Long, halfLife: Duration): Double {
        val elapsed = elapsedMillis.coerceAtLeast(0L)
        val halfLifeMillis = halfLife.inWholeMilliseconds.toDouble()
        return 2.0.pow(-elapsed / halfLifeMillis)
    }
}
