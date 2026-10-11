package com.hydrafit.app.feature.logger

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hydrafit.app.core.domain.engine.ObserveWorkoutPlanInputsUseCase
import com.hydrafit.app.core.domain.schedule.OccurrenceReplacementChoices
import com.hydrafit.app.core.domain.schedule.OccurrenceReplacementException
import com.hydrafit.app.core.domain.schedule.OccurrenceReplacementFailure
import com.hydrafit.app.core.domain.schedule.ReplaceOccurrenceExerciseUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class OccurrenceReplacementUiState(
    val open: Boolean = false,
    val loading: Boolean = false,
    val saving: Boolean = false,
    val choices: OccurrenceReplacementChoices? = null,
    val failure: OccurrenceReplacementFailure? = null
)

class OccurrenceReplacementViewModel(
    private val replace: ReplaceOccurrenceExerciseUseCase,
    private val inputs: ObserveWorkoutPlanInputsUseCase
) : ViewModel() {
    private val _state = MutableStateFlow(OccurrenceReplacementUiState())
    val state = _state.asStateFlow()
    private var generation = 0L

    fun open(occurrenceId: Long, entryId: Long) {
        if (_state.value.saving) return
        val currentGeneration = ++generation
        _state.value = OccurrenceReplacementUiState(open = true, loading = true)
        viewModelScope.launch {
            try {
                val choices = replace.candidates(occurrenceId, entryId, inputs().first().request)
                if (currentGeneration == generation) {
                    _state.value = OccurrenceReplacementUiState(open = true, choices = choices)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (currentGeneration == generation) {
                    _state.value = OccurrenceReplacementUiState(
                        open = true,
                        failure = (error as? OccurrenceReplacementException)?.reason
                            ?: OccurrenceReplacementFailure.STALE_CONTEXT
                    )
                }
            }
        }
    }

    fun confirm(exerciseId: String) {
        val current = _state.value
        val choices = current.choices ?: return
        if (current.saving || current.loading || current.failure != null) return
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            try {
                replace(choices, exerciseId, inputs().first().request)
                _state.value = OccurrenceReplacementUiState()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _state.update {
                    it.copy(
                        saving = false,
                        failure = (error as? OccurrenceReplacementException)?.reason
                            ?: OccurrenceReplacementFailure.STALE_CONTEXT
                    )
                }
            }
        }
    }

    fun dismiss() {
        if (_state.value.saving) return
        generation++
        _state.value = OccurrenceReplacementUiState()
    }
}
