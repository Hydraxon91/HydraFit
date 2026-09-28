package com.hydrafit.app.feature.logger

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hydrafit.app.core.domain.engine.ExerciseCatalog
import com.hydrafit.app.core.domain.time.TimeProvider
import com.hydrafit.app.core.domain.workout.GetWorkoutLogUseCase
import com.hydrafit.app.core.domain.workout.LogWorkoutSetUseCase
import com.hydrafit.app.core.domain.workout.WorkoutSet
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class WorkoutLoggerViewModel(
    private val logWorkoutSet: LogWorkoutSetUseCase,
    private val getWorkoutLog: GetWorkoutLogUseCase,
    private val exerciseCatalog: ExerciseCatalog,
    private val timeProvider: TimeProvider
) : ViewModel() {

    private val _state = MutableStateFlow(WorkoutLoggerUiState())
    val state: StateFlow<WorkoutLoggerUiState> = _state.asStateFlow()

    private var exerciseNames: Map<String, String> = emptyMap()

    init {
        viewModelScope.launch {
            val exercises = exerciseCatalog.all()
            exerciseNames = exercises.associate { it.id to it.name }
            _state.update { current ->
                current.copy(
                    exercises = exercises
                        .map { ExerciseOption(id = it.id, name = it.name) }
                        .sortedBy { it.name }
                )
            }
            refreshRecentSets()
        }
    }

    fun onExerciseSelected(exerciseId: String) {
        _state.update { it.copy(selectedExerciseId = exerciseId) }
    }

    fun onRepsChanged(value: String) {
        _state.update { it.copy(reps = value.filter(Char::isDigit)) }
    }

    fun onWeightChanged(value: String) {
        _state.update { it.copy(weightKg = value.filter { char -> char.isDigit() || char == '.' }) }
    }

    fun onWarmupToggled(isWarmup: Boolean) {
        _state.update { it.copy(isWarmup = isWarmup) }
    }

    fun log() {
        val current = _state.value
        val exerciseId = current.selectedExerciseId ?: return
        val reps = current.reps.toIntOrNull() ?: return
        if (reps <= 0) return
        val weightKg = current.weightKg.toDoubleOrNull()

        viewModelScope.launch {
            logWorkoutSet(
                WorkoutSet(
                    exerciseId = exerciseId,
                    reps = reps,
                    weightKg = weightKg,
                    performedAtMillis = timeProvider.nowMillis(),
                    isWarmup = current.isWarmup
                )
            )
            _state.update { it.copy(reps = "", weightKg = "", isWarmup = false) }
            refreshRecentSets()
        }
    }

    private suspend fun refreshRecentSets() {
        val rows = getWorkoutLog()
            .sortedByDescending { it.performedAtMillis }
            .map { set ->
                LoggedSetRow(
                    exerciseName = exerciseNames[set.exerciseId] ?: set.exerciseId,
                    reps = set.reps,
                    weightKg = set.weightKg,
                    isWarmup = set.isWarmup
                )
            }
        _state.update { it.copy(recentSets = rows) }
    }
}
