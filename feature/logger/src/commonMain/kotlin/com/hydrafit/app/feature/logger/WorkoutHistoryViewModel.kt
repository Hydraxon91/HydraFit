package com.hydrafit.app.feature.logger

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hydrafit.app.core.domain.time.TimeProvider
import com.hydrafit.app.core.domain.workout.ExerciseNoteRepository
import com.hydrafit.app.core.domain.workout.WorkoutHistoryCursor
import com.hydrafit.app.core.domain.workout.WorkoutHistoryKey
import com.hydrafit.app.core.domain.workout.WorkoutHistoryRepository
import com.hydrafit.app.core.domain.workout.WorkoutHistoryRequest
import com.hydrafit.app.core.domain.workout.WorkoutHistorySet
import com.hydrafit.app.core.domain.workout.WorkoutHistorySetCursor
import com.hydrafit.app.core.domain.workout.WorkoutHistorySetRequest
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
 * One applied list query plus the token that makes an explicit re-apply observable even when the
 * filter itself did not change.
 */
private data class HistoryListQuery(val request: WorkoutHistoryRequest, val token: Long)

/**
 * Presentation state for the Logger's bounded history browser.
 *
 * Every read goes through [WorkoutHistoryRepository], so the list and an open entry are as bounded as
 * the query contract already allows: the first page of each read stays live, and any further page is
 * appended on request. A new emission on the live first page invalidates the pages that were derived
 * from it, because a keyset cursor only describes the filter and content that produced it.
 */
