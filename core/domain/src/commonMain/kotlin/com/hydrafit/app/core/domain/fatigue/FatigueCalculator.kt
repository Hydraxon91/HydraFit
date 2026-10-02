package com.hydrafit.app.core.domain.fatigue

import com.hydrafit.app.core.domain.engine.OneRepMax
import kotlin.math.expm1
import kotlin.math.ln1p
import kotlin.math.nextDown
import kotlin.math.pow
import kotlin.time.Duration

class FatigueCalculator(private val config: FatigueConfig = FatigueConfig()) {
    fun calculate(sets: List<LoggedSet>, nowMillis: Long): Map<MuscleGroup, Double> {
        val working = sets.filterNot { it.isWarmup }
        val loadFactors = relativeLoadFactors(working)
        // Session identity is part of the batch key: an equal-timestamp run carrying different
        // session ids splits into separate deterministic batches (a boundary must still reset),
        // while all-null legacy data groups exactly as it did before, by timestamp alone.
        val batches = working.mapIndexed { index, set ->
            LoadedSet(set, loadFactors[index], effortMultiplier(set.rir))
        }
            .sortedBy { it.set.timestampMillis }
            .groupBy { it.set.timestampMillis to it.set.sessionId }
            .map { (key, sets) -> Batch(key.first, key.second, sets) }
        return MuscleGroup.entries.associateWith { muscle -> scoreFor(muscle, batches, nowMillis) }
    }

    /**
     * Relative-load factors, one per input set in input order. A set's reference is the best Epley
     * estimate among earlier sets of the same exercise within `referenceWindow`; candidates are
     * non-warmup sets with `weightKg > 0` and `reps <= maxReferenceReps`. The same timestamp never
     * counts as earlier. Neutral (1.0) when the reference or the set's own weight is missing, or the
     * set itself is incomparable (`reps > maxReferenceReps`).
     */
    internal fun relativeLoadFactors(sets: List<LoggedSet>): DoubleArray {
        val factors = DoubleArray(sets.size) { 1.0 }
        val byExercise = sets.indices
            .filter { sets[it].exerciseId != null }
            .groupBy { sets[it].exerciseId!! }
        val windowMillis = config.referenceWindow.inWholeMilliseconds
        for (indices in byExercise.values) {
            val ordered = indices.sortedWith(compareBy({ sets[it].timestampMillis }, { it }))
            // Monotonic deque of (timestamp, estimate) with strictly decreasing estimates, so the
            // front is always the best in-window reference.
            val candidates = ArrayDeque<Pair<Long, Double>>()
            var start = 0
            while (start < ordered.size) {
                val timestamp = sets[ordered[start]].timestampMillis
                val windowStart = timestamp - windowMillis
                while (candidates.isNotEmpty() && candidates.first().first < windowStart) {
                    candidates.removeFirst()
                }
                val reference = candidates.firstOrNull()?.second
                var end = start
                while (end < ordered.size && sets[ordered[end]].timestampMillis == timestamp) {
                    factors[ordered[end]] = relativeLoad(sets[ordered[end]], reference)
                    end++
                }
                for (position in start until end) {
                    val set = sets[ordered[position]]
                    val weight = set.weightKg
                    if (
                        !set.isWarmup &&
                        set.reps <= config.maxReferenceReps &&
                        weight != null &&
                        weight > 0.0
                    ) {
                        val estimate = OneRepMax.estimate(weight, set.reps)
                        while (candidates.isNotEmpty() && candidates.last().second <= estimate) {
                            candidates.removeLast()
                        }
                        candidates.addLast(timestamp to estimate)
                    }
                }
                start = end
            }
        }
        return factors
    }

    /**
     * Effort multiplier from reps in reserve. Missing effort assumes `defaultRir` (neutral at the
     * default), and the value is clamped to `minRir..maxRir`; nothing is written back anywhere.
     */
    internal fun effortMultiplier(rir: Int?): Double {
        val effectiveRir = (rir?.toDouble() ?: config.defaultRir)
            .coerceIn(config.minRir, config.maxRir)
        return 2.0.pow((config.effortNeutralRir - effectiveRir) / config.effortRirDivisor)
    }

