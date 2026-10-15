package com.hydrafit.app.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hydrafit.app.core.domain.schedule.WorkoutReminderScheduler
import com.hydrafit.app.core.domain.settings.WorkoutReminderPreferenceRepository
import com.hydrafit.app.core.domain.settings.WorkoutReminderPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class WorkoutReminderSettingsUiState(
    val preferences: WorkoutReminderPreferences = WorkoutReminderPreferences(),
    val permissionDenied: Boolean = false
)

/**
 * The user's reminder choices. Whether a reminder can actually be posted is device state, so a
 * blocked permission is reported here instead of being stored as an enabled reminder; the
 * app-lifetime coordinator still decides delivery from the stored preference.
 */
class WorkoutReminderSettingsViewModel(
    private val repository: WorkoutReminderPreferenceRepository,
    private val reminders: WorkoutReminderScheduler
) : ViewModel() {
    private val _state = MutableStateFlow(WorkoutReminderSettingsUiState())
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch {
            repository.observe().collect { preferences ->
                _state.update { it.copy(preferences = preferences) }
            }
        }
    }

    fun setEnabled(enabled: Boolean, notificationsGranted: Boolean) {
        viewModelScope.launch {
            repository.setEnabled(enabled && notificationsGranted)
            _state.update { it.copy(permissionDenied = enabled && !notificationsGranted) }
        }
    }

    fun setReminderMinuteOfDay(minuteOfDay: Int) {
        viewModelScope.launch { repository.setReminderMinuteOfDay(minuteOfDay) }
    }

    fun setQuietHoursEnabled(enabled: Boolean) {
        viewModelScope.launch { repository.setQuietHoursEnabled(enabled) }
    }

    fun setQuietHours(startMinuteOfDay: Int, endMinuteOfDay: Int) {
        viewModelScope.launch {
            repository.setQuietHours(startMinuteOfDay, endMinuteOfDay)
        }
    }

    /**
     * Reports that platform delivery state may have changed, such as notification access granted in
     * system settings while the app was open, so the coordinator decides delivery again.
     */
    fun refreshDelivery() {
        reminders.reportDeliveryChange()
    }
}
