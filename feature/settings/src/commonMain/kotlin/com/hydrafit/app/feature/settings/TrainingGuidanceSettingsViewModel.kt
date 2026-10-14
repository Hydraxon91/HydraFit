package com.hydrafit.app.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hydrafit.app.core.domain.settings.TrainingGuidancePreferenceRepository
import com.hydrafit.app.core.domain.settings.TrainingGuidancePreferences
import com.hydrafit.app.core.domain.workout.RestAlertScheduler
import com.hydrafit.app.core.domain.workout.RestAlertTiming
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TrainingGuidanceSettingsUiState(
    val preferences: TrainingGuidancePreferences = TrainingGuidancePreferences(),
    val permissionDenied: Boolean = false,
    val alertTiming: RestAlertTiming = RestAlertTiming.INEXACT
)

class TrainingGuidanceSettingsViewModel(
    private val repository: TrainingGuidancePreferenceRepository,
    private val alerts: RestAlertScheduler
) : ViewModel() {
    private val _state = MutableStateFlow(TrainingGuidanceSettingsUiState())
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch {
            alerts.timing.collect { timing ->
                _state.update { it.copy(alertTiming = timing) }
            }
        }
        viewModelScope.launch {
            repository.observe().collect { preferences ->
                _state.update { it.copy(preferences = preferences) }
            }
        }
    }

    fun refreshAlertTiming() = alerts.refreshTiming()

    fun setAlerts(enabled: Boolean, permissionGranted: Boolean) {
        viewModelScope.launch {
            val allowed = enabled && permissionGranted
            repository.setRestAlertsEnabled(allowed)
            if (!allowed) alerts.cancel()
            _state.update { it.copy(permissionDenied = enabled && !permissionGranted) }
        }
    }

    fun setCoaching(enabled: Boolean) {
        viewModelScope.launch { repository.setShortRestCoachingEnabled(enabled) }
    }
}
