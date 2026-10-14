package com.hydrafit.app.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hydrafit.app.core.domain.setup.SetupDisposition
import com.hydrafit.app.core.domain.startup.StartupReadiness
import com.hydrafit.app.core.userdata.settings.SetupRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface OnboardingEntryState {
    data object Loading : OnboardingEntryState
    data class Ready(val showSetup: Boolean) : OnboardingEntryState
    data object Failed : OnboardingEntryState
}

/** Reads startup disposition once, after staged restore/maintenance, without changing it. */
class OnboardingEntryViewModel(
    private val setup: SetupRepository,
    private val readiness: StartupReadiness,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default
) : ViewModel() {
    private val _state = MutableStateFlow<OnboardingEntryState>(OnboardingEntryState.Loading)
    val state: StateFlow<OnboardingEntryState> = _state.asStateFlow()
    private var reading = false
    private var handled = false

    init {
        readEntry()
    }

    fun retry() {
        if (_state.value == OnboardingEntryState.Failed) readEntry()
    }

    /** Activity recreation retains this guard; a new process re-evaluates the saved boundary. */
    fun claimAutomaticEntry(): Boolean {
        val ready = _state.value as? OnboardingEntryState.Ready ?: return false
        if (!ready.showSetup || handled) return false
        handled = true
        return true
    }

    private fun readEntry() {
        if (reading) return
        reading = true
        _state.value = OnboardingEntryState.Loading
        viewModelScope.launch {
            try {
                readiness.isReady.first { it }
                val disposition = withContext(dispatcher) { setup.current().disposition }
                _state.value = OnboardingEntryState.Ready(disposition == SetupDisposition.PENDING)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (failure: Exception) {
                _state.value = OnboardingEntryState.Failed
            } finally {
                reading = false
            }
        }
    }
}
