package com.hydrafit.app.feature.progress

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hydrafit.app.core.domain.engine.ExerciseCatalog
import com.hydrafit.app.core.domain.time.TimeProvider
import com.hydrafit.app.core.domain.time.localEpochDay
import com.hydrafit.app.core.domain.workout.ExerciseHistoryCursor
import com.hydrafit.app.core.domain.workout.ExerciseHistoryRequest
import com.hydrafit.app.core.domain.workout.ExerciseProgressRepository
import com.hydrafit.app.core.domain.workout.ExerciseProgressRequest
import com.hydrafit.app.core.userdata.settings.WeightUnitRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Presentation state for Exercise Progress. Read-only: every read goes through
 * [ExerciseProgressRepository] (bounded pages) and the exercise catalog; nothing is written, and a
 * presentation reload never touches the canonical workout log.
 *
 * Cancellation and stale-completion safety: each concern keeps its own generation counter. A newer
 * exercise selection, range, retry or paging context invalidates the older generation, so a late
 * completion from a superseded read can never publish under the new controls.
 */
class ProgressViewModel(
    private val progressRepository: ExerciseProgressRepository,
    exerciseCatalog: ExerciseCatalog,
    weightUnitRepository: WeightUnitRepository,
    private val timeProvider: TimeProvider
) : ViewModel() {

    private val _state =
        MutableStateFlow(ProgressUiState(utcOffsetMillis = timeProvider.utcOffsetMillis()))
    val state: StateFlow<ProgressUiState> = _state.asStateFlow()

    private data class ChartQuery(
        val exerciseId: String,
        val range: ProgressRange,
        val nowMillis: Long,
        val utcOffsetMillis: Long,
        val token: Long
    )

    private val chartQuery = MutableStateFlow<ChartQuery?>(null)
    private var chartGeneration = 0L

    private var historyGeneration = 0L
    private var historyNextCursor: ExerciseHistoryCursor? = null
    private var historyLoadMoreJob: Job? = null

    /**
     * Paging context, separate from the live first-page subscription ([historyGeneration]). A live
     * first-page emission only advances this value, so an appended page derived from an older first
     * page can never publish; a full restart advances both and also cancels the live subscription.
     */
    private var historyPagingGeneration = 0L

    private var supportingGeneration = 0L
    private var supportingJob: Job? = null

    init {
        viewModelScope.launch {
            exerciseCatalog.observeAll().collect { exercises ->
                val options = exercises
                    .map { ProgressExerciseOption(it.id, it.name) }
                    .sortedBy { it.name.lowercase() }
                _state.update { current ->
                    val id = current.selectedExerciseId
                    current.copy(
                        exercises = options,
                        // A selected id that disappears from the catalog keeps its identity and shows
                        // the localized missing-name state rather than being silently cleared.
                        selectedExerciseName = if (id == null) {
                            null
                        } else {
                            options.firstOrNull { it.id == id }?.name
                        }
                    )
                }
            }
        }
        viewModelScope.launch {
            weightUnitRepository.unitFlow().collect { unit ->
                _state.update { it.copy(weightUnit = unit) }
            }
        }
        viewModelScope.launch {
            chartQuery.collectLatest { query -> if (query != null) observeCharts(query) }
        }
    }

    // ── Selection ─────────────────────────────────────────────────────────

    fun onExerciseQueryChanged(value: String) {
        _state.update { it.copy(exerciseQuery = value) }
    }

    /** Selects an exercise and cancels every read that belonged to the previous one. */
    fun onExerciseSelected(exerciseId: String) {
        if (_state.value.selectedExerciseId == exerciseId) return
        cancelAllReads()
        _state.update {
            it.copy(
                selectedExerciseId = exerciseId,
                selectedExerciseName = it.exercises.firstOrNull { e -> e.id == exerciseId }?.name,
                days = emptyList(),
                records = emptyList(),
                chartsLoading = true,
                chartsFailed = false,
                historySets = emptyList(),
                historyHasMore = false,
                loadingMoreHistory = false,
                loadMoreHistoryFailed = false,
                selectedSetId = null,
                supportingSet = null,
                supportingSetLoading = false,
                supportingSetFailed = false,
                supportingSetMissing = false
            )
        }
        startCharts()
        startHistory()
    }

    /** Range limits the charts only; records and history stay all-time. */
    fun onRangeSelected(range: ProgressRange) {
        if (_state.value.range == range) return
        _state.update { it.copy(range = range) }
        startCharts()
    }

    // ── Charts, records ───────────────────────────────────────────────────

    fun onRetryCharts() {
        if (_state.value.hasSelectedExercise) startCharts()
    }

    /** Re-samples the current time and offset, e.g. when the screen returns to the foreground. */
    fun onResume() {
        if (!_state.value.hasSelectedExercise) return
        startCharts()
    }

    private fun startCharts() {
        val id = _state.value.selectedExerciseId ?: return
        val now = timeProvider.nowMillis()
        val offset = timeProvider.utcOffsetMillis()
        val dayTo = localEpochDay(now, offset)
        val dayFrom = dayTo - (_state.value.range.days - 1L)
        _state.update {
            it.copy(
                chartsLoading = true,
                chartsFailed = false,
                utcOffsetMillis = offset,
                chartDayFrom = dayFrom,
                chartDayTo = dayTo
            )
        }
        val current = chartQuery.value
        chartQuery.value = ChartQuery(
            exerciseId = id,
            range = _state.value.range,
            nowMillis = now,
            utcOffsetMillis = offset,
            token = (current?.token ?: 0L) + 1
        )
    }

    private suspend fun observeCharts(query: ChartQuery) {
        val generation = ++chartGeneration
        val dayTo = localEpochDay(query.nowMillis, query.utcOffsetMillis)
        val dayFrom = dayTo - (query.range.days - 1L)
        val request = ExerciseProgressRequest(
            exerciseId = query.exerciseId,
            dayFrom = dayFrom,
            dayTo = dayTo,
            nowMillis = query.nowMillis,
            utcOffsetMillis = query.utcOffsetMillis
        )
        try {
            progressRepository.observeProgress(request).collect { progress ->
                if (generation != chartGeneration) return@collect
                _state.update {
                    it.copy(
                        days = progress.days,
                        records = progress.records,
                        chartsLoading = false,
                        chartsFailed = false
                    )
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            if (generation == chartGeneration) {
                _state.update {
                    it.copy(chartsLoading = false, chartsFailed = true, days = emptyList())
                }
            }
        }
    }

    // ── Recorded history (all-time, newest first) ─────────────────────────

    fun onRetryHistory() {
        if (_state.value.hasSelectedExercise) startHistory()
    }

    private fun startHistory() {
        val id = _state.value.selectedExerciseId ?: return
        val generation = ++historyGeneration
        historyPagingGeneration++
        historyLoadMoreJob?.cancel()
        historyLoadMoreJob = null
        historyNextCursor = null
        _state.update {
            it.copy(
                historySets = emptyList(),
                historyLoading = true,
                historyFailed = false,
                historyHasMore = false,
                loadingMoreHistory = false,
                loadMoreHistoryFailed = false
            )
        }
        viewModelScope.launch {
            try {
                progressRepository.observeHistory(ExerciseHistoryRequest(id)).collect { page ->
                    if (generation != historyGeneration) return@collect
                    // A fresh live first page invalidates every appended page and its cursor: cancel
                    // any in-flight continuation and advance the paging context so a suspended older
                    // page cannot append after this emission.
                    historyPagingGeneration++
                    historyLoadMoreJob?.cancel()
                    historyLoadMoreJob = null
                    historyNextCursor = page.nextCursor
                    _state.update {
                        it.copy(
                            historySets = page.sets,
                            historyHasMore = page.hasMore,
                            historyLoading = false,
                            historyFailed = false,
                            loadingMoreHistory = false,
                            loadMoreHistoryFailed = false
                        )
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (generation == historyGeneration) {
                    _state.update {
                        it.copy(
                            historyLoading = false,
                            historyFailed = true,
                            historyHasMore = false
                        )
                    }
                }
            }
        }
    }

    fun onLoadMoreHistory() {
        if (historyLoadMoreJob?.isActive == true) return
        val cursor = historyNextCursor ?: return
        val current = _state.value
        if (!current.historyHasMore || current.historyLoading || current.loadingMoreHistory) return
        val id = current.selectedExerciseId ?: return
        val request = ExerciseHistoryRequest(id, cursor = cursor)
        val generation = historyGeneration
        val paging = historyPagingGeneration
        _state.update { it.copy(loadingMoreHistory = true, loadMoreHistoryFailed = false) }
        historyLoadMoreJob = viewModelScope.launch {
            try {
                val page = progressRepository.observeHistory(request).first()
                if (generation != historyGeneration || paging != historyPagingGeneration) {
                    return@launch
                }
                historyNextCursor = page.nextCursor
                _state.update {
                    it.copy(
                        historySets = it.historySets + page.sets,
                        historyHasMore = page.hasMore,
                        loadingMoreHistory = false,
                        loadMoreHistoryFailed = false
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (generation == historyGeneration && paging == historyPagingGeneration) {
                    _state.update {
                        it.copy(loadingMoreHistory = false, loadMoreHistoryFailed = true)
                    }
                }
            }
        }
    }

    // ── Supporting recorded set ───────────────────────────────────────────

    /** Resolves one contributing set by its exercise-scoped id, bounded to that id. */
    fun onSetSelected(setId: Long) {
        val id = _state.value.selectedExerciseId ?: return
        supportingJob?.cancel()
        val generation = ++supportingGeneration
        _state.update {
            it.copy(
                selectedSetId = setId,
                supportingSet = null,
                supportingSetLoading = true,
                supportingSetFailed = false,
                supportingSetMissing = false
            )
        }
        supportingJob = viewModelScope.launch {
            try {
                progressRepository.observeSet(id, setId).collect { set ->
                    if (generation != supportingGeneration) return@collect
                    _state.update {
                        it.copy(
                            supportingSet = set,
                            supportingSetLoading = false,
                            supportingSetFailed = false,
                            supportingSetMissing = set == null
                        )
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (generation == supportingGeneration) {
                    _state.update {
                        it.copy(supportingSetLoading = false, supportingSetFailed = true)
                    }
                }
            }
        }
    }

    fun onSupportingSetClosed() {
        supportingJob?.cancel()
        supportingJob = null
        supportingGeneration++
        _state.update {
            it.copy(
                selectedSetId = null,
                supportingSet = null,
                supportingSetLoading = false,
                supportingSetFailed = false,
                supportingSetMissing = false
            )
        }
    }

    private fun cancelAllReads() {
        chartGeneration++
        chartQuery.value = null
        historyGeneration++
        historyLoadMoreJob?.cancel()
        historyLoadMoreJob = null
        historyNextCursor = null
        supportingJob?.cancel()
        supportingJob = null
        supportingGeneration++
    }
}
