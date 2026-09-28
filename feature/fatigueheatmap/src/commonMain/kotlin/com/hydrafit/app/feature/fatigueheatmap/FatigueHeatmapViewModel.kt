package com.hydrafit.app.feature.fatigueheatmap

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hydrafit.app.core.domain.fatigue.CalculateMuscleFatigueUseCase
import com.hydrafit.app.core.domain.fatigue.MuscleGroup
import com.hydrafit.app.core.domain.time.TimeProvider
import com.hydrafit.app.core.domain.workout.WorkoutLogRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class FatigueHeatmapViewModel(
    private val workoutLogRepository: WorkoutLogRepository,
    private val calculateMuscleFatigue: CalculateMuscleFatigueUseCase,
    private val timeProvider: TimeProvider
) : ViewModel() {

    private val _state = MutableStateFlow(FatigueHeatmapUiState())
    val state: StateFlow<FatigueHeatmapUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val sets = workoutLogRepository.loggedSets()
            val scores = calculateMuscleFatigue(sets, timeProvider.nowMillis())
            val entries = MuscleGroup.entries.map { muscle ->
                MuscleFatigueEntry(muscle = muscle, score = scores[muscle] ?: 0.0)
            }
            _state.update { it.copy(entries = entries, isLoading = false) }
        }
    }
}
