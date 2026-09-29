package com.hydrafit.app.core.domain.engine

import com.hydrafit.app.core.domain.workout.WorkoutSet

/** The target the user was following for one exercise, taken from an accepted plan. */
data class Prescription(val exerciseId: String, val sets: Int, val reps: Int, val weightKg: Double)

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

    operator fun invoke(
        baseline: Map<String, Double>,
        prescriptions: Map<String, Prescription>,
        sets: List<WorkoutSet>,
        pauseIncrements: Boolean = false
    ): Map<String, Double> {
        if (pauseIncrements) return baseline

        val byExercise = sets
            .filter { !it.isWarmup && (it.weightKg ?: 0.0) > 0.0 }
            .groupBy { it.exerciseId }

        return baseline.mapValues { (exerciseId, base) ->
            val prescription = prescriptions[exerciseId] ?: return@mapValues base
            val sessions = byExercise[exerciseId].orEmpty()
            val increments = incrementsFor(sessions, prescription)
            base + increments * config.roundToKg
        }
    }

    private fun incrementsFor(sessions: List<WorkoutSet>, prescription: Prescription): Int {
        val chronological = sessions
            .groupBy { it.performedAtMillis / MILLIS_PER_DAY }
            .entries
            .sortedBy { it.key }
            .map { (_, daySets) -> daySets.isCompleted(prescription) }

        var streak = 0
        var increments = 0
        for (completed in chronological) {
            if (completed) {
                streak = if (streak < 0) 1 else streak + 1
                if (streak % config.successStreak == 0) increments++
            } else {
                streak = if (streak > 0) -1 else streak - 1
                if (-streak % config.failureStreak == 0) increments--
            }
        }
        return increments.coerceIn(0, config.maxIncrements)
    }

    private fun List<WorkoutSet>.isCompleted(prescription: Prescription): Boolean = count { set ->
        val weight = set.weightKg ?: return@count false
        set.reps >= prescription.reps && weight >= prescription.weightKg
    } >= prescription.sets

    private companion object {
        const val MILLIS_PER_DAY = 24L * 60L * 60L * 1000L
    }
}
