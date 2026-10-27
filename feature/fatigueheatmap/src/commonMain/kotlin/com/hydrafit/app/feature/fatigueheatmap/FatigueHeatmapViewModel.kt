package com.hydrafit.app.feature.fatigueheatmap

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hydrafit.app.core.domain.fatigue.CalculateMuscleFatigueUseCase
import com.hydrafit.app.core.domain.fatigue.CoverageSet
import com.hydrafit.app.core.domain.fatigue.LoggedSet
import com.hydrafit.app.core.domain.fatigue.MuscleGroup
import com.hydrafit.app.core.domain.fatigue.TrainingCoverageCalculator
import com.hydrafit.app.core.domain.fatigue.TrainingCoverageRepository
import com.hydrafit.app.core.domain.time.TimeProvider
import com.hydrafit.app.core.domain.workout.WorkoutLogRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class FatigueHeatmapViewModel(
    private val workoutLogRepository: WorkoutLogRepository,
    private val calculateMuscleFatigue: CalculateMuscleFatigueUseCase,
    private val timeProvider: TimeProvider,
    private val trainingCoverageRepository: TrainingCoverageRepository,
    private val calculationDispatcher: CoroutineDispatcher = Dispatchers.Default
) : ViewModel() {

    private val _state = MutableStateFlow(FatigueHeatmapUiState())
    val state: StateFlow<FatigueHeatmapUiState> = _state.asStateFlow()
    private var loggedSets: List<LoggedSet>? = null
    private var coverageSets: List<CoverageSet>? = null
    private var refreshJob: Job? = null

    // Refreshes can overlap (a repository emission, a resume and a tick all launch their own
    // coroutine), so the newest refresh owns the state: an older computation that finishes late
    // must not commit its now-stale capture over a newer one. All refreshes run on the ViewModel
    // scope, so a plain counter is enough.
    private var refreshGeneration = 0

    init {
        // Observe the log instead of reading it once: the ViewModel is retained across tab
        // switches, so a one-shot read would only refresh after the process is recreated.
        viewModelScope.launch {
            workoutLogRepository.loggedSetsFlow().collect { sets ->
                loggedSets = sets
                refresh()
            }
        }
        // Coverage is an independent recorded snapshot: it never borrows the fatigue reader's
        // catalog fallback, so it is observed on its own flow and recomputed with the same clock.
        viewModelScope.launch {
            trainingCoverageRepository.observeSets().collect { sets ->
                coverageSets = sets
                refresh()
            }
        }
    }

    fun onResume() {
        viewModelScope.launch { refresh() }
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

    private suspend fun refresh() {
        val sets = loggedSets
        val coverageInput = coverageSets
        if (sets == null && coverageInput == null) return
        val generation = ++refreshGeneration
        // One clock read pair per refresh — nowMillis() plus utcOffsetMillis(), whose Android
        // implementation consults the clock internally — keeps the existing resume/tick read count
        // observable; the coverage window then rolls over with the tick, while last-trained keeps
        // all history.
        val now = timeProvider.nowMillis()
        val utcOffset = timeProvider.utcOffsetMillis()
        val scores = if (sets != null) {
            withContext(calculationDispatcher) { calculateMuscleFatigue(sets, now) }
        } else {
            _state.value.entries.associate { it.muscle to it.score }
        }
        val coverage = if (coverageInput != null) {
            withContext(calculationDispatcher) {
                TrainingCoverageCalculator.calculate(coverageInput, now, utcOffset)
            }
        } else {
            null
        }
        val coverageByMuscle = coverage?.entries?.associateBy { it.muscle }
        val entries = MuscleGroup.entries.map { muscle ->
            val coverageEntry = coverageByMuscle?.get(muscle)
            MuscleFatigueEntry(
                muscle = muscle,
                score = scores[muscle] ?: 0.0,
                workingSets = coverageEntry?.workingSets ?: 0,
                clusters = coverageEntry?.clusters ?: 0,
                estimatedInvolvementCredits = coverageEntry?.estimatedInvolvementCredits ?: 0.0,
                lastTrainedAtMillis = coverageEntry?.lastTrainedAtMillis
            )
        }
        // A newer refresh has started since this one captured its time: leave the newer state alone.
        if (generation != refreshGeneration) return
        _state.update {
            it.copy(
                entries = entries,
                utcOffsetMillis = utcOffset,
                unclassifiedSetsInWindow = coverage?.unclassifiedSetsInWindow ?: 0,
                hasUnclassifiedHistory = coverage?.hasUnclassifiedHistory ?: false,
                coverageComputed = coverage != null,
                isLoading = false
            )
        }
    }
}
