package com.hydrafit.app.feature.equipment

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hydrafit.app.core.domain.engine.ExerciseCatalog
import com.hydrafit.app.core.domain.engine.PersonalRecord
import com.hydrafit.app.core.domain.equipment.CatalogProfileMatch
import com.hydrafit.app.core.domain.equipment.CatalogProfileMatcher
import com.hydrafit.app.core.domain.equipment.EquipmentStation
import com.hydrafit.app.core.domain.equipment.EquipmentTag
import com.hydrafit.app.core.domain.equipment.ExerciseLoadCapability
import com.hydrafit.app.core.domain.equipment.LoadingInventory
import com.hydrafit.app.core.domain.equipment.MAX_LOADING_TICKS
import com.hydrafit.app.core.domain.equipment.MovementPattern
import com.hydrafit.app.core.domain.equipment.StationLoadBasis
import com.hydrafit.app.core.domain.equipment.parseNativeTicks
import com.hydrafit.app.core.domain.fatigue.MuscleGroup
import com.hydrafit.app.core.domain.unit.formatWeight
import com.hydrafit.app.core.userdata.equipment.CustomExerciseException
import com.hydrafit.app.core.userdata.equipment.CustomExerciseFailureReason
import com.hydrafit.app.core.userdata.equipment.CustomExerciseRepository
import com.hydrafit.app.core.userdata.equipment.EquipmentProfile
import com.hydrafit.app.core.userdata.equipment.EquipmentProfileContext
import com.hydrafit.app.core.userdata.equipment.EquipmentProfileRepository
import com.hydrafit.app.core.userdata.equipment.EquipmentProfileState
import com.hydrafit.app.core.userdata.equipment.EquipmentRepository
import com.hydrafit.app.core.userdata.equipment.ExerciseOverrideRepository
import com.hydrafit.app.core.userdata.equipment.PersonalRecordRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class EquipmentProfilerViewModel(
    private val equipmentRepository: EquipmentRepository,
    private val profileRepository: EquipmentProfileRepository,
    private val exerciseCatalog: ExerciseCatalog,
    private val exerciseOverrideRepository: ExerciseOverrideRepository,
    private val customExerciseRepository: CustomExerciseRepository,
    private val personalRecordRepository: PersonalRecordRepository,
    /** The plate solver is bounded but not cheap; it never runs on the UI thread. */
    private val calculationDispatcher: CoroutineDispatcher = Dispatchers.Default
) : ViewModel() {

    private val _state = MutableStateFlow(EquipmentProfilerUiState())
    val state: StateFlow<EquipmentProfilerUiState> = _state.asStateFlow()

    private var personalRecords: List<PersonalRecord> = emptyList()
    private var profileRequestRevision = 0L

    /** The context every profile-owned write is authorized against, from the latest snapshot. */
    private var profileContext: EquipmentProfileContext? = null
    private var activeProfile: EquipmentProfile? = null
    private var lastObservedInventory: LoadingInventory? = null
    private var profileEditorToken = 0L
    private var loadingDraftToken = 0L
    private var calculatorRequestRevision = 0L
    private var profileWritePending = false

    init {
        viewModelScope.launch {
            profileRepository.observe().collectLatest { snapshot -> applyProfileSnapshot(snapshot) }
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

    /**
     * Projects one coherent profile snapshot. Selection and ceilings come from the same revision, so
     * a toggle never mixes an old selection with a newer ceiling set.
     */
    private fun applyProfileSnapshot(snapshot: EquipmentProfileState) {
        profileContext = snapshot.context
        activeProfile = snapshot.profiles.firstOrNull { it.id == snapshot.context.profileId }
        val inventory = activeProfile?.loadingInventory
        if (inventory != lastObservedInventory) {
            // A different configuration invalidates any calculation still in flight.
            lastObservedInventory = inventory
            calculatorRequestRevision++
        }
        val summary = snapshot.toSummary()
        _state.update { current ->
            current.copy(
                profile = summary,
                selectedTags = summary?.selected ?: emptySet(),
                isLoading = false
            )
        }
    }

    /** Re-reads the authoritative profile state after a refused or failed profile-owned write. */
    private suspend fun resyncProfileSnapshot() {
        runCatching { profileRepository.current() }.onSuccess(::applyProfileSnapshot)
    }

    fun onNewPersonalRecord() {
        _state.update { it.copy(personalRecordEditor = PersonalRecordEditorState(open = true)) }
    }

    fun onOpenInstructions(exerciseId: String) {
        _state.update { current ->
            if (current.exercises.none { it.id == exerciseId }) {
                current
            } else {
                current.copy(instructionExerciseId = exerciseId)
            }
        }
    }

    fun onDismissInstructions() {
        _state.update { it.copy(instructionExerciseId = null) }
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
        val context = profileContext ?: return
        val active = activeProfile ?: return
        val updated = if (tag in active.selected) active.selected - tag else active.selected + tag
        _state.update { it.copy(selectedTags = updated) }
        viewModelScope.launch {
            try {
                profileRepository.saveEquipment(context, updated, active.maxWeightsKg)
            } catch (failure: Exception) {
                // The write is refused rather than applied to whichever profile is active now.
                if (!failure.isStaleProfileContext()) {
                    _state.update { it.copy(profileFailure = EquipmentProfileFailure.FAILED) }
                }
                resyncProfileSnapshot()
            }
        }
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
        val context = profileContext ?: return
        val profileId = context.profileId
        viewModelScope.launch {
            try {
                // The custom tag id is stable, so a rename keeps the equipment selected.
                if (editor.isBuiltIn) {
                    equipmentRepository.setMaxWeight(context, tag, maxWeight)
                } else {
                    equipmentRepository.rename(context, tag, name, maxWeight)
                }
                closeEquipmentEditorWhileOn(profileId)
            } catch (failure: Exception) {
                _state.update {
                    val editor = it.equipmentEditor.copy(error = failure.message ?: "")
                    it.copy(equipmentEditor = editor)
                }
            }
        }
    }

    fun onDeleteEquipment() {
        val tag = _state.value.equipmentEditor.tag ?: return
        val context = profileContext ?: return
        val profileId = context.profileId
        viewModelScope.launch {
            try {
                equipmentRepository.remove(context, tag)
                closeEquipmentEditorWhileOn(profileId)
            } catch (failure: Exception) {
                _state.update {
                    val editor = it.equipmentEditor.copy(error = failure.message ?: "")
                    it.copy(equipmentEditor = editor)
                }
            }
        }
    }

    /** A late result only closes the editor the write still belongs to, never a newer one. */
    private fun closeEquipmentEditorWhileOn(profileId: Long) {
        if (profileContext?.profileId != profileId) return
        _state.update { it.copy(equipmentEditor = EquipmentEditorState()) }
    }

    fun onDismissEquipmentEditor() {
        _state.update { it.copy(equipmentEditor = EquipmentEditorState()) }
    }

    fun onAddStation(tag: EquipmentTag? = null) {
        val equipment = tag
            ?: _state.value.selectedTags.firstOrNull()
            ?: _state.value.equipment.firstOrNull()?.id
            ?: return
        _state.update {
            it.copy(stationEditor = StationEditorState(equipmentId = equipment))
        }
    }

    fun onEditStation(station: EquipmentStation) {
        _state.update {
            it.copy(
                stationEditor = StationEditorState(
                    stationId = station.id,
                    equipmentId = station.equipmentId,
                    name = station.name,
                    loadBasis = station.loadBasis,
                    minInput = station.minWeightKg?.let(::formatWeight).orEmpty(),
                    maxInput = station.maxWeightKg?.let(::formatWeight).orEmpty(),
                    incrementInput = station.incrementKg?.let(::formatWeight).orEmpty()
                )
            )
        }
    }

    fun onStationEquipmentChanged(tag: EquipmentTag) {
        _state.update {
            it.copy(stationEditor = it.stationEditor.copy(equipmentId = tag, error = null))
        }
    }

    fun onStationNameChanged(value: String) {
        _state.update {
            it.copy(stationEditor = it.stationEditor.copy(name = value, error = null))
        }
    }

    fun onStationLoadBasisChanged(value: StationLoadBasis) {
        _state.update {
            it.copy(stationEditor = it.stationEditor.copy(loadBasis = value, error = null))
        }
    }

    fun onStationMinChanged(value: String) {
        updateStationGrid { copy(minInput = value) }
    }

    fun onStationMaxChanged(value: String) {
        updateStationGrid { copy(maxInput = value) }
    }

    fun onStationIncrementChanged(value: String) {
        updateStationGrid { copy(incrementInput = value) }
    }

    private fun updateStationGrid(change: StationEditorState.() -> StationEditorState) {
        _state.update {
            it.copy(stationEditor = it.stationEditor.change().copy(error = null))
        }
    }

    fun onSaveStation() {
        val editor = _state.value.stationEditor
        val equipmentId = editor.equipmentId ?: return
        if (!editor.canSave) return
        val context = profileContext ?: return
        val profileId = context.profileId
        val minimum = editor.minInput.takeIf(String::isNotBlank)?.toDouble()
        val maximum = editor.maxInput.takeIf(String::isNotBlank)?.toDouble()
        val increment = editor.incrementInput.takeIf(String::isNotBlank)?.toDouble()
        viewModelScope.launch {
            try {
                profileRepository.saveStation(
                    context,
                    EquipmentStation(
                        id = editor.stationId,
                        profileId = profileId,
                        equipmentId = equipmentId,
                        name = editor.name,
                        loadBasis = editor.loadBasis,
                        minWeightKg = minimum,
                        maxWeightKg = maximum,
                        incrementKg = increment
                    )
                )
                closeStationEditorWhileOn(profileId)
            } catch (failure: Exception) {
                _state.update {
                    it.copy(
                        stationEditor = it.stationEditor.copy(
                            error = failure.message ?: "",
                            saving = false
                        )
                    )
                }
            }
        }
    }

    fun onRetireStation(stationId: Long) {
        val context = profileContext ?: return
        val profileId = context.profileId
        viewModelScope.launch {
            try {
                profileRepository.retireStation(context, stationId)
                closeStationEditorWhileOn(profileId)
            } catch (failure: Exception) {
                _state.update {
                    it.copy(
                        stationEditor = it.stationEditor.copy(error = failure.message ?: "")
                    )
                }
            }
        }
    }

    private fun closeStationEditorWhileOn(profileId: Long) {
        if (profileContext?.profileId != profileId) return
        _state.update { it.copy(stationEditor = StationEditorState()) }
    }

    fun onDismissStationEditor() {
        _state.update { it.copy(stationEditor = StationEditorState()) }
    }

    // --- Equipment profiles: create, copy, rename, switch and confirmed delete ---

    /** Opens the create dialog for a profile that starts empty. */
    fun onNewProfile() {
        if (profileWritePending) return
        openProfileEditor(EquipmentProfileEditorMode.CREATE, targetProfileId = null, name = "")
    }

    /** Opens the create dialog seeded from the active profile's whole configuration. */
    fun onCopyActiveProfile() {
        if (profileWritePending) return
        val active = activeProfile ?: return
        openProfileEditor(EquipmentProfileEditorMode.COPY, active.id, name = "")
    }

    /** Renames the active profile only; another profile's row cannot be renamed here. */
    fun onRenameActiveProfile() {
        if (profileWritePending) return
        val active = activeProfile ?: return
        openProfileEditor(EquipmentProfileEditorMode.RENAME, active.id, name = active.name)
    }

    private fun openProfileEditor(
        mode: EquipmentProfileEditorMode,
        targetProfileId: Long?,
        name: String
    ) {
        profileEditorToken++
        _state.update {
            it.copy(
                profileEditor = EquipmentProfileEditorState(
                    mode = mode,
                    token = profileEditorToken,
                    targetProfileId = targetProfileId,
                    name = name
                ),
                profileFailure = null
            )
        }
    }

    fun onProfileNameChanged(value: String) {
        _state.update {
            it.copy(profileEditor = it.profileEditor.copy(name = value, failure = null))
        }
    }

    /** Cancelling never writes. */
    fun onCancelProfileEditor() {
        if (_state.value.profileEditor.isSaving) return
        _state.update { it.copy(profileEditor = EquipmentProfileEditorState()) }
    }

    fun onSaveProfile() {
        val editor = _state.value.profileEditor
        val mode = editor.mode ?: return
        if (editor.isSaving || profileWritePending) return
        val name = editor.name.trim()
        when {
            name.isEmpty() -> setProfileEditorFailure(editor, EquipmentProfileFailure.NAME_REQUIRED)
            name.length > PROFILE_NAME_MAX_LENGTH ->
                setProfileEditorFailure(editor, EquipmentProfileFailure.NAME_TOO_LONG)

            mode == EquipmentProfileEditorMode.RENAME -> renameActiveProfile(editor, name)
            else -> saveNewProfile(editor, name)
        }
    }

    private fun setProfileEditorFailure(
        editor: EquipmentProfileEditorState,
        failure: EquipmentProfileFailure
    ) {
        _state.update { it.copy(profileEditor = editor.copy(failure = failure)) }
    }

    private fun saveNewProfile(editor: EquipmentProfileEditorState, name: String) {
        val copyFrom = editor.targetProfileId
            .takeIf { editor.mode == EquipmentProfileEditorMode.COPY }
        val token = editor.token
        profileWritePending = true
        _state.update { it.copy(profileEditor = editor.copy(isSaving = true, failure = null)) }
        viewModelScope.launch {
            try {
                profileRepository.create(name, copyFrom)
                closeProfileEditor(token)
            } catch (failure: Exception) {
                finishProfileEditorWith(failure)
            } finally {
                profileWritePending = false
            }
        }
    }

    private fun renameActiveProfile(editor: EquipmentProfileEditorState, name: String) {
        val context = profileContext ?: return
        // The draft names the profile it was opened on; a switched profile refuses this save.
        if (editor.targetProfileId != context.profileId) {
            setProfileEditorFailure(editor, EquipmentProfileFailure.STALE)
            return
        }
        val token = editor.token
        profileWritePending = true
        _state.update { it.copy(profileEditor = editor.copy(isSaving = true, failure = null)) }
        viewModelScope.launch {
            try {
                profileRepository.rename(context, name)
                closeProfileEditor(token)
            } catch (failure: Exception) {
                finishProfileEditorWith(failure)
            } finally {
                profileWritePending = false
            }
        }
    }

    /** A late result closes only the draft it belongs to. */
    private fun closeProfileEditor(token: Long) {
        if (_state.value.profileEditor.token != token) return
        _state.update { it.copy(profileEditor = EquipmentProfileEditorState()) }
    }

    private fun finishProfileEditorWith(failure: Exception) {
        val mapped = if (failure.isStaleProfileContext()) {
            EquipmentProfileFailure.STALE
        } else {
            EquipmentProfileFailure.FAILED
        }
        _state.update {
            it.copy(profileEditor = it.profileEditor.copy(isSaving = false, failure = mapped))
        }
    }

    /** Switching never rewrites plans or history; it only changes which profile edits target. */
    fun onActivateProfile(profileId: Long) {
        val context = profileContext ?: return
        if (profileWritePending || profileId == context.profileId) return
        profileWritePending = true
        viewModelScope.launch {
            try {
                profileRepository.activate(context, profileId)
                _state.update { it.copy(profileFailure = null) }
            } catch (failure: Exception) {
                _state.update {
                    it.copy(
                        profileFailure = if (failure.isStaleProfileContext()) {
                            EquipmentProfileFailure.STALE
                        } else {
                            EquipmentProfileFailure.FAILED
                        }
                    )
                }
                resyncProfileSnapshot()
            } finally {
                profileWritePending = false
            }
        }
    }

    /** Opens the visible confirmation; the last remaining profile is refused before that. */
    fun onRequestDeleteProfile(profileId: Long) {
        if (profileWritePending) return
        val summary = _state.value.profile ?: return
        if (!summary.canDelete) {
            _state.update { it.copy(profileFailure = EquipmentProfileFailure.LAST_PROFILE) }
            return
        }
        val row = summary.rows.firstOrNull { it.id == profileId } ?: return
        _state.update {
            it.copy(
                profileDelete = EquipmentProfileDeleteState(
                    profileId = profileId,
                    name = row.name,
                    requiresReplacement = row.isActive,
                    candidates = summary.replacementCandidates(profileId)
                ),
                profileFailure = null
            )
        }
    }

    /** Deleting the active profile needs an explicitly chosen replacement, never a default. */
    fun onSelectReplacementProfile(profileId: Long) {
        _state.update { current ->
            val draft = current.profileDelete ?: return@update current
            if (draft.candidates.none { it.id == profileId }) return@update current
            current.copy(profileDelete = draft.copy(replacementId = profileId, failure = null))
        }
    }

    fun onConfirmDeleteProfile() {
        val draft = _state.value.profileDelete ?: return
        if (!draft.canConfirm || profileWritePending) return
        val context = profileContext ?: return
        profileWritePending = true
        _state.update { it.copy(profileDelete = draft.copy(isDeleting = true, failure = null)) }
        viewModelScope.launch {
            try {
                profileRepository.delete(context, draft.profileId, draft.replacementId)
                closeProfileDelete(draft.profileId)
            } catch (failure: Exception) {
                _state.update {
                    it.copy(
                        profileDelete = it.profileDelete?.copy(
                            isDeleting = false,
                            failure = if (failure.isStaleProfileContext()) {
                                EquipmentProfileFailure.STALE
                            } else {
                                EquipmentProfileFailure.FAILED
                            }
                        )
                    )
                }
                resyncProfileSnapshot()
            } finally {
                profileWritePending = false
            }
        }
    }

    private fun closeProfileDelete(profileId: Long) {
        if (_state.value.profileDelete?.profileId != profileId) return
        _state.update { it.copy(profileDelete = null) }
    }

    fun onCancelProfileDelete() {
        if (_state.value.profileDelete?.isDeleting == true) return
        _state.update { it.copy(profileDelete = null) }
    }

    // --- Per-profile loading configuration ---

    /** Opens a write-free draft over the active profile's stored configuration. */
    fun onOpenLoadingEditor() {
        if (_state.value.loadingEditor.isSaving) return
        val inventory = activeProfile?.loadingInventory ?: return
        loadingDraftToken++
        _state.update {
            it.copy(
                loadingEditor = LoadingInventoryEditorState
                    .forInventory(inventory, loadingDraftToken)
            )
        }
    }

    fun onLoadingUnitSelected(option: LoadingUnitOption) {
        _state.update { it.copy(loadingEditor = it.loadingEditor.withUnit(option)) }
    }

    fun onLoadingPlateChanged(index: Int, draft: OwnedPlateDraft) {
        _state.update { it.copy(loadingEditor = it.loadingEditor.withPlateChanged(index, draft)) }
    }

    fun onLoadingPlateAdded() {
        _state.update { it.copy(loadingEditor = it.loadingEditor.withPlateAdded()) }
    }

    fun onLoadingPlateRemoved(index: Int) {
        _state.update { it.copy(loadingEditor = it.loadingEditor.withPlateRemoved(index)) }
    }

    fun onLoadingBarChanged(index: Int, draft: LoadingBarDraft) {
        _state.update { it.copy(loadingEditor = it.loadingEditor.withBarChanged(index, draft)) }
    }

    fun onLoadingBarAdded() {
        _state.update { it.copy(loadingEditor = it.loadingEditor.withBarAdded()) }
    }

    fun onLoadingBarRemoved(index: Int) {
        _state.update { it.copy(loadingEditor = it.loadingEditor.withBarRemoved(index)) }
    }

    fun onLoadingDumbbellChanged(index: Int, draft: LoadingDumbbellDraft) {
        _state.update {
            it.copy(loadingEditor = it.loadingEditor.withDumbbellChanged(index, draft))
        }
    }

    fun onLoadingDumbbellAdded() {
        _state.update { it.copy(loadingEditor = it.loadingEditor.withDumbbellAdded()) }
    }

    fun onLoadingDumbbellRemoved(index: Int) {
        _state.update { it.copy(loadingEditor = it.loadingEditor.withDumbbellRemoved(index)) }
    }

    /** Cancelling discards the draft and writes nothing. */
    fun onCancelLoadingEditor() {
        if (_state.value.loadingEditor.isSaving) return
        _state.update { it.copy(loadingEditor = LoadingInventoryEditorState()) }
    }

    fun onSaveLoadingInventory() {
        val draft = _state.value.loadingEditor
        if (!draft.canSave) return
        val context = profileContext ?: return
        val profileId = context.profileId
        val token = draft.token
        when (val built = draft.buildInventory()) {
            is LoadingDraftResult.Invalid -> _state.update {
                it.copy(loadingEditor = it.loadingEditor.copy(failure = built.failure))
            }

            is LoadingDraftResult.Valid -> {
                _state.update {
                    it.copy(loadingEditor = it.loadingEditor.copy(isSaving = true, failure = null))
                }
                viewModelScope.launch {
                    try {
                        profileRepository.saveLoadingInventory(context, built.inventory)
                        if (_state.value.loadingEditor.token == token &&
                            profileContext?.profileId == profileId
                        ) {
                            _state.update { it.copy(loadingEditor = LoadingInventoryEditorState()) }
                        }
                    } catch (failure: Exception) {
                        val stale = failure.isStaleProfileContext() ||
                            profileContext?.profileId != profileId
                        _state.update { current ->
                            current.copy(
                                loadingEditor = current.loadingEditor.copy(
                                    isSaving = false,
                                    staleContext = stale,
                                    failure = if (stale) {
                                        LoadingEditorFailure.STALE
                                    } else {
                                        LoadingEditorFailure.SAVE_FAILED
                                    }
                                )
                            )
                        }
                        if (failure.isStaleProfileContext()) resyncProfileSnapshot()
                    }
                }
            }
        }
    }

    // --- Manual loading calculators ---

    fun onOpenLoadingCalculator() {
        calculatorRequestRevision++
        _state.update { it.copy(calculator = LoadingCalculatorState(open = true)) }
    }

    fun onCalculatorKindChanged(kind: LoadingCalculatorKind) {
        calculatorRequestRevision++
        _state.update {
            it.copy(
                calculator = it.calculator.copy(
                    kind = kind,
                    selectedId = null,
                    targetInput = "",
                    result = null,
                    failure = null,
                    isRunning = false
                )
            )
        }
    }

    fun onCalculatorEquipmentSelected(equipmentId: String) {
        calculatorRequestRevision++
        _state.update {
            it.copy(
                calculator = it.calculator.copy(
                    selectedId = equipmentId,
                    result = null,
                    failure = null
                )
            )
        }
    }

    fun onCalculatorTargetChanged(value: String) {
        calculatorRequestRevision++
        _state.update {
            it.copy(
                calculator = it.calculator.copy(
                    targetInput = value.filter { char -> char.isDigit() || char == '.' },
                    result = null,
                    failure = null
                )
            )
        }
    }

    /**
     * Resolves the entered native target against the chosen saved configuration.
     *
     * The solver runs off the UI thread, and its result is dropped unless it still belongs to the
     * current selection, target and profile configuration.
     */
    fun onRunLoadingCalculator() {
        val calculator = _state.value.calculator
        val equipmentId = calculator.selectedId ?: return
        if (calculator.isRunning) return
        val inventory = activeProfile?.loadingInventory ?: return
        val targetTicks = runCatching { parseNativeTicks(calculator.targetInput.trim()) }
            .getOrNull()
        val failure = when {
            targetTicks == null -> LoadingCalculatorFailure.INVALID_TARGET
            targetTicks !in 0..MAX_LOADING_TICKS -> LoadingCalculatorFailure.OUT_OF_RANGE
            else -> null
        }
        if (failure != null) {
            _state.update { it.copy(calculator = it.calculator.copy(failure = failure)) }
            return
        }
        val revision = ++calculatorRequestRevision
        val kind = calculator.kind
        _state.update {
            it.copy(
                calculator = it.calculator.copy(isRunning = true, failure = null, result = null)
            )
        }
        viewModelScope.launch {
            val view = withContext(calculationDispatcher) {
                runLoadingCalculation(inventory, kind, equipmentId, requireNotNull(targetTicks))
            }
            if (revision != calculatorRequestRevision) return@launch
            _state.update { current ->
                current.copy(
                    calculator = current.calculator.copy(
                        isRunning = false,
                        result = view,
                        failure = if (view == null) LoadingCalculatorFailure.FAILED else null
                    )
                )
            }
        }
    }

    fun onDismissLoadingCalculator() {
        calculatorRequestRevision++
        _state.update { it.copy(calculator = LoadingCalculatorState()) }
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

    /**
     * Directly sets one muscle's involvement weight for a tier shortcut: null and zero remove the
     * contribution, and a non-finite or out-of-range value is refused without changing the map.
     */
    fun onEditorMuscleInvolvementChanged(muscle: MuscleGroup, weight: Double?) {
        _state.update { current ->
            val editor = current.exerciseEditor
            if (weight != null && !weight.isWritableInvolvement()) {
                return@update current
            }
            val updated = editor.involvements.toMutableMap()
            if (weight == null || weight == 0.0) {
                updated.remove(muscle)
            } else {
                updated[muscle] = weight
            }
            current.copy(
                exerciseEditor = editor.copy(
                    involvements = updated,
                    error = null,
                    touchedGroups = editor.touchedGroups + ExerciseProfileGroup.INVOLVEMENTS
                )
            )
        }
    }

    /** Opens the per-muscle numeric editor seeded with that muscle's current weight. */
    fun onEditMuscleInvolvement(muscle: MuscleGroup) {
        _state.update { current ->
            val editor = current.exerciseEditor
            current.copy(
                exerciseEditor = editor.copy(
                    involvementEditor = InvolvementEditorState.forMuscle(
                        muscle,
                        editor.involvements[muscle]
                    )
                )
            )
        }
    }

    fun onInvolvementInputChanged(value: String) {
        _state.update { current ->
            val editor = current.exerciseEditor
            val draft = editor.involvementEditor ?: return@update current
            val updatedEditor = editor.copy(involvementEditor = draft.copy(input = value))
            current.copy(exerciseEditor = updatedEditor)
        }
    }

    /** Applies a tier shortcut to the open draft; null selects the explicit `0`/None choice. */
    fun onInvolvementTierSelected(weight: Double?) {
        _state.update { current ->
            val editor = current.exerciseEditor
            val draft = editor.involvementEditor ?: return@update current
            val text = weight?.toString() ?: InvolvementEditorState.NONE_INPUT
            current.copy(exerciseEditor = editor.copy(involvementEditor = draft.copy(input = text)))
        }
    }

    /**
     * Commits the open draft. An invalid (including blank) value is refused and the dialog stays
     * open; an explicit `0`/None removes the muscle's contribution. Cancel is handled separately
     * and never writes.
     */
    fun onApplyInvolvement() {
        _state.update { current ->
            val editor = current.exerciseEditor
            val draft = editor.involvementEditor ?: return@update current
            if (!draft.isValid) return@update current
            val updated = editor.involvements.toMutableMap()
            val weight = draft.weightToApply()
            if (weight == null) updated.remove(draft.muscle) else updated[draft.muscle] = weight
            current.copy(
                exerciseEditor = editor.copy(
                    involvements = updated,
                    involvementEditor = null,
                    error = null,
                    touchedGroups = editor.touchedGroups + ExerciseProfileGroup.INVOLVEMENTS
                )
            )
        }
    }

    /** Discards the open draft without writing anything. */
    fun onCancelInvolvement() {
        _state.update { current ->
            val editor = current.exerciseEditor
            if (editor.involvementEditor == null) return@update current
            current.copy(exerciseEditor = editor.copy(involvementEditor = null))
        }
    }

    fun onSaveExercise() {
        val editor = _state.value.exerciseEditor
        if (!editor.isOpen) return
        // An open numeric draft with an invalid value blocks Save as well as its own Apply.
        if (editor.involvementEditor?.isValid == false) return
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
                            errorReason = failure.reason,
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
                        errorReason = failure.reason,
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
                    it.copy(
                        exerciseEditor = it.exerciseEditor.copy(
                            error = failure.message,
                            errorReason = failure.reason
                        )
                    )
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

/** A directly set involvement must be a finite value in [0, 1]; zero is handled as a removal. */
private fun Double.isWritableInvolvement(): Boolean {
    if (!isFinite()) return false
    return this >= InvolvementEditorState.MIN_WEIGHT && this <= InvolvementEditorState.MAX_WEIGHT
}

/** Matches the profile-name rule enforced by the repository, so a refusal never reaches the UI. */
private const val PROFILE_NAME_MAX_LENGTH = 80
