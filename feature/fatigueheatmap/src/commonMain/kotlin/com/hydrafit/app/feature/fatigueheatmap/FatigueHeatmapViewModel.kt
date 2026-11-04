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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
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
    private var readersJob: Job? = null

    /**
     * True once the modelled scores have been computed from an actual log read. A coverage-only
     * refresh may reuse the last computed scores, but before the first log read it must not publish
     * the empty grid as a computed map.
     */
    private var scoresReady = false

    // Refreshes can overlap (a repository emission, a resume and a tick all launch their own
    // coroutine), so the newest refresh owns the state: an older computation that finishes late
    // must not commit its now-stale capture over a newer one. All refreshes run on the ViewModel
    // scope, so a plain counter is enough.
    private var refreshGeneration = 0

    init {
        // Observe the log instead of reading it once: the ViewModel is retained across tab
        // switches, so a one-shot read would only refresh after the process is recreated.
        observeReads()
    }

    /**
     * Restarts both reads after a failure. A read failure ends the subscriptions, so the previous
     * partial grid would otherwise stay on screen as if it were current.
     */
    fun retry() {
        if (!_state.value.loadFailed) return
        // A refresh that started before the failure may still be computing; supersede it so it
        // cannot publish its pre-failure capture after this retry clears the screen to loading.
        refreshGeneration++
        // Drop the pre-failure captures too: a tick or resume in the retry window would otherwise
        // recompute from them and show old scores as if the new read had succeeded.
        loggedSets = null
        coverageSets = null
        scoresReady = false
        _state.update { it.copy(isLoading = true, loadFailed = false) }
        observeReads()
    }

    private fun observeReads() {
        readersJob?.cancel()
        readersJob = viewModelScope.launch {
            try {
                coroutineScope {
                    // The log drives the modelled estimate; coverage is an independent recorded
                    // snapshot and never borrows the fatigue reader's catalog fallback.
                    launch {
                        workoutLogRepository.loggedSetsFlow().collect { sets ->
                            loggedSets = sets
                            refresh()
                        }
                    }
                    launch {
                        trainingCoverageRepository.observeSets().collect { sets ->
                            coverageSets = sets
                            refresh()
                        }
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // Keep whatever was already computed, but stop presenting it as current: the screen
                // offers a retry instead of an unlabelled partial map.
                _state.update { it.copy(isLoading = false, loadFailed = true) }
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
        // Coverage can arrive before the log. Publishing a zero grid then would state "no fatigue"
        // before any score was modelled, so wait for the log read (or a previous computed score).
        if (sets == null && !scoresReady) return
        val generation = ++refreshGeneration
        // One clock read pair per refresh — nowMillis() plus utcOffsetMillis(), whose Android
        // implementation consults the clock internally — keeps the existing resume/tick read count
        // observable; the coverage window then rolls over with the tick, while last-trained keeps
        // all history.
        val now = timeProvider.nowMillis()
        val utcOffset = timeProvider.utcOffsetMillis()
        val scores = if (sets != null) {
            withContext(calculationDispatcher) { calculateMuscleFatigue(sets, now) }
                .also { scoresReady = true }
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
