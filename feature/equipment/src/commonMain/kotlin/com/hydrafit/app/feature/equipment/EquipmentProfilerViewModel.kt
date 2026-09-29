package com.hydrafit.app.feature.equipment

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hydrafit.app.core.domain.engine.ExerciseCatalog
import com.hydrafit.app.core.domain.equipment.EquipmentTag
import com.hydrafit.app.core.domain.fatigue.MuscleGroup
import com.hydrafit.app.core.userdata.equipment.EquipmentRepository
import com.hydrafit.app.core.userdata.equipment.EquipmentSelectionRepository
import com.hydrafit.app.core.userdata.equipment.ExerciseEquipmentRepository
import com.hydrafit.app.core.userdata.equipment.ExerciseMuscleRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class EquipmentProfilerViewModel(
    private val equipmentRepository: EquipmentRepository,
    private val selectionRepository: EquipmentSelectionRepository,
    private val exerciseCatalog: ExerciseCatalog,
    private val exerciseEquipmentRepository: ExerciseEquipmentRepository,
    private val exerciseMuscleRepository: ExerciseMuscleRepository
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
        viewModelScope.launch {
            _state.update { it.copy(exercises = exerciseCatalog.all()) }
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

    fun onExerciseTapped(exerciseId: String) {
        val current = _state.value
        if (current.editingExerciseId == exerciseId) {
            _state.update { it.copy(editingExerciseId = null).clearEdit() }
            return
        }
        val exercise = current.exercises.firstOrNull { it.id == exerciseId } ?: return
        _state.update {
            it.copy(
                editingExerciseId = exerciseId,
                editingEquipment = exercise.requiredEquipment,
                editingPrimary = exercise.primaryMuscles,
                editingSecondary = exercise.secondaryMuscles
            )
        }
    }

    fun onEditingEquipmentToggled(tag: EquipmentTag) {
        _state.update { current ->
            val updated = if (tag in current.editingEquipment) {
                current.editingEquipment - tag
            } else {
                current.editingEquipment + tag
            }
            current.copy(editingEquipment = updated)
        }
    }

    /** A muscle is either primary or secondary, never both, to avoid double fatigue weighting. */
    fun onEditingMuscleToggled(muscle: MuscleGroup, primary: Boolean) {
        _state.update { current ->
            if (primary) {
                current.copy(
                    editingPrimary = current.editingPrimary.toggle(muscle),
                    editingSecondary = current.editingSecondary - muscle
                )
            } else {
                current.copy(
                    editingSecondary = current.editingSecondary.toggle(muscle),
                    editingPrimary = current.editingPrimary - muscle
                )
            }
        }
    }

    fun onSaveExerciseEdit() {
        val current = _state.value
        val exerciseId = current.editingExerciseId ?: return
        if (!current.canSaveEdit) return
        viewModelScope.launch {
            exerciseEquipmentRepository.update(exerciseId, current.editingEquipment)
            exerciseMuscleRepository.update(
                exerciseId,
                current.editingPrimary,
                current.editingSecondary
            )
            refreshExercises()
        }
    }

    fun onResetExerciseEdit() {
        val exerciseId = _state.value.editingExerciseId ?: return
        viewModelScope.launch {
            exerciseEquipmentRepository.reset(exerciseId)
            exerciseMuscleRepository.reset(exerciseId)
            refreshExercises()
        }
    }

    private suspend fun refreshExercises() {
        _state.update {
            it.copy(exercises = exerciseCatalog.all()).clearEdit()
        }
    }

    private fun EquipmentProfilerUiState.clearEdit(): EquipmentProfilerUiState = copy(
        editingExerciseId = null,
        editingEquipment = emptySet(),
        editingPrimary = emptySet(),
        editingSecondary = emptySet()
    )

    private fun Set<MuscleGroup>.toggle(muscle: MuscleGroup): Set<MuscleGroup> =
        if (muscle in this) this - muscle else this + muscle
}
