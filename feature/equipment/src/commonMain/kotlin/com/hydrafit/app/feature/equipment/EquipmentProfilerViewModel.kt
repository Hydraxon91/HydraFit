package com.hydrafit.app.feature.equipment

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hydrafit.app.core.domain.equipment.EquipmentTag
import com.hydrafit.app.core.userdata.equipment.EquipmentSelectionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class EquipmentProfilerViewModel(private val repository: EquipmentSelectionRepository) :
    ViewModel() {

    private val _state = MutableStateFlow(EquipmentProfilerUiState())
    val state: StateFlow<EquipmentProfilerUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val selected = repository.selected()
            _state.update { it.copy(selectedTags = selected, isLoading = false) }
        }
    }

    fun onTagToggled(tag: EquipmentTag) {
        val updated = _state.value.selectedTags.let { current ->
            if (tag in current) current - tag else current + tag
        }
        _state.update { it.copy(selectedTags = updated) }
        viewModelScope.launch { repository.setSelected(updated) }
    }
}
