package com.hydrafit.app.feature.routines

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hydrafit.app.core.domain.engine.BuildSetPrescriptionUseCase
import com.hydrafit.app.core.domain.engine.DEFAULT_SET_PRESCRIPTION_ROUND_TO_KG
import com.hydrafit.app.core.domain.engine.ExerciseCatalog
import com.hydrafit.app.core.domain.engine.SetPrescriptionRequest
import com.hydrafit.app.core.domain.engine.SetPrescriptionShape
import com.hydrafit.app.core.domain.engine.SlotProgressionPolicy
import com.hydrafit.app.core.domain.engine.SlotSetTargets
import com.hydrafit.app.core.domain.engine.SplitFocus
import com.hydrafit.app.core.domain.equipment.ExerciseExclusion
import com.hydrafit.app.core.domain.equipment.ExerciseLoadCapability
import com.hydrafit.app.core.domain.routine.RoutineEntry
import com.hydrafit.app.core.domain.routine.RoutineTemplate
import com.hydrafit.app.core.domain.routine.RoutineTemplateActions
import com.hydrafit.app.core.domain.routine.RoutineTemplateException
import com.hydrafit.app.core.domain.routine.RoutineWorkout
import com.hydrafit.app.core.domain.routine.Superset
import com.hydrafit.app.core.domain.routine.validateSupersets
import com.hydrafit.app.core.domain.schedule.ActivationRequest
import com.hydrafit.app.core.domain.schedule.ScheduleException
import com.hydrafit.app.core.domain.schedule.ScheduleMode
import com.hydrafit.app.core.domain.schedule.WorkoutScheduleActions
import com.hydrafit.app.core.domain.settings.DEFAULT_REST_SECONDS
import com.hydrafit.app.core.domain.time.DayOfWeek
import com.hydrafit.app.core.domain.time.TimeProvider
import com.hydrafit.app.core.domain.time.localEpochDay
import com.hydrafit.app.core.domain.unit.WeightUnit
import com.hydrafit.app.core.domain.unit.formatWeight
import com.hydrafit.app.core.domain.workout.LoadKind
import com.hydrafit.app.core.domain.workout.WorkoutLoadPolicy
import com.hydrafit.app.core.userdata.equipment.ExerciseExclusionRepository
import com.hydrafit.app.core.userdata.settings.PreferredWeekdaysRepository
import com.hydrafit.app.core.userdata.settings.WeightUnitRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class RoutinesViewModel(
    private val routineActions: RoutineTemplateActions,
    private val scheduleActions: WorkoutScheduleActions,
    private val exerciseCatalog: ExerciseCatalog,
    private val timeProvider: TimeProvider,
    private val weightUnitRepository: WeightUnitRepository,
    private val exclusionRepository: ExerciseExclusionRepository,
    private val preferredWeekdaysRepository: PreferredWeekdaysRepository
) : ViewModel() {

    private val _state = MutableStateFlow(RoutinesUiState())
    val state: StateFlow<RoutinesUiState> = _state.asStateFlow()

    private var weightUnit: WeightUnit = WeightUnit.KG

    /** Builds the ordered per-set targets; stateless, so one instance serves every dialog. */
    private val buildSetPrescription = BuildSetPrescriptionUseCase()

    private var occurrencesJob: Job? = null

    /**
     * The in-flight activation-dialog resolution. A newer opening replaces it and a dismissal cancels
     * it, so a superseded read cannot publish and a dismissed dialog cannot be resurrected.
     */
    private var activationJob: Job? = null
    private var exerciseCapabilities: Map<String, ExerciseLoadCapability> = emptyMap()
    private var catalogExerciseNames: List<Pair<String, String>> = emptyList()
    private var exerciseExclusions: List<ExerciseExclusion> = emptyList()

    /**
     * Bumping this re-subscribes to [routineActions.observe], which re-reads the current rows. This
     * backstops a save made from another tab (e.g. SplitBuilder "Save as routine") whose emission the
     * long-lived collection may already have passed.
     */
    private val templatesRefresh = MutableStateFlow(0)

    init {
        viewModelScope.launch {
            templatesRefresh.collectLatest {
                routineActions.observe().collect { templates ->
                    _state.update { it.copy(templates = templates, isLoading = false) }
                }
            }
        }
        viewModelScope.launch {
            exerciseCatalog.observeAll().collect { catalog ->
                exerciseCapabilities = catalog.associate { it.id to it.loadCapability }
                catalogExerciseNames = catalog.map { it.id to it.name }
                updateExerciseOptions()
            }
        }
        viewModelScope.launch {
            exclusionRepository.observe().collect { exclusions ->
                exerciseExclusions = exclusions
                updateExerciseOptions()
            }
        }
        viewModelScope.launch {
            weightUnitRepository.unitFlow().collect { unit ->
                weightUnit = unit
                _state.update { it.copy(weightUnit = unit) }
            }
        }
        viewModelScope.launch {
            scheduleActions.observeActiveActivation().collect { activation ->
                _state.update { it.copy(activeActivation = activation) }
                occurrencesJob?.cancel()
                if (activation == null) {
                    _state.update { it.copy(occurrences = emptyList()) }
                } else {
                    occurrencesJob = viewModelScope.launch {
                        scheduleActions.observeOccurrences(activation.id).collect { occurrences ->
                            _state.update { it.copy(occurrences = occurrences) }
                        }
                    }
                }
            }
        }
    }

    /** Rebuilds the picker options, marking EX-01 exclusions without removing them from the list. */
    private fun updateExerciseOptions() {
        val now = timeProvider.nowMillis()
        val excludedExerciseIds = exerciseExclusions.filter { it.isActive(now) }
            .map { it.exerciseId }.toSet()
        _state.update { state ->
            state.copy(
                exercises = catalogExerciseNames.map { (id, name) ->
                    RoutineExerciseOption(id, name, isExcluded = id in excludedExerciseIds)
                },
                excludedExerciseIds = excludedExerciseIds
            )
        }
    }

    /** Re-reads the routines; the screen calls this on resume so another tab's save is visible. */
    fun refresh() {
        updateExerciseOptions()
        templatesRefresh.value += 1
    }

    // --- Editor ---

    fun onNewRoutine() {
        _state.update {
            it.copy(
                editor = RoutineEditorState(
                    id = 0,
                    revision = 0,
                    sourcePlanId = null,
                    name = "",
                    unit = weightUnit,
                    workouts = emptyList()
                )
            )
        }
    }

    fun onEditRoutine(template: RoutineTemplate) {
        val names = _state.value.exercises.associate { it.id to it.name }
        _state.update { it.copy(editor = template.toEditor(weightUnit, names)) }
    }

    fun onEditorNameChanged(name: String) = updateEditor { it.copy(name = name) }

    fun onEditorDismissed() = _state.update { it.copy(editor = null, picker = null) }

    fun onAddWorkout() = updateEditor { editor ->
        editor.copy(workouts = editor.workouts + EditorWorkout(name = "", focus = null))
    }

    fun onRemoveWorkout(index: Int) = updateEditor { editor ->
        editor.copy(workouts = editor.workouts.filterIndexed { i, _ -> i != index })
    }

    fun onMoveWorkout(index: Int, delta: Int) = updateEditor { editor ->
        val target = index + delta
        if (target !in editor.workouts.indices) {
            editor
        } else {
            editor.copy(
                workouts = editor.workouts.toMutableList().apply {
                    add(target, removeAt(index))
                }
            )
        }
    }

    fun onWorkoutNameChanged(index: Int, name: String) = updateWorkout(index) {
        it.copy(name = name)
    }

    fun onWorkoutFocusChanged(index: Int, focus: SplitFocus?) =
        updateWorkout(index) { it.copy(focus = focus) }

    fun onAddExercise(workoutIndex: Int) {
        updateExerciseOptions()
        _state.update { it.copy(picker = ExercisePickerState(workoutIndex = workoutIndex)) }
    }

    fun onReplaceExercise(workoutIndex: Int, entryIndex: Int) {
        updateExerciseOptions()
        _state.update {
            it.copy(
                picker = ExercisePickerState(
                    workoutIndex = workoutIndex,
                    replaceEntryIndex = entryIndex
                )
            )
        }
    }

    fun onPickerQueryChanged(query: String) = _state.update { current ->
        current.picker?.let { current.copy(picker = it.copy(query = query)) } ?: current
    }

    fun onPickerDismissed() = _state.update { it.copy(picker = null) }

    fun onPickerExerciseSelected(exerciseId: String, name: String) {
        val picker = _state.value.picker ?: return
        updateWorkout(picker.workoutIndex) { workout ->
            val capability =
                exerciseCapabilities[exerciseId] ?: ExerciseLoadCapability.UNSPECIFIED
            val entry = EditorEntry(
                exerciseId = exerciseId,
                name = name,
                sets = "3",
                reps = "8",
                weight = "",
                loadKind = WorkoutLoadPolicy.defaultKind(capability)
            )
            val entries = if (picker.replaceEntryIndex == null) {
                workout.entries + entry
            } else {
                // Replacing a slot's exercise can invalidate its pair, so the pair is dropped with it.
                val pairKey = workout.entries.getOrNull(picker.replaceEntryIndex)?.superset?.key
                val slots = pairKey?.let { workout.withoutSuperset(it).entries } ?: workout.entries
                slots.mapIndexed { index, existing ->
                    // Replace exactly the chosen slot; keep its persisted id so an edit is an update.
                    if (index == picker.replaceEntryIndex) {
                        entry.copy(id = existing.id)
                    } else {
                        existing
                    }
                }
            }
            workout.copy(entries = entries)
        }
        _state.update { it.copy(picker = null) }
    }

    fun onRemoveEntry(workoutIndex: Int, entryIndex: Int) = updateWorkout(workoutIndex) { workout ->
        val removed = workout.entries.getOrNull(entryIndex) ?: return@updateWorkout workout
        val remaining = workout.entries.filterIndexed { i, _ -> i != entryIndex }
        val key = removed.superset?.key
        // A deleted slot must not leave its partner holding half a pair.
        workout.copy(
            entries = if (key == null) {
                remaining
            } else {
                remaining.map { if (it.superset?.key == key) it.copy(superset = null) else it }
            }
        )
    }

    fun onMoveEntry(workoutIndex: Int, entryIndex: Int, delta: Int) =
        updateWorkout(workoutIndex) { workout ->
            val target = entryIndex + delta
            if (target !in workout.entries.indices) {
                workout
            } else {
                val reordered = workout.entries.toMutableList().apply {
                    add(target, removeAt(entryIndex))
                }
                // A move must never split a pair: a non-adjacent pair could not be saved at all.
                if (supersetsFormValidGroups(reordered)) {
                    workout.copy(entries = reordered)
                } else {
                    workout
                }
            }
        }

    fun onEntrySetsChanged(workoutIndex: Int, entryIndex: Int, value: String) =
        updateEntry(workoutIndex, entryIndex) { it.copy(sets = value) }

    fun onEntryRepsChanged(workoutIndex: Int, entryIndex: Int, value: String) =
        updateEntry(workoutIndex, entryIndex) { it.copy(reps = value) }

    fun onEntryMetricChanged(workoutIndex: Int, entryIndex: Int, metric: EntryMetric) =
        updateEntry(workoutIndex, entryIndex) { it.copy(metric = metric) }

    fun onEntryDurationChanged(workoutIndex: Int, entryIndex: Int, value: String) =
        updateEntry(workoutIndex, entryIndex) { it.copy(durationSeconds = value) }

    fun onEntryWeightChanged(workoutIndex: Int, entryIndex: Int, value: String) =
        updateEntry(workoutIndex, entryIndex) { it.copy(weight = value) }

    // --- Progression policy ---

    /**
     * Opens the progression dialog for a slot, seeded from its existing policy. A slot with no policy
     * opens empty: the app never invents a rep range or a load step for the user.
     */
    fun onEditProgressionPolicy(workoutIndex: Int, entryIndex: Int) {
        val entry = _state.value.editor
            ?.workouts?.getOrNull(workoutIndex)
            ?.entries?.getOrNull(entryIndex)
            ?: return
        val policy = entry.progressionPolicy
        _state.update {
            it.copy(
                progressionPolicyEditor = ProgressionPolicyEditorState(
                    workoutIndex = workoutIndex,
                    entryIndex = entryIndex,
                    minReps = policy?.minReps?.toString() ?: "",
                    maxReps = policy?.maxReps?.toString() ?: "",
                    incrementKg = policy?.let { stored -> kilogramStepText(stored.incrementKg) }
                        ?: "",
                    equipmentId = policy?.equipmentId ?: ""
                )
            )
        }
    }

    fun onProgressionPolicyMinRepsChanged(value: String) =
        updateProgressionPolicyEditor { it.copy(minReps = value, error = null) }

    fun onProgressionPolicyMaxRepsChanged(value: String) =
        updateProgressionPolicyEditor { it.copy(maxReps = value, error = null) }

    fun onProgressionPolicyIncrementChanged(value: String) =
        updateProgressionPolicyEditor { it.copy(incrementKg = value, error = null) }

    fun onProgressionPolicyEquipmentIdChanged(value: String) =
        updateProgressionPolicyEditor { it.copy(equipmentId = value, error = null) }

    fun onProgressionPolicySave() {
        val editor = _state.value.progressionPolicyEditor ?: return
        val minReps = editor.minReps.trim().toIntOrNull()
        val maxReps = editor.maxReps.trim().toIntOrNull()
        val incrementKg = editor.incrementKg.trim().toDoubleOrNull()
        if (minReps == null || maxReps == null) {
            rejectProgressionPolicy(editor, ProgressionPolicyError.REPS_RANGE)
            return
        }
        if (incrementKg == null) {
            rejectProgressionPolicy(editor, ProgressionPolicyError.INCREMENT)
            return
        }
        // A truly empty field means "no identity"; anything else must satisfy the domain rule.
        val equipmentId = editor.equipmentId.takeIf { it.isNotEmpty() }
        val error = progressionPolicyFieldError(minReps, maxReps, incrementKg, equipmentId)
        if (error != null) {
            rejectProgressionPolicy(editor, error)
            return
        }
        val policy = SlotProgressionPolicy(
            minReps = minReps,
            maxReps = maxReps,
            incrementKg = incrementKg,
            equipmentId = equipmentId
        )
        updateEntry(editor.workoutIndex, editor.entryIndex) { it.copy(progressionPolicy = policy) }
        _state.update { it.copy(progressionPolicyEditor = null) }
    }

    private fun rejectProgressionPolicy(
        editor: ProgressionPolicyEditorState,
        error: ProgressionPolicyError
    ) {
        _state.update { it.copy(progressionPolicyEditor = editor.copy(error = error)) }
    }

    fun onProgressionPolicyClear() {
        val editor = _state.value.progressionPolicyEditor ?: return
        updateEntry(editor.workoutIndex, editor.entryIndex) { it.copy(progressionPolicy = null) }
        _state.update { it.copy(progressionPolicyEditor = null) }
    }

    fun onProgressionPolicyDismissed() = _state.update { it.copy(progressionPolicyEditor = null) }

    /**
     * Renders a stored kilogram step for its editor field without rounding it away. The field edits
     * the stored value itself, so a lossy display would rewrite the user's policy on a plain reopen
     * and save; [formatWeight] stays for display-only surfaces.
     */
    private fun kilogramStepText(value: Double): String =
        if (value % 1.0 == 0.0) value.toInt().toString() else value.toString()

    // --- Per-set targets ---

    /**
     * Opens the ordered-prescription dialog for a slot, seeded from its existing targets. Only the
     * resulting targets are persisted, so the shape and endpoints are reconstructed from them; a
     * slot with no targets opens with empty recipe fields and the product's default rounding step.
     */
    fun onEditSetTargets(workoutIndex: Int, entryIndex: Int) {
        val entry = _state.value.editor
            ?.workouts?.getOrNull(workoutIndex)
            ?.entries?.getOrNull(entryIndex)
            ?: return
        val editor = seedSetTargetsEditor(workoutIndex, entryIndex, entry.setTargets)
        _state.update {
            it.copy(setTargetsEditor = editor.copy(preview = buildSetTargetsPreview(editor)))
        }
    }

    fun onSetTargetsShapeChanged(shape: SetPrescriptionShape) =
        updateSetTargetsEditor { it.copy(shape = shape, error = null) }

    fun onSetTargetsSetsChanged(value: String) =
        updateSetTargetsEditor { it.copy(sets = value, error = null) }

    fun onSetTargetsStartRepsChanged(value: String) =
        updateSetTargetsEditor { it.copy(startReps = value, error = null) }

    fun onSetTargetsEndRepsChanged(value: String) =
        updateSetTargetsEditor { it.copy(endReps = value, error = null) }

    fun onSetTargetsStartLoadChanged(value: String) =
        updateSetTargetsEditor { it.copy(startLoadKg = value, error = null) }

    fun onSetTargetsEndLoadChanged(value: String) =
        updateSetTargetsEditor { it.copy(endLoadKg = value, error = null) }

    fun onSetTargetsRoundToChanged(value: String) =
        updateSetTargetsEditor { it.copy(roundToKg = value, error = null) }

    fun onSetTargetsRirChanged(value: String) =
        updateSetTargetsEditor { it.copy(targetRir = value, error = null) }

    /** Stores the built targets on the slot, or keeps the dialog open with the offending field. */
    fun onSetTargetsApply() {
        val editor = _state.value.setTargetsEditor ?: return
        val request = when (val parsed = parseSetTargetsRequest(editor)) {
            is SetTargetsParseResult.Invalid -> {
                rejectSetTargets(parsed.error)
                return
            }
            is SetTargetsParseResult.Valid -> parsed.request
        }
        val targets = try {
            buildSetPrescription(request)
        } catch (_: IllegalArgumentException) {
            rejectSetTargets(setTargetsFieldError(request))
            return
        }
        updateEntry(editor.workoutIndex, editor.entryIndex) {
            // The ordered list and the slot's set count are one value: keep them in sync.
            it.copy(setTargets = targets, sets = targets.sets.toString())
        }
        _state.update { it.copy(setTargetsEditor = null) }
    }

    /** Removes the slot's ordered targets and closes the dialog. */
    fun onSetTargetsClear() {
        val editor = _state.value.setTargetsEditor ?: return
        updateEntry(editor.workoutIndex, editor.entryIndex) { it.copy(setTargets = null) }
        _state.update { it.copy(setTargetsEditor = null) }
    }

    fun onSetTargetsDismissed() = _state.update { it.copy(setTargetsEditor = null) }

    /**
     * Seeds the dialog from the recorded recipe when the slot has one. A slot whose targets carry no
     * recipe opens with every recipe field empty — the app never guesses what the user entered — but
     * still exposes the stored targets for display. A slot with no ordered targets opens empty and
     * keeps the product's default rounding step.
     */
    private fun seedSetTargetsEditor(
        workoutIndex: Int,
        entryIndex: Int,
        targets: SlotSetTargets?
    ): SetTargetsEditorState {
        val recipe = targets?.recipe
        if (recipe == null) {
            return SetTargetsEditorState(
                workoutIndex = workoutIndex,
                entryIndex = entryIndex,
                sets = "",
                startReps = "",
                endReps = "",
                startLoadKg = "",
                endLoadKg = "",
                roundToKg = if (targets == null) {
                    kilogramStepText(DEFAULT_SET_PRESCRIPTION_ROUND_TO_KG)
                } else {
                    ""
                },
                targetRir = "",
                storedTargets = targets
            )
        }
        return SetTargetsEditorState(
            workoutIndex = workoutIndex,
            entryIndex = entryIndex,
            shape = recipe.shape,
            sets = recipe.sets.toString(),
            startReps = recipe.startReps.toString(),
            endReps = recipe.endReps.toString(),
            startLoadKg = recipe.startLoadKg?.let(::kilogramStepText) ?: "",
            endLoadKg = recipe.endLoadKg?.let(::kilogramStepText) ?: "",
            roundToKg = kilogramStepText(recipe.roundToKg),
            targetRir = recipe.targetRir?.toString() ?: "",
            storedTargets = targets
        )
    }

    private fun updateSetTargetsEditor(
        transform: (SetTargetsEditorState) -> SetTargetsEditorState
    ) {
        _state.update { current ->
            val editor = current.setTargetsEditor ?: return@update current
            val updated = transform(editor)
            current.copy(setTargetsEditor = updated.copy(preview = buildSetTargetsPreview(updated)))
        }
    }

    private fun rejectSetTargets(error: SetTargetsError) =
        updateSetTargetsEditor { it.copy(error = error) }

    /** The ordered targets the current inputs would build, or null while they are not yet valid. */
    private fun buildSetTargetsPreview(editor: SetTargetsEditorState): SlotSetTargets? {
        val request = when (val parsed = parseSetTargetsRequest(editor)) {
            is SetTargetsParseResult.Invalid -> return null
            is SetTargetsParseResult.Valid -> parsed.request
        }
        return try {
            buildSetPrescription(request)
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    /**
     * Maps a rejected request back to its offending field by probing each field against
     * [buildSetPrescription] with the others replaced by known-valid values, so no numeric bound is
     * duplicated here and the two can't drift. Only called after a request was rejected.
     */
    private fun setTargetsFieldError(request: SetPrescriptionRequest): SetTargetsError {
        val base = SetPrescriptionRequest(
            shape = SetPrescriptionShape.STRAIGHT,
            sets = VALID_SETS_TARGETS_PROBE,
            startReps = VALID_REPS_TARGETS_PROBE,
            endReps = VALID_REPS_TARGETS_PROBE,
            startLoadKg = null,
            endLoadKg = null,
            roundToKg = DEFAULT_SET_PRESCRIPTION_ROUND_TO_KG,
            targetRir = null
        )
        return when {
            !isValidSetTargetsRequest(base.copy(sets = request.sets)) ->
                SetTargetsError.SETS_RANGE
            !isValidSetTargetsRequest(
                base.copy(startReps = request.startReps, endReps = request.endReps)
            ) -> SetTargetsError.REPS_RANGE
            !isValidSetTargetsRequest(
                base.copy(startLoadKg = request.startLoadKg, endLoadKg = request.endLoadKg)
            ) -> SetTargetsError.LOAD
            !isValidSetTargetsRequest(base.copy(roundToKg = request.roundToKg)) ->
                SetTargetsError.ROUNDING
            else -> SetTargetsError.RIR_RANGE
        }
    }

    private fun isValidSetTargetsRequest(request: SetPrescriptionRequest): Boolean = try {
        buildSetPrescription(request)
        true
    } catch (_: IllegalArgumentException) {
        false
    }

    // --- Superset pairs ---

    /**
     * Opens the pair dialog for a slot and the slot after it. The measurement and prescription of
     * either slot are untouched by pairing. The two fields are prefilled with editable values the
     * user can change (no transition, and the app's default rest); applying is explicit.
     */
    fun onEditSuperset(workoutIndex: Int, entryIndex: Int) {
        val entries = _state.value.editor?.workouts?.getOrNull(workoutIndex)?.entries ?: return
        val entry = entries.getOrNull(entryIndex) ?: return
        if (entries.getOrNull(entryIndex + 1) == null) return
        val pair = entry.superset
        _state.update {
            it.copy(
                supersetEditor = SupersetEditorState(
                    workoutIndex = workoutIndex,
                    entryIndex = entryIndex,
                    transitionSeconds = pair?.transitionSeconds?.toString() ?: "0",
                    pairRestSeconds = pair?.pairRestSeconds?.toString()
                        ?: DEFAULT_REST_SECONDS.toString()
                )
            )
        }
    }

    fun onSupersetTransitionChanged(value: String) =
        updateSupersetEditor { it.copy(transitionSeconds = value, error = null) }

    fun onSupersetPairRestChanged(value: String) =
        updateSupersetEditor { it.copy(pairRestSeconds = value, error = null) }

    /**
     * Stores the identical pair on both slots, or keeps the dialog open with the offending field.
     * The group is validated against the domain rule, whose backstop the save also runs.
     */
    fun onSupersetApply() {
        val dialog = _state.value.supersetEditor ?: return
        val transition = dialog.transitionSeconds.trim().toIntOrNull()
        if (transition == null) {
            rejectSuperset(dialog, SupersetError.TRANSITION_RANGE)
            return
        }
        val pairRest = dialog.pairRestSeconds.trim().toIntOrNull()
        if (pairRest == null) {
            rejectSuperset(dialog, SupersetError.PAIR_REST_RANGE)
            return
        }
        val fieldError = supersetFieldError(transition, pairRest)
        if (fieldError != null) {
            rejectSuperset(dialog, fieldError)
            return
        }
        val workout = _state.value.editor?.workouts?.getOrNull(dialog.workoutIndex) ?: return
        if (workout.entries.getOrNull(dialog.entryIndex + 1) == null) return
        val pair = Superset(
            key = supersetKey(dialog.workoutIndex, dialog.entryIndex),
            transitionSeconds = transition,
            pairRestSeconds = pairRest
        )
        val grouped = workout.entries.mapIndexed { index, entry ->
            if (index == dialog.entryIndex || index == dialog.entryIndex + 1) {
                entry.copy(superset = pair)
            } else {
                entry
            }
        }
        if (!supersetsFormValidGroups(grouped)) {
            rejectSuperset(dialog, SupersetError.SAME_EXERCISE)
            return
        }
        updateWorkout(dialog.workoutIndex) { it.copy(entries = grouped) }
        _state.update { it.copy(supersetEditor = null) }
    }

    /** Removes the slot's pair from both of its members. */
    fun onRemoveSuperset(workoutIndex: Int, entryIndex: Int) {
        val key = _state.value.editor
            ?.workouts?.getOrNull(workoutIndex)
            ?.entries?.getOrNull(entryIndex)
            ?.superset?.key
            ?: return
        updateWorkout(workoutIndex) { it.withoutSuperset(key) }
    }

    fun onSupersetDismissed() = _state.update { it.copy(supersetEditor = null) }

    private fun rejectSuperset(dialog: SupersetEditorState, error: SupersetError) {
        _state.update { it.copy(supersetEditor = dialog.copy(error = error)) }
    }

    private fun updateSupersetEditor(transform: (SupersetEditorState) -> SupersetEditorState) {
        _state.update { current ->
            current.supersetEditor?.let { current.copy(supersetEditor = transform(it)) } ?: current
        }
    }

    /**
     * Validates the pair's two rest inputs against the domain rule and, on failure, maps the
     * offending field by probing [Superset.requireValid] itself, so no numeric bound is duplicated
     * here and the two cannot drift. Returns null when the pair is a valid configuration.
     */
    private fun supersetFieldError(transitionSeconds: Int, pairRestSeconds: Int): SupersetError? =
        when {
            !isValidSupersetRest(transitionSeconds, VALID_PAIR_REST_PROBE) ->
                SupersetError.TRANSITION_RANGE
            !isValidSupersetRest(SUPERSET_TRANSITION_PROBE, pairRestSeconds) ->
                SupersetError.PAIR_REST_RANGE
            else -> null
        }

    /** True when the two rest values pass the domain rule; the other field is a known-good probe. */
    private fun isValidSupersetRest(transitionSeconds: Int, pairRestSeconds: Int): Boolean = try {
        Superset(SUPERSET_KEY_PROBE, transitionSeconds, pairRestSeconds).requireValid()
        true
    } catch (_: IllegalArgumentException) {
        false
    }

    /** True when every pair in [entries] still forms one valid two-slot group. */
    private fun supersetsFormValidGroups(entries: List<EditorEntry>): Boolean = try {
        validateSupersets(entries.map { it.exerciseId to it.superset })
        true
    } catch (_: IllegalArgumentException) {
        false
    }

    /**
     * The pair's workout-scoped identity. A pair is created only from the first slot of an unpaired
     * adjacent pair, and no reordering separates a live pair, so two live pairs can never share the
     * key derived from a first slot's position.
     */
    private fun supersetKey(workoutIndex: Int, entryIndex: Int): String =
        "routine-$workoutIndex-$entryIndex"

    fun onSaveEditor() {
        val editor = _state.value.editor ?: return
        viewModelScope.launch {
            try {
                routineActions.save(editor.toDraft())
                _state.update { it.copy(editor = null, picker = null) }
            } catch (error: RoutineTemplateException) {
                _state.update { it.copy(message = error.message) }
            }
        }
    }

    // --- Library actions ---

    fun onDuplicate(template: RoutineTemplate) {
        viewModelScope.launch { routineActions.duplicate(template.id) }
    }

    fun onToggleArchive(template: RoutineTemplate) {
        viewModelScope.launch { routineActions.setArchived(template.id, !template.isArchived) }
    }

    fun onDelete(template: RoutineTemplate) {
        viewModelScope.launch {
            try {
                routineActions.delete(template.id)
            } catch (error: RoutineTemplateException) {
                _state.update { it.copy(message = error.message) }
            }
        }
    }

    // --- Activation dialog ---

    /**
     * Opens the activation dialog for [template] with the saved preferred weekdays, resolved first.
     * The dialog is not built until that read returns, so it never opens on a placeholder set it
     * would then have to correct, and days the user has already toggled cannot be overwritten by a
     * late resolution. Opening it writes nothing and confirmation remains explicit.
     *
     * A newer opening supersedes an older in-flight read, and [onActivationDismissed] cancels one, so
     * neither a superseded read nor a dismissed dialog can be published into the state.
     */
    fun onActivateRequested(template: RoutineTemplate) {
        val workoutCount = template.workouts.size
        activationJob?.cancel()
        activationJob = viewModelScope.launch {
            val weekdays = resolvePreferredWeekdays()
            _state.update {
                val hasActive = it.activeActivation != null
                it.copy(
                    activation = ActivationUiState(
                        templateId = template.id,
                        templateName = template.name,
                        workoutCount = workoutCount,
                        weekdays = weekdays,
                        startEpochDay = todayEpochDay(),
                        hasActiveBlock = hasActive,
                        replaceActive = hasActive
                    ).withPreview(scheduleActions, workoutCount)
                )
            }
        }
    }

    /**
     * The saved preference, or the Mon/Wed/Fri default when it is genuinely undecided. A read that
     * fails resolves to the default too: the dialog only prefills a starting selection, confirmation
     * is always explicit and opening writes nothing, so degrading to the default days lets the user
     * still choose a schedule. Failing the open would block that choice, and an empty set would
     * instead read as "no days" — a different, undecidable-in-UI state. The read goes through
     * `observe()`, which emits off the main dispatcher, so this never blocks one.
     */
    private suspend fun resolvePreferredWeekdays(): Set<DayOfWeek> = try {
        preferredWeekdaysRepository.observe().first() ?: DefaultWeekdays
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Exception) {
        DefaultWeekdays
    }

    fun onActivationModeChanged(mode: ScheduleMode) = updateActivation { it.copy(mode = mode) }

    fun onActivationWeekdayToggled(day: DayOfWeek) = updateActivation { activation ->
        val weekdays = if (day in activation.weekdays) {
            activation.weekdays - day
        } else {
            activation.weekdays + day
        }
        activation.copy(weekdays = weekdays)
    }

    fun onActivationStartTodayChanged(startToday: Boolean) =
        updateActivation { it.copy(startToday = startToday) }

    fun onActivationStartDateChosen(epochDay: Long) =
        updateActivation { it.copy(startEpochDay = epochDay, startToday = false) }

    fun onActivationReplaceActiveChanged(replaceActive: Boolean) =
        updateActivation { it.copy(replaceActive = replaceActive) }

    /** Dismissing cancels an in-flight resolution so it cannot reopen the dialog afterwards. */
    fun onActivationDismissed() {
        activationJob?.cancel()
        _state.update { it.copy(activation = null) }
    }

    fun onConfirmActivation() {
        val activation = _state.value.activation ?: return
        val template = _state.value.templates.firstOrNull { it.id == activation.templateId }
        if (template == null) {
            _state.update { it.copy(activation = null) }
            return
        }
        if (activation.mode == ScheduleMode.WEEKDAY && activation.weekdays.isEmpty()) {
            _state.update { it.copy(activation = activation.copy(error = "weekdays")) }
            return
        }
        viewModelScope.launch {
            try {
                scheduleActions.activate(
                    template,
                    ActivationRequest(
                        name = template.name,
                        startEpochDay = activation.startEpochDay,
                        mode = activation.mode,
                        weekdays = activation.weekdays,
                        templateId = template.id,
                        templateRevision = template.revision,
                        sourcePlanId = template.sourcePlanId,
                        startToday = activation.startToday,
                        replaceActive = activation.replaceActive
                    )
                )
                _state.update { it.copy(activation = null) }
            } catch (error: ScheduleException) {
                _state.update { it.copy(activation = activation.copy(error = error.message)) }
            }
        }
    }

    // --- Active block ---

    fun onFinishBlock() {
        val active = _state.value.activeActivation ?: return
        viewModelScope.launch {
            try {
                scheduleActions.finishBlock(active.id)
            } catch (error: ScheduleException) {
                _state.update { it.copy(message = error.message) }
            }
        }
    }

    fun onCancelBlock() {
        viewModelScope.launch { scheduleActions.cancelActive() }
    }

    fun onRepeatBlock() {
        val active = _state.value.activeActivation ?: return
        viewModelScope.launch { scheduleActions.repeat(active.id, todayEpochDay()) }
    }

    fun onPostpone(occurrenceId: Long, epochDay: Long) {
        viewModelScope.launch {
            val changes = try {
                scheduleActions.previewMove(occurrenceId, epochDay)
            } catch (error: ScheduleException) {
                _state.update { it.copy(message = error.message) }
                return@launch
            }
            _state.update {
                it.copy(pendingPostpone = PendingPostpone(occurrenceId, epochDay, changes))
            }
        }
    }

    fun onConfirmPostpone() {
        val pending = _state.value.pendingPostpone ?: return
        viewModelScope.launch {
            scheduleActions.move(pending.occurrenceId, pending.newEpochDay)
            _state.update { it.copy(pendingPostpone = null) }
        }
    }

    fun onSwitchScheduleMode() {
        val active = _state.value.activeActivation ?: return
        val mode = if (active.mode == ScheduleMode.WEEKDAY) {
            ScheduleMode.SEQUENCE
        } else {
            ScheduleMode.WEEKDAY
        }
        val weekdays = active.weekdays.ifEmpty { DefaultWeekdays }
        val start = todayEpochDay()
        viewModelScope.launch {
            val changes = try {
                scheduleActions.previewSwitchMode(active.id, mode, weekdays, start)
            } catch (error: ScheduleException) {
                _state.update { it.copy(message = error.message) }
                return@launch
            }
            _state.update {
                it.copy(pendingSwitchMode = PendingSwitchMode(mode, weekdays, start, changes))
            }
        }
    }

    fun onConfirmSwitchMode() {
        val active = _state.value.activeActivation ?: return
        val pending = _state.value.pendingSwitchMode ?: return
        viewModelScope.launch {
            scheduleActions.switchMode(
                active.id,
                pending.mode,
                pending.weekdays,
                pending.startEpochDay
            )
            _state.update { it.copy(pendingSwitchMode = null) }
        }
    }

    fun onPendingDismissed() = _state.update {
        it.copy(pendingPostpone = null, pendingSwitchMode = null)
    }

    fun onMessageShown() = _state.update { it.copy(message = null) }

    private fun todayEpochDay(): Long =
        localEpochDay(timeProvider.nowMillis(), timeProvider.utcOffsetMillis())

    private fun updateEditor(transform: (RoutineEditorState) -> RoutineEditorState) {
        _state.update { current ->
            current.editor?.let { current.copy(editor = transform(it)) }
                ?: current
        }
    }

    private fun updateWorkout(index: Int, transform: (EditorWorkout) -> EditorWorkout) =
        updateEditor { editor ->
            if (index !in editor.workouts.indices) {
                editor
            } else {
                editor.copy(
                    workouts = editor.workouts.mapIndexed { i, workout ->
                        if (i == index) transform(workout) else workout
                    }
                )
            }
        }

    private fun updateEntry(
        workoutIndex: Int,
        entryIndex: Int,
        transform: (EditorEntry) -> EditorEntry
    ) = updateWorkout(workoutIndex) { workout ->
        workout.copy(
            entries = workout.entries.mapIndexed { i, entry ->
                if (i == entryIndex) transform(entry) else entry
            }
        )
    }

    private fun updateProgressionPolicyEditor(
        transform: (ProgressionPolicyEditorState) -> ProgressionPolicyEditorState
    ) {
        _state.update { current ->
            current.progressionPolicyEditor?.let {
                current.copy(progressionPolicyEditor = transform(it))
            } ?: current
        }
    }

    private fun updateActivation(transform: (ActivationUiState) -> ActivationUiState) {
        _state.update { current ->
            val activation = current.activation ?: return@update current
            val updated = transform(
                activation
            ).withPreview(scheduleActions, activation.workoutCount)
            current.copy(activation = updated)
        }
    }
}

private fun ActivationUiState.withPreview(
    actions: WorkoutScheduleActions,
    count: Int
): ActivationUiState {
    if (count <= 0) return copy(preview = emptyList())
    val dates = try {
        actions.preview(count, startEpochDay, mode, weekdays, startToday)
    } catch (_: ScheduleException) {
        emptyList()
    }
    return copy(preview = dates)
}

private fun RoutineTemplate.toEditor(
    unit: WeightUnit,
    exerciseNames: Map<String, String>
): RoutineEditorState = RoutineEditorState(
    id = id,
    revision = revision,
    sourcePlanId = sourcePlanId,
    name = name,
    unit = unit,
    workouts = workouts.sortedBy { it.position }.map { workout ->
        EditorWorkout(
            id = workout.id,
            name = workout.name,
            focus = workout.focus,
            entries = workout.entries.sortedBy { it.position }.map { entry ->
                EditorEntry(
                    id = entry.id,
                    exerciseId = entry.exerciseId,
                    name = exerciseNames[entry.exerciseId] ?: entry.exerciseId,
                    sets = entry.sets.toString(),
                    reps = entry.reps?.toString() ?: "",
                    weight = entry.weightKg
                        ?.let { formatWeight(unit.kilogramsToDisplay(it)) }
                        ?: "",
                    loadKind = entry.loadKind,
                    metric = if (entry.durationSeconds != null) {
                        EntryMetric.DURATION
                    } else {
                        EntryMetric.REPETITIONS
                    },
                    durationSeconds = entry.durationSeconds?.toString() ?: "",
                    progressionPolicy = entry.progressionPolicy,
                    setTargets = entry.setTargets,
                    superset = entry.superset
                )
            }
        )
    }
)

private fun RoutineEditorState.toDraft(): RoutineTemplate = RoutineTemplate(
    id = id,
    revision = revision,
    sourcePlanId = sourcePlanId,
    name = name,
    workouts = workouts.mapIndexed { workoutIndex, workout ->
        RoutineWorkout(
            id = workout.id,
            position = workoutIndex,
            name = workout.name,
            focus = workout.focus,
            entries = workout.entries.mapIndexed { entryIndex, entry ->
                RoutineEntry(
                    id = entry.id,
                    position = entryIndex,
                    exerciseId = entry.exerciseId,
                    sets = entry.sets.toIntOrNull() ?: 0,
                    reps = entry.repsValue,
                    durationSeconds = entry.durationValue,
                    weightKg = if (entry.loadKind == LoadKind.BODYWEIGHT) {
                        null
                    } else {
                        parseDisplayedWeight(entry.weight, unit)
                    },
                    loadKind = entry.loadKind,
                    progressionPolicy = entry.progressionPolicy,
                    setTargets = entry.setTargets,
                    superset = entry.superset
                )
            }
        )
    }
)

/** Drops the pair identified by [key] from every slot, leaving the rest of each slot untouched. */
private fun EditorWorkout.withoutSuperset(key: String): EditorWorkout =
    copy(entries = entries.map { if (it.superset?.key == key) it.copy(superset = null) else it })

/**
 * Blank means "no load recorded" (null). A non-blank value must be a finite, non-negative number;
 * anything else is rejected so a malformed entry can never silently clear a prescribed load.
 */
private fun parseDisplayedWeight(input: String, unit: WeightUnit): Double? {
    val trimmed = input.trim()
    if (trimmed.isEmpty()) return null
    val value = trimmed.toDoubleOrNull()
        ?: throw RoutineTemplateException("Weight must be a number")
    if (!value.isFinite() || value < 0.0) {
        throw RoutineTemplateException("Weight must be zero or positive")
    }
    return unit.displayToKilograms(value)
}

/**
 * Validates a candidate policy and, on failure, maps the domain rule's failure back to the offending
 * field. Each field is probed against [SlotProgressionPolicy.requireValid] itself, with a known-valid
 * value standing in for the others, so no numeric bound is duplicated here and the two can't drift.
 * Returns null when the policy is valid.
 */
private fun progressionPolicyFieldError(
    minReps: Int,
    maxReps: Int,
    incrementKg: Double,
    equipmentId: String?
): ProgressionPolicyError? {
    val policy = SlotProgressionPolicy(
        minReps = minReps,
        maxReps = maxReps,
        incrementKg = incrementKg,
        equipmentId = equipmentId
    )
    return try {
        policy.requireValid()
        null
    } catch (_: IllegalArgumentException) {
        when {
            !isValidPolicy(
                SlotProgressionPolicy(
                    minReps = minReps,
                    maxReps = maxReps,
                    incrementKg = VALID_INCREMENT_PROBE_KG
                )
            ) -> ProgressionPolicyError.REPS_RANGE
            !isValidPolicy(
                SlotProgressionPolicy(
                    minReps = SlotProgressionPolicy.MIN_REPS,
                    maxReps = SlotProgressionPolicy.MIN_REPS,
                    incrementKg = incrementKg
                )
            ) -> ProgressionPolicyError.INCREMENT
            else -> ProgressionPolicyError.EQUIPMENT_ID
        }
    }
}

/** A step known to satisfy the domain rule, used only to isolate a rep-range failure. */
private const val VALID_INCREMENT_PROBE_KG: Double = 1.0

private fun isValidPolicy(policy: SlotProgressionPolicy): Boolean = try {
    policy.requireValid()
    true
} catch (_: IllegalArgumentException) {
    false
}

/** A pair key known to satisfy the domain rule, used only to isolate one failing rest field. */
private const val SUPERSET_KEY_PROBE = "probe"

/** Rest values known to satisfy the pair rule, used only to isolate the other failing field. */
private const val VALID_PAIR_REST_PROBE = 120
private const val SUPERSET_TRANSITION_PROBE = 0

/** Values known to satisfy the ordered-prescription rule, used only to isolate one failing field. */
private const val VALID_SETS_TARGETS_PROBE = 3
private const val VALID_REPS_TARGETS_PROBE = 8

/** The outcome of parsing one load field; a blank field is a valid "no load". */
private data class SetTargetLoadInput(val valid: Boolean, val kilograms: Double?)

private fun parseSetTargetLoad(input: String): SetTargetLoadInput {
    val trimmed = input.trim()
    if (trimmed.isEmpty()) return SetTargetLoadInput(valid = true, kilograms = null)
    val value =
        trimmed.toDoubleOrNull() ?: return SetTargetLoadInput(valid = false, kilograms = null)
    return SetTargetLoadInput(valid = true, kilograms = value)
}

private sealed interface SetTargetsParseResult {
    data class Valid(val request: SetPrescriptionRequest) : SetTargetsParseResult
    data class Invalid(val error: SetTargetsError) : SetTargetsParseResult
}

/** Parses the dialog's text fields, or reports the first field whose text cannot become a request. */
private fun parseSetTargetsRequest(editor: SetTargetsEditorState): SetTargetsParseResult {
    val sets = editor.sets.trim().toIntOrNull()
        ?: return SetTargetsParseResult.Invalid(SetTargetsError.SETS_RANGE)
    val startReps = editor.startReps.trim().toIntOrNull()
        ?: return SetTargetsParseResult.Invalid(SetTargetsError.REPS_RANGE)
    val endReps = editor.endReps.trim().toIntOrNull()
        ?: return SetTargetsParseResult.Invalid(SetTargetsError.REPS_RANGE)
    val startLoad = parseSetTargetLoad(editor.startLoadKg)
    if (!startLoad.valid) return SetTargetsParseResult.Invalid(SetTargetsError.LOAD)
    val endLoad = parseSetTargetLoad(editor.endLoadKg)
    if (!endLoad.valid) return SetTargetsParseResult.Invalid(SetTargetsError.LOAD)
    val roundTo = editor.roundToKg.trim().toDoubleOrNull()
        ?: return SetTargetsParseResult.Invalid(SetTargetsError.ROUNDING)
    val rirText = editor.targetRir.trim()
    val rir = if (rirText.isEmpty()) {
        null
    } else {
        rirText.toIntOrNull() ?: return SetTargetsParseResult.Invalid(SetTargetsError.RIR_RANGE)
    }
    return SetTargetsParseResult.Valid(
        SetPrescriptionRequest(
            shape = editor.shape,
            sets = sets,
            startReps = startReps,
            endReps = endReps,
            startLoadKg = startLoad.kilograms,
            endLoadKg = endLoad.kilograms,
            roundToKg = roundTo,
            targetRir = rir
        )
    )
}
