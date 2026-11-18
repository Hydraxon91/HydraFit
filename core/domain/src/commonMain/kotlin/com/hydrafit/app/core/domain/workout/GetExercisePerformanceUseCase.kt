package com.hydrafit.app.core.domain.workout

/**
 * What the exercise details sheet shows about one exercise's history.
 *
 * [lastSessionSets] are the working sets of the most recent session that included the exercise,
 * oldest first, and [lastPerformedAtMillis] is when its newest set was logged. [heaviestSet] is the
 * heaviest recorded weight among the recent window, or null when none recorded a weight (a
 * bodyweight or timed exercise). All three are empty when the exercise has never been logged.
 */
data class ExercisePerformance(
    val lastSessionSets: List<WorkoutSet>,
    val lastPerformedAtMillis: Long?,
    val heaviestSet: WorkoutSet?
) {
    companion object {
        val NONE = ExercisePerformance(emptyList(), null, null)
    }
}

/** A bounded, read-only look at an exercise's recent working sets. Never reads the whole log. */
class GetExercisePerformanceUseCase(private val repository: WorkoutLogRepository) {
    suspend operator fun invoke(exerciseId: String): ExercisePerformance {
        val recent = repository.recentWorkingSetsForExercise(exerciseId, RECENT_SET_LIMIT)
        val newest = recent.firstOrNull() ?: return ExercisePerformance.NONE
        // A set with no recorded session is its own group: unrelated null-session sets are never
        // merged into one workout.
        val lastSession = newest.sessionId
            ?.let { id -> recent.filter { it.sessionId == id } }
            ?: listOf(newest)
        return ExercisePerformance(
            lastSessionSets = lastSession.reversed(),
            lastPerformedAtMillis = newest.performedAtMillis,
            heaviestSet = recent
                .filter { it.weightKg != null }
                .maxWithOrNull(
                    compareBy<WorkoutSet> { it.weightKg }.thenBy { it.reps ?: 0 }
                )
        )
    }

    private companion object {
        const val RECENT_SET_LIMIT = 60
    }
}
