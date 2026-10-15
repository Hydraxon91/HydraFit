package com.hydrafit.app.feature.logger

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hydrafit.app.core.domain.engine.ExerciseCatalog
import com.hydrafit.app.core.domain.time.TimeProvider
import com.hydrafit.app.core.domain.workout.ObserveOccurrenceRecordsUseCase
import com.hydrafit.app.core.domain.workout.OccurrenceRecordImprovement
import com.hydrafit.app.core.userdata.settings.WeightUnitRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Presentation state for the post-finish summary's records section.
 *
 * Route-owned and read-only: it observes occurrence record improvements, resolves effective exercise
 * names and the display unit, and never finishes a workout, writes a set or touches the summary.
 * Opening a new occurrence (or clearing it) cancels the previous observation, and a late result from
 * a superseded occurrence is dropped by a generation guard so stale context never keeps its badges.
 */
class WorkoutSummaryRecordsViewModel(
    private val observeOccurrenceRecords: ObserveOccurrenceRecordsUseCase,
    private val exerciseCatalog: ExerciseCatalog,
    private val weightUnitRepository: WeightUnitRepository,
    private val timeProvider: TimeProvider
) : ViewModel() {

    private val _state = MutableStateFlow(WorkoutSummaryRecordsUiState())
    val state: StateFlow<WorkoutSummaryRecordsUiState> = _state.asStateFlow()

    private var occurrenceId: Long? = null
    private var generation: Long = 0L
    private var observation: Job? = null

    init {
        viewModelScope.launch {
            weightUnitRepository.unitFlow().collect { unit ->
                _state.update { it.copy(unit = unit) }
            }
        }
    }

    /** Opens [id], or clears everything when null. A change cancels the previous observation. */
    fun onOccurrenceChanged(id: Long?) {
        if (id == occurrenceId) return
        occurrenceId = id
        if (id == null) {
            observation?.cancel()
            observation = null
            generation++
            _state.update { it.copy(screen = WorkoutSummaryRecordsScreen.Hidden) }
        } else {
            restart()
        }
    }

    /** Re-samples the clock and reloads after a resume; a no-op while no occurrence is open. */
    fun onResume() {
        if (occurrenceId != null) restart()
    }

    /** Retries a failed read; a no-op while no occurrence is open. */
    fun onRetry() {
        if (occurrenceId != null) restart()
    }

    private fun restart() {
        val id = occurrenceId ?: return
        observation?.cancel()
        val currentGeneration = ++generation
        // Sample the clock once per observation so the record read is bounded by the same instant.
        val now = timeProvider.nowMillis()
        val utcOffsetMillis = timeProvider.utcOffsetMillis()
        _state.update { it.copy(screen = WorkoutSummaryRecordsScreen.Loading) }
        observation = viewModelScope.launch {
            try {
                combine(
                    observeOccurrenceRecords(id, now, utcOffsetMillis),
                    exerciseCatalog.observeAll()
                ) { improvements, exercises -> improvements to exercises }
                    .collect { (improvements, exercises) ->
                        if (currentGeneration != generation) return@collect
                        val names = exercises.associate { it.id to it.name }
                        val rows = improvements.map { it.toRow(names) }
                        _state.update {
                            it.copy(
                                screen = if (rows.isEmpty()) {
                                    WorkoutSummaryRecordsScreen.Empty
                                } else {
                                    WorkoutSummaryRecordsScreen.Ready(rows)
                                }
                            )
                        }
                    }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (currentGeneration == generation) {
                    _state.update { it.copy(screen = WorkoutSummaryRecordsScreen.Failed) }
                }
            }
        }
    }
}

/**
 * Maps one improvement to a display row. The metric key supplies the qualifying reps and load when
 * it carries them; the contributing set fills the gaps (notably the estimated 1RM, whose key omits
 * both). The effective catalog name wins over the set's stored name so a rename is reflected.
 */
private fun OccurrenceRecordImprovement.toRow(names: Map<String, String>): SummaryRecordRow =
    SummaryRecordRow(
        exerciseId = exerciseId,
        exerciseName = names[exerciseId] ?: set?.exerciseName,
        type = event.record.key.type,
        loadKind = event.record.key.loadKind,
        reps = event.record.key.reps ?: set?.reps,
        weightKg = event.record.key.weightKg ?: set?.weightKg,
        value = event.record.value,
        setId = event.setId
    )
