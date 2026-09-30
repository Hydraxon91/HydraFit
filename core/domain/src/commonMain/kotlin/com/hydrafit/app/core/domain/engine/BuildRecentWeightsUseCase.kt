package com.hydrafit.app.core.domain.engine

import com.hydrafit.app.core.domain.time.localEpochDay
import com.hydrafit.app.core.domain.workout.WorkoutSet

/** A representative working set shared with the AI engines so they can see recent training. */
data class WeightHistoryEntry(
    val exerciseId: String,
    val performedAtMillis: Long,
    val weightKg: Double,
    val reps: Int
)

data class RecentWeightsConfig(val maxDatesPerExercise: Int = 2) {
    init {
        require(maxDatesPerExercise >= 1) { "maxDatesPerExercise must be at least 1" }
    }
}

/**
 * Bounds the weight history sent to the models: for each exercise, the heaviest non-warmup weighted
 * set on each of the most recent distinct days.
 */
class BuildRecentWeightsUseCase(private val config: RecentWeightsConfig = RecentWeightsConfig()) {
    operator fun invoke(
        sets: List<WorkoutSet>,
        utcOffsetMillis: Long = 0L
    ): List<WeightHistoryEntry> = sets
        .filter { !it.isWarmup && (it.weightKg ?: 0.0) > 0.0 }
        .groupBy { it.exerciseId }
        .flatMap { (exerciseId, rows) -> recentFor(exerciseId, rows, utcOffsetMillis) }
        .sortedWith(compareBy({ it.exerciseId }, { it.performedAtMillis }))

    private fun recentFor(
        exerciseId: String,
        rows: List<WorkoutSet>,
        utcOffsetMillis: Long
    ): List<WeightHistoryEntry> =
        rows.groupBy { localEpochDay(it.performedAtMillis, utcOffsetMillis) }
            .entries
            .sortedByDescending { it.key }
            .take(config.maxDatesPerExercise)
            .mapNotNull { (_, dayRows) ->
                val best = dayRows.maxWithOrNull(
                    compareBy({ it.weightKg ?: 0.0 }, { it.performedAtMillis })
                ) ?: return@mapNotNull null
                val weight = best.weightKg ?: return@mapNotNull null
                WeightHistoryEntry(
                    exerciseId = exerciseId,
                    performedAtMillis = best.performedAtMillis,
                    weightKg = weight,
                    reps = best.reps
                )
            }
}
