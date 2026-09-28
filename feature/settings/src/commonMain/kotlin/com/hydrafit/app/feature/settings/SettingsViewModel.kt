package com.hydrafit.app.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hydrafit.app.core.domain.engine.EngineAvailability
import com.hydrafit.app.core.domain.engine.PlannerEngineId
import com.hydrafit.app.core.userdata.settings.ApiKeyStore
import com.hydrafit.app.core.userdata.settings.EnginePreferenceRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val preference: EnginePreferenceRepository,
    private val availability: EngineAvailability,
    private val apiKeyStore: ApiKeyStore
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

    fun onApiKeyChanged(value: String) {
        _state.update { it.copy(apiKeyInput = value.trim()) }
    }

    fun saveApiKey() {
        val key = _state.value.apiKeyInput
        if (key.isBlank()) return
        apiKeyStore.save(key)
        _state.update { it.copy(apiKeyInput = "") }
        refresh()
    }

    fun clearApiKey() {
        apiKeyStore.clear()
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val available = availability.availableEngines()
            val stored = preference.selectedEngine()
            _state.update {
                it.copy(
                    availableEngines = available,
                    selectedEngine = if (stored in available) stored else available.firstOrNull(),
                    apiKeyConfigured = !apiKeyStore.load().isNullOrBlank(),
                    isLocalLlmInstalled = PlannerEngineId.LOCAL_LLM in available
                )
            }
        }
    }
}
