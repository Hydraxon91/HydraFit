package com.hydrafit.app.core.domain.engine

import com.hydrafit.app.core.domain.time.localEpochDay
import com.hydrafit.app.core.domain.workout.WorkoutSet

/** The target the user was following for one exercise, taken from an accepted plan. */
data class Prescription(val exerciseId: String, val sets: Int, val reps: Int, val weightKg: Double)

/** One exercise's progressed estimate together with the reasoning that produced it. */
data class ProgressedWeight(
    val baselineKg: Double,
    val progressedKg: Double,
    val explanation: LoadProgressionExplanation
)

/**
 * Applies progressive overload on top of the 1RM-derived baseline: a streak of completed sessions
 * earns increments, a streak of missed sessions gives them back, and the result never drops below
 * the baseline. Sessions are judged per calendar day against the accepted plan's prescription, so
 * only weighted work counts — a bodyweight or blank-weight day is ignored, not counted as a miss.
 *
 * Increments are paused while the latest accepted plan is a deload week, so intentionally light
 * deload targets do not earn a progression bump.
 */
class ProgressWeightsUseCase(private val config: ProgressionConfig = ProgressionConfig()) {

    /** The progressed, rounded estimate per exercise. See [explain] for the recorded reasoning. */
    operator fun invoke(
        baseline: Map<String, Double>,
        prescriptions: Map<String, Prescription>,
        sets: List<WorkoutSet>,
        pauseIncrements: Boolean = false,
        utcOffsetMillis: Long = 0L
    ): Map<String, Double> = resolve(
        baseline,
        prescriptions,
        sets,
        pauseIncrements,
        utcOffsetMillis
    ).mapValues { (_, resolved) -> resolved.progressedKg }

    /** The recorded progression reasoning per exercise id; always consistent with [invoke]. */
    fun explain(
        baseline: Map<String, Double>,
        prescriptions: Map<String, Prescription>,
        sets: List<WorkoutSet>,
        pauseIncrements: Boolean = false,
        utcOffsetMillis: Long = 0L
    ): Map<String, LoadProgressionExplanation> = resolve(
        baseline,
        prescriptions,
        sets,
        pauseIncrements,
        utcOffsetMillis
    ).mapValues { (_, resolved) -> resolved.explanation }

    /** Both outputs from one computation, so a recorded reason can never disagree with its value. */
    fun resolve(
        baseline: Map<String, Double>,
        prescriptions: Map<String, Prescription>,
        sets: List<WorkoutSet>,
        pauseIncrements: Boolean = false,
        utcOffsetMillis: Long = 0L
    ): Map<String, ProgressedWeight> {
        if (pauseIncrements) {
            return baseline.mapValues { (_, base) ->
                ProgressedWeight(base, base, explanation(ProgressionOutcome.PAUSED_DELOAD))
            }
        }

        val byExercise = sets
            .filter { !it.isWarmup && it.reps != null && (it.weightKg ?: 0.0) > 0.0 }
            .groupBy { it.exerciseId }

        return baseline.mapValues { (exerciseId, base) ->
            val prescription = prescriptions[exerciseId]
                ?: return@mapValues ProgressedWeight(
                    base,
                    base,
                    explanation(ProgressionOutcome.NONE_NO_TYPED_PRESCRIPTION)
                )
            val sessions = byExercise[exerciseId].orEmpty()
            val outcome = incrementsFor(sessions, prescription, utcOffsetMillis)
            val delta = outcome.increments * config.roundToKg
            ProgressedWeight(
                baselineKg = base,
                progressedKg = base + delta,
                explanation = explanation(
                    outcome = when {
                        outcome.increments > 0 -> ProgressionOutcome.APPLIED
                        // A hold caused by missed sessions is not the same as "not enough completed
                        // days yet"; the rule did remove increments and landed on the baseline.
                        outcome.failureApplied -> ProgressionOutcome.HELD_AFTER_MISSED_SESSIONS
                        else -> ProgressionOutcome.NONE_NO_STREAK_YET
                    },
                    increments = outcome.increments,
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

    /**
     * Applies the streak rule and reports whether a failure removed an increment, so a hold at the
     * baseline can be described as failure-driven instead of looking like thin evidence.
     */
    private fun incrementsFor(
        sessions: List<WorkoutSet>,
        prescription: Prescription,
        utcOffsetMillis: Long
    ): ProgressionResult {
        val chronological = sessions
            .groupBy { localEpochDay(it.performedAtMillis, utcOffsetMillis) }
            .entries
            .sortedBy { it.key }
            .map { (_, daySets) -> daySets.isCompleted(prescription) }

        var streak = 0
        var increments = 0
        var failureApplied = false
        for (completed in chronological) {
            if (completed) {
                streak = if (streak < 0) 1 else streak + 1
                if (streak % config.successStreak == 0) increments++
            } else {
                streak = if (streak > 0) -1 else streak - 1
                if (-streak % config.failureStreak == 0) {
                    increments--
                    failureApplied = true
                }
            }
        }
        return ProgressionResult(increments.coerceIn(0, config.maxIncrements), failureApplied)
    }

    /** The streak rule's net increments and whether a missed-session streak removed any. */
    private data class ProgressionResult(val increments: Int, val failureApplied: Boolean)

    private fun List<WorkoutSet>.isCompleted(prescription: Prescription): Boolean = count { set ->
        val reps = set.reps ?: return@count false
        val weight = set.weightKg ?: return@count false
        reps >= prescription.reps && weight >= prescription.weightKg
    } >= prescription.sets
}
