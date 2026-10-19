package com.hydrafit.app.feature.splitbuilder

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hydrafit.app.core.domain.engine.AcceptedExercise
import com.hydrafit.app.core.domain.engine.AcceptedPlan
import com.hydrafit.app.core.domain.engine.AutomaticSetPrescription
import com.hydrafit.app.core.domain.engine.ExerciseCatalog
import com.hydrafit.app.core.domain.engine.GenerateWeeklySplitUseCase
import com.hydrafit.app.core.domain.engine.ObserveWorkoutPlanInputsUseCase
import com.hydrafit.app.core.domain.engine.PlanBuilderActions
import com.hydrafit.app.core.domain.engine.PlanFailureReason
import com.hydrafit.app.core.domain.engine.PlanGenerationException
import com.hydrafit.app.core.domain.engine.PlanHistoryRepository
import com.hydrafit.app.core.domain.engine.PlanRequest
import com.hydrafit.app.core.domain.engine.PlannerEngineId
import com.hydrafit.app.core.domain.engine.PlannerSupersetPreference
import com.hydrafit.app.core.domain.engine.SetPrescriptionShape
import com.hydrafit.app.core.domain.engine.VolumeExplanationStatus
import com.hydrafit.app.core.domain.engine.WeeklyPlan
import com.hydrafit.app.core.domain.engine.WorkoutPlanInputs
import com.hydrafit.app.core.domain.engine.toWeeklyPlan
import com.hydrafit.app.core.domain.equipment.Exercise
import com.hydrafit.app.core.domain.routine.PrescriptionBounds
import com.hydrafit.app.core.domain.schedule.ActivationRequest
import com.hydrafit.app.core.domain.schedule.ScheduleException
import com.hydrafit.app.core.domain.schedule.ScheduleMode
import com.hydrafit.app.core.domain.time.DayOfWeek
import com.hydrafit.app.core.userdata.settings.EnginePreferenceRepository
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SplitBuilderViewModel(
    private val observeWorkoutPlanInputs: ObserveWorkoutPlanInputsUseCase,
    private val generateWeeklySplit: GenerateWeeklySplitUseCase,
    private val planBuilderActions: PlanBuilderActions,
    private val planHistory: PlanHistoryRepository,
    private val exerciseCatalog: ExerciseCatalog,
    private val enginePreference: EnginePreferenceRepository
) : ViewModel() {

    private val _state = MutableStateFlow(SplitBuilderUiState())
    val state: StateFlow<SplitBuilderUiState> = _state.asStateFlow()

    private val setsPerExercise = MutableStateFlow<Int?>(null)
    private val accessorySetsPerExercise = MutableStateFlow<Int?>(null)

    /** The confirmed planning dates; empty keeps the undated planning behavior. */
    private val planningDates = MutableStateFlow<List<Long>>(emptyList())

    /** The applied automatic per-set opt-in; null means no automatic ordered prescription. */
    private val automaticSetPrescription = MutableStateFlow<AutomaticSetPrescription?>(null)
    private var generationJob: Job? = null

    /** Fingerprint of the request that produced the currently shown plan; null until one exists. */
    private var lastGeneratedFingerprint: Int? = null

    /** The accepted plan currently rendered, so a swap can target its exact id and slot. */
    private var shownPlan: AcceptedPlan? = null

    /** The latest plan-generation request, reused as the ranking context for a swap. */
    private var lastRequest: PlanRequest? = null

    init {
        viewModelScope.launch {
            planHistory.observeHistory().collectLatest { history ->
                _state.update { it.copy(history = history) }
            }
        }
        viewModelScope.launch {
            planBuilderActions.observeActiveActivation().collectLatest { active ->
                _state.update { it.copy(hasActiveBlock = active != null) }
            }
        }
        viewModelScope.launch {
            val accepted = planHistory.latest()
            if (accepted != null) showAccepted(accepted)
            var previous: WorkoutPlanInputs? = null
            observeWorkoutPlanInputs(
                setsPerExercise = setsPerExercise,
                accessorySetsPerExercise = accessorySetsPerExercise,
                workoutEpochDays = planningDates,
                automaticSetPrescription = automaticSetPrescription
            ).collect { inputs ->
                val prior = previous
                previous = inputs
                lastRequest = inputs.request
                val settingsChanged = prior != null &&
                    (
                        prior.exercisePreferences != inputs.exercisePreferences ||
                            prior.exerciseExclusions != inputs.exerciseExclusions
                        )
                val equipmentChanged = prior != null &&
                    prior.equipmentContext != inputs.equipmentContext
                // A confirmed set the planner could not use reads as "no dates", so this also covers
                // a frequency or clock change that invalidated the confirmed dates.
                val datesChanged = prior != null &&
                    prior.request.workoutEpochDays != inputs.request.workoutEpochDays
                // The parameters the user sets explicitly, as opposed to a source-data refresh (a
                // logged set moves fatigue and load inputs without touching these).
                val planParametersChanged = prior != null &&
                    (
                        prior.request.daysPerWeek != inputs.request.daysPerWeek ||
                            prior.request.setsPerExercise != inputs.request.setsPerExercise ||
                            prior.request.accessorySetsPerExercise !=
                            inputs.request.accessorySetsPerExercise ||
                            prior.request.automaticSetPrescription !=
                            inputs.request.automaticSetPrescription
                        )
                if (equipmentChanged) {
                    generationJob?.cancel()
                    lastGeneratedFingerprint = null
                    _state.update {
                        it.copy(
                            canRegenerate = true,
                            isLoading = false,
                            appliedPlanningDates = inputs.request.workoutEpochDays
                        )
                    }
                } else if (settingsChanged) {
                    // Standing exercise settings affect future picks, never the displayed plan.
                    // An in-flight generation also retains the request it started with.
                    _state.update {
                        it.copy(
                            canRegenerate = true,
                            appliedPlanningDates = inputs.request.workoutEpochDays
                        )
                    }
                } else if (datesChanged) {
                    // The date context moved: keep the displayed plan, cancel any stale in-flight
                    // generation and let the explicit Regenerate apply the new dates.
                    generationJob?.cancel()
                    _state.update {
                        it.copy(
                            canRegenerate = true,
                            isLoading = false,
                            appliedPlanningDates = inputs.request.workoutEpochDays
                        )
                    }
                } else if (_state.value.isPlanAccepted && !planParametersChanged) {
                    // An accepted plan is frozen: an ordinary source refresh (a logged set moves
                    // fatigue and load inputs) keeps it displayed and accepted, and Regenerate
                    // applies the new inputs explicitly.
                    _state.update {
                        it.copy(
                            canRegenerate = true,
                            appliedPlanningDates = inputs.request.workoutEpochDays
                        )
                    }
                } else if (prior != null || accepted == null) {
                    startGeneration(inputs)
                } else {
                    _state.update {
                        it.copy(appliedPlanningDates = inputs.request.workoutEpochDays)
                    }
                }
            }
        }
    }

    fun onDaysPerWeekSelected(daysPerWeek: Int) {
        _state.update { it.copy(daysPerWeek = daysPerWeek) }
        viewModelScope.launch { enginePreference.setDaysPerWeek(daysPerWeek) }
    }

    fun onSetsPerExerciseChanged(setsPerExercise: Int) {
        this.setsPerExercise.value = setsPerExercise
        _state.update { it.copy(setsPerExercise = setsPerExercise) }
    }

    fun onAccessorySetsPerExerciseChanged(setsPerExercise: Int) {
        this.accessorySetsPerExercise.value = setsPerExercise
        _state.update { it.copy(accessorySetsPerExercise = setsPerExercise) }
    }

    // --- Curated planner superset pairing ---

    fun onPlannerSupersetEnabledChanged(enabled: Boolean) {
        _state.update { it.copy(plannerSupersetEnabled = enabled, canRegenerate = true) }
    }

    fun onPlannerSupersetTransitionChanged(seconds: String) {
        _state.update {
            it.copy(
                plannerSupersetTransitionSeconds = seconds.filterDigits(),
                canRegenerate = true
            )
        }
    }

    fun onPlannerSupersetPairRestChanged(seconds: String) {
        _state.update {
            it.copy(
                plannerSupersetPairRestSeconds = seconds.filterDigits(),
                canRegenerate = true
            )
        }
    }

    // --- Automatic per-set opt-in ---

    /** Opens the automatic-prescription dialog, seeded from the applied opt-in when one exists. */
    fun onAutomaticSetPrescriptionRequested() {
        _state.update { current ->
            val applied = current.automaticSetPrescription
            current.copy(
                automaticSetPrescriptionDialog = AutomaticSetPrescriptionState(
                    shape = applied?.shape ?: SetPrescriptionShape.STRAIGHT,
                    minReps = applied?.minReps?.toString() ?: "",
                    maxReps = applied?.maxReps?.toString() ?: "",
                    targetRir = applied?.targetRir?.toString() ?: ""
                )
            )
        }
    }

    fun onAutomaticSetPrescriptionShapeChanged(shape: SetPrescriptionShape) =
        updateAutomaticSetPrescriptionDialog { it.copy(shape = shape, error = null) }

    fun onAutomaticSetPrescriptionMinRepsChanged(value: String) =
        updateAutomaticSetPrescriptionDialog { it.copy(minReps = value, error = null) }

    fun onAutomaticSetPrescriptionMaxRepsChanged(value: String) =
        updateAutomaticSetPrescriptionDialog { it.copy(maxReps = value, error = null) }

    fun onAutomaticSetPrescriptionRirChanged(value: String) =
        updateAutomaticSetPrescriptionDialog { it.copy(targetRir = value, error = null) }

    /** Applies the opt-in, or keeps the dialog open with the offending field; nothing else changes. */
    fun onAutomaticSetPrescriptionApply() {
        val dialog = _state.value.automaticSetPrescriptionDialog ?: return
        val minReps = dialog.minReps.trim().toIntOrNull()
        val maxReps = dialog.maxReps.trim().toIntOrNull()
        if (minReps == null || maxReps == null) {
            rejectAutomaticSetPrescription(AutomaticSetPrescriptionError.REPS_RANGE)
            return
        }
        val rirText = dialog.targetRir.trim()
        val rir = if (rirText.isEmpty()) null else rirText.toIntOrNull()
        if (rirText.isNotEmpty() && rir == null) {
            rejectAutomaticSetPrescription(AutomaticSetPrescriptionError.RIR_RANGE)
            return
        }
        val candidate = AutomaticSetPrescription(
            shape = dialog.shape,
            minReps = minReps,
            maxReps = maxReps,
            targetRir = rir
        )
        val error = automaticSetPrescriptionFieldError(candidate)
        if (error != null) {
            rejectAutomaticSetPrescription(error)
            return
        }
        automaticSetPrescription.value = candidate
        _state.update {
            it.copy(
                automaticSetPrescription = candidate,
                automaticSetPrescriptionDialog = null
            )
        }
    }

    /** Turns automatic generation off; the next generation carries no automatic prescription. */
    fun onAutomaticSetPrescriptionCleared() {
        automaticSetPrescription.value = null
        _state.update {
            it.copy(automaticSetPrescription = null, automaticSetPrescriptionDialog = null)
        }
    }

    fun onAutomaticSetPrescriptionDismissed() =
        _state.update { it.copy(automaticSetPrescriptionDialog = null) }

    private fun updateAutomaticSetPrescriptionDialog(
        transform: (AutomaticSetPrescriptionState) -> AutomaticSetPrescriptionState
    ) {
        _state.update { current ->
            val dialog = current.automaticSetPrescriptionDialog ?: return@update current
            current.copy(automaticSetPrescriptionDialog = transform(dialog))
        }
    }

    private fun rejectAutomaticSetPrescription(error: AutomaticSetPrescriptionError) =
        updateAutomaticSetPrescriptionDialog { it.copy(error = error) }

    /**
     * Maps a rejected candidate back to its offending field by probing each field against
     * [AutomaticSetPrescription.requireValid] with the others replaced by known-valid values, so no
     * numeric bound is duplicated here. Only called after a candidate was rejected.
     */
    private fun automaticSetPrescriptionFieldError(
        candidate: AutomaticSetPrescription
    ): AutomaticSetPrescriptionError? {
        if (isValidAutomaticSetPrescription(candidate)) return null
        val validReps = AutomaticSetPrescription(
            shape = candidate.shape,
            minReps = VALID_AUTOMATIC_MIN_REPS,
            maxReps = VALID_AUTOMATIC_MAX_REPS,
            targetRir = null
        )
        return when {
            !isValidAutomaticSetPrescription(
                validReps.copy(minReps = candidate.minReps, maxReps = candidate.maxReps)
            ) -> AutomaticSetPrescriptionError.REPS_RANGE
            else -> AutomaticSetPrescriptionError.RIR_RANGE
        }
    }

    private fun isValidAutomaticSetPrescription(prescription: AutomaticSetPrescription): Boolean =
        try {
            prescription.requireValid()
            true
        } catch (_: IllegalArgumentException) {
            false
        }

    /** Opens the planning-date editor, seeded from the confirmed dates or today's weekday pattern. */
    fun onPlanningDatesRequested() {
        _state.update { current ->
            val confirmed = current.planningDates
            val seeded = if (confirmed.dates.isNotEmpty()) {
                confirmed
            } else {
                PlanningDatesState(
                    weekdays = DayOfWeek.entries.take(current.daysPerWeek).toSet(),
                    startEpochDay = planBuilderActions.todayEpochDay()
                )
            }
            current.copy(
                planningDatesDialog = seeded.copy(error = null)
                    .withPlanningPreview(planBuilderActions, current.daysPerWeek)
            )
        }
    }

    fun onPlanningDatesWeekdayToggled(day: DayOfWeek) = updatePlanningDatesDialog { dialog ->
        dialog.copy(
            weekdays = if (day in dialog.weekdays) dialog.weekdays - day else dialog.weekdays + day
        )
    }

    fun onPlanningDatesStartTodayChanged(startToday: Boolean) =
        updatePlanningDatesDialog { it.copy(startToday = startToday) }

    fun onPlanningDatesStartDateChosen(epochDay: Long) =
        updatePlanningDatesDialog { it.copy(startEpochDay = epochDay, startToday = false) }

    /** Moves the whole planned window one week later, keeping the chosen weekdays. */
    fun onPlanningDatesShiftedOneWeek() =
        updatePlanningDatesDialog { it.copy(startEpochDay = it.startEpochDay + 7) }

    fun onPlanningDatesDialogDismissed() = _state.update { it.copy(planningDatesDialog = null) }

    /** Returns to the undated planning behavior; the displayed plan is kept until Regenerate. */
    fun onPlanningDatesCleared() {
        planningDates.value = emptyList()
        _state.update {
            it.copy(planningDates = PlanningDatesState(), planningDatesDialog = null)
        }
    }

    /** Confirms one date per workout; an unusable set stays in the editor with its reason. */
    fun onPlanningDatesConfirmed() {
        val current = _state.value
        val dialog = current.planningDatesDialog ?: return
        val dates = dialog.preview.filterNotNull()
        val error = when {
            dialog.weekdays.size != current.daysPerWeek || dates.size != current.daysPerWeek ->
                "frequency"
            dates.firstOrNull()?.let { it < planBuilderActions.todayEpochDay() } == true -> "past"
            else -> null
        }
        if (error != null) {
            _state.update { it.copy(planningDatesDialog = dialog.copy(error = error)) }
            return
        }
        planningDates.value = dates
        _state.update {
            it.copy(
                planningDates = dialog.copy(dates = dates, preview = dates, error = null),
                planningDatesDialog = null,
                appliedPlanningDates = dates
            )
        }
    }

    private fun updatePlanningDatesDialog(transform: (PlanningDatesState) -> PlanningDatesState) {
        _state.update { current ->
            val dialog = current.planningDatesDialog ?: return@update current
            current.copy(
                planningDatesDialog = transform(dialog)
                    .withPlanningPreview(planBuilderActions, current.daysPerWeek)
            )
        }
    }

    fun refresh() {
        viewModelScope.launch { startGeneration(currentInputs()) }
    }

    /** Re-read expiry and ranking context on resume without generating or replacing a plan. */
    fun refreshContext() {
        viewModelScope.launch {
            val current = currentInputs()
            val previous = lastRequest
            lastRequest = current.request
            val datesChanged = previous != null &&
                previous.workoutEpochDays != current.request.workoutEpochDays
            if (datesChanged) {
                // A confirmed date may have passed, or the frequency may have changed: an in-flight
                // result would be planned for a date context that no longer exists.
                generationJob?.cancel()
            }
            if (previous != current.request) {
                _state.update {
                    it.copy(
                        canRegenerate = true,
                        isLoading = if (datesChanged) false else it.isLoading,
                        appliedPlanningDates = current.request.workoutEpochDays
                    )
                }
            }
        }
    }

    private suspend fun currentInputs(): WorkoutPlanInputs = observeWorkoutPlanInputs(
        setsPerExercise = setsPerExercise,
        accessorySetsPerExercise = accessorySetsPerExercise,
        workoutEpochDays = planningDates,
        automaticSetPrescription = automaticSetPrescription
    ).first()

    private fun startGeneration(inputs: WorkoutPlanInputs) {
        lastRequest = inputs.request
        generationJob?.cancel()
        generationJob = viewModelScope.launch { generate(inputs) }
    }

    fun onAcceptPlan() {
        val plan = _state.value.plan ?: return
        // A draft planned for other dates than the ones now in force must be regenerated first.
        if (_state.value.draftContextStale) return
        viewModelScope.launch {
            planBuilderActions.accept(plan)
            lastGeneratedFingerprint = null
            // The just-accepted plan is the persisted latest; remember it so a swap can target it.
            shownPlan = planHistory.latest()
            _state.update {
                it.copy(isPlanAccepted = true, canRegenerate = true, draftDates = null)
            }
        }
    }

    /** Opens the schedule dialog so the generated plan can be started as a training block. */
    fun onScheduleRequested() {
        val plan = _state.value.plan ?: return
        _state.update { current ->
            val dates = current.planningDates
            // Seed the block schedule from the same configuration that shaped the plan's dates, so
            // the two do not silently disagree; a plan whose weekday count differs keeps the default.
            val seed = dates.takeIf { it.dates.isNotEmpty() && it.weekdays.size == plan.days.size }
            current.copy(
                scheduleDialog = SplitScheduleDialogState(
                    weekdays = seed?.weekdays ?: DayOfWeek.entries.take(plan.days.size).toSet(),
                    startEpochDay = seed?.startEpochDay ?: planBuilderActions.todayEpochDay(),
                    startToday = seed?.startToday ?: true,
                    hasActiveBlock = current.hasActiveBlock,
                    replaceActive = current.hasActiveBlock
                ).withPreview(planBuilderActions, plan.days.size)
            )
        }
    }

    fun onScheduleModeChanged(mode: ScheduleMode) = updateScheduleDialog { it.copy(mode = mode) }

    fun onScheduleWeekdayToggled(day: DayOfWeek) = updateScheduleDialog { dialog ->
        val weekdays = if (day in dialog.weekdays) {
            dialog.weekdays - day
        } else {
            dialog.weekdays + day
        }
        dialog.copy(weekdays = weekdays)
    }

    fun onScheduleStartTodayChanged(startToday: Boolean) =
        updateScheduleDialog { it.copy(startToday = startToday) }

    fun onScheduleStartDateChosen(epochDay: Long) =
        updateScheduleDialog { it.copy(startEpochDay = epochDay, startToday = false) }

    fun onScheduleReplaceActiveChanged(replaceActive: Boolean) =
        updateScheduleDialog { it.copy(replaceActive = replaceActive) }

    fun onScheduleDismissed() = _state.update { it.copy(scheduleDialog = null) }

    /** Accepts (if needed) and starts the shown plan as an active block with the chosen schedule. */
    fun onConfirmSchedule() {
        val plan = _state.value.plan ?: return
        val dialog = _state.value.scheduleDialog ?: return
        if (dialog.mode == ScheduleMode.WEEKDAY && dialog.weekdays.size != plan.days.size) {
            _state.update { it.copy(scheduleDialog = dialog.copy(error = "frequency")) }
            return
        }
        // A context refresh may have invalidated the confirmed dates since the dialog opened;
        // re-check against the current request so Confirm cannot activate a stale date context.
        if (_state.value.draftContextStale || blockDatesDifferFromTheDraft(dialog)) {
            _state.update { it.copy(scheduleDialog = dialog.copy(error = "dates")) }
            return
        }
        val request = ActivationRequest(
            name = "Generated plan",
            startEpochDay = dialog.startEpochDay,
            mode = dialog.mode,
            weekdays = dialog.weekdays,
            startToday = dialog.startToday,
            replaceActive = dialog.replaceActive
        )
        viewModelScope.launch {
            try {
                val accepted = shownPlan
                if (_state.value.isPlanAccepted && accepted != null) {
                    planBuilderActions.scheduleAcceptedPlan(accepted, request)
                } else {
                    planBuilderActions.acceptAndSchedule(plan, request)
                }
                lastGeneratedFingerprint = null
                shownPlan = planHistory.latest()
                _state.update {
                    it.copy(
                        isPlanAccepted = true,
                        canRegenerate = true,
                        scheduleDialog = null,
                        draftDates = null
                    )
                }
            } catch (error: ScheduleException) {
                _state.update { state ->
                    state.copy(scheduleDialog = state.scheduleDialog?.copy(error = error.message))
                }
            }
        }
    }

    /**
     * A draft planned for confirmed dates must be scheduled on those same dates; otherwise the block
     * would claim a date context the plan was never generated for. An accepted plan keeps its own
     * frozen context, and an undated draft keeps the existing undated scheduling behavior.
     */
    private fun blockDatesDifferFromTheDraft(dialog: SplitScheduleDialogState): Boolean {
        val state = _state.value
        val draftDates = state.draftDates ?: return false
        if (state.isPlanAccepted || draftDates.isEmpty()) return false
        return dialog.mode != ScheduleMode.WEEKDAY || dialog.preview.filterNotNull() != draftDates
    }

    /** Copies the shown plan into a new editable routine without accepting the plan. */
    fun onSaveAsRoutine(name: String) {
        val plan = _state.value.plan ?: return
        viewModelScope.launch {
            val accepted = shownPlan?.takeIf { _state.value.isPlanAccepted }
            if (accepted != null) {
                planBuilderActions.saveAcceptedPlanAsRoutine(accepted, name)
            } else {
                // Saving a routine is not accepting a plan: no accepted-plan history is written.
                planBuilderActions.saveDraftPlanAsRoutine(plan, name)
            }
            // Signal the confirmation; the screen shows it and offers to open the Routines tab.
            _state.update { it.copy(routineSaved = true) }
        }
    }

    /** Clears the one-shot save confirmation after the screen has shown it. */
    fun onRoutineSavedShown() {
        _state.update { it.copy(routineSaved = false) }
    }

    private fun updateScheduleDialog(
        transform: (SplitScheduleDialogState) -> SplitScheduleDialogState
    ) {
        _state.update { current ->
            val dialog = current.scheduleDialog ?: return@update current
            val plan = current.plan ?: return@update current
            current.copy(
                scheduleDialog = transform(dialog).withPreview(planBuilderActions, plan.days.size)
            )
        }
    }

    /** Opens the candidate dialog for one slot of the shown accepted plan. */
    fun onSwapRequested(dayIndex: Int, position: Int) {
        val plan = shownPlan ?: return
        viewModelScope.launch {
            val request = currentInputs().request
            lastRequest = request
            val candidates = planBuilderActions.swapCandidates(plan, dayIndex, position, request)
            _state.update {
                it.copy(
                    swapDialogOpen = true,
                    swapTargetDayIndex = dayIndex,
                    swapTargetPosition = position,
                    swapCandidates = candidates,
                    swapNoCandidates = false
                )
            }
        }
    }

    /** Applies a chosen replacement, or flags the slot when the candidate is no longer valid. */
    fun onSwapCandidateSelected(exerciseId: String) {
        val plan = shownPlan ?: return
        val dayIndex = _state.value.swapTargetDayIndex ?: return
        val position = _state.value.swapTargetPosition ?: return
        viewModelScope.launch {
            val request = currentInputs().request
            lastRequest = request
            val updated = planBuilderActions.substitute(
                plan,
                dayIndex,
                position,
                request,
                exerciseId
            )
            if (updated == null) {
                _state.update { it.copy(swapNoCandidates = true) }
                return@launch
            }
            val swapped = plan.replacingExercise(dayIndex, position, updated)
            shownPlan = swapped
            _state.update {
                it.copy(
                    plan = swapped.toWeeklyPlan(),
                    exerciseNames = it.exerciseNames + (updated.exerciseId to updated.name),
                    swapDialogOpen = false,
                    swapTargetDayIndex = null,
                    swapTargetPosition = null,
                    swapCandidates = emptyList(),
                    swapNoCandidates = false,
                    volumeExplanationInvalidated = true
                )
            }
        }
    }

    /** Closes the swap dialog without changing the plan. */
    fun onSwapDialogDismissed() {
        _state.update {
            it.copy(
                swapDialogOpen = false,
                swapTargetDayIndex = null,
                swapTargetPosition = null,
                swapCandidates = emptyList(),
                swapNoCandidates = false
            )
        }
    }

    /** Opens the measure dialog for one slot of the shown plan, seeded from its current measure. */
    fun onMeasureRequested(dayIndex: Int, position: Int) {
        val slot = _state.value.plan?.days
            ?.firstOrNull { it.dayIndex == dayIndex }
            ?.exercises
            ?.getOrNull(position) ?: return
        val timed = slot.durationSeconds != null
        _state.update {
            it.copy(
                measureDialog = MeasureDialogState(
                    dayIndex = dayIndex,
                    position = position,
                    metric = if (timed) MeasureMetric.SECONDS else MeasureMetric.REPS,
                    value = (slot.durationSeconds ?: slot.reps)?.toString().orEmpty()
                )
            )
        }
    }

    fun onMeasureMetricChanged(metric: MeasureMetric) {
        _state.update { current ->
            val dialog = current.measureDialog ?: return@update current
            current.copy(measureDialog = dialog.copy(metric = metric, error = null))
        }
    }

    /** Keeps only digits, so the field can never carry a sign, separator or decimal point. */
    fun onMeasureValueChanged(value: String) {
        _state.update { current ->
            val dialog = current.measureDialog ?: return@update current
            current.copy(
                measureDialog = dialog.copy(value = value.filter(Char::isDigit), error = null)
            )
        }
    }

    fun onMeasureDialogDismissed() {
        _state.update { it.copy(measureDialog = null) }
    }

    /**
     * Applies the dialog's measure. Invalid input writes nothing and shows a specific reason. A draft
     * edit is state-only and carries into acceptance; an accepted-plan edit persists in place, like a
     * swap, and keeps the plan's id and acceptance time.
     */
    fun onMeasureConfirmed() {
        val current = _state.value
        val dialog = current.measureDialog ?: return
        val entered = dialog.value.toIntOrNull()
        val repBounds = PrescriptionBounds.MIN_REPS..PrescriptionBounds.MAX_REPS
        val valid = when (dialog.metric) {
            MeasureMetric.REPS -> entered != null && entered in repBounds
            MeasureMetric.SECONDS -> entered != null && entered > 0
        }
        if (!valid) {
            // A digits-only field that cannot be parsed as an Int is a value beyond the recordable
            // range, not a missing one; an empty field is simply not a positive count yet.
            val error = when {
                dialog.metric == MeasureMetric.REPS -> MeasureError.REPS_RANGE
                dialog.value.isNotEmpty() && entered == null -> MeasureError.SECONDS_TOO_LARGE
                else -> MeasureError.SECONDS_RANGE
            }
            _state.update { it.copy(measureDialog = dialog.copy(error = error)) }
            return
        }
        val value = requireNotNull(entered)
        val reps = if (dialog.metric == MeasureMetric.REPS) value else null
        val durationSeconds = if (dialog.metric == MeasureMetric.SECONDS) value else null
        val accepted = shownPlan?.takeIf { current.isPlanAccepted }
        if (accepted == null) {
            _state.update { state ->
                state.copy(
                    plan = state.plan?.withMeasure(
                        dialog.dayIndex,
                        dialog.position,
                        reps,
                        durationSeconds
                    ),
                    measureDialog = null
                )
            }
            return
        }
        viewModelScope.launch {
            val updated = planBuilderActions.setMeasure(
                accepted,
                dialog.dayIndex,
                dialog.position,
                reps,
                durationSeconds
            )
            if (updated == null) {
                // The slot no longer exists: nothing was written, so close without a change.
                _state.update { it.copy(measureDialog = null) }
                return@launch
            }
            val edited = accepted.withMeasure(dialog.dayIndex, dialog.position, updated)
            shownPlan = edited
            _state.update { it.copy(plan = edited.toWeeklyPlan(), measureDialog = null) }
        }
    }

    fun onViewAcceptedPlan(accepted: AcceptedPlan) {
        viewModelScope.launch { showAccepted(accepted) }
    }

    fun onDeletePlan(accepted: AcceptedPlan) {
        viewModelScope.launch {
            val wasLatest = _state.value.history.firstOrNull()?.id == accepted.id
            planHistory.delete(accepted.id)
            if (wasLatest) {
                // The deleted plan was the active week; clear the shown plan and regenerate so the
                // screen reflects the rewound week instead of a plan that no longer exists.
                _state.update { it.copy(plan = null, isPlanAccepted = false) }
                refresh()
            }
        }
    }

    private suspend fun showAccepted(accepted: AcceptedPlan) {
        val snapshotNames = accepted.days
            .flatMap { day -> day.exercises }
            .associate { it.exerciseId to it.name }
        val catalogNames = exerciseCatalog.all().associate { it.id to it.name }
        val exercises = accepted.days.flatMap { it.exercises }
        lastGeneratedFingerprint = null
        shownPlan = accepted
        _state.update {
            it.copy(
                plan = accepted.toWeeklyPlan(),
                // A persisted plan carries no date provenance: never claim it used the dates now in force.
                draftDates = null,
                exerciseNames = snapshotNames + catalogNames,
                isLoading = false,
                isPlanAccepted = true,
                canRegenerate = true,
                hasError = false,
                isTransientError = false,
                errorDetail = null,
                fallbackReason = null,
                volumeExplanationInvalidated = accepted.volumeExplanationStatus ==
                    VolumeExplanationStatus.INVALIDATED_BY_SUBSTITUTION,
                requestedEngine = accepted.engine,
                daysPerWeek = accepted.days.size,
                setsPerExercise = exercises.firstOrNull { exercise ->
                    exercise.movementPattern.isCompound
                }?.sets ?: it.setsPerExercise,
                accessorySetsPerExercise = exercises.firstOrNull { exercise ->
                    !exercise.movementPattern.isCompound
                }?.sets ?: it.accessorySetsPerExercise
            )
        }
    }

    private suspend fun generate(inputs: WorkoutPlanInputs) {
        val request = inputs.request.copy(
            plannerSupersetPreference = plannerSupersetPreference(
                enabled = _state.value.plannerSupersetEnabled,
                transitionSeconds = _state.value.plannerSupersetTransitionSeconds,
                pairRestSeconds = _state.value.plannerSupersetPairRestSeconds
            )
        )
        val fingerprint = fingerprintOf(request, exerciseCatalog.all())
        currentCoroutineContext().ensureActive()
        val canRegenerate = inputs.requestedEngine != PlannerEngineId.DETERMINISTIC ||
            fingerprint != lastGeneratedFingerprint
        shownPlan = null
        _state.update {
            it.copy(
                plan = null,
                isLoading = true,
                isPlanAccepted = false,
                canRegenerate = canRegenerate,
                hasError = false,
                isTransientError = false,
                errorDetail = null,
                failureReason = null,
                fallbackReason = null,
                volumeExplanationInvalidated = false,
                daysPerWeek = request.daysPerWeek,
                setsPerExercise = request.setsPerExercise,
                accessorySetsPerExercise = request.accessorySetsPerExercise,
                requestedEngine = inputs.requestedEngine,
                appliedPlanningDates = request.workoutEpochDays,
                draftDates = null
            )
        }
        try {
            val plan = generateWeeklySplit(request)
            currentCoroutineContext().ensureActive()
            // Engine preflight diagnoses hard eligibility. Empty output after that check does not
            // prove an exclusion/equipment cause (e.g. every candidate may be sore).
            if (plan.days.all { it.exercises.isEmpty() }) {
                _state.update {
                    it.copy(
                        plan = null,
                        isLoading = false,
                        hasError = true,
                        isTransientError = false,
                        errorDetail = null,
                        failureReason = PlanFailureReason.NO_USABLE_EXERCISES
                    )
                }
                return
            }
            val catalog = exerciseCatalog.all()
            currentCoroutineContext().ensureActive()
            val names = catalog.associate { it.id to it.name }
            lastGeneratedFingerprint = fingerprint
            // The plan now matches the current inputs, so a repeat tap would produce the same
            // result: re-lock the button for the deterministic engine.
            _state.update {
                it.copy(
                    plan = plan,
                    exerciseNames = names,
                    isLoading = false,
                    // Provenance for the draft: these are the dates its per-day fatigue was built for.
                    draftDates = request.workoutEpochDays,
                    canRegenerate = inputs.requestedEngine != PlannerEngineId.DETERMINISTIC ||
                        lastRequest?.let { fingerprintOf(it, catalog) != fingerprint } == true,
                    volumeExplanationInvalidated = false,
                    // Gemini only returns a fallback plan after a sanitize reject; any other failure
                    // throws, so a deterministic result for a Gemini request means an unusable reply.
                    fallbackReason = if (
                        plan.engine != inputs.requestedEngine &&
                        inputs.requestedEngine == PlannerEngineId.GEMINI_API
                    ) {
                        PlanFailureReason.INVALID_RESPONSE
                    } else {
                        null
                    }
                )
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (failure: PlanGenerationException) {
            currentCoroutineContext().ensureActive()
            _state.update {
                it.copy(
                    plan = null,
                    isLoading = false,
                    hasError = true,
                    isTransientError = failure.transient,
                    errorDetail = failure.message,
                    failureReason = failure.reason
                )
            }
        } catch (failure: Exception) {
            currentCoroutineContext().ensureActive()
            _state.update {
                it.copy(
                    plan = null,
                    isLoading = false,
                    hasError = true,
                    isTransientError = false,
                    errorDetail = failure.message,
                    failureReason = PlanFailureReason.UNKNOWN
                )
            }
        }
    }

    /** Returns a copy of this plan with one slot's exercise replaced. */
    private fun AcceptedPlan.replacingExercise(
        dayIndex: Int,
        position: Int,
        replacement: AcceptedExercise
    ): AcceptedPlan = copy(
        armCoverage = emptyList(),
        volumeAttribution = null,
        volumeExplanationInvalidated = true,
        days = days.map { day ->
            if (day.dayIndex != dayIndex) {
                day
            } else {
                day.copy(
                    exercises = day.exercises.mapIndexed { index, exercise ->
                        if (index == position) replacement else exercise
                    }
                )
            }
        }
    )

    /** Returns a copy of this draft with one slot's measure replaced; every other slot is untouched. */
    private fun WeeklyPlan.withMeasure(
        dayIndex: Int,
        position: Int,
        reps: Int?,
        durationSeconds: Int?
    ): WeeklyPlan = copy(
        days = days.map { day ->
            if (day.dayIndex != dayIndex) {
                day
            } else {
                day.copy(
                    exercises = day.exercises.mapIndexed { index, exercise ->
                        if (index == position) {
                            exercise.copy(reps = reps, durationSeconds = durationSeconds)
                        } else {
                            exercise
                        }
                    }
                )
            }
        }
    )

    /**
     * Returns a copy of this accepted plan with one slot's measure replaced. A measure edit changes
     * only how the work is measured, so the frozen volume assessment and the slot's recorded reasons
     * are deliberately left in place, unlike [replacingExercise], which replaces the exercise.
     */
    private fun AcceptedPlan.withMeasure(
        dayIndex: Int,
        position: Int,
        replacement: AcceptedExercise
    ): AcceptedPlan = copy(
        days = days.map { day ->
            if (day.dayIndex != dayIndex) {
                day
            } else {
                day.copy(
                    exercises = day.exercises.mapIndexed { index, exercise ->
                        if (index == position) replacement else exercise
                    }
                )
            }
        }
    )

    /**
     * Fingerprint of the inputs that shape a deterministic plan. `nowMillis` is excluded because the
     * engine never reads it directly, and fatigue is quantized because it decays with wall-clock time
     * — a raw comparison would report "changed" on every tap. The catalog is folded in so editing an
     * exercise (equipment/muscles/pattern/custom) re-enables Regenerate even though the plan sources
     * do not observe the catalog.
     */
    private fun fingerprintOf(request: PlanRequest, catalog: List<Exercise>): Int {
        val fatigue = request.muscleFatigue.entries
            .sortedBy { it.key.name }
            .map { it.key to (it.value * 1000).toInt() }
        val catalogSignature = catalog
            .map { exercise ->
                listOf(
                    exercise.id,
                    exercise.requiredEquipment.map { it.id }.sorted().joinToString(","),
                    exercise.primaryMuscles.map { it.name }.sorted().joinToString(","),
                    exercise.secondaryMuscles.map { it.name }.sorted().joinToString(","),
                    exercise.movementPattern.name,
                    exercise.isCustom.toString(),
                    exercise.loadCapability.name
                ).joinToString("|")
            }
            .sorted()
        return listOf(
            request.daysPerWeek,
            request.availableEquipment.map { it.id }.sorted(),
            request.splitPreference,
            request.goal,
            request.setsPerExercise,
            request.accessorySetsPerExercise,
            request.automaticSetPrescription,
            request.plannerSupersetPreference,
            request.recentExerciseIdsByPattern.entries
                .sortedBy { it.key.name }
                .map { it.key to it.value.sorted() },
            request.suggestedWeightsKg.entries.sortedBy { it.key }.map { it.key to it.value },
            request.exercisePreferences.entries.sortedBy { it.key }.map { it.key to it.value },
            request.excludedExerciseIds.sorted(),
            fatigue,
            // Dates and their projected fatigue are generation inputs: a changed date context must
            // unlock Regenerate even though the catalog and muscle fatigue map are unchanged.
            request.workoutEpochDays,
            request.fatigueByWorkout.map { perDate ->
                perDate.entries.sortedBy { it.key.name }.map { it.key to (it.value * 1000).toInt() }
            },
            catalogSignature
        ).hashCode()
    }
}

private fun SplitScheduleDialogState.withPreview(
    actions: PlanBuilderActions,
    count: Int
): SplitScheduleDialogState {
    if (count <= 0) return copy(preview = emptyList())
    val dates = try {
        actions.previewSchedule(count, startEpochDay, mode, weekdays, startToday)
    } catch (_: ScheduleException) {
        emptyList()
    }
    return copy(preview = dates)
}

/** Recomputes the planned civil dates for the editor's chosen weekdays and start date. */
private fun PlanningDatesState.withPlanningPreview(
    actions: PlanBuilderActions,
    count: Int
): PlanningDatesState {
    if (count <= 0) return copy(preview = emptyList())
    val dates = try {
        actions.previewSchedule(count, startEpochDay, ScheduleMode.WEEKDAY, weekdays, startToday)
    } catch (_: ScheduleException) {
        emptyList()
    }
    return copy(preview = dates)
}

/** Values known to satisfy the automatic-prescription rule, used only to isolate one failing field. */
private const val VALID_AUTOMATIC_MIN_REPS = 8
private const val VALID_AUTOMATIC_MAX_REPS = 12

/**
 * The pairing preference to send with a request, or null to leave the generated days untouched.
 *
 * A non-numeric, negative or out-of-range value disables pairing rather than sending a guess: the
 * user's own numbers are the only acceptable rest values, and the screen surfaces the invalid field.
 */
internal fun plannerSupersetPreference(
    enabled: Boolean,
    transitionSeconds: String,
    pairRestSeconds: String
): PlannerSupersetPreference? {
    if (!enabled) return null
    val transition = transitionSeconds.trim().toIntOrNull() ?: return null
    val pairRest = pairRestSeconds.trim().toIntOrNull() ?: return null
    return runCatching {
        PlannerSupersetPreference(transition, pairRest).also { it.requireValid() }
    }.getOrNull()
}

/** Keeps a rest field numeric and bounded in length; invalid input is never silently coerced. */
internal fun String.filterDigits(): String = filter { it.isDigit() }.take(5)
