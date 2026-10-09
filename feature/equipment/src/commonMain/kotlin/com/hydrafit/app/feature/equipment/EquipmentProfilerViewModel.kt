package com.hydrafit.app.feature.equipment

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hydrafit.app.core.domain.engine.ExerciseCatalog
import com.hydrafit.app.core.domain.engine.PersonalRecord
import com.hydrafit.app.core.domain.equipment.CatalogProfileMatch
import com.hydrafit.app.core.domain.equipment.CatalogProfileMatcher
import com.hydrafit.app.core.domain.equipment.EquipmentTag
import com.hydrafit.app.core.domain.equipment.ExerciseLoadCapability
import com.hydrafit.app.core.domain.equipment.MovementPattern
import com.hydrafit.app.core.domain.fatigue.MuscleGroup
import com.hydrafit.app.core.domain.unit.formatWeight
import com.hydrafit.app.core.userdata.equipment.CustomExerciseException
import com.hydrafit.app.core.userdata.equipment.CustomExerciseFailureReason
import com.hydrafit.app.core.userdata.equipment.CustomExerciseRepository
import com.hydrafit.app.core.userdata.equipment.EquipmentRepository
import com.hydrafit.app.core.userdata.equipment.EquipmentSelectionRepository
import com.hydrafit.app.core.userdata.equipment.ExerciseOverrideRepository
import com.hydrafit.app.core.userdata.equipment.PersonalRecordRepository
import kotlinx.coroutines.CancellationException
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
    private val exerciseOverrideRepository: ExerciseOverrideRepository,
    private val customExerciseRepository: CustomExerciseRepository,
    private val personalRecordRepository: PersonalRecordRepository
) : ViewModel() {

    private val _state = MutableStateFlow(EquipmentProfilerUiState())
    val state: StateFlow<EquipmentProfilerUiState> = _state.asStateFlow()

    private var personalRecords: List<PersonalRecord> = emptyList()
    private var profileRequestRevision = 0L

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
        viewModelScope.launch { refreshExercises() }
        viewModelScope.launch {
            personalRecordRepository.observe().collectLatest { records ->
                personalRecords = records
                updatePersonalRecordRows()
            }
        }
    }

    fun onNewPersonalRecord() {
        _state.update { it.copy(personalRecordEditor = PersonalRecordEditorState(open = true)) }
    }

    fun onPersonalRecordExerciseSelected(exerciseId: String) {
        val existing = personalRecords.firstOrNull { it.exerciseId == exerciseId }
        _state.update {
            it.copy(
                personalRecordEditor = PersonalRecordEditorState(
                    open = true,
                    exerciseId = exerciseId,
                    weightInput = existing?.weightKg?.let { kg -> formatWeight(kg) }.orEmpty(),
                    repsInput = existing?.reps?.toString().orEmpty()
                )
            )
        }
    }

    fun onPersonalRecordWeightChanged(value: String) {
        _state.update {
            it.copy(
                personalRecordEditor = it.personalRecordEditor.copy(
                    weightInput = value.filter { char -> char.isDigit() || char == '.' },
                    error = null
                )
            )
        }
    }

    fun onPersonalRecordRepsChanged(value: String) {
        _state.update {
            it.copy(
                personalRecordEditor = it.personalRecordEditor.copy(
                    repsInput = value.filter(Char::isDigit),
                    error = null
                )
            )
        }
    }

    fun onSavePersonalRecord() {
        val editor = _state.value.personalRecordEditor
        val exerciseId = editor.exerciseId ?: return
        val weightKg = editor.weightInput.toDoubleOrNull() ?: return
        val reps = editor.repsInput.toIntOrNull() ?: return
        if (weightKg <= 0.0 || reps <= 0) return
        // Personal records feed the external-load baseline only; a bodyweight exercise cannot hold one.
        val capability = _state.value.exercises.firstOrNull { it.id == exerciseId }?.loadCapability
        if (capability != ExerciseLoadCapability.EXTERNAL) {
            _state.update {
                it.copy(
                    personalRecordEditor = it.personalRecordEditor
                        .copy(error = "This exercise does not record external load")
                )
            }
            return
        }
        viewModelScope.launch {
            try {
                personalRecordRepository.set(PersonalRecord(exerciseId, weightKg, reps))
                _state.update { it.copy(personalRecordEditor = PersonalRecordEditorState()) }
            } catch (failure: Exception) {
                _state.update {
                    it.copy(
                        personalRecordEditor = it.personalRecordEditor
                            .copy(error = failure.message ?: "")
                    )
                }
            }
        }
    }

    fun onClearPersonalRecord(exerciseId: String) {
        viewModelScope.launch {
            personalRecordRepository.clear(exerciseId)
            _state.update { it.copy(personalRecordEditor = PersonalRecordEditorState()) }
        }
    }

    fun onDismissPersonalRecordEditor() {
        _state.update { it.copy(personalRecordEditor = PersonalRecordEditorState()) }
    }

    private fun updatePersonalRecordRows() {
        val names = _state.value.exercises.associate { it.id to it.name }
        val rows = personalRecords
            .map { record ->
                PersonalRecordRow(
                    exerciseId = record.exerciseId,
                    exerciseName = names[record.exerciseId] ?: record.exerciseId,
                    weightKg = record.weightKg,
                    reps = record.reps
                )
            }
            .sortedBy { it.exerciseName }
        _state.update { it.copy(personalRecords = rows) }
    }

    fun onTagToggled(tag: EquipmentTag) {
        val updated = _state.value.selectedTags.let { current ->
            if (tag in current) current - tag else current + tag
        }
        _state.update { it.copy(selectedTags = updated) }
        viewModelScope.launch { selectionRepository.setSelected(updated) }
    }

    fun onNewEquipmentNameChanged(value: String) {
        _state.update { it.copy(newEquipmentName = value, newEquipmentError = null) }
    }

    fun onAddEquipment() {
        val name = _state.value.newEquipmentName.trim()
        if (name.isEmpty()) return
        viewModelScope.launch {
            try {
                equipmentRepository.add(name)
                _state.update { it.copy(newEquipmentName = "", newEquipmentError = null) }
            } catch (failure: Exception) {
                _state.update { it.copy(newEquipmentError = failure.message ?: "") }
            }
        }
    }

    fun onManageEquipment(tag: EquipmentTag) {
        val equipment = _state.value.equipment.firstOrNull { it.id == tag } ?: return
        _state.update {
            it.copy(
                equipmentEditor = EquipmentEditorState(
                    tag = tag,
                    name = equipment.name,
                    isBuiltIn = equipment.isBuiltIn,
                    maxWeightInput = equipment.maxWeightKg?.let { kg -> formatWeight(kg) }.orEmpty()
                )
            )
        }
    }

    fun onRenameEquipmentNameChanged(value: String) {
        _state.update {
            it.copy(equipmentEditor = it.equipmentEditor.copy(name = value, error = null))
        }
    }

    fun onMaxWeightChanged(value: String) {
        _state.update {
            it.copy(
                equipmentEditor = it.equipmentEditor.copy(
                    maxWeightInput = value.filter { char -> char.isDigit() || char == '.' },
                    error = null
                )
            )
        }
    }

    fun onSaveEquipmentRenamed() {
        val editor = _state.value.equipmentEditor
        val tag = editor.tag ?: return
        val rawMax = editor.maxWeightInput.trim()
        if (rawMax.isNotEmpty() && rawMax.toDoubleOrNull() == null) return
        val maxWeight = rawMax.takeIf { it.isNotEmpty() }?.toDouble()
        val name = editor.name.trim()
        if (!editor.isBuiltIn && name.isEmpty()) return
        viewModelScope.launch {
            try {
                // The custom tag id is stable, so a rename keeps the equipment selected.
                if (editor.isBuiltIn) {
                    equipmentRepository.setMaxWeight(tag, maxWeight)
                } else {
                    equipmentRepository.rename(tag, name, maxWeight)
                }
                _state.update { it.copy(equipmentEditor = EquipmentEditorState()) }
            } catch (failure: Exception) {
                _state.update {
                    it.copy(
                        equipmentEditor = it.equipmentEditor.copy(error = failure.message ?: "")
                    )
                }
            }
        }
    }

    fun onDeleteEquipment() {
        val tag = _state.value.equipmentEditor.tag ?: return
        viewModelScope.launch {
            equipmentRepository.remove(tag)
            val updatedSelection = _state.value.selectedTags - tag
            selectionRepository.setSelected(updatedSelection)
            _state.update {
                it.copy(
                    selectedTags = updatedSelection,
                    equipmentEditor = EquipmentEditorState()
                )
            }
        }
    }

    fun onDismissEquipmentEditor() {
        _state.update { it.copy(equipmentEditor = EquipmentEditorState()) }
    }

    fun onSearchChanged(value: String) {
        _state.update { it.copy(search = value) }
    }

    fun onEditExercise(exerciseId: String) {
        val exercise = _state.value.exercises.firstOrNull { it.id == exerciseId } ?: return
        profileRequestRevision++
        _state.update {
            it.copy(
                exerciseEditor = ExerciseEditorState(
                    exerciseId = exercise.id,
                    isCustom = exercise.isCustom,
                    name = exercise.name,
                    movementPattern = exercise.movementPattern,
                    equipment = exercise.requiredEquipment,
                    involvements = exercise.effectiveInvolvements,
                    isUnilateral = exercise.isUnilateral,
                    loadCapability = exercise.loadCapability
                )
            )
        }
    }

    fun onNewCustomExercise() {
        profileRequestRevision++
        _state.update {
            it.copy(exerciseEditor = ExerciseEditorState(isCustom = true, isNew = true))
        }
    }

    fun onEditorNameChanged(value: String) {
        profileRequestRevision++
        _state.update {
            it.copy(
                exerciseEditor = it.exerciseEditor.copy(
                    name = value,
                    error = null,
                    nameConflict = false,
                    suggestion = ExerciseProfileSuggestionState()
                )
            )
        }
    }

    fun onFindProfile() {
        val editor = _state.value.exerciseEditor
        if (!editor.isNew ||
            !editor.isCustom ||
            editor.name.isBlank() ||
            editor.suggestion.isFinding
        ) {
            return
        }
        val revision = ++profileRequestRevision
        _state.update {
            it.copy(
                exerciseEditor = it.exerciseEditor.copy(
                    suggestion = ExerciseProfileSuggestionState(isFinding = true)
                )
            )
        }
        viewModelScope.launch {
            try {
                val match = CatalogProfileMatcher.match(
                    editor.name,
                    exerciseCatalog.profileCandidates()
                )
                if (revision != profileRequestRevision) return@launch
                _state.update { current ->
                    val updated = when (match) {
                        CatalogProfileMatch.Unknown -> current.exerciseEditor.copy(
                            suggestion = ExerciseProfileSuggestionState(noMatch = true)
                        )
                        is CatalogProfileMatch.Unique ->
                            current.exerciseEditor.previewProfile(match.candidate)
                        is CatalogProfileMatch.Ambiguous -> current.exerciseEditor.copy(
                            suggestion = ExerciseProfileSuggestionState(
                                candidates = match.candidates
                            )
                        )
                    }
                    current.copy(exerciseEditor = updated)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                if (revision != profileRequestRevision) return@launch
                _state.update {
                    it.copy(
                        exerciseEditor = it.exerciseEditor.copy(
                            suggestion = ExerciseProfileSuggestionState(failed = true)
                        )
                    )
                }
            }
        }
    }

    fun onProfileCandidateSelected(catalogId: String) {
        _state.update { current ->
            val editor = current.exerciseEditor
            val candidate = editor.suggestion.candidates.singleOrNull { it.catalogId == catalogId }
            if (!editor.isNew || !editor.isCustom || candidate == null) return@update current
            current.copy(exerciseEditor = editor.previewProfile(candidate))
        }
    }

    fun onProfileGroupToggled(group: ExerciseProfileGroup) {
        _state.update { current ->
            val editor = current.exerciseEditor
            if (editor.suggestion.preview == null) return@update current
            val selected = editor.suggestion.selectedGroups
            current.copy(
                exerciseEditor = editor.copy(
                    suggestion = editor.suggestion.copy(
                        selectedGroups =
                        if (group in selected) selected - group else selected + group
                    )
                )
            )
        }
    }

    fun onApplyProfile() {
        _state.update { it.copy(exerciseEditor = it.exerciseEditor.applySelectedProfile()) }
    }

    fun onDismissProfileSuggestion() {
        profileRequestRevision++
        _state.update {
            it.copy(
                exerciseEditor = it.exerciseEditor.copy(
                    suggestion = ExerciseProfileSuggestionState()
                )
            )
        }
    }

    fun onEditorPatternChanged(pattern: MovementPattern) {
        _state.update {
            it.copy(
                exerciseEditor = it.exerciseEditor.copy(
                    movementPattern = pattern,
                    touchedGroups = it.exerciseEditor.touchedGroups + ExerciseProfileGroup.PATTERN
                )
            )
        }
    }

    fun onEditorUnilateralToggled(isUnilateral: Boolean) {
        _state.update {
            it.copy(
                exerciseEditor = it.exerciseEditor.copy(
                    isUnilateral = isUnilateral,
                    touchedGroups = it.exerciseEditor.touchedGroups +
                        ExerciseProfileGroup.UNILATERAL
                )
            )
        }
    }

    fun onEditorLoadCapabilityChanged(capability: ExerciseLoadCapability) {
        _state.update {
            it.copy(
                exerciseEditor = it.exerciseEditor.copy(
                    loadCapability = capability,
                    touchedGroups = it.exerciseEditor.touchedGroups + ExerciseProfileGroup.LOAD
                )
            )
        }
    }

    fun onEditorEquipmentToggled(tag: EquipmentTag) {
        _state.update { current ->
            val editor = current.exerciseEditor
            val updated = if (tag in editor.equipment) {
                editor.equipment - tag
            } else {
                editor.equipment + tag
            }
            current.copy(
                exerciseEditor = editor.copy(
                    equipment = updated,
                    touchedGroups = editor.touchedGroups + ExerciseProfileGroup.EQUIPMENT
                )
            )
        }
    }

    /** Sets one muscle's involvement weight, or removes it when [weight] is null. */
    fun onEditorMuscleInvolvementChanged(muscle: MuscleGroup, weight: Double?) {
        _state.update { current ->
            val editor = current.exerciseEditor
            val updated = editor.involvements.toMutableMap()
            if (weight == null) updated.remove(muscle) else updated[muscle] = weight
            current.copy(
                exerciseEditor = editor.copy(
                    involvements = updated,
                    error = null,
                    touchedGroups = editor.touchedGroups + ExerciseProfileGroup.INVOLVEMENTS
                )
            )
        }
    }

    fun onSaveExercise() {
        val editor = _state.value.exerciseEditor
        if (!editor.isOpen) return
        if (editor.isNew) {
            if (editor.name.isBlank() || editor.involvements.isEmpty()) return
            viewModelScope.launch { persistNew(editor) }
            return
        }
        val exerciseId = editor.exerciseId ?: return
        viewModelScope.launch {
            try {
                if (editor.isCustom) {
                    customExerciseRepository.update(
                        id = exerciseId,
                        name = editor.name,
                        requiredEquipment = editor.equipment,
                        involvements = editor.involvements,
                        movementPattern = editor.movementPattern,
                        isUnilateral = editor.isUnilateral,
                        loadCapability = editor.loadCapability
                    )
                } else {
                    writeBuiltInOverrides(exerciseId, editor)
                }
                closeEditorAndRefresh()
            } catch (failure: CustomExerciseException) {
                _state.update {
                    it.copy(
                        exerciseEditor = it.exerciseEditor.copy(
                            error = failure.message,
                            nameConflict =
                            failure.reason == CustomExerciseFailureReason.NAME_CONFLICT
                        )
                    )
                }
            }
        }
    }

    private suspend fun persistNew(editor: ExerciseEditorState) {
        try {
            val created = customExerciseRepository.add(
                name = editor.name,
                requiredEquipment = editor.equipment,
                involvements = editor.involvements,
                movementPattern = editor.movementPattern,
                isUnilateral = editor.isUnilateral,
                loadCapability = editor.loadCapability
            )
            closeEditorAndRefresh(created.id)
        } catch (failure: CustomExerciseException) {
            _state.update {
                it.copy(
                    exerciseEditor = it.exerciseEditor.copy(
                        error = failure.message,
                        nameConflict = failure.reason == CustomExerciseFailureReason.NAME_CONFLICT
                    )
                )
            }
        }
    }

    private suspend fun writeBuiltInOverrides(exerciseId: String, editor: ExerciseEditorState) {
        exerciseOverrideRepository.update(
            exerciseId = exerciseId,
            name = editor.name,
            requiredEquipment = editor.equipment,
            movementPattern = editor.movementPattern,
            unilateral = editor.isUnilateral,
            loadCapability = editor.loadCapability,
            involvements = editor.involvements
        )
    }

    fun onResetExercise() {
        val exerciseId = _state.value.exerciseEditor.exerciseId ?: return
        viewModelScope.launch {
            exerciseOverrideRepository.reset(exerciseId)
            closeEditorAndRefresh()
        }
    }

    fun onDeleteCustomExercise() {
        val editor = _state.value.exerciseEditor
        val exerciseId = editor.exerciseId ?: return
        if (!editor.isCustom) return
        viewModelScope.launch {
            try {
                customExerciseRepository.delete(exerciseId)
                closeEditorAndRefresh()
            } catch (failure: CustomExerciseException) {
                _state.update {
                    it.copy(exerciseEditor = it.exerciseEditor.copy(error = failure.message))
                }
            }
        }
    }

    fun onDismissExerciseEditor() {
        profileRequestRevision++
        _state.update { it.copy(exerciseEditor = ExerciseEditorState()) }
    }

    /** Keeps editing after a name conflict: the name, profile and protection flags all stay. */
    fun onDismissNameConflict() {
        _state.update {
            it.copy(exerciseEditor = it.exerciseEditor.copy(error = null, nameConflict = false))
        }
    }

    private suspend fun closeEditorAndRefresh(highlightId: String? = null) {
        profileRequestRevision++
        _state.update {
            it.copy(exercises = exerciseCatalog.all(), exerciseEditor = ExerciseEditorState())
        }
        updatePersonalRecordRows()
    }

    private suspend fun refreshExercises() {
        _state.update { it.copy(exercises = exerciseCatalog.all()) }
        updatePersonalRecordRows()
    }
}
