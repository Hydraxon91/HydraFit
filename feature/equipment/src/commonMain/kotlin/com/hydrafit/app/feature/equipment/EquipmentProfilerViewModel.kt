package com.hydrafit.app.feature.equipment

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hydrafit.app.core.domain.equipment.EquipmentTag
import com.hydrafit.app.core.userdata.equipment.EquipmentRepository
import com.hydrafit.app.core.userdata.equipment.EquipmentSelectionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class EquipmentProfilerViewModel(
    private val equipmentRepository: EquipmentRepository,
    private val selectionRepository: EquipmentSelectionRepository
) : ViewModel() {

    private val _state = MutableStateFlow(EquipmentProfilerUiState())
    val state: StateFlow<EquipmentProfilerUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val selected = selectionRepository.selected()
            _state.update { it.copy(selectedTags = selected, isLoading = false) }
        }
        viewModelScope.launch {
            equipmentRepository.observeAll().collectLatest { equipment ->
                _state.update { it.copy(equipment = equipment) }
            }
        }
    }

    fun onTagToggled(tag: EquipmentTag) {
        val updated = _state.value.selectedTags.let { current ->
            if (tag in current) current - tag else current + tag
        }
        _state.update { it.copy(selectedTags = updated) }
        viewModelScope.launch { selectionRepository.setSelected(updated) }
    }

    fun onNewEquipmentNameChanged(value: String) {
        _state.update { it.copy(newEquipmentName = value) }
    }

    fun onAddEquipment() {
        val name = _state.value.newEquipmentName.trim()
        if (name.isEmpty()) return
        viewModelScope.launch {
            equipmentRepository.add(name)
            _state.update { it.copy(newEquipmentName = "") }
        }
    }

    fun onRemoveEquipment(tag: EquipmentTag) {
        val updated = _state.value.selectedTags - tag
        viewModelScope.launch {
            equipmentRepository.remove(tag)
            selectionRepository.setSelected(updated)
            _state.update { it.copy(selectedTags = updated) }
        }
    }
}
