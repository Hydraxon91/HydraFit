package com.hydrafit.app.feature.fatigueheatmap

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hydrafit.app.core.domain.fatigue.CalculateMuscleFatigueUseCase
import com.hydrafit.app.core.domain.fatigue.LoggedSet
import com.hydrafit.app.core.domain.fatigue.MuscleGroup
import com.hydrafit.app.core.domain.time.TimeProvider
import com.hydrafit.app.core.domain.workout.WorkoutLogRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class FatigueHeatmapViewModel(
    private val workoutLogRepository: WorkoutLogRepository,
    private val calculateMuscleFatigue: CalculateMuscleFatigueUseCase,
    private val timeProvider: TimeProvider
) : ViewModel() {

    private val _state = MutableStateFlow(FatigueHeatmapUiState())
    val state: StateFlow<FatigueHeatmapUiState> = _state.asStateFlow()
    private var loggedSets: List<LoggedSet>? = null
    private var refreshJob: Job? = null

    init {
        // Observe the log instead of reading it once: the ViewModel is retained across tab
        // switches, so a one-shot read would only refresh after the process is recreated.
        viewModelScope.launch {
            workoutLogRepository.loggedSetsFlow().collect { sets ->
                loggedSets = sets
                refresh()
            }
        }
    }

    fun onResume() {
        refresh()
        if (refreshJob?.isActive == true) return
        refreshJob = viewModelScope.launch {
            while (isActive) {
                delay(60_000L)
                refresh()
            }
        }
    }

    fun onPause() {
        refreshJob?.cancel()
        refreshJob = null
    }

    private fun refresh() {
        val sets = loggedSets ?: return
        val scores = calculateMuscleFatigue(sets, timeProvider.nowMillis())
        val entries = MuscleGroup.entries.map { muscle ->
            MuscleFatigueEntry(muscle = muscle, score = scores[muscle] ?: 0.0)
        }
        _state.update { it.copy(entries = entries, isLoading = false) }
    }
}