    private fun relativeLoad(set: LoggedSet, reference: Double?): Double {
        val weight = set.weightKg
        if (weight == null || weight <= 0.0) return 1.0
        if (reference == null || reference <= 0.0) return 1.0
        if (set.reps > config.maxReferenceReps) return 1.0
        return ((weight / reference) / config.relativeLoadDivisor)
            .coerceIn(config.relativeLoadMin, config.relativeLoadMax)
    }

    private fun scoreFor(muscle: MuscleGroup, batches: List<Batch>, nowMillis: Long): Double {
        if (batches.isEmpty()) return 0.0

        val isolationHalfLife = config.isolationHalfLifeFor(muscle)
        val compoundHalfLife = config.compoundHalfLifeFor(muscle)
        // Compound and isolation fatigue are one bounded state (shared headroom); only their decay
        // channels differ, so a compound set recovers on a longer half-life than an isolation set.
        var isolationFatigue = 0.0
        var compoundFatigue = 0.0
        var sessionStimulus = 0.0
        var previousMillis = batches.first().timestampMillis
        var previousSessionId = batches.first().sessionId
        for ((index, batch) in batches.withIndex()) {
            val timestampMillis = batch.timestampMillis
            val sets = batch.sets
            val elapsed = timestampMillis - previousMillis
            isolationFatigue *= decayFactor(elapsed, isolationHalfLife)
            compoundFatigue *= decayFactor(elapsed, compoundHalfLife)
            // The first batch has nothing before it, so it never triggers a reset.
            if (index > 0 && sessionBoundary(previousSessionId, batch.sessionId, elapsed)) {
                sessionStimulus = 0.0
            }

            // The set's addition goes to the component matching its type. Equal-timestamp sets of the
            // same type share one dose (canonical order), preserving the Phase B path bit-for-bit
            // when every set is isolation.
            for (isCompound in COMPONENT_ORDER) {
                val typeSets = sets.filter { it.set.isCompound == isCompound }
                if (typeSets.isEmpty()) continue
                // The logarithmic doses telescope at a shared timestamp. Summing in canonical order
                // avoids insertion-order-dependent floating-point differences as well.
                val stimulus = typeSets.map { loaded ->
                    val set = loaded.set
                    val involvement = set.targets.filter { it.muscle == muscle }
                        .map { it.weight }.sorted().sum()
                    val repsFactor = (set.reps.toDouble() / config.referenceReps)
                        .pow(config.repExponent)
                        .coerceIn(config.minRepMultiplier, config.maxRepMultiplier)
                    involvement * repsFactor * loaded.relativeLoad * loaded.effort
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
            previousSessionId = batch.sessionId
        }

        val finalIsolation = isolationFatigue *
            decayFactor(nowMillis - previousMillis, isolationHalfLife)
        val finalCompound = compoundFatigue *
            decayFactor(nowMillis - previousMillis, compoundHalfLife)
        return (finalIsolation + finalCompound).coerceAtMost(1.0.nextDown())
    }

    private data class LoadedSet(val set: LoggedSet, val relativeLoad: Double, val effort: Double)

    /** Sets sharing one timestamp and one session id are applied together as one dose. */
    private data class Batch(
        val timestampMillis: Long,
        val sessionId: String?,
        val sets: List<LoadedSet>
    )

    private companion object {
        /** Deterministic order for routing mixed-type same-timestamp batches; isolation (-) first. */
        val COMPONENT_ORDER = listOf(false, true)
    }

    /**
     * Whether a new session starts between two consecutive timestamp-ordered batches. Two explicit
     * ids reset only when they differ; a null/non-null boundary is always a boundary (a residual
     * null row must never join an identified session); only when both batches are null does the
     * legacy 2h `sessionGap` decide.
     */
    private fun sessionBoundary(previous: String?, current: String?, elapsedMillis: Long): Boolean =
        when {
            previous != null && current != null -> previous != current
            previous == null && current == null ->
                elapsedMillis >= config.sessionGap.inWholeMilliseconds
            else -> true
        }

    private fun decayFactor(elapsedMillis: Long, halfLife: Duration): Double {
        val elapsed = elapsedMillis.coerceAtLeast(0L)
        val halfLifeMillis = halfLife.inWholeMilliseconds.toDouble()
        return 2.0.pow(-elapsed / halfLifeMillis)
    }
}
