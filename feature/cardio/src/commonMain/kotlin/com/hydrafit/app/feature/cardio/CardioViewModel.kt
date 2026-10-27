package com.hydrafit.app.feature.cardio

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hydrafit.app.core.domain.cardio.AddCardioActivityUseCase
import com.hydrafit.app.core.domain.cardio.CardioActivity
import com.hydrafit.app.core.domain.cardio.CardioActivityType
import com.hydrafit.app.core.domain.cardio.CardioIdentity
import com.hydrafit.app.core.domain.cardio.CardioRepository
import com.hydrafit.app.core.domain.cardio.DeleteCardioActivityUseCase
import com.hydrafit.app.core.domain.cardio.EditCardioActivityUseCase
import com.hydrafit.app.core.domain.time.TimeProvider
import com.hydrafit.app.core.domain.time.isoLocalDateTime
import com.hydrafit.app.core.domain.time.localEpochDay
import com.hydrafit.app.core.userdata.settings.DistanceUnitRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Presentation state for the standalone Cardio destination.
 *
 * Writes go through the shared cardio use cases, which re-validate every activity with
 * [CardioActivity.requireValid]; the ViewModel only translates the entered text into canonical
 * values and owns the ephemeral editor. A single in-flight guard ignores a repeated submit while a
 * write is running, and each write captures a revision so a completion that lands after the draft
 * was replaced or cancelled cannot close or mutate the newer draft.
 */
