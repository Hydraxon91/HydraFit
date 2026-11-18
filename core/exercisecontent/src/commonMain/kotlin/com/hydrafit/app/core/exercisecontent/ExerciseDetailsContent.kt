package com.hydrafit.app.core.exercisecontent

import com.hydrafit.app.core.domain.fatigue.MuscleGroup

/**
 * What the exercise details card shows beside the instructions. The owning feature supplies it
 * already formatted (units, dates), so this module holds no formatting or storage logic.
 */
data class ExerciseDetailsContent(
    val primaryMuscles: List<MuscleGroup>,
    val secondaryMuscles: List<MuscleGroup>,
    val history: ExerciseDetailsHistory
)

/** The "last time" part of the card. [Hidden] while it loads or when the read failed. */
sealed interface ExerciseDetailsHistory {
    data object Hidden : ExerciseDetailsHistory

    /** The exercise has never been logged. */
    data object None : ExerciseDetailsHistory

    /** [setLines] are the last session's working sets, such as "80 kg × 8". */
    data class Logged(
        val dateLabel: String,
        val setLines: List<String>,
        val heaviestLine: String?
    ) : ExerciseDetailsHistory
}