class WorkoutHistoryViewModel(
    private val historyRepository: WorkoutHistoryRepository,
    private val exerciseNoteRepository: ExerciseNoteRepository,
    weightUnitRepository: WeightUnitRepository,
    timeProvider: TimeProvider
) : ViewModel() {

    private val utcOffsetMillis: Long = timeProvider.utcOffsetMillis()

    private val _state = MutableStateFlow(WorkoutHistoryUiState(utcOffsetMillis = utcOffsetMillis))
    val state: StateFlow<WorkoutHistoryUiState> = _state.asStateFlow()

    private val listQuery = MutableStateFlow(
        HistoryListQuery(
            WorkoutHistoryRequest(utcOffsetMillis = utcOffsetMillis),
            token = 0L
        )
    )

    private var listGeneration: Long = 0L
    private var listNextCursor: WorkoutHistoryCursor? = null
    private var listLoadMoreJob: Job? = null

    private var detailGeneration: Long = 0L
    private var detailObservation: Job? = null
    private var setsNextCursor: WorkoutHistorySetCursor? = null
    private var setsLoadMoreJob: Job? = null
    private val requestedNoteIds = mutableSetOf<String>()

    init {
        viewModelScope.launch {
            weightUnitRepository.unitFlow().collect { unit ->
                _state.update { it.copy(weightUnit = unit) }
            }
        }
        viewModelScope.launch {
            listQuery.collectLatest { query -> observeFirstListPage(query.request) }
        }
    }

    // ── Filters ───────────────────────────────────────────────────────────

    fun onSearchDraftChanged(value: String) {
        _state.update { it.copy(searchDraft = value) }
    }

    /** Applies the typed search; the draft is kept so the user can keep refining it. */
    fun onApplySearch() {
        applyFilter(_state.value.filter.copy(search = _state.value.searchDraft))
    }

    fun onDayFromChanged(epochDay: Long?) {
        applyFilter(_state.value.filter.copy(dayFrom = epochDay))
    }

    fun onDayToChanged(epochDay: Long?) {
        applyFilter(_state.value.filter.copy(dayTo = epochDay))
    }

    fun onClearFilters() {
        _state.update { it.copy(searchDraft = "") }
        applyFilter(WorkoutHistoryFilter())
    }

    /** Drops both day bounds in one apply, so the list reloads once. */
    fun onClearDates() {
        applyFilter(_state.value.filter.copy(dayFrom = null, dayTo = null))
    }

    /** Reloads the first page for the current filter, e.g. after a recoverable failure. */
    fun onRetry() {
        val current = listQuery.value
        listQuery.value = current.copy(token = current.token + 1)
    }

    fun onLoadMore() {
        if (listLoadMoreJob?.isActive == true) return
        val cursor = listNextCursor ?: return
        val current = _state.value
        if (!current.hasMore || current.loading) return
        val request = listQuery.value.request.copy(cursor = cursor)
        val generation = listGeneration
        _state.update { it.copy(loadingMore = true, loadMoreFailed = false) }
        listLoadMoreJob = viewModelScope.launch {
            try {
                val page = historyRepository.observeEntries(request).first()
                if (generation != listGeneration) return@launch
                listNextCursor = page.nextCursor(request)
                _state.update {
                    it.copy(
                        entries = it.entries + page.entries,
                        hasMore = page.hasMore,
                        loadingMore = false,
                        loadMoreFailed = false
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (generation == listGeneration) {
                    _state.update { it.copy(loadingMore = false, loadMoreFailed = true) }
                }
            }
        }
    }

    private fun applyFilter(filter: WorkoutHistoryFilter) {
        // The chosen dates stay visible even when they are inverted, so the user can fix the range;
        // the last valid query remains in effect in the meantime.
        _state.update { it.copy(filter = filter) }
        if (filter.hasInvalidDayRange) return
        val current = listQuery.value
        listQuery.value = HistoryListQuery(
            request = WorkoutHistoryRequest(
                search = filter.search,
                dayFrom = filter.dayFrom,
                dayTo = filter.dayTo,
                utcOffsetMillis = utcOffsetMillis
            ),
            token = current.token + 1
        )
    }

    private suspend fun observeFirstListPage(request: WorkoutHistoryRequest) {
        val generation = ++listGeneration
        listLoadMoreJob?.cancel()
        listLoadMoreJob = null
        listNextCursor = null
        _state.update {
            it.copy(
                loading = true,
                failed = false,
                hasMore = false,
                loadingMore = false,
                loadMoreFailed = false
            )
        }
        try {
            historyRepository.observeEntries(request).collect { page ->
                if (generation != listGeneration) return@collect
                listNextCursor = page.nextCursor(request)
                _state.update {
                    it.copy(
                        entries = page.entries,
                        loading = false,
                        failed = false,
                        hasMore = page.hasMore
                    )
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            if (generation == listGeneration) {
                _state.update {
                    it.copy(entries = emptyList(), loading = false, failed = true, hasMore = false)
                }
            }
        }
    }

    // ── Open entry ────────────────────────────────────────────────────────

    /** Opens [key] by its explicit identity; nothing is inferred from dates or neighbors. */
    fun onEntrySelected(key: WorkoutHistoryKey) {
        stopDetailObservation()
        val generation = detailGeneration
        requestedNoteIds.clear()
        _state.update {
            it.copy(
                selectedKey = key,
                detailEntry = null,
                detailLoading = true,
                detailFailed = false,
                sets = emptyList(),
                setsLoading = true,
                setsFailed = false,
                hasMoreSets = false,
                loadingMoreSets = false,
                loadMoreSetsFailed = false,
                exerciseNotes = emptyMap()
            )
        }
        detailObservation = viewModelScope.launch {
            launch { observeDetailEntry(key, generation) }
            launch { observeFirstSetPage(key, generation) }
        }
    }

    /** Leaves the open entry; the list keeps its filters and loaded pages. */
    fun onEntryClosed() {
        stopDetailObservation()
        _state.update {
            it.copy(
                selectedKey = null,
                detailEntry = null,
                detailLoading = false,
                detailFailed = false,
                sets = emptyList(),
                setsLoading = false,
                setsFailed = false,
                hasMoreSets = false,
                loadingMoreSets = false,
                loadMoreSetsFailed = false,
                exerciseNotes = emptyMap()
            )
        }
    }

    fun onRetryDetail() {
        val key = _state.value.selectedKey ?: return
        onEntrySelected(key)
    }

    fun onLoadMoreSets() {
        if (setsLoadMoreJob?.isActive == true) return
        val cursor = setsNextCursor ?: return
        val key = _state.value.selectedKey ?: return
        val current = _state.value
        if (!current.hasMoreSets || current.setsLoading) return
        val request = WorkoutHistorySetRequest(cursor = cursor)
        val generation = detailGeneration
        _state.update { it.copy(loadingMoreSets = true, loadMoreSetsFailed = false) }
        setsLoadMoreJob = viewModelScope.launch {
            try {
                val page = historyRepository.observeEntrySets(key, request).first()
                if (generation != detailGeneration) return@launch
                setsNextCursor = page.nextCursor(request)
                _state.update {
                    it.copy(
                        sets = it.sets + page.sets,
                        hasMoreSets = page.hasMore,
                        loadingMoreSets = false,
                        loadMoreSetsFailed = false
                    )
                }
                requestExerciseNotes(page.sets, generation)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (generation == detailGeneration) {
                    _state.update { it.copy(loadingMoreSets = false, loadMoreSetsFailed = true) }
                }
            }
        }
    }

    /** Retries one exercise's current setup note after a failed read. */
    fun onRetryExerciseNote(exerciseId: String) {
        requestedNoteIds.remove(exerciseId)
        requestExerciseNote(exerciseId, detailGeneration)
    }

    private fun stopDetailObservation() {
        detailGeneration++
        detailObservation?.cancel()
        detailObservation = null
        setsLoadMoreJob?.cancel()
        setsLoadMoreJob = null
        setsNextCursor = null
    }

    private suspend fun observeDetailEntry(key: WorkoutHistoryKey, generation: Long) {
        try {
            historyRepository.observeEntry(key, utcOffsetMillis).collect { entry ->
                if (generation != detailGeneration) return@collect
                _state.update {
                    it.copy(detailEntry = entry, detailLoading = false, detailFailed = false)
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            if (generation == detailGeneration) {
                _state.update { it.copy(detailLoading = false, detailFailed = true) }
            }
        }
    }

    private suspend fun observeFirstSetPage(key: WorkoutHistoryKey, generation: Long) {
        val request = WorkoutHistorySetRequest()
        try {
            historyRepository.observeEntrySets(key, request).collect { page ->
                if (generation != detailGeneration) return@collect
                // A fresh first page invalidates every page derived from the previous one.
                setsNextCursor = page.nextCursor(request)
                _state.update {
                    it.copy(
                        sets = page.sets,
                        setsLoading = false,
                        setsFailed = false,
                        hasMoreSets = page.hasMore
                    )
                }
                requestExerciseNotes(page.sets, generation)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            if (generation == detailGeneration) {
                _state.update {
                    it.copy(setsLoading = false, setsFailed = true, hasMoreSets = false)
                }
            }
        }
    }

    /**
     * Reads the current setup note for each distinct exercise in [sets]. Setup notes are current
     * context, not a snapshot of the recorded set, and a failed read is reported as unknown rather
     * than as "no note".
     */
    private fun requestExerciseNotes(sets: List<WorkoutHistorySet>, generation: Long) {
        sets.map { it.exerciseId }.distinct().forEach { exerciseId ->
            requestExerciseNote(exerciseId, generation)
        }
    }

    private fun requestExerciseNote(exerciseId: String, generation: Long) {
        if (!requestedNoteIds.add(exerciseId)) return
        viewModelScope.launch {
            val note = try {
                exerciseNoteRepository.note(exerciseId)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (generation == detailGeneration) {
                    _state.update {
                        it.copy(
                            exerciseNotes = it.exerciseNotes + (
                                exerciseId to
                                    HistoryExerciseNote(exerciseId, null, loadFailed = true)
                                )
                        )
                    }
                }
                return@launch
            }
            if (generation != detailGeneration) return@launch
            _state.update {
                it.copy(
                    exerciseNotes = it.exerciseNotes +
                        (exerciseId to HistoryExerciseNote(exerciseId, note))
                )
            }
        }
    }
}
