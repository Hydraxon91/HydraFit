package com.hydrafit.app.feature.splitbuilder

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hydrafit.app.core.domain.engine.AcceptedExercise
import com.hydrafit.app.core.domain.engine.AcceptedPlan
import com.hydrafit.app.core.domain.engine.ExerciseCatalog
import com.hydrafit.app.core.domain.engine.GenerateWeeklySplitUseCase
import com.hydrafit.app.core.domain.engine.ObserveWorkoutPlanInputsUseCase
import com.hydrafit.app.core.domain.engine.PlanBuilderActions
import com.hydrafit.app.core.domain.engine.PlanFailureReason
import com.hydrafit.app.core.domain.engine.PlanGenerationException
import com.hydrafit.app.core.domain.engine.PlanHistoryRepository
import com.hydrafit.app.core.domain.engine.PlanRequest
import com.hydrafit.app.core.domain.engine.PlannerEngineId
import com.hydrafit.app.core.domain.engine.WorkoutPlanInputs
import com.hydrafit.app.core.domain.engine.toWeeklyPlan
import com.hydrafit.app.core.domain.equipment.Exercise
import com.hydrafit.app.core.domain.schedule.ActivationRequest
import com.hydrafit.app.core.domain.schedule.ScheduleException
import com.hydrafit.app.core.domain.schedule.ScheduleMode
import com.hydrafit.app.core.domain.time.DayOfWeek
import com.hydrafit.app.core.userdata.settings.EnginePreferenceRepository
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.onEach
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
    private val refreshRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

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
            val inputs = observeWorkoutPlanInputs(
                setsPerExercise = setsPerExercise,
                accessorySetsPerExercise = accessorySetsPerExercise,
                refreshRequests = refreshRequests
            ).onEach { lastRequest = it.request }
            val accepted = planHistory.latest()
            if (accepted == null) {
                inputs.collectLatest { generate(it) }
            } else {
                // Show what the user already accepted instead of silently generating a new draft;
                // only regenerate once an input changes or they ask for a fresh plan.
                showAccepted(accepted)
                inputs.drop(1).collectLatest { generate(it) }
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

    fun refresh() {
        refreshRequests.tryEmit(Unit)
    }

    fun onAcceptPlan() {
        val plan = _state.value.plan ?: return
        viewModelScope.launch {
            planBuilderActions.accept(plan)
            lastGeneratedFingerprint = null
            // The just-accepted plan is the persisted latest; remember it so a swap can target it.
            shownPlan = planHistory.latest()
            _state.update { it.copy(isPlanAccepted = true, canRegenerate = true) }
        }
    }

    /** Opens the schedule dialog so the generated plan can be started as a training block. */
    fun onScheduleRequested() {
        val plan = _state.value.plan ?: return
        _state.update { current ->
            current.copy(
                scheduleDialog = SplitScheduleDialogState(
                    weekdays = DayOfWeek.entries.take(plan.days.size).toSet(),
                    startEpochDay = planBuilderActions.todayEpochDay(),
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
                    it.copy(isPlanAccepted = true, canRegenerate = true, scheduleDialog = null)
                }
            } catch (error: ScheduleException) {
                _state.update { state ->
                    state.copy(scheduleDialog = state.scheduleDialog?.copy(error = error.message))
                }
            }
        }
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
        val request = lastRequest ?: return
        viewModelScope.launch {
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
        val request = lastRequest ?: return
        val dayIndex = _state.value.swapTargetDayIndex ?: return
        val position = _state.value.swapTargetPosition ?: return
        viewModelScope.launch {
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
                exerciseNames = snapshotNames + catalogNames,
                isLoading = false,
                isPlanAccepted = true,
                canRegenerate = true,
                hasError = false,
                isTransientError = false,
                errorDetail = null,
                fallbackReason = null,
                volumeExplanationInvalidated = false,
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
        val request = inputs.request
        val fingerprint = fingerprintOf(request, exerciseCatalog.all())
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
                requestedEngine = inputs.requestedEngine
            )
        }
        try {
            val plan = generateWeeklySplit(request)
            // Exclusions are a hard gate: when they (or equipment) leave no eligible exercise at all,
            // surface an actionable failure instead of an empty week. A merely partial plan (some
            // days empty) keeps the existing behavior.
            if (plan.days.all { it.exercises.isEmpty() } &&
                request.excludedExerciseIds.isNotEmpty()
            ) {
                _state.update {
                    it.copy(
                        plan = null,
                        isLoading = false,
                        hasError = true,
                        isTransientError = false,
                        errorDetail = null,
                        failureReason = PlanFailureReason.NO_ELIGIBLE_EXERCISES
                    )
                }
                return
            }
            val names = exerciseCatalog.all().associate { it.id to it.name }
            lastGeneratedFingerprint = fingerprint
            // The plan now matches the current inputs, so a repeat tap would produce the same
            // result: re-lock the button for the deterministic engine.
            _state.update {
                it.copy(
                    plan = plan,
                    exerciseNames = names,
                    isLoading = false,
                    canRegenerate = inputs.requestedEngine != PlannerEngineId.DETERMINISTIC,
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
            request.recentExerciseIdsByPattern.entries
                .sortedBy { it.key.name }
                .map { it.key to it.value.sorted() },
            request.suggestedWeightsKg.entries.sortedBy { it.key }.map { it.key to it.value },
            fatigue,
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
