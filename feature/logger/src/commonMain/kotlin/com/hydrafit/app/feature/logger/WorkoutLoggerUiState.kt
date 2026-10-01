package com.hydrafit.app.feature.logger

import com.hydrafit.app.core.domain.engine.SplitFocus
import com.hydrafit.app.core.domain.unit.WeightUnit

data class ExerciseOption(
    val id: String,
    val name: String,
    val isBodyweight: Boolean,
    val isUnilateral: Boolean = false
)

data class LoggedSetRow(
    val id: Long,
    val exerciseName: String,
    val reps: Int,
    val weightKg: Double?,
    val isWarmup: Boolean,
    val weekNumber: Int? = null,
    val dayIndex: Int? = null
)

/** A planned exercise offered in the Logger; nothing here counts until it is confirmed. */
data class DraftSet(
    val exerciseId: String,
    val name: String,
    val sets: Int,
    val reps: Int,
    val weightKg: Double?
)

data class WorkoutLoggerUiState(
    val exercises: List<ExerciseOption> = emptyList(),
    val exerciseSearch: String = "",
    val selectedExerciseId: String? = null,
    val reps: String = "",
    val weightInput: String = "",
    val rir: String = "",
    val isWarmup: Boolean = false,
    val recentSets: List<LoggedSetRow> = emptyList(),
    val draftSets: List<DraftSet> = emptyList(),
    val todayFocus: SplitFocus? = null,
    val weightUnit: WeightUnit = WeightUnit.KG,
    val weightRevealed: Boolean = false
) {
    val canLog: Boolean
        get() = selectedExerciseId != null && (reps.toIntOrNull() ?: 0) > 0

    /**
     * Whether the weight field is shown: always for weighted exercises, and for bodyweight
     * exercises only once the user reveals it to log a weighted variant.
     */
    val showWeightField: Boolean
        get() = weightRevealed ||
            exercises.firstOrNull { it.id == selectedExerciseId }?.isBodyweight != true

    /** True when the selected exercise is one-side-at-a-time, so the entered weight is per hand. */
    val selectedExerciseIsUnilateral: Boolean
        get() = exercises.firstOrNull { it.id == selectedExerciseId }?.isUnilateral == true

    /** Exercises matching the picker search, or all of them when the search is blank. */
    val visibleExercises: List<ExerciseOption>
        get() = if (exerciseSearch.isBlank()) {
            exercises
        } else {
            exercises.filter { it.name.contains(exerciseSearch.trim(), ignoreCase = true) }
        }
}
