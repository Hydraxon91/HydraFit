package com.hydrafit.app.feature.equipment

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hydrafit.app.core.domain.equipment.ExerciseExclusion
import com.hydrafit.app.core.domain.equipment.ExercisePreference
import com.hydrafit.app.core.domain.time.TimeProvider
import com.hydrafit.app.core.userdata.equipment.ExerciseExclusionRepository
import com.hydrafit.app.core.userdata.equipment.ExercisePreferenceRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Equipment-owned planning settings the planner consumes: the explicit per-exercise preference and
 * the EX-01 exclusions. Kept separate from [EquipmentProfilerViewModel] so neither ViewModel
 * exceeds its dependency budget, and each setting is written on its own, never as part of saving a
 * catalog edit.
 */
class ExercisePlanningSettingsViewModel(
    private val preferenceRepository: ExercisePreferenceRepository,
    private val exclusionRepository: ExerciseExclusionRepository,
    private val timeProvider: TimeProvider
) : ViewModel() {

    private val _preferences = MutableStateFlow<Map<String, ExercisePreference>>(emptyMap())
    val preferences: StateFlow<Map<String, ExercisePreference>> = _preferences.asStateFlow()

    private val _exclusions = MutableStateFlow<List<ExerciseExclusion>>(emptyList())
    val exclusions: StateFlow<List<ExerciseExclusion>> = _exclusions.asStateFlow()

    init {
        viewModelScope.launch {
            preferenceRepository.observe().collectLatest { stored ->
                _preferences.value = stored
            }
        }
        viewModelScope.launch {
            exclusionRepository.observe().collectLatest { stored ->
                _exclusions.value = stored
            }
        }
    }

    fun onPreferenceChanged(exerciseId: String, preference: ExercisePreference) {
        viewModelScope.launch { preferenceRepository.set(exerciseId, preference) }
    }

    /** Excludes an exercise for the default window, or indefinitely when [indefinite] is true. */
    fun onExclude(exerciseId: String, indefinite: Boolean) {
        val expiresAt = if (indefinite) {
            null
        } else {
            ExerciseExclusion.defaultExpiryFrom(timeProvider.nowMillis())
        }
        viewModelScope.launch { exclusionRepository.set(ExerciseExclusion(exerciseId, expiresAt)) }
    }

    /** Re-enables an exercise by removing its exclusion entirely. */
    fun onInclude(exerciseId: String) {
        viewModelScope.launch { exclusionRepository.clear(exerciseId) }
    }

    /** The current UTC offset, so the UI can show a local expiry date without its own clock. */
    fun utcOffsetMillis(): Long = timeProvider.utcOffsetMillis()

    /** The current instant, so the UI can distinguish an active exclusion from an expired one. */
    fun nowMillis(): Long = timeProvider.nowMillis()
}
