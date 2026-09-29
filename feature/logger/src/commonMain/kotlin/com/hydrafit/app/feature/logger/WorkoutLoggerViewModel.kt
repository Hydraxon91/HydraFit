package com.hydrafit.app.feature.logger

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hydrafit.app.core.domain.engine.AcceptedDay
import com.hydrafit.app.core.domain.engine.AcceptedPlan
import com.hydrafit.app.core.domain.engine.ExerciseCatalog
import com.hydrafit.app.core.domain.engine.ObserveAcceptedPlanUseCase
import com.hydrafit.app.core.domain.equipment.Exercise
import com.hydrafit.app.core.domain.time.TimeProvider
import com.hydrafit.app.core.domain.time.dayOfWeek
import com.hydrafit.app.core.domain.unit.WeightUnit
import com.hydrafit.app.core.domain.unit.formatWeight
import com.hydrafit.app.core.domain.workout.DeleteWorkoutSetUseCase
import com.hydrafit.app.core.domain.workout.GetWorkoutLogUseCase
import com.hydrafit.app.core.domain.workout.LogWorkoutSetUseCase
import com.hydrafit.app.core.domain.workout.WorkoutSet
import com.hydrafit.app.core.userdata.settings.WeightUnitRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class WorkoutLoggerViewModel(
    private val logWorkoutSet: LogWorkoutSetUseCase,
    private val getWorkoutLog: GetWorkoutLogUseCase,
    private val deleteWorkoutSet: DeleteWorkoutSetUseCase,
    private val observeAcceptedPlan: ObserveAcceptedPlanUseCase,
    private val exerciseCatalog: ExerciseCatalog,
    private val timeProvider: TimeProvider,
    private val weightUnitRepository: WeightUnitRepository
) : ViewModel() {

    private val _state = MutableStateFlow(WorkoutLoggerUiState())
    val state: StateFlow<WorkoutLoggerUiState> = _state.asStateFlow()

    private var exercises: List<Exercise> = emptyList()
    private var exerciseNames: Map<String, String> = emptyMap()
    private var acceptedToday: AcceptedDay? = null
    private var suggestedWeightKgByExercise: Map<String, Double> = emptyMap()

    init {
        viewModelScope.launch {
            exercises = exerciseCatalog.all()
            exerciseNames = exercises.associate { it.id to it.name }
            _state.update { current ->
                current.copy(
                    exercises = prioritizedByToday(exercises, acceptedToday)
                )
            }
            refreshRecentSets()
        }
        viewModelScope.launch {
            observeAcceptedPlan().collectLatest { plan -> updateTodayPlan(plan) }
        }
        viewModelScope.launch {
            weightUnitRepository.unitFlow().collectLatest { unit ->
                _state.update { it.copy(weightUnit = unit) }
            }
        }
    }

    fun onExerciseSelected(exerciseId: String) {
        _state.update {
            it.copy(
                selectedExerciseId = exerciseId,
                weightInput = suggestedInputFor(exerciseId, it.weightUnit),
                reps = suggestedRepsFor(exerciseId) ?: it.reps
            )
        }
    }

    fun onRepsChanged(value: String) {
        _state.update { it.copy(reps = value.filter(Char::isDigit)) }
    }

    fun onWeightChanged(value: String) {
        _state.update {
            it.copy(weightInput = value.filter { char -> char.isDigit() || char == '.' })
        }
    }

    fun onWarmupToggled(isWarmup: Boolean) {
        _state.update { it.copy(isWarmup = isWarmup) }
    }

    fun deleteSet(id: Long) {
        viewModelScope.launch {
            deleteWorkoutSet(id)
            refreshRecentSets()
        }
    }

    fun log() {
        val current = _state.value
        val exerciseId = current.selectedExerciseId ?: return
        val reps = current.reps.toIntOrNull() ?: return
        if (reps <= 0) return
        val weightKg = current.weightInput.toDoubleOrNull()
            ?.let { current.weightUnit.displayToKilograms(it) }

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
            _state.update { it.copy(reps = "", weightInput = "", isWarmup = false) }
            refreshRecentSets()
        }
    }

    private fun updateTodayPlan(plan: AcceptedPlan?) {
        acceptedToday = plan?.dayFor(dayOfWeek(timeProvider.nowMillis()))
        suggestedWeightKgByExercise = acceptedToday?.exercises
            ?.mapNotNull { exercise ->
                exercise.suggestedWeightKg?.let { exercise.exerciseId to it }
            }
            ?.toMap()
            .orEmpty()
        _state.update { current ->
            current.copy(
                exercises = prioritizedByToday(exercises, acceptedToday),
                todayFocus = acceptedToday?.focus,
                reps = current.reps,
                weightInput = current.selectedExerciseId
                    ?.let { suggestedInputFor(it, current.weightUnit) }
                    ?: current.weightInput
            )
        }
    }

    /** Prefills the weight field with the accepted plan's suggestion, in the display unit. */
    private fun suggestedInputFor(exerciseId: String, unit: WeightUnit): String =
        suggestedWeightKgByExercise[exerciseId]
            ?.let { formatWeight(unit.kilogramsToDisplay(it)) }
            .orEmpty()

    private fun suggestedRepsFor(exerciseId: String): String? = acceptedToday?.exercises
        ?.firstOrNull { it.exerciseId == exerciseId }
        ?.reps
        ?.toString()

    private fun prioritizedByToday(
        exercises: List<Exercise>,
        today: AcceptedDay?
    ): List<ExerciseOption> {
        val priority = today?.exercises
            ?.mapIndexed { index, accepted -> accepted.exerciseId to index }
            ?.toMap()
            .orEmpty()
        return exercises
            .map { ExerciseOption(id = it.id, name = it.name) }
            .sortedWith(compareBy({ priority[it.id] ?: Int.MAX_VALUE }, { it.name }))
    }

    private suspend fun refreshRecentSets() {
        val rows = getWorkoutLog()
            .sortedByDescending { it.performedAtMillis }
            .map { set ->
                LoggedSetRow(
                    id = set.id,
                    exerciseName = exerciseNames[set.exerciseId] ?: set.exerciseId,
                    reps = set.reps,
                    weightKg = set.weightKg,
                    isWarmup = set.isWarmup
                )
            }
        _state.update { it.copy(recentSets = rows) }
    }
}
