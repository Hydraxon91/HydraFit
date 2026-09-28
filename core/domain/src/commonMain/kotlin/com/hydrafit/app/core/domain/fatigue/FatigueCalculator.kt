package com.hydrafit.app.core.domain.fatigue

import kotlin.math.pow
import kotlin.time.Duration

class FatigueCalculator(private val config: FatigueConfig = FatigueConfig()) {
    fun calculate(sets: List<LoggedSet>, nowMillis: Long): Map<MuscleGroup, Double> =
        MuscleGroup.entries.associateWith { muscle -> scoreFor(muscle, sets, nowMillis) }

    private fun scoreFor(muscle: MuscleGroup, sets: List<LoggedSet>, nowMillis: Long): Double {
        val events = sets
            .filterNot { it.isWarmup }
            .mapNotNull { set ->
                val volume = set.targets
                    .filter { it.muscle == muscle }
                    .sumOf { it.involvement.volumeWeight }
                if (volume == 0.0) null else VolumeEvent(set.timestampMillis, volume)
            }
            .sortedBy { it.timestampMillis }

        if (events.isEmpty()) return 0.0

        val halfLife = config.halfLifeFor(muscle)
        var raw = 0.0
        var previousMillis = events.first().timestampMillis
        for (event in events) {
            raw = raw * decayFactor(event.timestampMillis - previousMillis, halfLife) + event.volume
            previousMillis = event.timestampMillis
        }

        val recovered = raw * decayFactor(nowMillis - events.last().timestampMillis, halfLife)
        return (recovered / config.referenceVolume).coerceIn(0.0, 1.0)
    }

    private fun decayFactor(elapsedMillis: Long, halfLife: Duration): Double {
        val elapsed = elapsedMillis.coerceAtLeast(0L)
        val halfLifeMillis = halfLife.inWholeMilliseconds.toDouble()
        return 2.0.pow(-elapsed / halfLifeMillis)
    }

    private data class VolumeEvent(val timestampMillis: Long, val volume: Double)
}
