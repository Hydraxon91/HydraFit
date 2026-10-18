package com.hydrafit.app.core.domain.engine

/** One exercise's progressed estimate together with the reasoning that produced it. */
data class ProgressedWeight(
    val baselineKg: Double,
    val progressedKg: Double,
    val explanation: LoadProgressionExplanation
)

/**
 * Applies progressive overload on top of the 1RM-derived baseline from each exercise's recorded,
 * comparable history: a streak of sessions that satisfied their own recorded target earns an
 * increment, a streak of comparable sessions that missed theirs gives one back, and the result never
 * drops below the baseline.
 *
 * The recorded target — not today's accepted plan — decides comparability, so a corrected or deleted
 * prescription cannot silently reinterpret older work. An exposure whose target is missing, mixed or
 * not yet resolved is neither a success nor a miss: it breaks the streak and adds no reduction.
 *
 * Increments are paused while the latest accepted plan is a deload week, so intentionally light
 * deload targets do not earn a progression bump.
 */
class ProgressWeightsUseCase(private val config: ProgressionConfig = ProgressionConfig()) {

    /** The progressed, rounded estimate per exercise. See [explain] for the recorded reasoning. */
    operator fun invoke(
        baseline: Map<String, Double>,
        history: ProgressionHistory,
        pauseIncrements: Boolean = false,
        nowMillis: Long = 0L
    ): Map<String, Double> = resolve(baseline, history, pauseIncrements, nowMillis)
        .mapValues { (_, resolved) -> resolved.progressedKg }

    /** The recorded progression reasoning per exercise id; always consistent with [invoke]. */
    fun explain(
        baseline: Map<String, Double>,
        history: ProgressionHistory,
        pauseIncrements: Boolean = false,
        nowMillis: Long = 0L
    ): Map<String, LoadProgressionExplanation> = resolve(
        baseline,
        history,
        pauseIncrements,
        nowMillis
    ).mapValues { (_, resolved) -> resolved.explanation }

    /** Both outputs from one computation, so a recorded reason can never disagree with its value. */
    fun resolve(
        baseline: Map<String, Double>,
        history: ProgressionHistory,
        pauseIncrements: Boolean = false,
        nowMillis: Long = 0L
    ): Map<String, ProgressedWeight> {
        if (pauseIncrements) {
            return baseline.mapValues { (_, base) ->
                ProgressedWeight(base, base, explanation(ProgressionOutcome.PAUSED_DELOAD))
            }
        }

        return baseline.mapValues { (exerciseId, base) ->
            val replay = history.replay(exerciseId, nowMillis, config)
            val delta = replay.increments * config.roundToKg
            ProgressedWeight(
                baselineKg = base,
                progressedKg = base + delta,
                explanation = explanation(
                    outcome = when {
                        replay.increments > 0 -> ProgressionOutcome.APPLIED
                        // A hold caused by missed sessions is not the same as "not enough comparable
                        // sessions yet"; the rule did remove increments and landed on the baseline.
                        replay.failureApplied -> ProgressionOutcome.HELD_AFTER_MISSED_SESSIONS
                        // No comparable recorded target at all: there is nothing to progress from.
                        !replay.hasEligibleExposure -> ProgressionOutcome.NONE_NO_TYPED_PRESCRIPTION
                        else -> ProgressionOutcome.NONE_NO_STREAK_YET
                    },
                    increments = replay.increments,
                    deltaKg = delta
                )
            )
        }
    }

    /** The thresholds are recorded with the outcome so the rule, not an inferred gain, is explained. */
    private fun explanation(
        outcome: ProgressionOutcome,
        increments: Int = 0,
        deltaKg: Double = 0.0
    ): LoadProgressionExplanation = LoadProgressionExplanation(
        outcome = outcome,
        increments = increments,
        deltaKg = deltaKg,
        roundToKg = config.roundToKg,
        successStreakSize = config.successStreak,
        failureStreakSize = config.failureStreak
    )
}
