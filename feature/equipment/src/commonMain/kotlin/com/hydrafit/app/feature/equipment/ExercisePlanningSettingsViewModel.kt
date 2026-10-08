package com.hydrafit.app.feature.equipment

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hydrafit.app.core.domain.equipment.ExercisePreference
import com.hydrafit.app.core.userdata.equipment.ExercisePreferenceRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Equipment-owned planning settings the planner consumes: currently the explicit per-exercise
 * preference (future EX-01 exclusions belong here too). It is kept separate from
 * [EquipmentProfilerViewModel] so neither ViewModel exceeds its dependency budget, and a preference
 * change is written on its own, never as part of saving a catalog edit.
 */
class ExercisePlanningSettingsViewModel(
    private val preferenceRepository: ExercisePreferenceRepository
) : ViewModel() {

    private val _preferences = MutableStateFlow<Map<String, ExercisePreference>>(emptyMap())
    val preferences: StateFlow<Map<String, ExercisePreference>> = _preferences.asStateFlow()

    init {
        viewModelScope.launch {
            preferenceRepository.observe().collectLatest { stored ->
                _preferences.value = stored
            }
        }
    }

    fun onPreferenceChanged(exerciseId: String, preference: ExercisePreference) {
        viewModelScope.launch { preferenceRepository.set(exerciseId, preference) }
    }
}