class CardioViewModel(
    private val repository: CardioRepository,
    distanceUnitRepository: DistanceUnitRepository,
    private val timeProvider: TimeProvider
) : ViewModel() {

    private val addActivity = AddCardioActivityUseCase(repository)
    private val editActivity = EditCardioActivityUseCase(repository)
    private val deleteActivity = DeleteCardioActivityUseCase(repository)

    private val utcOffsetMillis: Long = timeProvider.utcOffsetMillis()

    private val _state = MutableStateFlow(
        CardioUiState(
            utcOffsetMillis = utcOffsetMillis,
            todayLocalEpochDay = today()
        )
    )
    val state: StateFlow<CardioUiState> = _state.asStateFlow()

    private var activitiesJob: Job? = null

    /** Invalidates in-flight write completions; a newer draft or a cancel bumps it. */
    private var writeRevision = 0L

    init {
        startObserving()
        viewModelScope.launch {
            distanceUnitRepository.unitFlow().collect { unit ->
                _state.update { it.copy(distanceUnit = unit) }
            }
        }
    }

    /** Re-samples the local civil day, e.g. when the screen returns to the foreground. */
    fun onResume() {
        _state.update { it.copy(todayLocalEpochDay = today()) }
    }

    fun onRangeSelected(range: CardioRange) {
        _state.update { it.copy(range = range) }
    }

    fun onRetryLoad() {
        startObserving()
    }

    // ── Editor ────────────────────────────────────────────────────────────

    fun onAddActivity() {
        _state.update {
            it.copy(
                editor = CardioEditorState(
                    initialStartText = startTextAt(timeProvider.nowMillis()),
                    type = CardioActivityType.WALKING
                )
            )
        }
    }

    fun onEditActivity(id: Long) {
        val current = _state.value
        val activity = current.activities.firstOrNull { it.id == id } ?: return
        val distanceText = activity.distanceMeters
            ?.let { current.distanceUnit.displayWhole(it).toString() }
            ?: ""
        val minutes = activity.durationSeconds / 60
        val seconds = activity.durationSeconds % 60
        _state.update {
            it.copy(
                editor = CardioEditorState(
                    previousId = activity.id,
                    identity = activity.identity,
                    initialStartText = startTextAt(activity.startedAtMillis),
                    type = activity.type,
                    minutesText = minutes.toString(),
                    secondsText = seconds.toString(),
                    initialDistanceText = distanceText,
                    noteText = activity.note.orEmpty()
                )
            )
        }
    }

    fun onEditorTypeChanged(type: CardioActivityType) {
        updateEditor { it.copy(type = type, error = null) }
    }

    fun onEditorStartChanged(value: String) {
        updateEditor { it.copy(startText = value, error = null) }
    }

    fun onEditorMinutesChanged(value: String) {
        updateEditor { it.copy(minutesText = value, error = null) }
    }

    fun onEditorSecondsChanged(value: String) {
        updateEditor { it.copy(secondsText = value, error = null) }
    }

    fun onEditorDistanceChanged(value: String) {
        updateEditor { it.copy(distanceText = value, error = null) }
    }

    fun onEditorNoteChanged(value: String) {
        updateEditor { it.copy(noteText = value, error = null) }
    }

    /** Discards the open draft without writing; a newer draft invalidates any in-flight completion. */
    fun onEditorCancel() {
        if (_state.value.submitting) return
        writeRevision++
        _state.update { it.copy(editor = null) }
    }

    fun onEditorSubmit() {
        val current = _state.value
        val editor = current.editor ?: return
        if (current.submitting) return

        val startedAtMillis = parseCardioStartTime(editor.startText, current.utcOffsetMillis)
            ?: return setEditorError(CardioEditorError.INVALID_START_TIME)
        val durationSeconds = parsedDurationSeconds(editor)
            ?: return setEditorError(CardioEditorError.INVALID_DURATION)
        val distanceMeters = parsedDistanceMeters(editor, current)
        if (distanceMeters == null && editor.distanceText.isNotBlank()) {
            return setEditorError(CardioEditorError.INVALID_DISTANCE)
        }
        val note = editor.noteText.trim().ifEmpty { null }
        if (note != null && note.length > CardioActivity.MAX_NOTE_LENGTH) {
            return setEditorError(CardioEditorError.INVALID_NOTE)
        }
        val activity = CardioActivity(
            id = editor.previousId ?: 0L,
            identity = editor.identity.ifBlank { CardioIdentity.new() },
            type = editor.type,
            startedAtMillis = startedAtMillis,
            durationSeconds = durationSeconds,
            distanceMeters = distanceMeters,
            note = note
        )
        try {
            activity.requireValid()
        } catch (_: IllegalArgumentException) {
            return setEditorError(CardioEditorError.INVALID_ACTIVITY)
        }

        val revision = beginWrite()
        viewModelScope.launch {
            try {
                if (activity.id == 0L) {
                    addActivity(activity)
                } else {
                    editActivity(activity)
                }
                completeSuccess(revision)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: IllegalArgumentException) {
                completeFailure(revision) {
                    it.copy(editor = it.editor?.copy(error = CardioEditorError.INVALID_ACTIVITY))
                }
            } catch (_: Exception) {
                completeFailure(revision) {
                    it.copy(editor = it.editor?.copy(error = CardioEditorError.SAVE_FAILED))
                }
            }
        }
    }

    fun onEditorDeleteRequested() {
        updateEditor { it.copy(pendingDelete = true) }
    }

    fun onEditorDeleteDismissed() {
        updateEditor { it.copy(pendingDelete = false) }
    }

    fun onEditorDeleteConfirmed() {
        val current = _state.value
        val id = current.editor?.previousId ?: return
        if (current.submitting) return
        val revision = beginWrite()
        updateEditor { it.copy(pendingDelete = false) }
        viewModelScope.launch {
            try {
                deleteActivity(id)
                completeSuccess(revision)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                completeFailure(revision) {
                    it.copy(editor = it.editor?.copy(error = CardioEditorError.SAVE_FAILED))
                }
            }
        }
    }

    // ── Internals ─────────────────────────────────────────────────────────

    private fun startObserving() {
        activitiesJob?.cancel()
        _state.update { it.copy(loading = true, loadFailed = false) }
        activitiesJob = viewModelScope.launch {
            try {
                repository.activitiesFlow().collect { activities ->
                    _state.update { it.copy(activities = activities, loading = false) }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _state.update { it.copy(loading = false, loadFailed = true) }
            }
        }
    }

    private fun today(): Long = localEpochDay(timeProvider.nowMillis(), utcOffsetMillis)

    private fun startTextAt(epochMillis: Long): String =
        isoLocalDateTime(epochMillis, utcOffsetMillis)

    private fun updateEditor(transform: (CardioEditorState) -> CardioEditorState) {
        _state.update { current ->
            current.copy(editor = current.editor?.let(transform))
        }
    }

    private fun setEditorError(error: CardioEditorError) {
        updateEditor { it.copy(error = error) }
    }

    /**
     * Total seconds from the minutes and seconds fields, or null when either field is not a whole
     * number or the total falls outside the activity bounds. A blank field counts as zero, so a
     * duration of exactly one minute can be entered as `1` and `0`.
     */
    private fun parsedDurationSeconds(editor: CardioEditorState): Int? {
        val minutes = editor.minutesText.trim().ifEmpty { "0" }.toIntOrNull() ?: return null
        val seconds = editor.secondsText.trim().ifEmpty { "0" }.toIntOrNull() ?: return null
        if (minutes < 0 || seconds !in 0..59) return null
        val total = minutes.toLong() * 60L + seconds.toLong()
        val minSeconds = CardioActivity.MIN_DURATION_SECONDS.toLong()
        val maxSeconds = CardioActivity.MAX_DURATION_SECONDS.toLong()
        if (total !in minSeconds..maxSeconds) return null
        return total.toInt()
    }

    /**
     * Distance in whole metres, or null when the field is blank or could not be interpreted. An
     * untouched distance field on an existing activity reuses the exact stored metres, so a rounded
     * feet display is never re-encoded.
     */
    private fun parsedDistanceMeters(editor: CardioEditorState, state: CardioUiState): Int? {
        val text = editor.distanceText.trim()
        if (text.isEmpty()) return null
        val display = text.toDoubleOrNull()
        if (display == null || !display.isFinite() || display <= 0.0) return null
        val previousId = editor.previousId
        val stored = if (previousId != null && editor.distanceText == editor.initialDistanceText) {
            state.activities.firstOrNull { it.id == previousId }?.distanceMeters
        } else {
            null
        }
        val metres = stored ?: state.distanceUnit.displayToMetres(display)
        return metres.takeIf { it in 1..CardioActivity.MAX_DISTANCE_METERS }
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
            _state.update { it.copy(submitting = false, editor = null) }
        } else {
            clearSubmitting()
        }
    }

    private fun completeFailure(revision: Long, transform: (CardioUiState) -> CardioUiState) {
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
