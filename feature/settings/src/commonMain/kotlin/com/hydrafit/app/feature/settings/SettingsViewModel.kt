package com.hydrafit.app.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hydrafit.app.core.domain.engine.EngineAvailability
import com.hydrafit.app.core.domain.engine.PlannerEngineId
import com.hydrafit.app.core.userdata.settings.EnginePreferenceRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val preference: EnginePreferenceRepository,
    private val availability: EngineAvailability
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun onEngineSelected(engine: PlannerEngineId) {
        viewModelScope.launch {
            preference.setEngine(engine)
            refresh()
        }
    }

    private fun refresh() {
        viewModelScope.launch {
            val available = availability.availableEngines()
            val stored = preference.selectedEngine()
            _state.value = SettingsUiState(
                availableEngines = available,
                selectedEngine = if (stored in available) stored else available.firstOrNull()
            )
        }
    }
}
