package com.hydrafit.app.feature.splitbuilder

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hydrafit.app.core.domain.engine.ExerciseCatalog
import com.hydrafit.app.core.domain.engine.GenerateWeeklySplitUseCase
import com.hydrafit.app.core.domain.engine.PlanRequest
import com.hydrafit.app.core.domain.fatigue.CalculateMuscleFatigueUseCase
import com.hydrafit.app.core.domain.time.TimeProvider
import com.hydrafit.app.core.domain.workout.WorkoutLogRepository
import com.hydrafit.app.core.userdata.equipment.EquipmentSelectionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SplitBuilderViewModel(
    private val generateWeeklySplit: GenerateWeeklySplitUseCase,
    private val equipmentSelectionRepository: EquipmentSelectionRepository,
    private val workoutLogRepository: WorkoutLogRepository,
    private val calculateMuscleFatigue: CalculateMuscleFatigueUseCase,
    private val exerciseCatalog: ExerciseCatalog,
    private val timeProvider: TimeProvider
) : ViewModel() {

    private val _state = MutableStateFlow(SplitBuilderUiState())
    val state: StateFlow<SplitBuilderUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun onDaysPerWeekSelected(daysPerWeek: Int) {
        _state.update { it.copy(daysPerWeek = daysPerWeek) }
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            val nowMillis = timeProvider.nowMillis()
            val request = PlanRequest(
                daysPerWeek = _state.value.daysPerWeek,
                availableEquipment = equipmentSelectionRepository.selected(),
                muscleFatigue = calculateMuscleFatigue(workoutLogRepository.loggedSets(), nowMillis),
                nowMillis = nowMillis
            )
            val plan = generateWeeklySplit(request)
            val names = exerciseCatalog.all().associate { it.id to it.name }
            _state.update { it.copy(plan = plan, exerciseNames = names, isLoading = false) }
        }
    }
}
