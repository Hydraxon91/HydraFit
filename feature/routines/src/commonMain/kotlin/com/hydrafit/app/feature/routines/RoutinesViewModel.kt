package com.hydrafit.app.feature.routines

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hydrafit.app.core.domain.engine.ExerciseCatalog
import com.hydrafit.app.core.domain.engine.SplitFocus
import com.hydrafit.app.core.domain.routine.RoutineEntry
import com.hydrafit.app.core.domain.routine.RoutineTemplate
import com.hydrafit.app.core.domain.routine.RoutineTemplateActions
import com.hydrafit.app.core.domain.routine.RoutineTemplateException
import com.hydrafit.app.core.domain.routine.RoutineWorkout
import com.hydrafit.app.core.domain.schedule.ActivationRequest
import com.hydrafit.app.core.domain.schedule.ScheduleException
import com.hydrafit.app.core.domain.schedule.ScheduleMode
import com.hydrafit.app.core.domain.schedule.WorkoutScheduleActions
import com.hydrafit.app.core.domain.time.DayOfWeek
import com.hydrafit.app.core.domain.time.TimeProvider
import com.hydrafit.app.core.domain.time.localEpochDay
import com.hydrafit.app.core.domain.unit.WeightUnit
import com.hydrafit.app.core.domain.unit.formatWeight
import com.hydrafit.app.core.userdata.settings.WeightUnitRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class RoutinesViewModel(
    private val routineActions: RoutineTemplateActions,
    private val scheduleActions: WorkoutScheduleActions,
    private val exerciseCatalog: ExerciseCatalog,
    private val timeProvider: TimeProvider,
    private val weightUnitRepository: WeightUnitRepository
) : ViewModel() {

    private val _state = MutableStateFlow(RoutinesUiState())
    val state: StateFlow<RoutinesUiState> = _state.asStateFlow()

    private var weightUnit: WeightUnit = WeightUnit.KG
    private var occurrencesJob: Job? = null

    init {
        viewModelScope.launch {
            routineActions.observe().collect { templates ->
                _state.update { it.copy(templates = templates, isLoading = false) }
            }
        }
        viewModelScope.launch {
            exerciseCatalog.observeAll().collect { catalog ->
                _state.update {
                    it.copy(
                        exercises = catalog.map { exercise ->
                            RoutineExerciseOption(exercise.id, exercise.name)
                        }
                    )
                }
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

    // --- Editor ---

    fun onNewRoutine() {
        _state.update {
            it.copy(
                editor = RoutineEditorState(
                    id = 0,
                    revision = 0,
                    sourcePlanId = null,
                    name = "",
                    workouts = emptyList()
                )
            )
        }
    }

    fun onEditRoutine(template: RoutineTemplate) {
        _state.update { it.copy(editor = template.toEditor(weightUnit)) }
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

    fun onAddExercise(workoutIndex: Int) = _state.update {
        it.copy(picker = ExercisePickerState(workoutIndex = workoutIndex))
    }

    fun onReplaceExercise(workoutIndex: Int, entryId: Long) = _state.update {
        it.copy(picker = ExercisePickerState(workoutIndex = workoutIndex, replaceEntryId = entryId))
    }

    fun onPickerQueryChanged(query: String) = _state.update { current ->
        current.picker?.let { current.copy(picker = it.copy(query = query)) } ?: current
    }

    fun onPickerDismissed() = _state.update { it.copy(picker = null) }

    fun onPickerExerciseSelected(exerciseId: String, name: String) {
        val picker = _state.value.picker ?: return
        updateWorkout(picker.workoutIndex) { workout ->
            val entry = EditorEntry(
                exerciseId = exerciseId,
                name = name,
                sets = "3",
                reps = "8",
                weight = ""
            )
            val entries = if (picker.replaceEntryId == null) {
                workout.entries + entry
            } else {
                workout.entries.map {
                    if (it.id ==
                        picker.replaceEntryId
                    ) {
                        entry.copy(id = it.id)
                    } else {
                        it
                    }
                }
            }
            workout.copy(entries = entries)
        }
        _state.update { it.copy(picker = null) }
    }

    fun onRemoveEntry(workoutIndex: Int, entryIndex: Int) = updateWorkout(workoutIndex) { workout ->
        workout.copy(entries = workout.entries.filterIndexed { i, _ -> i != entryIndex })
    }

    fun onMoveEntry(workoutIndex: Int, entryIndex: Int, delta: Int) =
        updateWorkout(workoutIndex) { workout ->
            val target = entryIndex + delta
            if (target !in workout.entries.indices) {
                workout
            } else {
                workout.copy(
                    entries = workout.entries.toMutableList().apply {
                        add(target, removeAt(entryIndex))
                    }
                )
            }
        }

    fun onEntrySetsChanged(workoutIndex: Int, entryIndex: Int, value: String) =
        updateEntry(workoutIndex, entryIndex) { it.copy(sets = value) }

    fun onEntryRepsChanged(workoutIndex: Int, entryIndex: Int, value: String) =
        updateEntry(workoutIndex, entryIndex) { it.copy(reps = value) }

    fun onEntryWeightChanged(workoutIndex: Int, entryIndex: Int, value: String) =
        updateEntry(workoutIndex, entryIndex) { it.copy(weight = value) }

    fun onSaveEditor() {
        val editor = _state.value.editor ?: return
        viewModelScope.launch {
            try {
                routineActions.save(editor.toDraft(weightUnit))
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

    fun onActivateRequested(template: RoutineTemplate) {
        val workoutCount = template.workouts.size
        _state.update {
            it.copy(
                activation = ActivationUiState(
                    templateId = template.id,
                    templateName = template.name,
                    workoutCount = workoutCount,
                    startEpochDay = todayEpochDay(),
                    replaceActive = it.activeActivation != null
                ).withPreview(scheduleActions, workoutCount)
            )
        }
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

    fun onActivationDismissed() = _state.update { it.copy(activation = null) }

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
        viewModelScope.launch { scheduleActions.move(occurrenceId, epochDay) }
    }

    fun onSwitchScheduleMode() {
        val active = _state.value.activeActivation ?: return
        val mode = if (active.mode ==
            ScheduleMode.WEEKDAY
        ) {
            ScheduleMode.SEQUENCE
        } else {
            ScheduleMode.WEEKDAY
        }
        val weekdays = active.weekdays.ifEmpty { DefaultWeekdays }
        viewModelScope.launch {
            scheduleActions.switchMode(active.id, mode, weekdays, todayEpochDay())
        }
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

private fun RoutineTemplate.toEditor(unit: WeightUnit): RoutineEditorState = RoutineEditorState(
    id = id,
    revision = revision,
    sourcePlanId = sourcePlanId,
    name = name,
    workouts = workouts.sortedBy { it.position }.map { workout ->
        EditorWorkout(
            id = workout.id,
            name = workout.name,
            focus = workout.focus,
            entries = workout.entries.sortedBy { it.position }.map { entry ->
                EditorEntry(
                    id = entry.id,
                    exerciseId = entry.exerciseId,
                    name = entry.exerciseId,
                    sets = entry.sets.toString(),
                    reps = entry.reps.toString(),
                    weight = entry.weightKg
                        ?.let { formatWeight(unit.kilogramsToDisplay(it)) }
                        ?: ""
                )
            }
        )
    }
)

private fun RoutineEditorState.toDraft(unit: WeightUnit): RoutineTemplate = RoutineTemplate(
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
                    reps = entry.reps.toIntOrNull() ?: 0,
                    weightKg = entry.weight.trim().takeIf { it.isNotEmpty() }
                        ?.toDoubleOrNull()
                        ?.let { unit.displayToKilograms(it) }
                )
            }
        )
    }
)
