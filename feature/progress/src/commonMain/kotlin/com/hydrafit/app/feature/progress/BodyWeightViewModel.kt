package com.hydrafit.app.feature.progress

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hydrafit.app.core.domain.time.TimeProvider
import com.hydrafit.app.core.domain.time.localEpochDay
import com.hydrafit.app.core.domain.unit.WeightUnit
import com.hydrafit.app.core.domain.unit.formatWeight
import com.hydrafit.app.core.userdata.measurements.BodyWeightException
import com.hydrafit.app.core.userdata.measurements.BodyWeightFailureReason
import com.hydrafit.app.core.userdata.measurements.BodyWeightMeasurement
import com.hydrafit.app.core.userdata.measurements.BodyWeightRepository
import com.hydrafit.app.core.userdata.settings.WeightUnitRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Presentation state for the pushed Body weight destination. Measurements are canonical kilograms and
 * days are local epoch days; the display unit is applied only at the edges, so switching it never
 * writes to the repository.
 *
 * Submission safety: a single in-flight guard ignores a repeated submit while a write is running, and
 * each write captures a revision so a completion that lands after the draft was replaced or cancelled
 * cannot close or mutate the newer draft.
 */
class BodyWeightViewModel(
    private val bodyWeightRepository: BodyWeightRepository,
    weightUnitRepository: WeightUnitRepository,
    private val timeProvider: TimeProvider
) : ViewModel() {

    private val _state = MutableStateFlow(BodyWeightUiState(todayLocalEpochDay = today()))
    val state: StateFlow<BodyWeightUiState> = _state.asStateFlow()

    private var measurementsLoaded = false
    private var goalLoaded = false
    private var measurementsJob: Job? = null
    private var goalJob: Job? = null

    /** Invalidates in-flight write completions; a newer draft or a cancel bumps it. */
    private var writeRevision = 0L

    init {
        startObserving()
        viewModelScope.launch {
            weightUnitRepository.unitFlow().collect { unit ->
                _state.update { it.copy(weightUnit = unit) }
            }
        }
    }

    /** Re-samples the local civil day, e.g. when the screen returns to the foreground. */
    fun onResume() {
        _state.update { it.copy(todayLocalEpochDay = today()) }
    }

    fun onRangeSelected(range: ProgressRange) {
        _state.update { it.copy(range = range) }
    }

    fun onRetryLoad() {
        startObserving()
    }

    // ── Editor ────────────────────────────────────────────────────────────

    fun onAddMeasurement() {
        val day = today()
        _state.update {
            it.copy(
                todayLocalEpochDay = day,
                editor = BodyWeightEditorState(
                    previousLocalEpochDay = null,
                    initialDateText = progressDayLabel(day),
                    initialWeightText = ""
                ),
                pendingDuplicate = null,
                pendingDeleteDay = null
            )
        }
    }

    fun onEditMeasurement(localEpochDay: Long) {
        val current = _state.value
        val measurement =
            current.measurements.firstOrNull { it.localEpochDay == localEpochDay } ?: return
        val weightText = formatWeight(current.weightUnit.kilogramsToDisplay(measurement.weightKg))
        _state.update {
            it.copy(
                editor = BodyWeightEditorState(
                    previousLocalEpochDay = localEpochDay,
                    initialDateText = progressDayLabel(localEpochDay),
                    initialWeightText = weightText
                ),
                pendingDuplicate = null,
                pendingDeleteDay = null
            )
        }
    }

    fun onEditorDateChanged(value: String) {
        _state.update { current ->
            current.copy(editor = current.editor?.copy(dateText = value, error = null))
        }
    }

    fun onEditorWeightChanged(value: String) {
        _state.update { current ->
            current.copy(editor = current.editor?.copy(weightText = value, error = null))
        }
    }

    /** Discards the open draft without writing; a newer draft invalidates any in-flight completion. */
    fun onEditorCancel() {
        writeRevision++
        _state.update {
            it.copy(
                editor = null,
                pendingDuplicate = null,
                pendingDeleteDay = null,
                goalError = false
            )
        }
    }

    fun onEditorSubmit() {
        val current = _state.value
        val editor = current.editor ?: return
        if (current.submitting) return
        val day = editor.parsedLocalEpochDay
        if (day == null) {
            setEditorError(BodyWeightEditorError.INVALID_DATE)
            return
        }
        val displayWeight = editor.parsedDisplayWeight
        if (displayWeight == null || !displayWeight.isFinite() || displayWeight <= 0.0) {
            setEditorError(BodyWeightEditorError.INVALID_WEIGHT)
            return
        }
        if (day > current.todayLocalEpochDay) {
            setEditorError(BodyWeightEditorError.FUTURE_DATE)
            return
        }
        val weightKg = kilogramsToWrite(editor, current.weightUnit, displayWeight)
        val previous = editor.previousLocalEpochDay
        val revision = beginWrite()
        viewModelScope.launch {
            try {
                if (previous == null) {
                    bodyWeightRepository.add(day, weightKg)
                } else {
                    bodyWeightRepository.update(previous, day, weightKg)
                }
                completeSuccess(revision)
            } catch (failure: BodyWeightException) {
                completeFailure(revision) { state ->
                    if (failure.reason == BodyWeightFailureReason.DUPLICATE_DATE &&
                        previous == null
                    ) {
                        state.copy(pendingDuplicate = PendingBodyWeightAdd(day, weightKg))
                    } else {
                        state.copy(
                            editor = state.editor?.copy(error = failure.reason.toEditorError())
                        )
                    }
                }
            }
        }
    }

    /** Applies the explicit replacement after a duplicate-day confirmation; never a silent overwrite. */
    fun onReplaceConfirmed() {
        val current = _state.value
        val pending = current.pendingDuplicate ?: return
        if (current.submitting) return
        val revision = beginWrite()
        _state.update { it.copy(pendingDuplicate = null) }
        viewModelScope.launch {
            try {
                bodyWeightRepository.add(pending.localEpochDay, pending.weightKg, replace = true)
                completeSuccess(revision)
            } catch (failure: BodyWeightException) {
                completeFailure(revision) { state ->
                    state.copy(editor = state.editor?.copy(error = failure.reason.toEditorError()))
                }
            }
        }
    }

    fun onDuplicateDismissed() {
        _state.update { it.copy(pendingDuplicate = null) }
    }

    fun onDeleteRequested() {
        val previous = _state.value.editor?.previousLocalEpochDay ?: return
        _state.update { it.copy(pendingDeleteDay = previous) }
    }

    fun onDeleteDismissed() {
        _state.update { it.copy(pendingDeleteDay = null) }
    }

    fun onDeleteConfirmed() {
        val current = _state.value
        val day = current.pendingDeleteDay ?: return
        if (current.submitting) return
        val revision = beginWrite()
        _state.update { it.copy(pendingDeleteDay = null) }
        viewModelScope.launch {
            try {
                bodyWeightRepository.delete(day)
                completeSuccess(revision)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                completeFailure(revision) { it }
            }
        }
    }

    // ── Goal ──────────────────────────────────────────────────────────────

    fun onGoalInputChanged(value: String) {
        _state.update { it.copy(goalInput = value, goalError = false) }
    }

    fun onSaveGoal() {
        val current = _state.value
        val display = current.goalInput.trim().toDoubleOrNull()
        if (display == null || !display.isFinite() || display <= 0.0) {
            _state.update { it.copy(goalError = true) }
            return
        }
        if (current.submitting) return
        val kilograms = current.weightUnit.displayToKilograms(display)
        val revision = beginWrite()
        viewModelScope.launch {
            try {
                bodyWeightRepository.setGoal(kilograms)
                completeGoal(revision)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                completeFailure(revision) { it.copy(goalError = true) }
            }
        }
    }

    fun onClearGoal() {
        if (_state.value.submitting) return
        val revision = beginWrite()
        viewModelScope.launch {
            try {
                bodyWeightRepository.setGoal(null)
                completeGoal(revision)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                completeFailure(revision) { it }
            }
        }
    }

    // ── Internals ─────────────────────────────────────────────────────────

    private fun startObserving() {
        measurementsLoaded = false
        goalLoaded = false
        measurementsJob?.cancel()
        goalJob?.cancel()
        _state.update { it.copy(loading = true, loadFailed = false) }
        measurementsJob = viewModelScope.launch {
            try {
                bodyWeightRepository.observeMeasurements().collect { list ->
                    measurementsLoaded = true
                    publishMeasurements(list)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _state.update { it.copy(loading = false, loadFailed = true) }
            }
        }
        goalJob = viewModelScope.launch {
            try {
                bodyWeightRepository.observeGoal().collect { goal ->
                    goalLoaded = true
                    publishGoal(goal)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _state.update { it.copy(loading = false, loadFailed = true) }
            }
        }
    }

    private fun publishMeasurements(list: List<BodyWeightMeasurement>) {
        _state.update { current ->
            current.copy(
                measurements = list.sortedBy { it.localEpochDay },
                loading = !(measurementsLoaded && goalLoaded) && !current.loadFailed
            )
        }
    }

    private fun publishGoal(goal: Double?) {
        _state.update { current ->
            current.copy(
                goalKg = goal,
                loading = !(measurementsLoaded && goalLoaded) && !current.loadFailed
            )
        }
    }

    private fun today(): Long =
        localEpochDay(timeProvider.nowMillis(), timeProvider.utcOffsetMillis())

    private fun setEditorError(error: BodyWeightEditorError) {
        _state.update { current -> current.copy(editor = current.editor?.copy(error = error)) }
    }

    /**
     * The kilograms a submit writes. An untouched weight field on an existing entry reuses the exact
     * stored kilograms, so a rounded pound display is never re-encoded; any other value converts from
     * the display unit at the edge.
     */
    private fun kilogramsToWrite(
        editor: BodyWeightEditorState,
        unit: WeightUnit,
        displayWeight: Double
    ): Double {
        val previous = editor.previousLocalEpochDay
        if (previous != null && editor.weightText == editor.initialWeightText) {
            val stored = _state.value.measurements.firstOrNull { it.localEpochDay == previous }
            if (stored != null) return stored.weightKg
        }
        return unit.displayToKilograms(displayWeight)
    }

    /** Claims the single in-flight guard and bumps the revision that invalidates older completions. */
    private fun beginWrite(): Long {
        val revision = ++writeRevision
        _state.update { current ->
            current.copy(submitting = true, editor = current.editor?.copy(error = null))
        }
        return revision
    }

    private fun completeSuccess(revision: Long) {
        if (revision == writeRevision) {
            _state.update { it.copy(submitting = false, editor = null, pendingDuplicate = null) }
        } else {
            clearSubmitting()
        }
    }

    private fun completeGoal(revision: Long) {
        if (revision == writeRevision) {
            _state.update { it.copy(submitting = false, goalInput = "", goalError = false) }
        } else {
            clearSubmitting()
        }
    }

    private fun completeFailure(
        revision: Long,
        transform: (BodyWeightUiState) -> BodyWeightUiState
    ) {
        if (revision == writeRevision) {
            _state.update { transform(it).copy(submitting = false) }
        } else {
            clearSubmitting()
        }
    }

    private fun clearSubmitting() {
        _state.update { it.copy(submitting = false) }
    }
}

private fun BodyWeightFailureReason.toEditorError(): BodyWeightEditorError = when (this) {
    BodyWeightFailureReason.INVALID_WEIGHT -> BodyWeightEditorError.INVALID_WEIGHT
    BodyWeightFailureReason.FUTURE_DATE -> BodyWeightEditorError.FUTURE_DATE
    BodyWeightFailureReason.DUPLICATE_DATE -> BodyWeightEditorError.INVALID_DATE
    BodyWeightFailureReason.DATE_OCCUPIED -> BodyWeightEditorError.DATE_OCCUPIED
    BodyWeightFailureReason.MISSING_MEASUREMENT -> BodyWeightEditorError.MISSING_MEASUREMENT
}
