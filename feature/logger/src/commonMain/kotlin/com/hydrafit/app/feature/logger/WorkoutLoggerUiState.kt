package com.hydrafit.app.feature.logger

data class ExerciseOption(val id: String, val name: String)

data class LoggedSetRow(
    val exerciseName: String,
    val reps: Int,
    val weightKg: Double?,
    val isWarmup: Boolean
)

data class WorkoutLoggerUiState(
    val exercises: List<ExerciseOption> = emptyList(),
    val selectedExerciseId: String? = null,
    val reps: String = "",
    val weightKg: String = "",
    val isWarmup: Boolean = false,
    val recentSets: List<LoggedSetRow> = emptyList()
) {
    val canLog: Boolean
        get() = selectedExerciseId != null && (reps.toIntOrNull() ?: 0) > 0
}
