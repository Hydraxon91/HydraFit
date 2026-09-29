package com.hydrafit.app.feature.logger

import com.hydrafit.app.core.domain.engine.SplitFocus
import com.hydrafit.app.core.domain.unit.WeightUnit

data class ExerciseOption(val id: String, val name: String)

data class LoggedSetRow(
    val id: Long,
    val exerciseName: String,
    val reps: Int,
    val weightKg: Double?,
    val isWarmup: Boolean
)

data class WorkoutLoggerUiState(
    val exercises: List<ExerciseOption> = emptyList(),
    val selectedExerciseId: String? = null,
    val reps: String = "",
    val weightInput: String = "",
    val isWarmup: Boolean = false,
    val recentSets: List<LoggedSetRow> = emptyList(),
    val todayFocus: SplitFocus? = null,
    val weightUnit: WeightUnit = WeightUnit.KG
) {
    val canLog: Boolean
        get() = selectedExerciseId != null && (reps.toIntOrNull() ?: 0) > 0
}
