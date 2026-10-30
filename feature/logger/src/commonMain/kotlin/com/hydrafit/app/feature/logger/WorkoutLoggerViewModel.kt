package com.hydrafit.app.feature.logger

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hydrafit.app.core.domain.engine.AcceptedDay
import com.hydrafit.app.core.domain.engine.AcceptedPlan
import com.hydrafit.app.core.domain.engine.ApplySlotProgressionSuggestionUseCase
import com.hydrafit.app.core.domain.engine.EquipmentWeightLimit
import com.hydrafit.app.core.domain.engine.ExerciseCatalog
import com.hydrafit.app.core.domain.engine.PeriodizationConfig
import com.hydrafit.app.core.domain.engine.SetTarget
import com.hydrafit.app.core.domain.engine.SuggestSlotProgressionUseCase
import com.hydrafit.app.core.domain.equipment.EquipmentTag
import com.hydrafit.app.core.domain.equipment.Exercise
import com.hydrafit.app.core.domain.equipment.ExerciseLoadCapability
import com.hydrafit.app.core.domain.fatigue.FatigueConfig
import com.hydrafit.app.core.domain.routine.ClusterPrescription
import com.hydrafit.app.core.domain.routine.ClusterSegmentTarget
import com.hydrafit.app.core.domain.schedule.ApplyGuidedWorkoutTargetsUseCase
import com.hydrafit.app.core.domain.schedule.ChangeOccurrenceEntryMeasureUseCase
import com.hydrafit.app.core.domain.schedule.ClusterSegmentField
import com.hydrafit.app.core.domain.schedule.ClusterSegmentPatch
import com.hydrafit.app.core.domain.schedule.FinishMode
import com.hydrafit.app.core.domain.schedule.GuidedTargetField
import com.hydrafit.app.core.domain.schedule.GuidedTargetPatch
import com.hydrafit.app.core.domain.schedule.GuidedWorkoutTargetOverride
import com.hydrafit.app.core.domain.schedule.OccurrenceEntry
import com.hydrafit.app.core.domain.schedule.OccurrenceEntryMeasure
import com.hydrafit.app.core.domain.schedule.OccurrenceStatus
import com.hydrafit.app.core.domain.schedule.ScheduleException
import com.hydrafit.app.core.domain.schedule.TrainingActivation
import com.hydrafit.app.core.domain.schedule.WorkoutLoggingActions
import com.hydrafit.app.core.domain.schedule.WorkoutOccurrence
import com.hydrafit.app.core.domain.schedule.WorkoutScheduleState
import com.hydrafit.app.core.domain.schedule.freezesOccurrenceSlot
import com.hydrafit.app.core.domain.schedule.originalTargets
import com.hydrafit.app.core.domain.time.localDayOfWeek
import com.hydrafit.app.core.domain.time.localEpochDay
import com.hydrafit.app.core.domain.unit.WeightUnit
import com.hydrafit.app.core.domain.unit.formatWeight
import com.hydrafit.app.core.domain.workout.GetWorkoutLogUseCase
import com.hydrafit.app.core.domain.workout.GuidedWorkoutProgress
import com.hydrafit.app.core.domain.workout.LiveRestCompletion
import com.hydrafit.app.core.domain.workout.LoadKind
import com.hydrafit.app.core.domain.workout.LogWorkoutSetUseCase
import com.hydrafit.app.core.domain.workout.NOTE_MAX_LENGTH
import com.hydrafit.app.core.domain.workout.PerformedClusterSegment
import com.hydrafit.app.core.domain.workout.RestCountdown
import com.hydrafit.app.core.domain.workout.ShortRestCoach
import com.hydrafit.app.core.domain.workout.WarmupAction
import com.hydrafit.app.core.domain.workout.WarmupEquipment
import com.hydrafit.app.core.domain.workout.WarmupGuidance
import com.hydrafit.app.core.domain.workout.WarmupPreparationItem
import com.hydrafit.app.core.domain.workout.WarmupPreparationOverrideRepository
import com.hydrafit.app.core.domain.workout.WarmupRampSet
import com.hydrafit.app.core.domain.workout.WorkoutCluster
import com.hydrafit.app.core.domain.workout.WorkoutLoadPolicy
import com.hydrafit.app.core.domain.workout.WorkoutLogMutations
import com.hydrafit.app.core.domain.workout.WorkoutPrescriptionSnapshot
import com.hydrafit.app.core.domain.workout.WorkoutSet
import com.hydrafit.app.core.domain.workout.WorkoutSetCorrection
import com.hydrafit.app.core.domain.workout.WorkoutSummary
import com.hydrafit.app.core.domain.workout.WorkoutSummarySet
import com.hydrafit.app.core.domain.workout.WorkoutTimingProvenance
import com.hydrafit.app.core.domain.workout.buildGuidedWorkoutProgress
import com.hydrafit.app.core.domain.workout.buildWarmupGuidance
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private data class GuidedSetIntent(val entryId: Long, val isWarmup: Boolean)

/** Preparation countdown before a timed set's work timer starts. */
private const val SET_TIMER_PREPARATION_SECONDS = 5

private data class GuidedSetWriteResult(
    val succeeded: Boolean,
    val sessionId: String?,
    val isWarmup: Boolean
)

/** One snapshot of an occurrence's recorded work: working counts, frozen entries and presence. */
private data class OccurrenceRecordedSets(
    val performed: Map<Long, Int>,
    val frozenEntryIds: Set<Long>,
    val anyRecorded: Boolean
)

class WorkoutLoggerViewModel(
    private val logMutations: WorkoutLogMutations,
    private val logWorkoutSet: LogWorkoutSetUseCase,
    private val getWorkoutLog: GetWorkoutLogUseCase,
    private val loggingActions: WorkoutLoggingActions,
    private val exerciseCatalog: ExerciseCatalog,
    private val runtime: WorkoutLoggerRuntime,
    private val settings: WorkoutLoggerSettings,
    private val exerciseNoteRepository: com.hydrafit.app.core.domain.workout.ExerciseNoteRepository,
    private val sessionNoteRepository: com.hydrafit.app.core.domain.workout.SessionNoteRepository,
    private val warmupOverrideRepository: WarmupPreparationOverrideRepository,
    private val equipment: WorkoutLoggerEquipment,
    private val applyGuidedTargets: ApplyGuidedWorkoutTargetsUseCase,
    private val changeEntryMeasure: ChangeOccurrenceEntryMeasureUseCase,
    private val suggestSlotProgression: SuggestSlotProgressionUseCase,
    private val applySlotProgressionSuggestion: ApplySlotProgressionSuggestionUseCase
) : ViewModel() {

    private val _state = MutableStateFlow(WorkoutLoggerUiState())
    val state: StateFlow<WorkoutLoggerUiState> = _state.asStateFlow()

    private var exercises: List<Exercise> = emptyList()
    private var exerciseNames: Map<String, String> = emptyMap()
    private var acceptedPlan: AcceptedPlan? = null
    private var acceptedToday: AcceptedDay? = null
    private var suggestedWeightKgByExercise: Map<String, Double> = emptyMap()

    /** Current equipment availability/ceilings; null until the first read resolves. */
    private var warmupEquipment: WarmupEquipment? = null

    /** True once the first equipment snapshot has resolved; until then ramp suggestions are withheld. */
    private var warmupEquipmentObserved = false

    /** The active block and its queue, when the user has started one; null uses the accepted plan. */
    private var activeActivation: TrainingActivation? = null
    private var currentOccurrences: List<WorkoutOccurrence> = emptyList()
    private var scheduleState: WorkoutScheduleState? = null
    private var currentOccurrence: WorkoutOccurrence? = null
    private var occurrencesJob: Job? = null

    /** The occurrence the open summary route belongs to, or null when no summary is open. */
    private var summaryOccurrenceId: Long? = null

    /**
     * The plan identity and local day the current [WorkoutLoggerUiState.draftSets] were derived for,
     * so a resume does not rebuild (and resurrect) drafts the user already handled. The plan identity
     * is its id plus acceptance time; an unsaved plan has id 0, so the acceptance time also tells two
     * different unsaved plans apart.
     */
    private var lastDraftsKey: Triple<Long?, Long?, Long>? = null

    /**
     * The backdated session created for the current chosen time, reused so a draft batch shares one
     * session. Cleared whenever the chosen time or the force-new toggle changes.
     */
    private var resolvedBackdatedSessionId: String? = null
    private var resolvedBackdatedSessionKey: Pair<Long, Boolean>? = null
    private var draftSubmissionInProgress = false

    /** Guards manual "Log set" submissions; claimed before the write and held through success handling. */
    private var manualLogSubmissionInProgress = false

    /**
     * Bumped whenever a manual-form input or selection changes, so a completion that lands after the
     * user replaced the form cannot reset a newer input or show a failure for the replaced entries.
     */
    private var manualInputGeneration = 0L
    private var draftContextRevision = 0L
    private var clusterLogGeneration = 0L
    private var missingLoadPromptRevision = 0L

    /**
     * Guidance freshness: bumped before each occurrence refresh's read and published when the new
     * guidance lands, so a suggestion can only be written while it still matches the current slot.
     */
    private var warmupGuidanceGeneration = 0L
    private var warmupGuidancePublishedGeneration = 0L

    /**
     * Each entry's edited effective target at its next remaining set position, resolved with the same
     * performed-set read that publishes the occurrence. Only entries with guided overrides appear; an
     * absent entry keeps ramping from its frozen prescription.
     */
    private var rampTargetsByEntry: Map<Long, SetTarget> = emptyMap()
    private val restTimer = runtime.restTimer(viewModelScope)
    private var setTimerJob: Job? = null
    private var setTimerGeneration = 0L
    private var setTimerWorkStartedAtElapsedMillis: Long? = null
    private val shortRestCoach = ShortRestCoach()
    private var shortRestCoachingEnabled = false
    private var liveSetIntentEntryId: Long? = null
    private var liveSetStartedAtElapsedMillis: Long? = null
    private var liveSetCompletedAtElapsedMillis: Long? = null
    private var liveSetCompletedAtWallMillis: Long? = null
    private var guidedSetIntent: GuidedSetIntent? = null
    private var timerOccurrenceId: Long? = null
    private var timerSessionId: String? = null
    private var timerExerciseId: String? = null
    private var liveTimerGeneration = 0L
    private var activationObserved = false
    private var occurrenceContextObserved = false
    private var guidedPreferenceObserved = false
    private var sessionObserved = false
    private var resumeLifecycleChecked = false
    private var restRestoreAttempted = false

    init {
        _state.update { it.copy(utcOffsetMillis = runtime.utcOffsetMillis()) }
        viewModelScope.launch {
            settings.shortRestCoachingFlow().collect { enabled ->
                shortRestCoachingEnabled = enabled
                if (!enabled) resetShortRestCoaching()
            }
        }
        viewModelScope.launch {
            restTimer.state.collect { timer ->
                _state.update {
                    if (timer == null) {
                        it.copy(restTimer = null).withRestCleared()
                    } else {
                        it.copy(restTimer = timer)
                    }
                }
            }
        }
        viewModelScope.launch {
            // Observe the catalog so a newly added/edited custom exercise appears without a restart.
            exerciseCatalog.observeAll().collect { catalog ->
                exercises = catalog
                exerciseNames = catalog.associate { it.id to it.name }
                _state.update { current ->
                    current.copy(
                        exercises = prioritizedByToday(catalog, acceptedToday),
                        exerciseLoadBasis = catalog.loadBases()
                    )
                }
                refreshRecentSets()
            }
        }
        viewModelScope.launch {
            // Equipment changes re-bound the ramp suggestions; the frozen prescription is untouched.
            // Until this first resolves, availability and ceilings are unknown, so no ramp is offered.
            equipment.equipmentFlow().collectLatest { current ->
                warmupEquipment = current
                warmupEquipmentObserved = true
                // A refresh may return without publishing when superseded. Visible readiness belongs
                // to the successful guidance publication, not to this collector's completion.
                refreshToday()
            }
        }
        viewModelScope.launch {
            equipment.stationsFlow().collectLatest { stations ->
                _state.update { current ->
                    val available = current.copy(stations = stations).availableStations
                    current.copy(
                        stations = stations,
                        selectedStationId = current.selectedStationId
                            ?.takeIf { id -> available.any { it.id == id } }
                    )
                }
            }
        }
        viewModelScope.launch {
            // Preparation overrides are global, not per occurrence, so an edit made in an earlier
            // workout still applies here: a change rebuilds the current guidance rather than waiting
            // for the next occurrence.
            warmupOverrideRepository.observeAll().collectLatest { overrides ->
                _state.update { it.copy(warmupPreparationOverrides = overrides) }
                refreshToday()
            }
        }
        viewModelScope.launch {
            loggingActions.observeAcceptedPlan().collectLatest { plan ->
                acceptedPlan = plan
                if (activeActivation == null) updateTodayPlan(plan)
            }
        }
        viewModelScope.launch {
            loggingActions.observeScheduleState().collect { state ->
                scheduleState = state
                if (activeActivation != null) refreshOccurrence()
            }
        }
        viewModelScope.launch {
            loggingActions.observeActiveActivation().collect { activation ->
                if (activeActivation != activation) {
                    draftContextRevision++
                    cancelSetTimer()
                    dismissInstructions()
                    // A refresh still reading for the previous block must not publish after this
                    // change, so a superseded result can never resurrect its guidance or editor.
                    warmupGuidanceGeneration++
                    invalidateRestTimer(clearPersisted = restTimer.state.value != null)
                    liveSetIntentEntryId = null
                    // A block change replaces the logging context, so a manual-set failure from the
                    // previous context no longer describes the form on screen, and a completed save's
                    // refresh message described that same context.
                    _state.update {
                        it.copy(
                            manualLogFailed = false,
                            manualLogRefreshFailed = false,
                            clusterDraftError = null
                        )
                    }
                }
                activeActivation = activation
                activationObserved = true
                occurrencesJob?.cancel()
                if (activation == null) {
                    currentOccurrences = emptyList()
                    currentOccurrence = null
                    summaryOccurrenceId = null
                    occurrenceContextObserved = true
                    // Leaving the active block ends any in-flight batch or partial-write retry.
                    _state.update {
                        it.copy(
                            activeOccurrence = null,
                            confirmingAllDrafts = false,
                            draftWriteRetries = emptyList(),
                            guidedProgress = null,
                            guidedExpandedEntries = emptyMap(),
                            appliedGuidedEdits = emptyMap(),
                            progressionSuggestions = emptyMap(),
                            progressionApplyEntryId = null,
                            progressionApplyFailed = false,
                            frozenEntryIds = emptySet(),
                            guidedSetWriteFailed = false,
                            summary = null
                        ).withWarmupCleared()
                    }
                    updateTodayPlan(acceptedPlan)
                    restoreRestTimerIfReady()
                } else {
                    occurrencesJob = viewModelScope.launch {
                        loggingActions.observeOccurrences(activation.id).collect { occurrences ->
                            currentOccurrences = occurrences
                            refreshOccurrence()
                            occurrenceContextObserved = true
                            restoreRestTimerIfReady()
                        }
                    }
                }
            }
        }
        viewModelScope.launch {
            settings.weightUnitFlow().collectLatest { unit ->
                _state.update { current ->
                    val value = current.weightInput.toDoubleOrNull()
                    current.copy(
                        weightUnit = unit,
                        weightInput = if (unit == current.weightUnit ||
                            value == null ||
                            !value.isFinite() ||
                            current.manualWeightKg == null
                        ) {
                            current.weightInput
                        } else {
                            exactEntryText(unit.kilogramsToDisplay(current.manualWeightKg))
                        }
                    )
                }
            }
        }
        viewModelScope.launch {
            settings.distanceUnitFlow().collectLatest { unit ->
                _state.update { current ->
                    val value = current.distanceMeters.toDoubleOrNull()
                    current.copy(
                        distanceUnit = unit,
                        distanceMeters = if (unit == current.distanceUnit ||
                            value == null ||
                            !value.isFinite() ||
                            current.manualDistanceMetres == null
                        ) {
                            current.distanceMeters
                        } else {
                            exactEntryText(unit.metresToDisplay(current.manualDistanceMetres))
                        }
                    )
                }
            }
        }
        viewModelScope.launch {
            settings.guidedWorkoutFlow().collectLatest { enabled ->
                if (_state.value.guidedEnabled != enabled) dismissInstructions()
                // The timer is offered on the planned-draft row, which the guided section replaces.
                cancelSetTimer()
                _state.update { it.copy(guidedEnabled = enabled) }
                guidedPreferenceObserved = true
                if (!enabled) {
                    invalidateRestTimer()
                    liveSetIntentEntryId = null
                }
                restoreRestTimerIfReady()
            }
        }
        viewModelScope.launch {
            // Active-session state is derived from the persisted open session, so it survives a restart.
            logMutations.observeOpenSession().collectLatest { session ->
                sessionObserved = true
                if (timerSessionId != null && session?.id != timerSessionId) {
                    invalidateRestTimer()
                }
                // A different session identity invalidates the session-note editor and any in-flight
                // read or write, so a draft or result can never attach to another session.
                if (_state.value.activeSession?.id != session?.id) {
                    resetSessionNoteState()
                    dismissInstructions()
                }
                _state.update { it.copy(activeSession = session) }
                restoreRestTimerIfReady()
            }
        }
    }

    override fun onCleared() {
        cancelSetTimer()
        invalidateRestTimer(clearPersisted = false)
        super.onCleared()
    }

    private fun invalidateRestTimer(clearPersisted: Boolean = true) {
        liveTimerGeneration++
        timerOccurrenceId = null
        timerSessionId = null
        timerExerciseId = null
        restTimer.cancel(clearPersisted)
        _state.update { it.withRestCleared() }
        resetShortRestCoaching()
        clearLiveSetStart()
    }

    private fun restoreRestTimerIfReady() {
        if (restRestoreAttempted ||
            !activationObserved ||
            !occurrenceContextObserved ||
            !guidedPreferenceObserved ||
            !sessionObserved ||
            !resumeLifecycleChecked
        ) {
            return
        }
        restRestoreAttempted = true
        val occurrence = currentOccurrence
        val session = _state.value.activeSession
        if (!isGuidedActive || occurrence == null || session == null || occurrence.isResolved) {
            restTimer.cancel()
            return
        }
        val contextRevision = draftContextRevision
        viewModelScope.launch {
            val restored = restTimer.restore(
                currentSessionId = session.id,
                currentOccurrenceId = occurrence.id,
                validExerciseIds = occurrence.entries.map { it.exerciseId }.toSet()
            ) ?: return@launch
            if (contextRevision != draftContextRevision ||
                currentOccurrence?.id != occurrence.id ||
                _state.value.activeSession?.id != session.id ||
                !isGuidedActive
            ) {
                restTimer.cancel()
                return@launch
            }
            timerOccurrenceId = restored.occurrenceId
            timerSessionId = restored.sessionId
            timerExerciseId = restored.exerciseId
            val isOverride = settings.hasExerciseRestDurationOverride(restored.exerciseId)
            val paired = _state.value.guidedProgress?.exercises?.any {
                it.exerciseId == restored.exerciseId && it.superset != null
            } == true
            _state.update {
                it.copy(
                    restDurationSeconds = restored.durationMillis.div(1_000L).toString(),
                    restDurationIsOverride = isOverride,
                    // A restored countdown keeps the read-only superset treatment: the persisted
                    // countdown does not carry the flag, but the frozen pairing still does.
                    restDurationIsPairRest = paired,
                    restDurationIsSupersetTransition = false
                )
            }
        }
    }

    private fun clearLiveSetStart() {
        guidedSetIntent = null
        liveSetIntentEntryId = null
        liveSetStartedAtElapsedMillis = null
        liveSetCompletedAtElapsedMillis = null
        liveSetCompletedAtWallMillis = null
        _state.update { it.copy(startedSetEntryId = null) }
    }

    fun onExerciseSelected(exerciseId: String) {
        // A note write is in flight; ignore the selection change so its failure stays retryable
        // against the exercise it started with. The write is short-lived, so this is not noticeable.
        if (noteSaving) return
        val unit = _state.value.weightUnit
        val planWeight = suggestedInputFor(exerciseId, unit)
        val planReps = suggestedRepsFor(exerciseId)
        manualInputGeneration++
        _state.update {
            it.copy(
                selectedExerciseId = exerciseId,
                selectedStationId = null,
                exerciseInstructions = null,
                weightInput = planWeight,
                manualWeightKg = suggestedWeightKgByExercise[exerciseId],
                reps = planReps?.toString() ?: it.reps,
                // A prescription target is a rep count, so a plan suggestion returns the form to reps.
                metric = if (planReps != null) SetMetric.REPETITIONS else it.metric,
                durationSeconds = if (planReps != null) "" else it.durationSeconds,
                // Bodyweight exercises hide the weight field again on each selection.
                weightRevealed = false,
                // The previous set's failure messages described these replaced entries.
                manualLogFailed = false,
                manualLogRefreshFailed = false
            )
        }
        // The accepted plan's suggestion wins; only fall back to the last logged set when the plan
        // has no suggestion for this exercise.
        if (planWeight.isBlank()) {
            viewModelScope.launch { prefillFromLastSet(exerciseId) }
        }
        // Load the exercise setup note when an exercise is selected.
        onNoteExerciseSelected(exerciseId)
    }

    /** Selects a station for the manual form, or clears it when [stationId] is null. */
    fun onStationSelected(stationId: Long?) {
        val current = _state.value
        val exerciseId = current.selectedExerciseId ?: return
        val allowed = stationId == null || current.availableStations.any { it.id == stationId }
        if (!allowed) return
        manualInputGeneration++
        _state.update { it.copy(selectedStationId = stationId) }
        // Station evidence is a separate comparison population; refresh the form from that station's
        // own latest set instead of leaving the exercise-level planner number in place.
        viewModelScope.launch { prefillFromLastSet(exerciseId) }
    }

    fun openManualInstructions() {
        val current = _state.value
        val exercise = current.exercises.firstOrNull {
            it.id == current.selectedExerciseId
        } ?: return
        _state.update {
            it.copy(exerciseInstructions = LoggerExerciseInstructions(exercise.id, exercise.name))
        }
    }

    fun openDraftInstructions(draft: DraftSet) {
        if (draft !in _state.value.draftSets) return
        _state.update {
            it.copy(exerciseInstructions = LoggerExerciseInstructions(draft.exerciseId, draft.name))
        }
    }

    fun openGuidedInstructions(entryId: Long) {
        val exercise = _state.value.guidedProgress?.exercises
            ?.firstOrNull { it.occurrenceEntryId == entryId } ?: return
        _state.update {
            it.copy(
                exerciseInstructions = LoggerExerciseInstructions(
                    exercise.exerciseId,
                    exercise.exerciseName
                )
            )
        }
    }

    fun dismissInstructions() {
        _state.update { it.copy(exerciseInstructions = null) }
    }

    /** Fills reps and weight from the most recent non-warmup set logged for this exercise. */
    private suspend fun prefillFromLastSet(exerciseId: String) {
        val stationId = _state.value.selectedStationId
        val last = getWorkoutLog()
            .filter {
                it.exerciseId == exerciseId &&
                    !it.isWarmup &&
                    if (stationId == null) it.station == null else it.station?.id == stationId
            }
            .maxByOrNull { it.performedAtMillis }
            ?: return
        if (_state.value.selectedExerciseId != exerciseId) return
        // This read can suspend, so the prefill may land after a manual submission was started. It is
        // then a newer input that replaces the form the user had: invalidate the older submission's
        // form identity and drop failure feedback that described the replaced entries.
        manualInputGeneration++
        _state.update { current ->
            if (current.selectedExerciseId != exerciseId) return@update current
            val option = current.exercises.firstOrNull { it.id == exerciseId }
            current.copy(
                weightInput = last.weightKg?.let {
                    exactEntryText(current.weightUnit.kilogramsToDisplay(it))
                }.orEmpty(),
                manualWeightKg = last.weightKg,
                metric = when {
                    last.forceNewtons != null -> SetMetric.FORCE
                    last.distanceMeters != null -> SetMetric.DISTANCE
                    last.durationSeconds != null -> SetMetric.DURATION
                    else -> SetMetric.REPETITIONS
                },
                reps = last.reps?.toString().orEmpty(),
                durationSeconds = last.durationSeconds?.toString().orEmpty(),
                distanceMeters = last.distanceMeters
                    ?.let { current.distanceUnit.displayWhole(it) }
                    ?.toString()
                    .orEmpty(),
                manualDistanceMetres = last.distanceMeters,
                forceNewtons = last.forceNewtons?.let(::exactEntryText).orEmpty(),
                deviceName = last.device?.name.orEmpty(),
                deviceRatingKg = last.device?.ratingKg?.let(::exactEntryText).orEmpty(),
                // An added-load history reveals the field on an addable bodyweight exercise.
                weightRevealed = current.weightRevealed ||
                    (option?.canAddLoad == true && last.weightKg != null),
                manualLogFailed = false,
                manualLogRefreshFailed = false
            )
        }
    }

    /**
     * Quick-fills the input from a past set without applying the accepted plan's suggestion, so a
     * tapped row reproduces exactly what was logged: exercise, reps, weight, warm-up flag, and RIR.
     */
    fun onRecentSetSelected(row: LoggedSetRow) {
        if (noteSaving) return
        val unit = _state.value.weightUnit
        manualInputGeneration++
        _state.update {
            it.copy(
                selectedExerciseId = row.exerciseId,
                exerciseInstructions = null,
                weightInput = row.weightKg?.let {
                    exactEntryText(unit.kilogramsToDisplay(it))
                }.orEmpty(),
                manualWeightKg = row.weightKg,
                metric = when {
                    row.forceNewtons != null -> SetMetric.FORCE
                    row.distanceMeters != null -> SetMetric.DISTANCE
                    row.durationSeconds != null -> SetMetric.DURATION
                    else -> SetMetric.REPETITIONS
                },
                reps = row.reps?.toString().orEmpty(),
                durationSeconds = row.durationSeconds?.toString().orEmpty(),
                distanceMeters = row.distanceMeters
                    ?.let { meters -> it.distanceUnit.displayWhole(meters) }
                    ?.toString()
                    .orEmpty(),
                manualDistanceMetres = row.distanceMeters,
                forceNewtons = row.forceNewtons?.let(::exactEntryText).orEmpty(),
                deviceName = row.device?.name.orEmpty(),
                deviceRatingKg = row.device?.ratingKg?.let(::exactEntryText).orEmpty(),
                // Reveal the weight field for a bodyweight exercise only when that row had a weight.
                weightRevealed = row.weightKg != null,
                isWarmup = row.isWarmup,
                rir = row.rir?.toString().orEmpty(),
                // The previous set's failure messages described these replaced entries.
                manualLogFailed = false,
                manualLogRefreshFailed = false
            )
        }
        // Load the exercise setup note when a recent set is selected.
        onNoteExerciseSelected(row.exerciseId)
    }

    /** The weight input string for [weightKg] in the display unit, or blank when there is none. */
    private fun weightInputFor(weightKg: Double?, unit: WeightUnit): String =
        weightKg?.let { formatWeight(unit.kilogramsToDisplay(it)) }.orEmpty()

    /**
     * Sets the explicit time to stamp new sets with, or clears it with null to log live. Returns
     * false when a future time is rejected; the current selection is left unchanged in that case.
     */
    fun onPerformedAtChanged(millis: Long?): Boolean {
        if (millis != null && millis > runtime.nowMillis()) return false
        manualInputGeneration++
        clearLiveSetStart()
        resolvedBackdatedSessionId = null
        resolvedBackdatedSessionKey = null
        _state.update { it.copy(performedAtMillis = millis, startedSetEntryId = null) }
        return true
    }

    /**
     * Forces a fresh backdated session instead of attaching to the open one. Takes effect on the
     * next log and drops any session already resolved for the previous choice.
     */
    fun onForceNewSessionChanged(forceNewSession: Boolean) {
        manualInputGeneration++
        resolvedBackdatedSessionId = null
        resolvedBackdatedSessionKey = null
        _state.update { it.copy(forceNewSession = forceNewSession) }
    }

    /**
     * Applies a picked local date (start-of-day UTC millis) and wall-clock time. Returns false when
     * the resulting instant is in the future.
     */
    fun onBackdatedDateTimePicked(dateStartOfDayUtcMillis: Long, hour: Int, minute: Int): Boolean =
        onPerformedAtChanged(
            pickedLocalDateTimeToEpochMillis(
                dateStartOfDayUtcMillis,
                hour,
                minute,
                runtime.utcOffsetMillis()
            )
        )

    /** The current wall-clock time, used to seed the backdated pickers. */
    fun currentTimeMillis(): Long = runtime.nowMillis()

    fun editLoggedSet(row: LoggedSetRow) {
        if (_state.value.savingLoggedSet) return
        val summary = row.cluster
        if (summary == null) {
            _state.update {
                it.copy(
                    loggedSetEdit = loggedSetEditFor(row, emptyList()),
                    loggedSetEditFailed = false
                )
            }
            return
        }
        // A cluster's recorded segments are separate rows, so the editor loads them before it opens;
        // showing the ordinary fields alone would let a save silently discard them.
        viewModelScope.launch {
            val segments = try {
                logMutations.clusterSegments(row.id).drop(1)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _state.update { it.copy(loggedSetEditFailed = true) }
                return@launch
            }
            if (_state.value.savingLoggedSet) return@launch
            _state.update {
                it.copy(
                    loggedSetEdit = loggedSetEditFor(row, segments),
                    loggedSetEditFailed = false
                )
            }
        }
    }

    /** Seeds the correction editor from a recorded row and, for a cluster, its later segments. */
    private fun loggedSetEditFor(
        row: LoggedSetRow,
        segments: List<PerformedClusterSegment>
    ): LoggedSetEdit {
        val unit = _state.value.weightUnit
        val distanceUnit = _state.value.distanceUnit
        val weight = weightInputFor(row.weightKg, unit)
        return LoggedSetEdit(
            row = row,
            weightUnit = unit,
            distanceUnit = distanceUnit,
            // The editor opens on the metric the row was recorded with; the other fields are
            // seeded but only submitted when the user switches to one of them.
            metric = when {
                row.forceNewtons != null -> SetMetric.FORCE
                row.distanceMeters != null -> SetMetric.DISTANCE
                row.durationSeconds != null -> SetMetric.DURATION
                else -> SetMetric.REPETITIONS
            },
            reps = row.reps?.toString().orEmpty(),
            durationSeconds = row.durationSeconds?.toString().orEmpty(),
            distanceMeters = row.distanceMeters
                ?.let { meters -> distanceUnit.displayWhole(meters) }
                ?.toString()
                .orEmpty(),
            forceNewtons = row.forceNewtons?.let(::exactEntryText).orEmpty(),
            deviceName = row.device?.name.orEmpty(),
            deviceRatingKg = row.device?.ratingKg?.let(::exactEntryText).orEmpty(),
            weightInput = weight,
            originalWeightInput = weight,
            rir = row.rir?.toString().orEmpty(),
            performedAtMillis = row.performedAtMillis,
            clusterSegments = segments.map { segment ->
                LoggedClusterSegmentEdit(
                    position = segment.position,
                    reps = segment.reps.toString(),
                    weightInput = weightInputFor(segment.weightKg, unit),
                    rir = segment.rir?.toString().orEmpty()
                )
            }
        )
    }

    fun onLoggedSetRepsChanged(value: String) = updateLoggedSetEdit { copy(reps = value) }

    fun onLoggedSetDurationChanged(value: String) = updateLoggedSetEdit {
        copy(durationSeconds = value.filter(Char::isDigit))
    }

    fun onLoggedSetDistanceChanged(value: String) = updateLoggedSetEdit {
        copy(distanceMeters = value.filter { it.isDigit() || it == '.' })
    }

    fun onLoggedSetForceChanged(value: String) = updateLoggedSetEdit {
        copy(forceNewtons = value.filter { it.isDigit() || it == '.' })
    }

    fun onLoggedSetDeviceNameChanged(value: String) = updateLoggedSetEdit {
        copy(deviceName = value)
    }

    fun onLoggedSetDeviceRatingChanged(value: String) = updateLoggedSetEdit {
        copy(deviceRatingKg = value.filter { it.isDigit() || it == '.' })
    }

    /**
     * Switching the metric clears the fields the user just left, so nothing stale is submitted. A
     * device belongs to a force reading only, so leaving Force also clears it.
     */
    fun onLoggedSetMetricChanged(metric: SetMetric) = updateLoggedSetEdit {
        // A cluster is rep-based external-load work; leaving repetitions would not describe it.
        if (hasCluster && metric != SetMetric.REPETITIONS) return@updateLoggedSetEdit this
        when (metric) {
            SetMetric.REPETITIONS -> copy(
                metric = metric,
                durationSeconds = "",
                distanceMeters = "",
                forceNewtons = "",
                deviceName = "",
                deviceRatingKg = ""
            )
            SetMetric.DURATION -> copy(
                metric = metric,
                reps = "",
                distanceMeters = "",
                forceNewtons = "",
                deviceName = "",
                deviceRatingKg = ""
            )
            SetMetric.DISTANCE -> copy(
                metric = metric,
                reps = "",
                durationSeconds = "",
                forceNewtons = "",
                deviceName = "",
                deviceRatingKg = ""
            )
            SetMetric.FORCE -> copy(
                metric = metric,
                reps = "",
                durationSeconds = "",
                distanceMeters = ""
            )
        }
    }

    /**
     * Opens the existing set-correction editor for one set of the finished-workout summary. The
     * summary is keyed by occurrence, so [occurrenceId] supplies the attribution the editor and the
     * rest-prompt cancellation need; nothing is written until the editor saves.
     */
    fun editSummarySet(occurrenceId: Long, set: WorkoutSummarySet) {
        editLoggedSet(
            LoggedSetRow(
                id = set.id,
                performedAtMillis = set.performedAtMillis,
                exerciseId = set.exerciseId,
                exerciseName = set.exerciseName ?: set.exerciseId,
                reps = set.reps,
                durationSeconds = set.durationSeconds,
                distanceMeters = set.distanceMeters,
                forceNewtons = set.forceNewtons,
                device = set.device,
                weightKg = set.weightKg,
                rir = set.rir,
                isWarmup = set.isWarmup,
                loadKind = set.loadKind,
                occurrenceId = occurrenceId,
                sessionId = set.sessionId,
                cluster = set.cluster
            )
        )
    }

    fun onLoggedSetWeightChanged(value: String) = updateLoggedSetEdit { copy(weightInput = value) }

    fun onLoggedSetClusterRepsChanged(position: Int, value: String) = updateLoggedSetEdit {
        copy(
            clusterSegments = clusterSegments.update(position) {
                it.copy(reps = value.filter(Char::isDigit))
            }
        )
    }

    fun onLoggedSetClusterWeightChanged(position: Int, value: String) = updateLoggedSetEdit {
        copy(
            clusterSegments = clusterSegments.update(position) {
                it.copy(
                    weightInput = value.filter { c ->
                        c.isDigit() ||
                            c == '.'
                    }
                )
            }
        )
    }

    fun onLoggedSetClusterRirChanged(position: Int, value: String) = updateLoggedSetEdit {
        copy(
            clusterSegments = clusterSegments.update(position) {
                it.copy(rir = value.filter(Char::isDigit))
            }
        )
    }

    /** Replaces the segment at [position]; an unknown position leaves the list unchanged. */
    private fun List<LoggedClusterSegmentEdit>.update(
        position: Int,
        transform: (LoggedClusterSegmentEdit) -> LoggedClusterSegmentEdit
    ): List<LoggedClusterSegmentEdit> = map { segment ->
        if (segment.position == position) transform(segment) else segment
    }

    fun onLoggedSetRirChanged(value: String) = updateLoggedSetEdit { copy(rir = value) }

    fun onLoggedSetTimeChanged(millis: Long): Boolean {
        if (millis > runtime.nowMillis()) return false
        updateLoggedSetEdit { copy(performedAtMillis = millis) }
        return true
    }

    private fun updateLoggedSetEdit(transform: LoggedSetEdit.() -> LoggedSetEdit) {
        if (_state.value.savingLoggedSet) return
        _state.update {
            it.copy(loggedSetEdit = it.loggedSetEdit?.transform(), loggedSetEditFailed = false)
        }
    }

    fun cancelLoggedSetEdit() {
        if (!_state.value.savingLoggedSet) {
            _state.update { it.copy(loggedSetEdit = null, loggedSetEditFailed = false) }
        }
    }

    fun saveLoggedSetEdit() {
        val current = _state.value
        val edit = current.loggedSetEdit ?: return
        if (current.savingLoggedSet || !edit.canSave) return
        if (edit.performedAtMillis > runtime.nowMillis()) return
        val weight = when {
            edit.metric == SetMetric.FORCE -> null
            edit.row.loadKind == LoadKind.BODYWEIGHT -> null
            edit.weightInput == edit.originalWeightInput -> edit.row.weightKg
            edit.weightInput.isBlank() -> null
            else -> edit.weightUnit.displayToKilograms(
                requireNotNull(edit.weightInput.toDoubleOrNull())
            )
        }
        _state.update { it.copy(savingLoggedSet = true, loggedSetEditFailed = false) }
        viewModelScope.launch {
            try {
                logMutations.correctSet(
                    edit.row.id,
                    WorkoutSetCorrection(
                        reps = edit.correctionReps,
                        weightKg = weight,
                        rir = edit.rir.toIntOrNull(),
                        performedAtMillis = edit.performedAtMillis,
                        durationSeconds = edit.correctionDurationSeconds,
                        distanceMeters = edit.correctionDistanceMeters,
                        forceNewtons = edit.correctionForceNewtons,
                        device = edit.correctionDevice,
                        clusterSegments = edit.correctionCluster(weight)
                    )
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _state.update { it.copy(loggedSetEditFailed = true) }
                return@launch
            } finally {
                _state.update { it.copy(savingLoggedSet = false) }
            }
            _state.update { it.copy(loggedSetEdit = null) }
            // A correction edits recorded work; it neither ends the workout nor its running rest.
            resetShortRestCoaching()
            refreshRecentSets()
            if (activeActivation != null) refreshOccurrence()
            // A correction opened from the summary must be reflected there: re-read it rather than
            // leaving the pre-correction reconciliation on screen.
            summaryOccurrenceId?.let { loadWorkoutSummary(it) }
        }
    }

    /**
     * Applies a picked local date and wall-clock time to an existing logged set. Returns false when
     * the resulting instant is in the future; the set is left unchanged in that case.
     */
    fun correctSetTime(
        setId: Long,
        dateStartOfDayUtcMillis: Long,
        hour: Int,
        minute: Int
    ): Boolean {
        val millis = pickedLocalDateTimeToEpochMillis(
            dateStartOfDayUtcMillis,
            hour,
            minute,
            runtime.utcOffsetMillis()
        )
        if (millis > runtime.nowMillis()) return false
        viewModelScope.launch {
            logMutations.correctTime(setId, millis)
            // Time corrections leave the workout and any running rest prompt untouched.
            resetShortRestCoaching()
            refreshRecentSets()
        }
        return true
    }

    /** Reveals the weight field for an addable bodyweight exercise so added load can be logged. */
    fun onRevealWeight() {
        val exercise = exercises.firstOrNull { it.id == _state.value.selectedExerciseId } ?: return
        if (exercise.loadCapability != ExerciseLoadCapability.BODYWEIGHT_ADDABLE) return
        manualInputGeneration++
        _state.update { it.copy(weightRevealed = true) }
    }

    fun onExerciseSearchChanged(value: String) {
        _state.update { it.copy(exerciseSearch = value) }
    }

    fun onRepsChanged(value: String) {
        manualInputGeneration++
        _state.update { it.copy(reps = value.filter(Char::isDigit)) }
    }

    fun onDurationSecondsChanged(value: String) {
        manualInputGeneration++
        _state.update { it.copy(durationSeconds = value.filter(Char::isDigit)) }
    }

    /** Distance is entered in the user's display unit and converted to whole metres on submit. */
    fun onDistanceChanged(value: String) {
        manualInputGeneration++
        _state.update {
            val text = value
            val parsed = text.toDoubleOrNull()
            it.copy(
                distanceMeters = text,
                manualDistanceMetres = parsed?.takeIf { number -> number.isFinite() }
                    ?.let(it.distanceUnit::displayToMetres)
            )
        }
    }

    /** Force is entered in canonical newtons and submitted unchanged. */
    fun onForceChanged(value: String) {
        manualInputGeneration++
        _state.update {
            it.copy(forceNewtons = value.filter { char -> char.isDigit() || char == '.' })
        }
    }

    fun onDeviceNameChanged(value: String) {
        manualInputGeneration++
        _state.update { it.copy(deviceName = value) }
    }

    /** The rating is descriptive; it is never converted into measured force or lifted mass. */
    fun onDeviceRatingChanged(value: String) {
        manualInputGeneration++
        _state.update {
            it.copy(deviceRatingKg = value.filter { char -> char.isDigit() || char == '.' })
        }
    }

    /**
     * Switching the manual form's metric clears the other fields, so only one can be submitted. A
     * device belongs to a force reading only, so leaving Force also clears it.
     */
    fun onMetricChanged(metric: SetMetric) {
        manualInputGeneration++
        _state.update {
            when (metric) {
                SetMetric.REPETITIONS -> it.copy(
                    metric = metric,
                    durationSeconds = "",
                    distanceMeters = "",
                    manualDistanceMetres = null,
                    forceNewtons = "",
                    deviceName = "",
                    deviceRatingKg = ""
                )
                SetMetric.DURATION -> it.copy(
                    metric = metric,
                    reps = "",
                    distanceMeters = "",
                    manualDistanceMetres = null,
                    forceNewtons = "",
                    deviceName = "",
                    deviceRatingKg = ""
                )
                SetMetric.DISTANCE -> it.copy(
                    metric = metric,
                    reps = "",
                    durationSeconds = "",
                    forceNewtons = "",
                    deviceName = "",
                    deviceRatingKg = ""
                )
                SetMetric.FORCE -> it.copy(
                    metric = metric,
                    reps = "",
                    durationSeconds = "",
                    distanceMeters = "",
                    manualDistanceMetres = null
                )
            }
        }
    }

    fun onWeightChanged(value: String) {
        manualInputGeneration++
        _state.update {
            val text = value
            val parsed = text.toDoubleOrNull()
            it.copy(
                weightInput = text,
                manualWeightKg = parsed?.takeIf { number -> number.isFinite() && number >= 0.0 }
                    ?.let(it.weightUnit::displayToKilograms)
            )
        }
    }

    /** RIR is optional; non-digits are dropped and values above the maximum are ignored outright. */
    fun onRirChanged(value: String) {
        val digits = value.filter(Char::isDigit)
        val withinRange = digits.toIntOrNull()
            ?.let { it <= FatigueConfig.DEFAULT_MAX_RIR.toInt() }
            ?: false
        if (digits.isEmpty() || withinRange) {
            manualInputGeneration++
            _state.update { it.copy(rir = digits) }
        }
    }

    fun onWarmupToggled(isWarmup: Boolean) {
        manualInputGeneration++
        _state.update { it.copy(isWarmup = isWarmup) }
    }

    fun deleteSet(id: Long) {
        viewModelScope.launch {
            logMutations.delete(id)
            // A deletion is an edit of recorded work, not a workout end: rest keeps counting down.
            resetShortRestCoaching()
            refreshRecentSets()
            // A deleted set changes the active occurrence's remaining/progress, so refresh it too.
            if (activeActivation != null) refreshOccurrence()
        }
    }

    /**
     * Persists the manual form as one set. The submission guard is claimed synchronously and held
     * through the post-write success handling, so a repeated tap during either the write or its
     * refresh cannot record a second set. A failed write keeps every entered value and is retried
     * through this same action; a successful write is never reported as unsaved.
     */
    fun log() {
        if (manualLogSubmissionInProgress) return
        val current = _state.value
        val exerciseId = current.selectedExerciseId ?: return
        // A rating without a device name is not a storable attribution, so the form cannot submit.
        if (!current.deviceIsValid) return
        // Exactly one metric is submitted, so the field the user did not select is never parsed: the
        // selected one must be a positive whole number or the submission is refused outright.
        val reps = if (current.metric == SetMetric.REPETITIONS) current.reps.toIntOrNull() else null
        val durationSeconds = if (current.metric == SetMetric.DURATION) {
            current.durationSeconds.toIntOrNull()
        } else {
            null
        }
        val distanceMeters = if (current.metric == SetMetric.DISTANCE) {
            if (current.distanceMeters.isBlank()) null else current.manualDistanceMetres
        } else {
            null
        }
        val forceNewtons = if (current.metric == SetMetric.FORCE) {
            current.forceNewtons.toDoubleOrNull()?.takeIf { it.isFinite() && it > 0.0 }
        } else {
            null
        }
        val device = if (forceNewtons != null) {
            deviceInputOrNull(current.deviceName, current.deviceRatingKg)
        } else {
            null
        }
        if ((reps ?: 0) <= 0 &&
            (durationSeconds ?: 0) <= 0 &&
            (distanceMeters ?: 0) <= 0 &&
            (forceNewtons ?: 0.0) <= 0.0
        ) {
            return
        }
        // A hidden weight field (bodyweight exercise not revealed, or any force reading) never logs
        // a weight, even if a value was retained from a previous exercise.
        val typedWeight = if (current.showWeightField) {
            if (current.weightInput.isBlank()) null else current.manualWeightKg
        } else {
            null
        }
        if (current.showWeightField &&
            current.weightInput.isNotBlank() &&
            typedWeight == null
        ) {
            return
        }
        if (current.metric == SetMetric.DISTANCE &&
            current.distanceMeters.isNotBlank() &&
            current.manualDistanceMetres == null
        ) {
            return
        }
        val capability = exercises.firstOrNull { it.id == exerciseId }?.loadCapability
            ?: ExerciseLoadCapability.EXTERNAL
        val (loadKind, weightKg) = if (forceNewtons != null) {
            // A force reading is load-free, but it still belongs to the exercise's own load identity:
            // a grip measurement on a bodyweight exercise is not an external load.
            WorkoutLoadPolicy.defaultKind(capability) to null
        } else {
            when (capability) {
                ExerciseLoadCapability.BODYWEIGHT_ONLY -> LoadKind.BODYWEIGHT to null
                ExerciseLoadCapability.BODYWEIGHT_ADDABLE ->
                    if (typedWeight != null) {
                        LoadKind.ADDED to typedWeight
                    } else {
                        LoadKind.BODYWEIGHT to null
                    }
                ExerciseLoadCapability.EXTERNAL -> LoadKind.EXTERNAL to typedWeight
                ExerciseLoadCapability.UNSPECIFIED -> LoadKind.LEGACY_UNSPECIFIED to typedWeight
            }
        }

        // Capture attribution at submission: the link and its day come from the tapped context, so a
        // later occurrence or block change cannot relabel the set.
        val link = slotFor(exerciseId)
        val dayIndex = dayIndexFor(link?.first)
        val submittedSet = WorkoutSet(
            exerciseId = exerciseId,
            reps = reps,
            durationSeconds = durationSeconds,
            distanceMeters = distanceMeters,
            forceNewtons = forceNewtons,
            device = device,
            weightKg = weightKg,
            loadKind = loadKind,
            performedAtMillis = current.performedAtMillis ?: runtime.nowMillis(),
            isWarmup = current.isWarmup,
            weekNumber = activeActivation?.weekNumber ?: acceptedPlan?.weekNumber,
            cycleNumber = activeActivation?.cycleNumber ?: acceptedPlan?.cycleNumber,
            dayIndex = dayIndex,
            rir = current.rir.toIntOrNull(),
            occurrenceId = link?.first,
            occurrenceEntryId = link?.second,
            prescriptionSnapshot = capturePrescription(
                exerciseId,
                loadKind,
                link,
                current.performedAtMillis != null,
                reps,
                durationSeconds,
                distanceMeters,
                forceNewtons
            ),
            timingProvenance = if (current.performedAtMillis != null) {
                WorkoutTimingProvenance.CATCH_UP
            } else {
                WorkoutTimingProvenance.UNKNOWN
            },
            station = current.selectedStationAttribution
        )
        // Capture the form identity at submission: if the user replaces any input, or the surrounding
        // context changes, a later completion must not reset the newer value or report a failure for
        // entries that are no longer on screen.
        val submissionInputGeneration = manualInputGeneration
        val submissionContextRevision = draftContextRevision
        // Claim the guard synchronously: a repeated tap cannot submit a second set while the first
        // write, or its refresh, is still running.
        manualLogSubmissionInProgress = true
        _state.update {
            it.copy(
                manualLogInProgress = true,
                manualLogFailed = false,
                manualLogRefreshFailed = false
            )
        }
        viewModelScope.launch {
            fun formStillCurrent(): Boolean = manualInputGeneration == submissionInputGeneration &&
                draftContextRevision == submissionContextRevision
            try {
                val persisted = try {
                    logResolved(submittedSet, current)
                    true
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    // Nothing below this point has run, so every entered value is still intact. Report
                    // the failure only while the form it describes is still on screen; a form the user
                    // replaced must not receive a stale error about entries it no longer holds.
                    if (formStillCurrent()) {
                        _state.update { it.copy(manualLogFailed = true) }
                    }
                    false
                }
                if (!persisted) return@launch
                resetShortRestCoaching()
                _state.update { state ->
                    state.copy(
                        // Keep the reps and weight so repeated sets of the same exercise do not need
                        // retyping; only the warm-up flag resets, and only while the form is unchanged
                        // since submission.
                        isWarmup = if (formStillCurrent()) false else state.isWarmup
                    )
                }
                try {
                    refreshRecentSets()
                    if (activeActivation != null) refreshOccurrence()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    // The set is already persisted: report only the refresh failure so it is never
                    // labelled unsaved and never re-logged as a duplicate. The message belongs to the
                    // submission's logging context, so a context the user left while the read was
                    // still in flight must not receive it.
                    if (draftContextRevision == submissionContextRevision) {
                        _state.update { it.copy(manualLogRefreshFailed = true) }
                    }
                }
            } finally {
                manualLogSubmissionInProgress = false
                _state.update { it.copy(manualLogInProgress = false) }
            }
        }
    }

    /**
     * Persists [set] into the session it belongs to. A live set auto-resolves; a backdated set
     * attaches to the eligible open session or starts a fresh closed one, whose id is cached so a
     * draft batch lands in the same session.
     */
    private suspend fun logResolved(
        set: WorkoutSet,
        current: WorkoutLoggerUiState,
        returnSessionId: Boolean = false
    ): String? {
        val utcOffsetMillis = runtime.utcOffsetMillis()
        if (current.performedAtMillis == null) {
            logMutations(set, utcOffsetMillis)
            return if (returnSessionId) {
                logMutations.observeOpenSession().first()?.id
            } else {
                null
            }
        }
        val cacheKey = set.performedAtMillis to current.forceNewSession
        val cached = resolvedBackdatedSessionId.takeIf { resolvedBackdatedSessionKey == cacheKey }
        if (cached != null) {
            logMutations.logInto(set, cached)
            return cached
        }
        val session = logMutations.logBackdated(set, utcOffsetMillis, current.forceNewSession)
        if (session.id != current.activeSession?.id) {
            resolvedBackdatedSessionId = session.id
            resolvedBackdatedSessionKey = cacheKey
        }
        return session.id
    }

    /**
     * Persists a performed cluster through the cluster mutation, which writes the parent set and its
     * segments in one transaction. The cluster's session is resolved from the parent's own stamp by
     * the use case, so [logResolved]'s cached backdated session is not reused here.
     */
    private suspend fun logResolvedCluster(
        set: WorkoutSet,
        cluster: WorkoutCluster,
        current: WorkoutLoggerUiState,
        returnSessionId: Boolean = false
    ): String? {
        val utcOffsetMillis = runtime.utcOffsetMillis()
        if (current.performedAtMillis == null) {
            logWorkoutSet.logCluster(set, cluster, utcOffsetMillis)
            return if (returnSessionId) logWorkoutSet.observeOpenSession().first()?.id else null
        }
        val cacheKey = set.performedAtMillis to current.forceNewSession
        val cached = resolvedBackdatedSessionId.takeIf { resolvedBackdatedSessionKey == cacheKey }
        if (cached != null) {
            logWorkoutSet.logIntoCluster(set, cluster, cached)
            return cached
        }
        val session = logWorkoutSet.logBackdatedCluster(
            set,
            cluster,
            utcOffsetMillis,
            current.forceNewSession
        )
        if (session.id != current.activeSession?.id) {
            resolvedBackdatedSessionId = session.id
            resolvedBackdatedSessionKey = cacheKey
        }
        return session.id
    }

    /**
     * The performed cluster for one logged cluster set: the parent set is always in position 0 and
     * the editor's later segments follow it. The preflight refusal already ran, so a refusal here is
     * an internal contradiction rather than a user input problem.
     */
    private fun performedCluster(
        prescription: ClusterPrescription,
        parent: WorkoutSet,
        edit: DraftEdit?
    ): WorkoutCluster {
        val later = when (
            val outcome = laterClusterSegments(
                prescription = prescription,
                inputs = edit?.laterSegments,
                weightUnit = edit?.weightUnit ?: _state.value.weightUnit
            )
        ) {
            is ClusterSegments.Refused -> error("A refused cluster draft cannot be logged")
            is ClusterSegments.Ready -> outcome.segments
        }
        return WorkoutCluster.of(
            parent = parent,
            kind = prescription.kind,
            plannedSegments = prescription.segments.size,
            additionalSegments = later
        )
    }

    /** Closes the open session; the next logged set auto-starts a new one. */
    fun endSession() {
        invalidateRestTimer()
        // Close the session-note editor before the session changes so its draft or an in-flight
        // write can never attach to the next session.
        resetSessionNoteState()
        viewModelScope.launch { logMutations.endSession(runtime.nowMillis()) }
    }

    /** Closes the current session and immediately starts a new one. */
    fun newSession() {
        invalidateRestTimer()
        resetSessionNoteState()
        viewModelScope.launch {
            logMutations.startNewSession(
                startedAtMillis = runtime.nowMillis(),
                utcOffsetMillis = runtime.utcOffsetMillis()
            )
        }
    }

    /** Recomputes today's focus/drafts, e.g. when the screen resumes after a local midnight. */
    fun onResume() {
        _state.update { it.copy(utcOffsetMillis = runtime.utcOffsetMillis()) }
        viewModelScope.launch {
            refreshToday()
            // Opening the logger is an "open time": expire a session that rolled into a new day or went idle.
            logMutations.expireOpenSession(
                nowMillis = runtime.nowMillis(),
                utcOffsetMillis = runtime.utcOffsetMillis()
            )
            resumeLifecycleChecked = true
            restoreRestTimerIfReady()
        }
    }

    private suspend fun updateTodayPlan(plan: AcceptedPlan?) {
        val nowMillis = runtime.nowMillis()
        val utcOffsetMillis = runtime.utcOffsetMillis()
        acceptedToday = plan?.dayFor(localDayOfWeek(nowMillis, utcOffsetMillis))
        suggestedWeightKgByExercise = acceptedToday?.exercises
            ?.mapNotNull { exercise ->
                exercise.suggestedWeightKg?.let { exercise.exerciseId to it }
            }
            ?.toMap()
            .orEmpty()
        // Only rebuild drafts when the plan or the local day changes, so a confirmed or dismissed
        // draft is not resurrected by a resume. A dismissal is not persisted, so it can reappear
        // after the process restarts.
        val draftsKey = Triple(
            plan?.id,
            plan?.acceptedAtMillis,
            localEpochDay(nowMillis, utcOffsetMillis)
        )
        val rebuildDrafts = draftsKey != lastDraftsKey
        if (rebuildDrafts) {
            draftContextRevision++
            cancelSetTimer()
            dismissInstructions()
        }
        val rebuiltDrafts = if (rebuildDrafts) {
            satisfiedDraftFree(
                drafts = acceptedToday?.exercises.orEmpty().map { exercise ->
                    val prefill = exercise.setTargets?.targets?.firstOrNull()
                    DraftSet(
                        exerciseId = exercise.exerciseId,
                        name = exercise.name,
                        sets = exercise.sets,
                        reps = prefill?.reps ?: exercise.reps,
                        weightKg = prefill?.weightKg ?: exercise.suggestedWeightKg,
                        loadKind = prefill?.loadKind ?: exercise.loadKind,
                        loadCapability = exercise.loadCapability,
                        explanation = exercise.explanation,
                        durationSeconds = prefill?.durationSeconds ?: exercise.durationSeconds,
                        distanceMeters = prefill?.distanceMeters ?: exercise.distanceMeters,
                        forceNewtons = prefill?.forceNewtons ?: exercise.forceNewtons,
                        setTargets = exercise.setTargets,
                        orderedPrefill = prefill,
                        cluster = exercise.cluster
                    )
                },
                nowMillis = nowMillis,
                utcOffsetMillis = utcOffsetMillis
            )
        } else {
            null
        }
        _state.update { current ->
            current.copy(
                exercises = prioritizedByToday(exercises, acceptedToday),
                todayFocus = acceptedToday?.focus,
                // Today's planned exercises become drafts the user must confirm before they count.
                draftSets = rebuiltDrafts ?: current.draftSets,
                draftEdit = if (rebuildDrafts) null else current.draftEdit,
                missingLoadPrompt = if (rebuildDrafts) null else current.missingLoadPrompt,
                legacyResolution = if (rebuildDrafts) null else current.legacyResolution,
                clusterDraftError = if (rebuildDrafts) null else current.clusterDraftError,
                draftWriteRetries = if (rebuildDrafts) emptyList() else current.draftWriteRetries,
                appliedGuidedEdits = if (rebuildDrafts) emptyMap() else current.appliedGuidedEdits,
                confirmingAllDrafts = if (rebuildDrafts) false else current.confirmingAllDrafts,
                // A rebuilt plan/day changes the form's suggested values, so a manual-set failure
                // from the previous context no longer describes what is on screen; a completed save's
                // refresh message described that same context.
                manualLogFailed = if (rebuildDrafts) false else current.manualLogFailed,
                manualLogRefreshFailed =
                if (rebuildDrafts) false else current.manualLogRefreshFailed,
                reps = current.reps,
                weightInput = if (rebuildDrafts) {
                    current.selectedExerciseId
                        ?.let { suggestedInputFor(it, current.weightUnit) }
                        ?: current.weightInput
                } else {
                    current.weightInput
                },
                manualWeightKg = if (rebuildDrafts) {
                    current.selectedExerciseId
                        ?.let(suggestedWeightKgByExercise::get)
                        ?: current.manualWeightKg
                } else {
                    current.manualWeightKg
                }
            )
        }
        // Recorded only after the rebuild lands, so a `collectLatest` cancellation during the
        // suspending read cannot suppress the next rebuild.
        if (rebuildDrafts) lastDraftsKey = draftsKey
    }

    /**
     * Drops drafts whose exercise already has a logged working set on the same local day, so a draft
     * confirmed before the ViewModel was recreated (e.g. after process death) is not offered again.
     * Any logged set counts, so a partial log (e.g. 1 of 3 prescribed sets) still drops the whole
     * draft and the remaining prescribed sets are not prompted again that day.
     */
    private suspend fun satisfiedDraftFree(
        drafts: List<DraftSet>,
        nowMillis: Long,
        utcOffsetMillis: Long
    ): List<DraftSet> {
        if (drafts.isEmpty()) return drafts
        val today = localEpochDay(nowMillis, utcOffsetMillis)
        val loggedToday = getWorkoutLog()
            .filter { set ->
                !set.isWarmup &&
                    localEpochDay(set.performedAtMillis, utcOffsetMillis) == today
            }
            .mapTo(mutableSetOf()) { it.exerciseId }
        return drafts.filterNot { it.exerciseId in loggedToday }
    }

    /** Opens the next unsaved segment; reopening resumes the persisted parent, not a new set. */
    fun openClusterLog(draft: DraftSet) {
        if (draftSubmissionInProgress) return
        val prescription = draft.cluster ?: return
        val occurrenceId = draft.occurrenceId ?: return
        if (currentOccurrence?.id != occurrenceId || currentOccurrence?.isResolved != false) return
        val generation = ++clusterLogGeneration
        val context = draftContextRevision
        viewModelScope.launch {
            try {
                val parent = logWorkoutSet.clusterParents(occurrenceId).firstOrNull {
                    it.occurrenceEntryId == draft.occurrenceEntryId &&
                        it.cluster?.isComplete == false
                }
                if (generation != clusterLogGeneration ||
                    context != draftContextRevision
                ) {
                    return@launch
                }
                val position = parent?.cluster?.performedSegments ?: 0
                val target = prescription.segments.getOrNull(position) ?: return@launch
                val unit = _state.value.weightUnit
                _state.update {
                    it.copy(
                        clusterLog = ClusterLogState(
                            draft,
                            position,
                            unit,
                            target.reps.toString(),
                            exactEntryText(unit.kilogramsToDisplay(target.weightKg)),
                            target.rir?.toString().orEmpty()
                        )
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _state.update { it.copy(clusterDraftError = ClusterDraftError.INVALID_CLUSTER) }
            }
        }
    }

    fun closeClusterLog() {
        if (draftSubmissionInProgress) return
        clusterLogGeneration++
        _state.update { it.copy(clusterLog = null) }
    }

    fun onClusterLogRepsChanged(value: String) {
        if (!draftSubmissionInProgress) {
            _state.update {
                it.copy(clusterLog = it.clusterLog?.copy(reps = value, failed = false))
            }
        }
    }

    fun onClusterLogWeightChanged(value: String) {
        if (!draftSubmissionInProgress) {
            _state.update {
                it.copy(clusterLog = it.clusterLog?.copy(weightInput = value, failed = false))
            }
        }
    }

    fun onClusterLogRirChanged(value: String) {
        if (!draftSubmissionInProgress) {
            _state.update {
                it.copy(clusterLog = it.clusterLog?.copy(rir = value, failed = false))
            }
        }
    }

    /** Each button press durably records exactly one segment under the shared submission guard. */
    fun saveClusterSegment() {
        val edit = _state.value.clusterLog ?: return
        if (!edit.canSave || !beginDraftSubmission()) return
        val context = draftContextRevision
        viewModelScope.launch {
            try {
                ensureDraftContext(context)
                val draft = edit.draft
                val occurrenceId = requireNotNull(draft.occurrenceId)
                val prescription = requireNotNull(draft.cluster)
                val parent = logWorkoutSet.clusterParents(occurrenceId).firstOrNull {
                    it.occurrenceEntryId == draft.occurrenceEntryId &&
                        it.cluster?.isComplete == false
                }
                ensureDraftContext(context)
                require((parent?.cluster?.performedSegments ?: 0) == edit.position) {
                    "The saved cluster changed; reopen it before continuing"
                }
                val segment = PerformedClusterSegment(
                    edit.position,
                    requireNotNull(edit.reps.toIntOrNull()),
                    edit.weightUnit.displayToKilograms(
                        requireNotNull(edit.weightInput.toDoubleOrNull())
                    ),
                    edit.rir.toIntOrNull()
                )
                segment.requireValid()
                if (parent == null) {
                    val current = _state.value
                    val set = WorkoutSet(
                        exerciseId = draft.exerciseId,
                        reps = segment.reps, weightKg = segment.weightKg, rir = segment.rir,
                        loadKind = LoadKind.EXTERNAL,
                        performedAtMillis = current.performedAtMillis ?: runtime.nowMillis(),
                        isWarmup = false, occurrenceId = occurrenceId,
                        occurrenceEntryId = draft.occurrenceEntryId,
                        weekNumber = activeActivation?.weekNumber,
                        cycleNumber = activeActivation?.cycleNumber,
                        dayIndex = dayIndexFor(occurrenceId),
                        prescriptionSnapshot = capturePrescription(
                            draft.exerciseId,
                            LoadKind.EXTERNAL,
                            occurrenceId to draft.occurrenceEntryId,
                            current.performedAtMillis != null,
                            segment.reps,
                            null,
                            null,
                            null
                        )?.copy(cluster = prescription)
                    )
                    logResolvedCluster(
                        set,
                        WorkoutCluster(
                            prescription.kind,
                            prescription.segments.size,
                            listOf(segment)
                        ),
                        current
                    )
                } else {
                    logWorkoutSet.resumeCluster(parent.id, listOf(segment))
                }
                ensureDraftContext(context)
                val next = prescription.segments.getOrNull(edit.position + 1)
                _state.update {
                    it.copy(
                        clusterLog = next?.let { target ->
                            edit.copy(
                                position = edit.position + 1,
                                reps = target.reps.toString(),
                                weightInput = exactEntryText(
                                    edit.weightUnit.kilogramsToDisplay(target.weightKg)
                                ),
                                rir = target.rir?.toString().orEmpty(),
                                failed = false
                            )
                        }
                    )
                }
                refreshRecentSets()
                refreshOccurrence()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _state.update { it.copy(clusterLog = it.clusterLog?.copy(failed = true)) }
            } finally {
                endDraftSubmission()
            }
        }
    }

    fun confirmDraft(draft: DraftSet) {
        if (draftSubmissionInProgress || draft !in _state.value.draftSets) return
        cancelSetTimer()
        val admitted = admitDraftWrite(draft) ?: return
        launchDraftWrite { contextRevision ->
            val written = writeDraft(
                draft,
                admitted.resolved,
                admitted.edit,
                contextRevision
            )
            if (written) {
                removeDraft(draft)
                refreshRecentSets()
                if (activeActivation != null) refreshOccurrence()
            }
            written && _state.value.confirmingAllDrafts
        }
    }

    fun editDraft(draft: DraftSet) {
        val current = _state.value
        val retry = retryFor(draft)
        val applied = draft.occurrenceEntryId
            ?.let { current.appliedGuidedEdits[it] }
            ?.takeIf { it.metadataBelongsToPendingDraft(draft) }
        // A reopened editor shows the row as it stands (including an applied overlay) converted to
        // the current display unit; the baseline weight stays canonical so a unit change cannot
        // reinterpret it. Only a reset discards the overlay by rebuilding from the source entry.
        val base = retry?.draft ?: draft
        val inherited = when {
            retry?.edit != null -> retry?.edit
            applied != null -> applied
            else -> null
        }
        _state.update {
            it.copy(
                draftEdit = DraftEdit(
                    draft = base,
                    reps = base.reps?.toString().orEmpty(),
                    durationSeconds = base.durationSeconds?.toString().orEmpty(),
                    distanceMeters = base.distanceMeters
                        ?.let { current.distanceUnit.displayWhole(it) }
                        ?.toString()
                        .orEmpty(),
                    forceNewtons = base.forceNewtons?.let(::exactEntryText).orEmpty(),
                    deviceName = inherited?.deviceName ?: base.device?.name.orEmpty(),
                    deviceRatingKg = inherited?.deviceRatingKg
                        ?: base.device?.ratingKg?.let(::exactEntryText).orEmpty(),
                    distanceUnit = current.distanceUnit,
                    weightInput = weightInputFor(base.weightKg, current.weightUnit),
                    weightUnit = current.weightUnit,
                    canonicalWeightKg = base.weightKg,
                    rir = inherited?.rir.orEmpty(),
                    weightRevealed = base.loadKind == LoadKind.ADDED,
                    // An inherited edit carries an explicit "use now" as a null time, which must
                    // not be replaced by the screen's default time.
                    performedAtMillis = if (inherited != null) {
                        inherited.performedAtMillis
                    } else {
                        current.performedAtMillis
                    },
                    performedAtExplicit = inherited?.performedAtExplicit ?: false,
                    // A reopened editor keeps the segment values it already holds, unless the display
                    // unit changed since: that text would be re-read in the new unit, so the
                    // prescribed values are seeded again instead.
                    laterSegments = inherited
                        ?.takeIf { it.weightUnit == current.weightUnit }
                        ?.laterSegments
                        ?: base.cluster?.laterSegmentInputs(current.weightUnit).orEmpty()
                ),
                clusterDraftError = null
            )
        }
    }

    fun onDraftRepsChanged(value: String) = updateDraftEdit {
        copy(reps = value.filter(Char::isDigit))
    }

    /** Edits the frozen seconds of a timed draft; a counted draft never shows this field. */
    fun onDraftDurationSecondsChanged(value: String) = updateDraftEdit {
        copy(durationSeconds = value.filter(Char::isDigit))
    }

    /** Edits the frozen distance of a distance draft, entered in the user's display unit. */
    fun onDraftDistanceChanged(value: String) = updateDraftEdit {
        copy(distanceMeters = value.filter { it.isDigit() || it == '.' })
    }

    /** Edits the frozen force of a force draft, entered in canonical newtons. */
    fun onDraftForceChanged(value: String) = updateDraftEdit {
        copy(forceNewtons = value.filter { it.isDigit() || it == '.' })
    }

    fun onDraftDeviceNameChanged(value: String) = updateDraftEdit {
        copy(deviceName = value)
    }

    /** The rating is descriptive; it is never converted into measured force or lifted mass. */
    fun onDraftDeviceRatingChanged(value: String) = updateDraftEdit {
        copy(deviceRatingKg = value.filter { it.isDigit() || it == '.' })
    }

    fun onDraftWeightChanged(value: String) = updateDraftEdit {
        copy(weightInput = value.filter { it.isDigit() || it == '.' })
    }

    fun onDraftRirChanged(value: String) {
        val digits = value.filter(Char::isDigit)
        val withinRange =
            digits.toIntOrNull()?.let { it <= FatigueConfig.DEFAULT_MAX_RIR.toInt() } ?: false
        if (digits.isEmpty() || withinRange) updateDraftEdit { copy(rir = digits) }
    }

    fun onDraftWeightRevealed() = updateDraftEdit { copy(weightRevealed = true) }

    /** Edits one later cluster segment's repetitions; segment 1 keeps [onDraftRepsChanged]. */
    fun onDraftSegmentRepsChanged(index: Int, value: String) = updateLaterSegment(index) { input ->
        input.copy(reps = value.filter(Char::isDigit))
    }

    /** Edits one later cluster segment's load, entered in the editor's frozen display unit. */
    fun onDraftSegmentWeightChanged(index: Int, value: String) =
        updateLaterSegment(index) { input ->
            input.copy(weightInput = value.filter { char -> char.isDigit() || char == '.' })
        }

    private fun updateLaterSegment(
        index: Int,
        transform: (ClusterSegmentInput) -> ClusterSegmentInput
    ) = updateDraftEdit {
        if (index !in laterSegments.indices) {
            this
        } else {
            copy(
                laterSegments = laterSegments.mapIndexed { position, input ->
                    if (position == index) transform(input) else input
                }
            )
        }
    }

    fun onDraftPerformedAtChanged(millis: Long?): Boolean {
        if (millis != null && millis > runtime.nowMillis()) return false
        if (millis != null) clearLiveSetStart()
        resolvedBackdatedSessionId = null
        resolvedBackdatedSessionKey = null
        updateDraftEdit { copy(performedAtMillis = millis, performedAtExplicit = true) }
        return true
    }

    fun cancelDraftEdit() {
        if (draftSubmissionInProgress) return
        clearLiveSetStart()
        _state.update {
            it.copy(draftEdit = null, confirmingAllDrafts = false, clusterDraftError = null)
        }
    }

    fun resetDraftEdit() {
        val edit = _state.value.draftEdit ?: return
        if (isGuidedActive && edit.draft.occurrenceEntryId != null) {
            val base = originalDraftFor(edit.draft)
            _state.update { current ->
                current.copy(
                    draftEdit = edit.copy(
                        draft = base,
                        reps = base.reps?.toString().orEmpty(),
                        durationSeconds = base.durationSeconds?.toString().orEmpty(),
                        distanceMeters = base.distanceMeters
                            ?.let { current.distanceUnit.displayWhole(it) }
                            ?.toString()
                            .orEmpty(),
                        forceNewtons = base.forceNewtons?.let(::exactEntryText).orEmpty(),
                        deviceName = base.device?.name.orEmpty(),
                        deviceRatingKg = base.device?.ratingKg?.let(::exactEntryText).orEmpty(),
                        weightInput = weightInputFor(base.weightKg, current.weightUnit),
                        weightUnit = current.weightUnit,
                        canonicalWeightKg = base.weightKg,
                        rir = "",
                        weightRevealed = base.loadKind == LoadKind.ADDED,
                        laterSegments = base.cluster?.laterSegmentInputs(
                            current.weightUnit
                        ).orEmpty(),
                        resetGuidedTargets = true,
                        performedAtMillis = current.performedAtMillis,
                        performedAtExplicit = false
                    ),
                    guidedTargetsFailed = false
                )
            }
            return
        }
        val base = sourceDraftFor(edit.draft)
        _state.update { current ->
            current.copy(
                draftEdit = DraftEdit(
                    draft = base,
                    reps = base.reps?.toString().orEmpty(),
                    durationSeconds = base.durationSeconds?.toString().orEmpty(),
                    distanceMeters = base.distanceMeters
                        ?.let { current.distanceUnit.displayWhole(it) }
                        ?.toString()
                        .orEmpty(),
                    forceNewtons = base.forceNewtons?.let(::exactEntryText).orEmpty(),
                    deviceName = base.device?.name.orEmpty(),
                    deviceRatingKg = base.device?.ratingKg?.let(::exactEntryText).orEmpty(),
                    distanceUnit = current.distanceUnit,
                    weightInput = weightInputFor(base.weightKg, current.weightUnit),
                    weightUnit = current.weightUnit,
                    canonicalWeightKg = base.weightKg,
                    rir = "",
                    weightRevealed = base.loadKind == LoadKind.ADDED,
                    performedAtMillis = current.performedAtMillis,
                    performedAtExplicit = false,
                    laterSegments = base.cluster?.laterSegmentInputs(current.weightUnit).orEmpty()
                )
            )
        }
    }

    /**
     * Applies the editor to its draft. In guided mode this only updates the pending set: the values
     * stay unwritten until the separate logging action records them.
     */
    fun confirmDraftEdit() {
        val edit = _state.value.draftEdit ?: return
        if (!edit.enteredMetricIsPositive || !edit.deviceIsValid) return
        val draft = edit.draft
        val entryId = draft.occurrenceEntryId
        if (isGuidedActive && entryId != null) {
            applyGuidedDraftEdit(entryId, edit)
            return
        }
        confirmDraft(draft)
    }

    fun cancelMissingLoadPrompt() {
        missingLoadPromptRevision++
        clearLiveSetStart()
        _state.update { it.copy(missingLoadPrompt = null, confirmingAllDrafts = false) }
    }

    fun enterMissingLoad() {
        val draft = _state.value.missingLoadPrompt ?: return
        _state.update { it.copy(missingLoadPrompt = null) }
        if (_state.value.draftEdit?.draft != draft) editDraft(draft)
    }

    fun logMissingLoadWithoutWeight() {
        val draft = _state.value.missingLoadPrompt ?: return
        if (draftSubmissionInProgress) return
        val edit = _state.value.draftEdit?.takeIf { it.draft == draft }
        val resolved = (edit?.let { editedDraft(it) } ?: draft)
            .copy(loadKind = LoadKind.EXTERNAL, weightKg = null)
        _state.update { it.copy(missingLoadPrompt = null) }
        if (isGuidedActive && resolved.occurrenceEntryId != null) {
            launchGuidedWrite(
                resolved,
                edit,
                liveCompletion = liveSetIntentEntryId == resolved.occurrenceEntryId
            )
            return
        }
        launchDraftWrite { contextRevision ->
            val written = writeDraft(draft, resolved, edit, contextRevision)
            if (written) {
                removeDraft(draft)
                refreshRecentSets()
                if (activeActivation != null) refreshOccurrence()
            }
            written && _state.value.confirmingAllDrafts
        }
    }

    fun useLastLoggedLoad() {
        val draft = _state.value.missingLoadPrompt ?: return
        val promptRevision = missingLoadPromptRevision
        val contextRevision = draftContextRevision
        val timerGeneration = liveTimerGeneration
        // Claim the guard across the suspending history lookup so a Cancel or a second action cannot
        // race the write; re-validate the prompt once the lookup returns.
        if (!beginDraftSubmission()) return
        viewModelScope.launch {
            var shouldContinue = false
            try {
                val last = getWorkoutLog()
                    .filter {
                        it.exerciseId == draft.exerciseId &&
                            !it.isWarmup &&
                            it.loadKind == LoadKind.EXTERNAL &&
                            it.weightKg != null
                    }
                    .maxWithOrNull(compareBy<WorkoutSet> { it.performedAtMillis }.thenBy { it.id })
                if (missingLoadPromptRevision != promptRevision ||
                    draftContextRevision != contextRevision ||
                    _state.value.missingLoadPrompt != draft
                ) {
                    return@launch
                }
                missingLoadPromptRevision++
                _state.update { it.copy(missingLoadPrompt = null) }
                if (last == null) {
                    if (_state.value.draftEdit?.draft != draft) editDraft(draft)
                } else {
                    val edit = _state.value.draftEdit?.takeIf { it.draft == draft }
                    val resolved = (edit?.let { editedDraft(it) } ?: draft)
                        .copy(loadKind = LoadKind.EXTERNAL, weightKg = last.weightKg)
                    if (isGuidedActive && resolved.occurrenceEntryId != null) {
                        val liveCompletion = liveSetIntentEntryId == resolved.occurrenceEntryId
                        val written = writeGuidedSet(
                            resolved,
                            edit,
                            contextRevision,
                            liveCompletion
                        )
                        applyGuidedWriteResult(
                            resolved,
                            written,
                            contextRevision,
                            timerGeneration,
                            liveCompletion
                        )
                    } else {
                        val written = writeDraft(draft, resolved, edit, contextRevision)
                        if (written) {
                            removeDraft(draft)
                            refreshRecentSets()
                            if (activeActivation != null) refreshOccurrence()
                        }
                        shouldContinue = written && _state.value.confirmingAllDrafts
                    }
                }
            } finally {
                endDraftSubmission()
            }
            if (shouldContinue) continueConfirmAllDrafts()
        }
    }

    private fun updateDraftEdit(transform: DraftEdit.() -> DraftEdit) {
        _state.update { state ->
            state.copy(
                draftEdit = state.draftEdit?.transform(),
                // A refusal described the values that were just replaced, so a new input clears it.
                clusterDraftError = null
            )
        }
    }

    /**
     * The pending row [base] with one editor's values applied, using [base] for the slot identity and
     * measure so a refreshed row keeps its current remaining count.
     */
    private fun appliedGuidedDraft(base: DraftSet, edit: DraftEdit): DraftSet =
        editedDraft(edit.copy(draft = base))

    private fun applyGuidedDraftEdit(entryId: Long, edit: DraftEdit) {
        if (draftSubmissionInProgress) return
        val occurrence = currentOccurrence ?: return
        val entry = occurrence.entries.firstOrNull { it.id == entryId } ?: return
        val base = if (edit.resetGuidedTargets) originalDraftFor(edit.draft) else edit.draft
        val edited = editedDraft(edit.copy(draft = base))
        val index = entry.sets - edit.draft.sets
        val originalTarget = originalTargets(entry).getOrNull(index) ?: return
        val currentTarget = if (edit.resetGuidedTargets) {
            originalTarget
        } else {
            entry.guidedTargets?.targetFor(entry, index) ?: originalTarget
        }
        val target = editedTarget(edited, currentTarget)
        val fields = buildSet {
            if (target.reps != currentTarget.target.reps) add(GuidedTargetField.REPS)
            if (target.durationSeconds != currentTarget.target.durationSeconds) {
                add(GuidedTargetField.DURATION_SECONDS)
            }
            if (target.distanceMeters != currentTarget.target.distanceMeters) {
                add(GuidedTargetField.DISTANCE_METERS)
            }
            if (target.forceNewtons != currentTarget.target.forceNewtons) {
                add(GuidedTargetField.FORCE_NEWTONS)
            }
            if (target.loadKind != currentTarget.target.loadKind ||
                target.weightKg != currentTarget.target.weightKg
            ) {
                add(GuidedTargetField.LOAD)
            }
        }
        val cluster = currentTarget.cluster
        val clusterPatches = if (cluster == null) {
            emptyMap()
        } else {
            edit.laterSegments.mapIndexedNotNull { offset, segment ->
                val original = cluster.segments[offset + 1]
                val reps = segment.reps.toIntOrNull() ?: return@mapIndexedNotNull null
                val weight = segment.weightInput.toDoubleOrNull()
                    ?.let(edit.weightUnit::displayToKilograms) ?: return@mapIndexedNotNull null
                val changed = buildSet {
                    if (reps != original.reps) add(ClusterSegmentField.REPS)
                    if (weight != original.weightKg) add(ClusterSegmentField.LOAD)
                }
                if (changed.isEmpty()) {
                    null
                } else {
                    offset + 1 to ClusterSegmentPatch(
                        ClusterSegmentTarget(reps, weight, original.rir),
                        changed
                    )
                }
            }.toMap()
        }
        val patches = if (fields.isEmpty() && clusterPatches.isEmpty()) {
            emptyMap()
        } else {
            (index until entry.sets).associateWith { setIndex ->
                GuidedTargetPatch(target, fields, clusterPatches)
            }
        }
        if (patches.isEmpty() && !edit.resetGuidedTargets) {
            _state.update {
                it.copy(
                    draftEdit = null,
                    appliedGuidedEdits = it.appliedGuidedEdits + (entryId to edit),
                    guidedTargetsFailed = false
                )
            }
            return
        }
        val contextRevision = draftContextRevision
        val occurrenceId = occurrence.id
        if (!beginDraftSubmission()) return
        _state.update { it.copy(guidedTargetsSaving = true, guidedTargetsFailed = false) }
        viewModelScope.launch {
            try {
                applyGuidedTargets(occurrenceId, entryId, edit.resetGuidedTargets, patches)
                if (contextRevision != draftContextRevision ||
                    currentOccurrence?.id != occurrenceId
                ) {
                    return@launch
                }
                _state.update {
                    it.copy(
                        draftEdit = null,
                        appliedGuidedEdits = it.appliedGuidedEdits + (entryId to edit),
                        guidedTargetsSaving = false
                    )
                }
                refreshOccurrence()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                if (contextRevision == draftContextRevision &&
                    currentOccurrence?.id == occurrenceId
                ) {
                    _state.update {
                        it.copy(guidedTargetsFailed = true, guidedTargetsSaving = false)
                    }
                }
            } finally {
                endDraftSubmission()
                _state.update { it.copy(guidedTargetsSaving = false) }
            }
        }
    }

    private fun editedTarget(draft: DraftSet, original: GuidedWorkoutTargetOverride): SetTarget =
        original.target.copy(
            reps = draft.reps,
            durationSeconds = draft.durationSeconds,
            distanceMeters = draft.distanceMeters,
            forceNewtons = draft.forceNewtons,
            loadKind = draft.loadKind,
            weightKg = draft.weightKg
        )

    private fun editedDraft(edit: DraftEdit): DraftSet {
        val capability = edit.draft.loadCapability
        val typed = if (edit.weightInput ==
            weightInputFor(edit.canonicalWeightKg, edit.weightUnit)
        ) {
            edit.canonicalWeightKg
        } else {
            edit.weightInput.toDoubleOrNull()?.let {
                edit.weightUnit.displayToKilograms(it)
            }
        }
        if (edit.isForce) {
            // A force reading is load-free and carries no reps, seconds or distance. Its device is
            // descriptive identity only, and a blank name explicitly records none. The slot's own load
            // identity stays as prescribed, so the set still matches the target it followed.
            return edit.draft.copy(
                reps = null,
                durationSeconds = null,
                distanceMeters = null,
                forceNewtons = edit.forceNewtons.toDoubleOrNull(),
                device = deviceInputOrNull(edit.deviceName, edit.deviceRatingKg),
                weightKg = null,
                loadKind = edit.draft.loadKind
            )
        }
        val (kind, weight) = when (capability) {
            ExerciseLoadCapability.EXTERNAL -> LoadKind.EXTERNAL to typed
            ExerciseLoadCapability.BODYWEIGHT_ONLY -> LoadKind.BODYWEIGHT to null
            ExerciseLoadCapability.BODYWEIGHT_ADDABLE -> when {
                edit.weightRevealed && typed != null -> LoadKind.ADDED to typed
                edit.draft.loadKind == LoadKind.ADDED -> LoadKind.ADDED to null
                else -> LoadKind.BODYWEIGHT to null
            }
            ExerciseLoadCapability.UNSPECIFIED -> edit.draft.loadKind to edit.draft.weightKg
        }
        val reps = if (edit.isTimed || edit.isDistance) null else edit.reps.toIntOrNull() ?: 0
        val duration = if (edit.isTimed) edit.durationSeconds.toIntOrNull() ?: 0 else null
        val distance = if (edit.isDistance) {
            edit.distanceMeters.toDoubleOrNull()?.let(edit.distanceUnit::displayToMetres) ?: 0
        } else {
            null
        }
        return edit.draft.copy(
            // The frozen measure alone is submitted, so the other fields are always cleared.
            reps = reps,
            durationSeconds = duration,
            distanceMeters = distance,
            forceNewtons = null,
            device = null,
            weightKg = weight,
            loadKind = kind
        )
    }

    /**
     * The unedited pending row behind [draft]. The displayed row may already carry an applied
     * editor overlay, so a reset or a reopened editor must rebuild from the frozen occurrence entry
     * rather than reuse the overlaid values.
     */
    private fun sourceDraftFor(draft: DraftSet): DraftSet {
        val entryId = draft.occurrenceEntryId ?: return draft
        val occurrence = currentOccurrence ?: return draft
        val entry = occurrence.entries.firstOrNull { it.id == entryId } ?: return draft
        return draftForEntry(entry, occurrence.id, draft.sets)
    }

    private fun originalDraftFor(draft: DraftSet): DraftSet {
        val entryId = draft.occurrenceEntryId ?: return draft
        val occurrence = currentOccurrence ?: return draft
        val entry = occurrence.entries.firstOrNull { it.id == entryId } ?: return draft
        val index = (entry.sets - draft.sets).coerceIn(0, entry.sets - 1)
        val original = originalTargets(entry)[index]
        val target = original.target
        return draft.copy(
            reps = target.reps,
            durationSeconds = target.durationSeconds,
            distanceMeters = target.distanceMeters,
            forceNewtons = target.forceNewtons,
            loadKind = target.loadKind,
            weightKg = target.weightKg,
            orderedPrefill = target,
            cluster = original.cluster
        )
    }

    /** One frozen entry's remaining prescribed sets as the pending draft it describes. */
    private fun draftForEntry(
        entry: OccurrenceEntry,
        occurrenceId: Long,
        remaining: Int
    ): DraftSet {
        // An ordered slot seeds the draft from its first remaining target, so an untouched
        // draft matches what it will log and is never misread as an explicit override.
        val targetIndex = entry.sets - remaining
        val effective = entry.guidedTargets?.targetFor(entry, targetIndex)
        val prefill = effective?.target ?: entry.setTargets?.targets?.getOrNull(targetIndex)
        val effectiveCluster = effective?.cluster ?: entry.cluster
        val effectiveTargets = entry.guidedTargets?.let { stored ->
            com.hydrafit.app.core.domain.engine.SlotSetTargets.of(
                originalTargets(entry).map { original ->
                    stored.overrides.firstOrNull { it.setIndex == original.setIndex }
                        ?.target ?: original.target
                },
                entry.setTargets?.recipe
            )
        } ?: entry.setTargets
        return DraftSet(
            entry.exerciseId,
            entry.exerciseName,
            remaining,
            prefill?.reps ?: entry.reps,
            prefill?.weightKg ?: if (prefill == null) entry.weightKg else null,
            prefill?.loadKind ?: entry.loadKind,
            loadCapability = entry.loadCapability,
            occurrenceId = occurrenceId,
            occurrenceEntryId = entry.id,
            explanation = entry.explanation,
            durationSeconds =
            prefill?.durationSeconds ?: if (prefill == null) entry.durationSeconds else null,
            distanceMeters =
            prefill?.distanceMeters ?: if (prefill == null) entry.distanceMeters else null,
            forceNewtons =
            prefill?.forceNewtons ?: if (prefill == null) entry.forceNewtons else null,
            setTargets = effectiveTargets,
            orderedPrefill = prefill,
            cluster = effectiveCluster
        )
    }

    /** Dumbbell load basis per catalog id; non-dumbbell exercises stay unlabeled. */
    private fun List<Exercise>.loadBases(): Map<String, LoadBasis> = mapNotNull { exercise ->
        loadBasisFor(
            requiresDumbbell = EquipmentTag.DUMBBELL in exercise.requiredEquipment,
            isUnilateral = exercise.isUnilateral
        )?.let { basis -> exercise.id to basis }
    }.toMap()

    /**
     * Claims the single-submission guard, marking the write in progress. Returns false when a write
     * is already running so overlapping confirmations cannot double-log a draft.
     */
    private fun beginDraftSubmission(): Boolean {
        if (draftSubmissionInProgress) return false
        draftSubmissionInProgress = true
        _state.update { it.copy(draftWriteInProgress = true) }
        return true
    }

    /** Releases the submission guard after a write, however it ended. */
    private fun endDraftSubmission() {
        draftSubmissionInProgress = false
        _state.update { it.copy(draftWriteInProgress = false) }
    }

    private fun setMissingLoadPrompt(draft: DraftSet) {
        missingLoadPromptRevision++
        _state.update { it.copy(missingLoadPrompt = draft) }
    }

    private fun ensureDraftContext(contextRevision: Long) {
        if (contextRevision != draftContextRevision) {
            throw CancellationException("Draft context changed")
        }
    }

    /**
     * Runs at most one draft submission at a time. [block] performs the write and returns true when a
     * Confirm-all batch should continue; the guard is held for the whole block, so a second action
     * (or a suspending lookup) cannot overlap it.
     */
    private fun launchDraftWrite(block: suspend (Long) -> Boolean) {
        val contextRevision = draftContextRevision
        if (!beginDraftSubmission()) return
        viewModelScope.launch {
            var shouldContinue = false
            try {
                shouldContinue = block(contextRevision)
            } finally {
                endDraftSubmission()
            }
            if (shouldContinue) continueConfirmAllDrafts()
        }
    }

    /**
     * Writes [resolved] (the confirmed values for [source]) set by set. On success the return is true
     * and the caller clears the row. On a partial failure the return is false and the not-yet-written
     * sets are kept as a [DraftWriteRetry] with the same one-off values, so a retry resumes exactly
     * the remaining sets. The row and retry are not persisted; a process restart starts over.
     */
    private suspend fun writeDraft(
        source: DraftSet,
        resolved: DraftSet,
        edit: DraftEdit?,
        contextRevision: Long = draftContextRevision
    ): Boolean {
        // A cluster whose entered segments cannot form valid performed work is refused outright:
        // nothing is written, and the draft and editor stay as they are for correction.
        if (refuseClusterWrite(resolved, edit)) return false
        var completed = 0
        return try {
            logDraft(resolved, edit, contextRevision, onSetLogged = { completed++ })
            ensureDraftContext(contextRevision)
            true
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            ensureDraftContext(contextRevision)
            val priorRetry = retryFor(source)
            val remaining = resolved.copy(sets = (source.sets - completed).coerceAtLeast(1))
            val retrySource = priorRetry?.source ?: source
            val savedSets = (priorRetry?.savedSets ?: 0) + completed
            _state.update {
                it.copy(
                    draftSets = it.draftSets.map { row ->
                        if (row == source || row == priorRetry?.draft) remaining else row
                    },
                    draftEdit = null,
                    missingLoadPrompt = null,
                    draftWriteRetries = it.draftWriteRetries.filterNot { retry ->
                        retry == priorRetry
                    } + DraftWriteRetry(retrySource, remaining, edit, savedSets),
                    legacyResolution = it.legacyResolution?.let { resolution ->
                        if (resolution.current.draft == source) {
                            resolution.items.drop(1).takeIf { items -> items.isNotEmpty() }
                                ?.let(::LegacyResolution)
                        } else {
                            resolution
                        }
                    },
                    confirmingAllDrafts = false
                )
            }
            false
        }
    }

    /** Removes a cleared draft and any transient state that referenced it. */
    private fun removeDraft(draft: DraftSet) {
        if (_state.value.setTimer?.draft?.sameSlotAs(draft) == true) cancelSetTimer()
        _state.update {
            it.copy(
                draftSets = it.draftSets - draft,
                draftEdit = it.draftEdit?.takeUnless { edit -> edit.draft == draft },
                missingLoadPrompt = it.missingLoadPrompt?.takeUnless { pending ->
                    pending == draft
                },
                draftWriteRetries = it.draftWriteRetries.filterNot { retry ->
                    retry.source == draft || retry.draft == draft
                },
                appliedGuidedEdits = it.appliedGuidedEdits.filterKeys { key ->
                    key != draft.occurrenceEntryId
                },
                setTimer = it.setTimer?.takeIf { timer -> !timer.draft.sameSlotAs(draft) },
                clusterDraftError = null
            )
        }
    }

    /**
     * Keeps a slot's draft after one of its prescribed sets was logged. The remaining count drops by
     * one and the row keeps following its pending targets, so a planned draft (which has no
     * occurrence to republish from) still shows the sets left. A spent slot is removed instead.
     */
    private fun consumeOnePendingSet(draft: DraftSet) {
        val remainingSets = draft.sets - 1
        if (remainingSets <= 0) {
            removeDraft(draft)
            return
        }
        val remaining = draft.copy(sets = remainingSets).followingNextTarget()
        _state.update {
            it.copy(
                draftSets = it.draftSets.map { row -> if (row == draft) remaining else row },
                draftEdit = it.draftEdit?.takeUnless { edit -> edit.draft == draft },
                missingLoadPrompt = it.missingLoadPrompt?.takeUnless { pending ->
                    pending == draft
                },
                draftWriteRetries = it.draftWriteRetries.filterNot { retry ->
                    retry.source == draft || retry.draft == draft
                },
                appliedGuidedEdits = it.appliedGuidedEdits.filterKeys { key ->
                    key != draft.occurrenceEntryId
                },
                setTimer = it.setTimer?.takeIf { timer -> !timer.draft.sameSlotAs(draft) },
                clusterDraftError = null
            )
        }
    }

    /** Seeds a draft's scalar values from its next ordered target, matching [draftForEntry]. */
    private fun DraftSet.followingNextTarget(): DraftSet {
        val targets = setTargets ?: return this
        val prefill = targets.targets.getOrNull(targets.sets - sets) ?: return this
        return copy(
            reps = prefill.reps,
            durationSeconds = prefill.durationSeconds,
            distanceMeters = prefill.distanceMeters,
            forceNewtons = prefill.forceNewtons,
            loadKind = prefill.loadKind,
            weightKg = prefill.weightKg,
            orderedPrefill = prefill
        )
    }

    /** The pending retry for [draft], if any, matched by its source row or its remaining row. */
    private fun retryFor(draft: DraftSet): DraftWriteRetry? =
        _state.value.draftWriteRetries.firstOrNull { it.source == draft || it.draft == draft }

    /** The values one gated draft write submits, with the gate decisions already made. */
    private data class AdmittedDraftWrite(val resolved: DraftSet, val edit: DraftEdit?)

    /**
     * Resolves what one confirmation writes and runs every pre-write gate the Confirm action and the
     * timed-set timer share: an unconfirmed legacy load, a missing external load, and a refused
     * cluster. Returns null when a gate took over the screen, so the caller must write nothing. The
     * gates stay here, in one place, rather than in each caller.
     */
    private fun admitDraftWrite(
        draft: DraftSet,
        measuredSeconds: Int? = null,
        guided: Boolean = false
    ): AdmittedDraftWrite? {
        val retry = retryFor(draft)
        val edit = _state.value.draftEdit?.takeIf { it.draft == draft || editedDraft(it) == draft }
        // A guided row's applied editor value is pending state that one confirmation consumes.
        val appliedEdit = if (guided) {
            draft.occurrenceEntryId?.let { _state.value.appliedGuidedEdits[it] }
        } else {
            null
        }
        // A resolved retry already decided this draft's load; otherwise an unconfirmed legacy number
        // always needs the explicit decision, even when an editor is open.
        if (retry == null) {
            legacyResolutionFor(listOf(draft))?.let { resolution ->
                _state.update { it.copy(legacyResolution = resolution) }
                return null
            }
        }
        val resolved = when {
            edit != null -> editedDraft(edit)
            appliedEdit != null -> appliedGuidedDraft(draft, appliedEdit)
            retry != null -> retry.draft
            else -> draft
        }.let { values ->
            // A timer measures the set it just performed; the prescribed target stays unchanged.
            if (measuredSeconds == null) values else values.copy(durationSeconds = measuredSeconds)
        }
        if (retry == null &&
            resolved.forceNewtons == null &&
            resolved.loadKind == LoadKind.EXTERNAL &&
            resolved.weightKg == null
        ) {
            setMissingLoadPrompt(draft)
            return null
        }
        val writeEdit = edit ?: appliedEdit ?: retry?.edit
        if (refuseClusterWrite(resolved, writeEdit)) return null
        return AdmittedDraftWrite(resolved, writeEdit)
    }

    /** The reason [draft]'s cluster cannot be logged exactly as entered, or null for ordinary work. */
    private fun clusterRefusal(draft: DraftSet, edit: DraftEdit?): ClusterDraftError? {
        val prescription = draft.cluster ?: return null
        return clusterDraftRefusal(
            prescription = prescription,
            parentReps = draft.reps,
            parentWeightKg = draft.weightKg,
            parentRir = edit?.rir?.toIntOrNull(),
            inputs = edit?.laterSegments,
            // An edit without its own frozen unit cannot happen; the current preference is only a
            // fallback so the refusal never depends on a null unit.
            weightUnit = edit?.weightUnit ?: _state.value.weightUnit
        )
    }

    /**
     * Refuses a cluster write before anything is written. The typed reason is published and the
     * draft and its editor stay in place, so the reported segment can be corrected; the draft batch
     * retry state is not involved because no write was attempted.
     */
    private fun refuseClusterWrite(draft: DraftSet, edit: DraftEdit?): Boolean {
        val error = clusterRefusal(draft, edit) ?: return false
        _state.update { it.copy(clusterDraftError = error, confirmingAllDrafts = false) }
        return true
    }

    /** The confirmed values and editor state to write for [draft], honoring an open editor or retry. */
    private fun resolveForWrite(draft: DraftSet): Pair<DraftSet, DraftEdit?> {
        val edit = _state.value.draftEdit?.takeIf { it.draft == draft || editedDraft(it) == draft }
        if (edit != null) return editedDraft(edit) to edit
        val retry = retryFor(draft)
        return (retry?.draft ?: draft) to retry?.edit
    }

    /**
     * Logs every pending draft and clears the list. A preflight stops before any write when a draft's
     * stored load meaning is unconfirmed, so a legacy number is never silently logged as external.
     */
    fun confirmAllDrafts() {
        if (draftSubmissionInProgress || _state.value.confirmingAllDrafts) return
        _state.update { it.copy(confirmingAllDrafts = true) }
        continueConfirmAllDrafts()
    }

    private fun continueConfirmAllDrafts() {
        if (!_state.value.confirmingAllDrafts) return
        if (draftSubmissionInProgress) return
        val drafts = _state.value.draftSets
        if (drafts.isEmpty()) {
            _state.update { it.copy(confirmingAllDrafts = false) }
            return
        }
        // A resolved retry already decided its load; only unresolved legacy drafts need the queue.
        val resolution = legacyResolutionFor(drafts.filter { retryFor(it) == null })
        if (resolution != null) {
            _state.update { it.copy(legacyResolution = resolution) }
            return
        }
        drafts.firstOrNull { draft ->
            retryFor(draft) == null &&
                draft.forceNewtons == null &&
                draft.loadKind == LoadKind.EXTERNAL &&
                draft.weightKg == null
        }?.let { missing ->
            setMissingLoadPrompt(missing)
            return
        }
        // A cluster the user entered incompletely is refused before the batch starts, so no earlier
        // draft is written and then left behind by the refusal.
        drafts.forEach { draft ->
            val (resolved, edit) = resolveForWrite(draft)
            if (refuseClusterWrite(resolved, edit)) return
        }
        val contextRevision = draftContextRevision
        if (!beginDraftSubmission()) return
        viewModelScope.launch {
            var currentIndex = 0
            var completed = 0
            var failed = false
            var currentResolved: DraftSet? = null
            var currentEdit: DraftEdit? = null
            try {
                drafts.forEachIndexed { index, draft ->
                    currentIndex = index
                    completed = 0
                    val (resolved, edit) = resolveForWrite(draft)
                    currentResolved = resolved
                    currentEdit = edit
                    logDraft(resolved, edit, contextRevision, onSetLogged = { completed++ })
                }
                ensureDraftContext(contextRevision)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                ensureDraftContext(contextRevision)
                val source = drafts[currentIndex]
                val priorRetry = retryFor(source)
                val resolved = currentResolved ?: resolveForWrite(source).first
                val edit = currentEdit ?: priorRetry?.edit
                val remaining = resolved.copy(sets = (source.sets - completed).coerceAtLeast(1))
                val retrySource = priorRetry?.source ?: source
                val savedSets = (priorRetry?.savedSets ?: 0) +
                    drafts.take(currentIndex).sumOf { it.sets } + completed
                _state.update {
                    it.copy(
                        draftSets = listOf(remaining) + drafts.drop(currentIndex + 1),
                        draftEdit = null,
                        missingLoadPrompt = null,
                        draftWriteRetries = it.draftWriteRetries.filter { retry ->
                            retry.draft in drafts.drop(currentIndex + 1)
                        } + DraftWriteRetry(retrySource, remaining, edit, savedSets),
                        legacyResolution = it.legacyResolution?.let { resolution ->
                            if (resolution.current.draft == source) {
                                resolution.items.drop(1).takeIf { items -> items.isNotEmpty() }
                                    ?.let(::LegacyResolution)
                            } else {
                                resolution
                            }
                        },
                        confirmingAllDrafts = false
                    )
                }
                failed = true
            } finally {
                endDraftSubmission()
            }
            if (!failed) {
                ensureDraftContext(contextRevision)
                _state.update {
                    it.copy(
                        draftSets = emptyList(),
                        draftEdit = null,
                        missingLoadPrompt = null,
                        draftWriteRetries = emptyList(),
                        confirmingAllDrafts = false,
                        clusterDraftError = null
                    )
                }
            }
            refreshRecentSets()
            // Do not rebuild occurrence drafts after a failure: it would discard the retry row.
            if (activeActivation != null && !failed) refreshOccurrence()
        }
    }

    /** Removes a draft without logging anything. */
    fun dismissDraft(draft: DraftSet) {
        if (draftSubmissionInProgress) return
        if (_state.value.setTimer?.draft?.sameSlotAs(draft) == true) cancelSetTimer()
        _state.update {
            it.copy(
                draftSets = it.draftSets - draft,
                draftEdit = it.draftEdit?.takeUnless { edit -> edit.draft == draft },
                missingLoadPrompt = it.missingLoadPrompt?.takeUnless { pending ->
                    pending == draft
                },
                draftWriteRetries = it.draftWriteRetries.filterNot { retry ->
                    retry.source == draft || retry.draft == draft
                },
                clusterDraftError = null
            )
        }
    }

    /**
     * Applies the active occurrence's progression suggestion for [entryId], then refreshes the drafts
     * so the row shows the target the write produced. The in-flight id is claimed synchronously, so a
     * repeated tap cannot submit twice; nothing is applied automatically.
     */
    fun applyProgressionSuggestion(entryId: Long) {
        if (_state.value.progressionApplyEntryId != null) return
        val occurrenceId = currentOccurrence?.id ?: return
        if (_state.value.progressionSuggestions[entryId]?.canApply != true) return
        _state.update { it.copy(progressionApplyEntryId = entryId, progressionApplyFailed = false) }
        viewModelScope.launch {
            try {
                applySlotProgressionSuggestion(occurrenceId, entryId)
                _state.update { it.copy(progressionApplyEntryId = null) }
                if (activeActivation != null && currentOccurrence?.id == occurrenceId) {
                    refreshOccurrence()
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                if (currentOccurrence?.id == occurrenceId) {
                    _state.update {
                        it.copy(progressionApplyEntryId = null, progressionApplyFailed = true)
                    }
                }
            }
        }
    }

    /**
     * Opens the measurement dialog for one unstarted guided slot. The action is offered only while
     * the occurrence has no recorded set and the slot has neither ordered targets nor a cluster.
     */
    fun openMeasureChange(entryId: Long) {
        if (_state.value.measureChange != null) return
        val occurrence = currentOccurrence ?: return
        if (entryId !in _state.value.measureChangeEntryIds) return
        val entry = occurrence.entries.firstOrNull { it.id == entryId } ?: return
        _state.update {
            it.copy(
                measureChange = MeasureChangeDialog(
                    occurrenceEntryId = entryId,
                    exerciseName = entry.exerciseName,
                    metric = entry.measureMetric(),
                    value = entry.measureInput()
                )
            )
        }
    }

    /** Picks the metric explicitly; the typed value is cleared so no old-measure digits carry over. */
    fun onMeasureChangeMetricSelected(metric: SetMetric) {
        _state.update { state ->
            val dialog = state.measureChange ?: return@update state
            if (dialog.metric == metric) {
                state
            } else {
                state.copy(
                    measureChange = dialog.copy(
                        metric = metric,
                        value = "",
                        invalidValue = false,
                        error = null
                    )
                )
            }
        }
    }

    fun onMeasureChangeValueChanged(value: String) {
        _state.update { state ->
            val dialog = state.measureChange ?: return@update state
            state.copy(
                measureChange = dialog.copy(value = value, invalidValue = false, error = null)
            )
        }
    }

    /** Closes the dialog without writing; a refused write keeps it open and retryable. */
    fun dismissMeasureChange() {
        if (_state.value.measureChange?.applying == true) return
        _state.update { it.copy(measureChange = null) }
    }

    /**
     * Applies the dialog's explicit metric and value to the occurrence entry, then refreshes the
     * drafts so the row shows the measure the write produced. A repeated apply is ignored while one
     * is in flight.
     */
    fun applyMeasureChange() {
        val dialog = _state.value.measureChange ?: return
        if (dialog.applying) return
        val occurrenceId = currentOccurrence?.id ?: return
        val value = dialog.value.trim().toDoubleOrNull()
        if (value == null || !value.isFinite() || value <= 0.0) {
            _state.update { state ->
                state.copy(measureChange = state.measureChange?.copy(invalidValue = true))
            }
            return
        }
        val metric = dialog.metric.toMeasure()
        _state.update { state ->
            state.copy(measureChange = state.measureChange?.copy(applying = true, error = null))
        }
        viewModelScope.launch {
            try {
                changeEntryMeasure(occurrenceId, dialog.occurrenceEntryId, metric, value)
                _state.update { it.copy(measureChange = null) }
                if (activeActivation != null && currentOccurrence?.id == occurrenceId) {
                    refreshOccurrence()
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (currentOccurrence?.id == occurrenceId) {
                    _state.update { state ->
                        state.copy(
                            measureChange = state.measureChange?.copy(
                                applying = false,
                                error = error.message
                            )
                        )
                    }
                }
            }
        }
    }

    private fun OccurrenceEntry.measureMetric(): SetMetric = when {
        durationSeconds != null -> SetMetric.DURATION
        distanceMeters != null -> SetMetric.DISTANCE
        forceNewtons != null -> SetMetric.FORCE
        else -> SetMetric.REPETITIONS
    }

    private fun OccurrenceEntry.measureInput(): String = when {
        durationSeconds != null -> durationSeconds.toString()
        distanceMeters != null -> distanceMeters.toString()
        forceNewtons != null -> forceNewtons.toString()
        else -> reps?.toString().orEmpty()
    }

    private fun SetMetric.toMeasure(): OccurrenceEntryMeasure = when (this) {
        SetMetric.REPETITIONS -> OccurrenceEntryMeasure.REPETITIONS
        SetMetric.DURATION -> OccurrenceEntryMeasure.SECONDS
        SetMetric.DISTANCE -> OccurrenceEntryMeasure.DISTANCE_METERS
        SetMetric.FORCE -> OccurrenceEntryMeasure.FORCE_NEWTONS
    }

    /** Logs the current legacy draft as bodyweight/no added load and advances the queue. */
    fun resolveLegacyAsBodyweight() = resolveLegacy(LoadKind.BODYWEIGHT, null)

    /** Logs the current legacy draft's recorded number as external load (only when permitted). */
    fun resolveLegacyAsExternal() {
        val item = _state.value.legacyResolution?.current ?: return
        if (!item.canBeExternal) return
        resolveLegacy(LoadKind.EXTERNAL, item.draft.weightKg)
    }

    /** Closes the resolution without logging; the drafts stay pending. */
    fun dismissLegacyResolution() {
        if (draftSubmissionInProgress) return
        clearLiveSetStart()
        val item = _state.value.legacyResolution?.current
        _state.update {
            it.copy(
                legacyResolution = null,
                confirmingAllDrafts = false,
                draftWriteRetries = it.draftWriteRetries.filterNot { retry ->
                    item != null && (retry.source == item.draft || retry.draft == item.draft)
                }
            )
        }
    }

    private fun resolveLegacy(kind: LoadKind, weightKg: Double?) {
        if (draftSubmissionInProgress) return
        val resolution = _state.value.legacyResolution ?: return
        val item = resolution.current
        val retry = retryFor(item.draft)
        val edit = _state.value.draftEdit?.takeIf { it.draft == item.draft }
        val editForWrite = edit ?: retry?.edit
        val base = when {
            edit != null -> editedDraft(edit)
            retry != null -> retry.draft
            else -> item.draft
        }
        val resolved = base.copy(loadKind = kind, weightKg = weightKg)
        if (isGuidedActive && resolved.occurrenceEntryId != null) {
            launchGuidedWrite(
                resolved,
                editForWrite,
                liveCompletion = liveSetIntentEntryId == resolved.occurrenceEntryId
            )
            return
        }
        launchDraftWrite { contextRevision ->
            val written = writeDraft(item.draft, resolved, editForWrite, contextRevision)
            if (written) {
                ensureDraftContext(contextRevision)
                val remaining = resolution.items.drop(1)
                _state.update { state ->
                    state.copy(
                        draftSets = state.draftSets - item.draft,
                        legacyResolution = remaining.takeIf { it.isNotEmpty() }
                            ?.let(::LegacyResolution),
                        draftEdit = null,
                        draftWriteRetries = state.draftWriteRetries.filterNot { retry ->
                            retry.source == item.draft || retry.draft == item.draft
                        }
                    )
                }
                refreshRecentSets()
                if (activeActivation != null) refreshOccurrence()
            }
            written && _state.value.confirmingAllDrafts && _state.value.legacyResolution == null
        }
    }

    /** The legacy drafts among [drafts] that need an explicit load decision, or null when none. */
    private fun legacyResolutionFor(drafts: List<DraftSet>): LegacyResolution? {
        val legacy = drafts.filter { it.loadKind == LoadKind.LEGACY_UNSPECIFIED }
        if (legacy.isEmpty()) return null
        return LegacyResolution(
            legacy.map { draft ->
                val capability = if (draft.loadCapability == ExerciseLoadCapability.UNSPECIFIED) {
                    exercises.firstOrNull { it.id == draft.exerciseId }?.loadCapability
                        ?: ExerciseLoadCapability.UNSPECIFIED
                } else {
                    draft.loadCapability
                }
                LegacyResolutionItem(draft, capability)
            }
        )
    }

    private suspend fun logDraft(
        draft: DraftSet,
        edit: DraftEdit? = null,
        contextRevision: Long = draftContextRevision,
        liveCompletion: Boolean = false,
        onSetLogged: () -> Unit = {},
        orderedOffsetOverride: Int? = null
    ): String? {
        if (draft.cluster != null && draft.occurrenceId != null) {
            require(
                logWorkoutSet.clusterParents(draft.occurrenceId).none {
                    it.occurrenceEntryId == draft.occurrenceEntryId &&
                        it.cluster?.isComplete == false
                }
            ) { "Resume the saved cluster before logging another set position" }
        }
        val link = if (draft.occurrenceId != null) {
            draft.occurrenceId to draft.occurrenceEntryId
        } else {
            occurrenceLink(draft.exerciseId)
        }
        // Attribution is captured here, under the same context revision each write below re-checks.
        val dayIndex = dayIndexFor(link?.first)
        var sessionId: String? = null
        val backdated = edit?.performedAtExplicit == true || _state.value.performedAtMillis != null
        // An ordered slot logs each set against its own prescribed target. A draft-level reps/load edit
        // is an explicit override and applies to every set; otherwise each set follows its own target.
        val ordered = draft.setTargets?.takeIf { !draft.isWarmup && it.sets >= draft.sets }
        // An explicit one-off edit is detected against the target the draft was actually seeded from,
        // so an untouched draft always follows its own per-set targets. The frozen measure decides
        // counted reps or timed seconds; comparing only reps would miss a timed edit entirely.
        val prefill = draft.orderedPrefill
        val measureOverridden = prefill != null &&
            if (draft.forceNewtons != null || prefill.forceNewtons != null) {
                draft.forceNewtons != prefill.forceNewtons
            } else if (draft.durationSeconds != null || prefill.durationSeconds != null) {
                draft.durationSeconds != prefill.durationSeconds
            } else if (draft.distanceMeters != null || prefill.distanceMeters != null) {
                draft.distanceMeters != prefill.distanceMeters
            } else {
                draft.reps != prefill.reps
            }
        val overridden = ordered != null &&
            prefill != null &&
            (measureOverridden || draft.weightKg != prefill.weightKg)
        // Sets already recorded for this slot were the earlier ordered entries. A single-set guided
        // submission keeps the slot's remaining position separate from the number it writes.
        val orderedOffset = orderedOffsetOverride
            ?: if (ordered == null) 0 else (ordered.sets - draft.sets).coerceAtLeast(0)
        repeat(
            minOf(ordered?.sets?.minus(orderedOffset) ?: draft.sets, draft.sets)
                .coerceAtLeast(1)
        ) { index ->
            if (contextRevision != draftContextRevision) {
                throw CancellationException("Draft context changed")
            }
            val prescribed = ordered?.targets?.getOrNull(orderedOffset + index)
            // A target is followed only when the draft was not edited and the slot's own load kind
            // matches it; a bodyweight draft never adopts an external target's kind.
            val followsTarget = prescribed != null &&
                !overridden &&
                prescribed.loadKind == draft.loadKind
            val current = _state.value.copy(
                performedAtMillis = if (edit?.performedAtExplicit == true) {
                    edit.performedAtMillis
                } else {
                    _state.value.performedAtMillis
                }
            )
            val setForce = if (followsTarget) {
                prescribed?.forceNewtons
            } else {
                draft.forceNewtons
            }
            val submittedSet = WorkoutSet(
                exerciseId = draft.exerciseId,
                reps = if (followsTarget) prescribed?.reps else draft.reps,
                durationSeconds = if (followsTarget) {
                    prescribed?.durationSeconds
                } else {
                    draft.durationSeconds
                },
                distanceMeters = if (followsTarget) {
                    prescribed?.distanceMeters
                } else {
                    draft.distanceMeters
                },
                forceNewtons = setForce,
                // A device belongs to a force reading only; it never accompanies another measure.
                device = if (setForce != null) draft.device else null,
                weightKg = when {
                    setForce != null -> null
                    draft.loadKind == LoadKind.BODYWEIGHT -> null
                    followsTarget -> prescribed?.weightKg
                    else -> draft.weightKg
                },
                loadKind = if (followsTarget) {
                    requireNotNull(prescribed).loadKind
                } else {
                    draft.loadKind
                },
                performedAtMillis = if (liveCompletion) {
                    liveSetCompletedAtWallMillis ?: runtime.nowMillis()
                } else {
                    current.performedAtMillis ?: runtime.nowMillis()
                },
                isWarmup = draft.isWarmup,
                weekNumber = activeActivation?.weekNumber ?: acceptedPlan?.weekNumber,
                cycleNumber = activeActivation?.cycleNumber ?: acceptedPlan?.cycleNumber,
                dayIndex = dayIndex,
                occurrenceId = link?.first,
                occurrenceEntryId = link?.second,
                prescriptionSnapshot = if (followsTarget) {
                    captureOrderedPrescription(
                        draft,
                        link,
                        orderedOffset + index,
                        requireNotNull(prescribed),
                        backdated
                    )
                } else {
                    capturePrescription(
                        draft.exerciseId,
                        draft.loadKind,
                        link,
                        backdated,
                        draft.reps,
                        draft.durationSeconds,
                        draft.distanceMeters,
                        draft.forceNewtons,
                        setIndex = (orderedOffset + index).takeIf {
                            link?.second?.let { entryId ->
                                currentOccurrence?.entries?.singleOrNull { it.id == entryId }
                                    ?.guidedTargets != null
                            } == true
                        }
                    )
                },
                rir = edit?.rir?.toIntOrNull(),
                timingProvenance = when {
                    liveCompletion -> WorkoutTimingProvenance.LIVE
                    current.performedAtMillis != null -> WorkoutTimingProvenance.CATCH_UP
                    else -> WorkoutTimingProvenance.UNKNOWN
                },
                startedAtElapsedMillis = if (liveCompletion) {
                    liveSetStartedAtElapsedMillis
                } else {
                    null
                },
                completedAtElapsedMillis = if (liveCompletion) {
                    liveSetCompletedAtElapsedMillis ?: runtime.elapsedRealtimeMillis()
                } else {
                    null
                }
            )
            val prescription = draft.cluster
            sessionId = if (prescription != null) {
                logResolvedCluster(
                    submittedSet,
                    performedCluster(prescription, submittedSet, edit),
                    current,
                    returnSessionId = liveCompletion
                )
            } else {
                logResolved(submittedSet, current, returnSessionId = liveCompletion)
            }
            onSetLogged()
        }
        return sessionId
    }

    /** Prefills the weight field with the accepted plan's suggestion, in the display unit. */
    private fun suggestedInputFor(exerciseId: String, unit: WeightUnit): String =
        suggestedWeightKgByExercise[exerciseId]
            ?.let { exactEntryText(unit.kilogramsToDisplay(it)) }
            .orEmpty()

    private fun suggestedRepsFor(exerciseId: String): String? = acceptedToday?.exercises
        ?.firstOrNull { it.exerciseId == exerciseId }
        ?.reps
        ?.toString()

    private fun prioritizedByToday(
        exercises: List<Exercise>,
        today: AcceptedDay?
    ): List<ExerciseOption> {
        val priority = today?.exercises
            ?.mapIndexed { index, accepted -> accepted.exerciseId to index }
            ?.toMap()
            .orEmpty()
        return exercises
            .map {
                ExerciseOption(
                    id = it.id,
                    name = it.name,
                    isBodyweight = it.loadCapability != ExerciseLoadCapability.EXTERNAL,
                    isUnilateral = it.isUnilateral,
                    loadCapability = it.loadCapability,
                    requiredEquipment = it.requiredEquipment
                )
            }
            .sortedWith(compareBy({ priority[it.id] ?: Int.MAX_VALUE }, { it.name }))
    }

    /** Refreshes today's context: the active block's occurrence when one is active, else the plan. */
    private suspend fun refreshToday() {
        if (activeActivation != null) refreshOccurrence() else updateTodayPlan(acceptedPlan)
    }

    /**
     * Recomputes the current workout of the active block: the selected occurrence (or the oldest
     * unresolved one), its remaining sets as drafts, and its performed/prescribed progress.
     */
    private suspend fun refreshOccurrence() {
        val activation = activeActivation ?: return
        // Any suggestion is invalid for the duration of this refresh's read: a ramp tap arriving
        // before the new guidance is published must not write values resolved against a stale slot.
        val generation = ++warmupGuidanceGeneration
        val selectedId = scheduleState?.selectedOccurrenceId
        val occurrence = currentOccurrences.firstOrNull { it.id == selectedId }
            ?: currentOccurrences.filterNot { it.isResolved }.minByOrNull { it.queuePosition }
        val previousOccurrence = currentOccurrence
        val previousOccurrenceId = previousOccurrence?.id
        // A new or removed occurrence replaces the logging context: the same change that invalidates
        // an in-flight submission's completion also drops the failure feedback it left behind.
        val occurrenceContextChanged = previousOccurrence != occurrence
        // Only a different workout invalidates a running countdown. Re-reading or updating the same
        // occurrence (progress, recorded sets) keeps it, so an edit never cancels rest.
        val workoutIdentityChanged =
            previousOccurrenceId != null && previousOccurrenceId != occurrence?.id
        if (occurrence == null) {
            if (occurrenceContextChanged) {
                draftContextRevision++
                cancelSetTimer()
                dismissInstructions()
                invalidateRestTimer()
                liveSetIntentEntryId = null
            }
            currentOccurrence = null
            rampTargetsByEntry = emptyMap()
            _state.update {
                it.copy(
                    activeOccurrence = null,
                    draftSets = emptyList(),
                    draftEdit = null,
                    missingLoadPrompt = null,
                    legacyResolution = null,
                    draftWriteRetries = emptyList(),
                    confirmingAllDrafts = false,
                    appliedGuidedEdits = emptyMap(),
                    progressionSuggestions = emptyMap(),
                    progressionApplyEntryId = null,
                    progressionApplyFailed = false,
                    measureChangeEntryIds = emptySet(),
                    measureChange = null,
                    todayFocus = null,
                    guidedProgress = null,
                    guidedExpandedEntries = emptyMap(),
                    frozenEntryIds = emptySet(),
                    guidedSetWriteFailed = false,
                    // A removed occurrence leaves the context a manual-set failure described, and the
                    // context a completed save's refresh message described.
                    manualLogFailed = if (occurrenceContextChanged) false else it.manualLogFailed,
                    manualLogRefreshFailed =
                    if (occurrenceContextChanged) false else it.manualLogRefreshFailed
                ).withWarmupCleared()
            }
            warmupGuidancePublishedGeneration = generation
            return
        }
        if (occurrenceContextChanged) {
            draftContextRevision++
            cancelSetTimer()
            clusterLogGeneration++
            _state.update { it.copy(clusterLog = null) }
            dismissInstructions()
            // A replaced occurrence no longer matches the context a manual-set failure or a completed
            // save's refresh message described.
            _state.update {
                it.copy(
                    manualLogFailed = false,
                    manualLogRefreshFailed = false,
                    // Progression suggestions belong to one occurrence's slots, and a failed apply
                    // describes the occurrence it was started for; a new occurrence starts clean.
                    progressionSuggestions = emptyMap(),
                    progressionApplyEntryId = null,
                    progressionApplyFailed = false,
                    // Applied one-off values belong to their workout; a different workout discards them.
                    appliedGuidedEdits = if (workoutIdentityChanged) {
                        emptyMap()
                    } else {
                        it.appliedGuidedEdits
                    }
                )
            }
            if (previousOccurrence != null) {
                liveSetIntentEntryId = null
                if (workoutIdentityChanged) invalidateRestTimer()
            }
        }
        val recorded = occurrenceRecordedSets(occurrence)
        val performed = recorded.performed
        val frozenEntryIds = recorded.frozenEntryIds
        // The read may suspend. If a newer refresh or a block change has started since, this result
        // is superseded: publishing it would republish a stale occurrence and guidance and would
        // leave the published generation behind the current one, refusing every later ramp tap.
        if (generation != warmupGuidanceGeneration) return
        val drafts = occurrence.entries.mapNotNull { entry ->
            val remaining = entry.sets - (performed[entry.id] ?: 0)
            if (remaining <= 0) null else draftForEntry(entry, occurrence.id, remaining)
        }
        // Applying a suggestion writes only while the occurrence has no recorded set at all. This is
        // stricter than the per-slot apply guard, which refuses only the slot that has recorded work,
        // so a warm-up or a partial cluster blocks the offer here: publishing one would show an Apply
        // that must then fail.
        val started = recorded.anyRecorded
        // A measure correction is offered only before any work exists and only for slots whose
        // structure a single scalar measure can replace; a live started set owns its own measure.
        val measureChangeEntryIds = if (started ||
            occurrence.status == OccurrenceStatus.IN_PROGRESS
        ) {
            emptySet()
        } else {
            occurrence.entries
                .filter { entry ->
                    entry.setTargets == null &&
                        entry.cluster == null &&
                        entry.id != _state.value.startedSetEntryId
                }
                .map { it.id }
                .toSet()
        }
        // One batched read supplies every policy-bearing slot's decision for this occurrence. It is
        // never per draft, and a hold/review is stored but stays silent in the Logger. The suggestion
        // is optional UI: a failed read must not turn an ordinary logger refresh into a failure.
        val progressionSuggestions = if (started) {
            emptyMap()
        } else {
            try {
                suggestSlotProgression.decisionsFor(occurrence.id)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                emptyMap()
            }
        }
        // The suggestion read may suspend too; a superseded refresh must not publish its map against
        // the newer occurrence and guidance.
        if (generation != warmupGuidanceGeneration) return
        // Each partial-write retry keeps its own row and one-off values across a refresh.
        val retries = _state.value.draftWriteRetries.filter { retry ->
            drafts.any { it.sameSlotAs(retry.source) }
        }
        val appliedEdits = _state.value.appliedGuidedEdits.filterValues { edit ->
            drafts.any { draft -> edit.metadataBelongsToPendingDraft(draft) }
        }
        val displayDrafts = drafts.map { draft ->
            retries.firstOrNull { draft.sameSlotAs(it.source) }?.draft ?: draft
        }
        val workout = activation.workouts.firstOrNull { it.id == occurrence.activationWorkoutId }
        val prescribed = occurrence.entries.sumOf { it.sets }
        val performedTotal = occurrence.entries.sumOf {
            (performed[it.id] ?: 0).coerceAtMost(it.sets)
        }
        // Transient per-workout state follows the same occurrence; switching away clears it.
        val sameOccurrence = previousOccurrenceId == occurrence.id
        val equipment = warmupEquipment
        val rampTargets = effectiveRampTargets(occurrence, performed)
        rampTargetsByEntry = rampTargets
        val nextGuidance = buildWarmupGuidance(
            occurrence,
            equipment,
            _state.value.warmupPreparationOverrides,
            rampTargets
        )
        // A slot whose prescription changed loses its transient edits, so a value typed for the old
        // exercise can never be shown against the replacement.
        val previousRamps = _state.value.warmupGuidance?.ramps.orEmpty()
            .associateBy { it.occurrenceEntryId }
        val changedSlots = nextGuidance.ramps
            .filter { plan ->
                previousRamps[plan.occurrenceEntryId]?.let { !it.samePrescriptionAs(plan) } == true
            }
            .map { it.occurrenceEntryId }
            .toSet()
        val rampEdits = _state.value.warmupRampEdits.filterKeys { key ->
            sameOccurrence &&
                key.occurrenceEntryId !in changedSlots &&
                // A slot that no longer has a ramp (for example unowned equipment) keeps no edit.
                nextGuidance.ramps.any { it.occurrenceEntryId == key.occurrenceEntryId }
        }
        // Publish the occurrence only once its performed-set read has finished, so the guidance shown
        // and the occurrence a ramp write resolves against can never describe different prescriptions.
        // A timer belongs to the pending slot it measures; a refresh that consumes or replaces that
        // slot leaves nothing to confirm, so both the timer and its ticker end here.
        val timerSlotStillPending = _state.value.setTimer?.draft?.let { timerDraft ->
            displayDrafts.any { draft -> timerDraft.sameSlotAs(draft) }
        } ?: true
        if (!timerSlotStillPending) cancelSetTimer()
        currentOccurrence = occurrence
        val progress = buildGuidedWorkoutProgress(occurrence, performed)
        _state.update {
            it.copy(
                activeOccurrence = ActiveOccurrence(
                    occurrenceId = occurrence.id,
                    workoutName = workout?.name.orEmpty(),
                    performedSets = performedTotal,
                    prescribedSets = prescribed
                ),
                draftSets = displayDrafts,
                measureChangeEntryIds = measureChangeEntryIds,
                measureChange = it.measureChange?.takeIf { dialog ->
                    sameOccurrence && dialog.occurrenceEntryId in measureChangeEntryIds
                },
                progressionSuggestions = progressionSuggestions,
                draftEdit = it.draftEdit?.takeIf { edit ->
                    edit.draft in displayDrafts ||
                        displayDrafts.any { draft -> edit.targetsPendingDraft(draft) }
                },
                missingLoadPrompt = it.missingLoadPrompt?.takeIf { draft ->
                    draft in displayDrafts
                },
                legacyResolution = it.legacyResolution?.takeIf { resolution ->
                    resolution.items.all { item -> item.draft in displayDrafts }
                },
                draftWriteRetries = retries,
                appliedGuidedEdits = appliedEdits,
                // A batch continuation is scoped to one occurrence: switching away ends it.
                confirmingAllDrafts = it.confirmingAllDrafts &&
                    sameOccurrence &&
                    displayDrafts.isNotEmpty(),
                todayFocus = workout?.focus,
                frozenEntryIds = frozenEntryIds,
                warmupGuidance = nextGuidance,
                // Readiness and guidance share the snapshot and publication: a superseded refresh
                // cannot expose the previous guidance's ramps while a newer read is still pending.
                warmupEquipmentKnown = equipment != null,
                // Preparation marks and the hidden flag belong to one workout: switching occurrence
                // clears them, while an ordinary refresh of the same occurrence keeps them.
                warmupPreparationChecked = if (sameOccurrence) {
                    it.warmupPreparationChecked
                } else {
                    emptySet()
                },
                warmupPreparationEdits = if (sameOccurrence) {
                    it.warmupPreparationEdits
                } else {
                    emptyMap()
                },
                warmupPreparationSkipped = if (sameOccurrence) {
                    it.warmupPreparationSkipped
                } else {
                    emptySet()
                },
                warmupRampEdits = rampEdits,
                warmupEditor = it.warmupEditor?.let { editor ->
                    reconcileWarmupEditor(editor, sameOccurrence, changedSlots, nextGuidance)
                },
                warmupGuidanceHidden = sameOccurrence && it.warmupGuidanceHidden,
                warmupRampWriteFailed = sameOccurrence && it.warmupRampWriteFailed,
                guidedSetWriteFailed = it.guidedSetWriteFailed && sameOccurrence,
                // A skipped pair turn and a session-local unpair belong to one workout, not to the
                // stored prescription: switching occurrence forgets both.
                supersetSkippedEntryId = it.supersetSkippedEntryId.takeIf { sameOccurrence },
                supersetUnpairedKeys = if (sameOccurrence) it.supersetUnpairedKeys else emptySet()
            ).withGuidedProgress(progress, sameOccurrence)
        }
        warmupGuidancePublishedGeneration = generation
    }

    /**
     * The occurrence + entry a set for [exerciseId] belongs to, when a block is active.
     *
     * Attribution needs the entry id, not just the exercise: the replacement and completion guards
     * key on it. A null entry means the set is linked to the occurrence only, which those guards
     * would then miss, so fall back to the prescribed entry when the id alone does not match.
     */
    private fun occurrenceLink(exerciseId: String): Pair<Long, Long?>? {
        val occurrence = currentOccurrence ?: return null
        val entry = occurrence.entries.firstOrNull { it.exerciseId == exerciseId }
        return occurrence.id to entry?.id
    }

    /** The frozen slot a manual log belongs to, reusing the draft's identity when one is present. */
    private fun slotFor(exerciseId: String): Pair<Long, Long?>? = _state.value
        .draftSets
        .firstOrNull { it.exerciseId == exerciseId && it.occurrenceId != null }
        ?.let { requireNotNull(it.occurrenceId) to it.occurrenceEntryId }
        ?: occurrenceLink(exerciseId)

    /**
     * The zero-based position, inside the active block's frozen workouts, of the workout the
     * occurrence belongs to. Null when there is no active block or the occurrence is not part of it,
     * so an unattributed set keeps its plan-calendar day. The position - never the occurrence's queue
     * index or the weekday - is what the block's day label is derived from.
     */
    private fun blockDayIndex(occurrenceId: Long?): Int? {
        val activation = activeActivation ?: return null
        val workoutId = occurrenceId
            ?.let { id -> currentOccurrences.firstOrNull { it.id == id }?.activationWorkoutId }
            ?: return null
        return activation.workouts.firstOrNull { it.id == workoutId }?.position
    }

    /** The day a new set is attributed to: its occurrence's block position, else the plan calendar. */
    private fun dayIndexFor(occurrenceId: Long?): Int? =
        blockDayIndex(occurrenceId) ?: acceptedToday?.dayIndex

    /** Capture the submitted slot, never the edited performance or a calendar guess for past work. */
    private fun capturePrescription(
        exerciseId: String,
        kind: LoadKind,
        link: Pair<Long, Long?>?,
        backdated: Boolean,
        reps: Int?,
        durationSeconds: Int?,
        distanceMeters: Int?,
        forceNewtons: Double?,
        setIndex: Int? = null
    ): WorkoutPrescriptionSnapshot? {
        val captured = captureSlotPrescription(
            exerciseId,
            kind,
            link,
            backdated,
            setIndex
        ) ?: return null
        // A reps/duration/distance/force switch is a legitimate edit, but the captured slot keeps
        // its own measure. Recording the slot's target against a differently measured set would
        // contradict itself, so the set simply carries no target and its exposure stays unjudgeable.
        val sameMeasurement = (captured.reps != null) == (reps != null) &&
            (captured.durationSeconds != null) == (durationSeconds != null) &&
            (captured.distanceMeters != null) == (distanceMeters != null) &&
            (captured.forceNewtons != null) == (forceNewtons != null)
        return captured.takeIf { sameMeasurement }
    }

    /**
     * The recorded target for one set of an ordered slot. The slot's own frozen targets are the
     * prescription, so the snapshot is taken from them and carries this set's index; a set that is
     * not attributable to a slot records no target, exactly as the repeated path does.
     */
    private fun captureOrderedPrescription(
        draft: DraftSet,
        link: Pair<Long, Long?>?,
        setIndex: Int,
        prescribed: SetTarget,
        backdated: Boolean
    ): WorkoutPrescriptionSnapshot? {
        val targets = draft.setTargets ?: return null
        if (setIndex !in targets.targets.indices) return null
        val (policy, isDeload) = if (link != null) {
            val occurrence = currentOccurrence?.takeIf { it.id == link.first } ?: return null
            val entry = occurrence.entries.singleOrNull {
                it.id == link.second && it.exerciseId == draft.exerciseId
            } ?: return null
            // The draft's targets resolve this entry's own prescription, which for a scalar slot is
            // synthesized from its guided overrides; a plan draft never carries those.
            if (targets.sets != entry.sets) return null
            entry.progressionPolicy to isDeloadWeek(activeActivation?.weekNumber)
        } else {
            if (backdated || activeActivation != null) return null
            val entry = acceptedToday?.exercises?.singleOrNull {
                it.exerciseId == draft.exerciseId
            } ?: return null
            if (entry.setTargets?.sets != targets.sets) return null
            entry.progressionPolicy to isDeloadWeek(acceptedPlan?.weekNumber)
        }
        return WorkoutPrescriptionSnapshot(
            exerciseId = draft.exerciseId,
            sets = targets.sets,
            reps = prescribed.reps,
            durationSeconds = prescribed.durationSeconds,
            distanceMeters = prescribed.distanceMeters,
            forceNewtons = prescribed.forceNewtons,
            loadKind = prescribed.loadKind,
            weightKg = prescribed.weightKg,
            isDeload = isDeload,
            progressionPolicy = policy,
            setIndex = setIndex,
            targetRir = prescribed.targetRir
        )
    }

    private fun captureSlotPrescription(
        exerciseId: String,
        kind: LoadKind,
        link: Pair<Long, Long?>?,
        backdated: Boolean,
        setIndex: Int? = null
    ): WorkoutPrescriptionSnapshot? {
        if (link != null) {
            val occurrence = currentOccurrence?.takeIf { it.id == link.first } ?: return null
            val entry = occurrence.entries.singleOrNull {
                it.id == link.second && it.exerciseId == exerciseId && it.loadKind == kind
            } ?: return null
            val target = if (setIndex != null) {
                entry.guidedTargets?.targetFor(entry, setIndex)?.target
                    ?: originalTargets(entry).getOrNull(setIndex)?.target
            } else {
                null
            }
            return WorkoutPrescriptionSnapshot(
                exerciseId = exerciseId,
                sets = entry.sets,
                reps = target?.reps ?: entry.reps,
                durationSeconds = target?.durationSeconds ?: entry.durationSeconds,
                distanceMeters = target?.distanceMeters ?: entry.distanceMeters,
                forceNewtons = target?.forceNewtons ?: entry.forceNewtons,
                loadKind = target?.loadKind ?: entry.loadKind,
                weightKg = target?.weightKg ?: if (target == null) entry.weightKg else null,
                isDeload = isDeloadWeek(activeActivation?.weekNumber),
                progressionPolicy = entry.progressionPolicy,
                setIndex = setIndex,
                targetRir = target?.targetRir
            )
        }
        if (backdated || activeActivation != null) return null
        val entry = acceptedToday?.exercises?.singleOrNull {
            it.exerciseId == exerciseId && it.loadKind == kind
        } ?: return null
        return WorkoutPrescriptionSnapshot(
            exerciseId = exerciseId,
            sets = entry.sets,
            reps = entry.reps,
            durationSeconds = entry.durationSeconds,
            distanceMeters = entry.distanceMeters,
            forceNewtons = entry.forceNewtons,
            loadKind = entry.loadKind,
            weightKg = entry.suggestedWeightKg,
            isDeload = isDeloadWeek(acceptedPlan?.weekNumber),
            progressionPolicy = entry.progressionPolicy
        )
    }

    /** The periodization cycle is accept-ordinal based; a missing week is not a deload. */
    private fun isDeloadWeek(weekNumber: Int?): Boolean =
        weekNumber?.let(PeriodizationConfig()::isDeload) == true

    /**
     * One read of the current occurrence's recorded sets, returning the working-set count by entry,
     * the entry ids frozen by any recorded set (working or warm-up), and whether the occurrence has
     * any recorded set at all. All three come from one snapshot so the guidance, the replacement
     * eligibility and the progression gate describe the same recorded work.
     */
    private suspend fun occurrenceRecordedSets(
        occurrence: WorkoutOccurrence
    ): OccurrenceRecordedSets {
        val sets = getWorkoutLog()
        val occurrenceSets = sets.filter { it.occurrenceId == occurrence.id }
        val performed = occurrenceSets
            .filter { set ->
                !set.isWarmup &&
                    set.occurrenceEntryId != null &&
                    set.cluster?.isComplete != false
            }
            .groupingBy { requireNotNull(it.occurrenceEntryId) }
            .eachCount()
        val frozen = occurrence.entries
            .filter { entry ->
                sets.any { it.freezesOccurrenceSlot(occurrence.id, entry.id, entry.exerciseId) }
            }
            .map { it.id }
            .toSet()
        return OccurrenceRecordedSets(
            performed = performed,
            frozenEntryIds = frozen,
            anyRecorded = occurrenceSets.isNotEmpty()
        )
    }

    /**
     * Each entry's edited effective target at its next remaining set position, for ramp suggestions
     * only. Once every set is performed the last position is used, so the ramp does not disappear
     * while the work is still on screen. An entry without guided overrides is absent, so its ramp
     * keeps describing the frozen prescription.
     */
    private fun effectiveRampTargets(
        occurrence: WorkoutOccurrence,
        performed: Map<Long, Int>
    ): Map<Long, SetTarget> = occurrence.entries.mapNotNull { entry ->
        val targets = entry.guidedTargets ?: return@mapNotNull null
        val index = (performed[entry.id] ?: 0).coerceIn(0, (entry.sets - 1).coerceAtLeast(0))
        entry.id to targets.targetFor(entry, index).target
    }.toMap()

    /** Concludes the current workout occurrence: finish, finish partially, or skip. */
    fun finishWorkout() = resolveCurrentOccurrence(FinishMode.FULL)

    fun finishWorkoutPartially() = resolveCurrentOccurrence(FinishMode.PARTIAL)

    fun skipWorkout() = resolveCurrentOccurrence(null)

    private fun resolveCurrentOccurrence(mode: FinishMode?) {
        val occurrence = currentOccurrence ?: return
        viewModelScope.launch {
            try {
                val state = if (mode == null) {
                    loggingActions.skip(occurrence.id)
                } else {
                    loggingActions.finish(occurrence.id, mode)
                }
                // Only a finish offers a summary, and only for the block it concluded: a concurrent
                // replacement or cancellation drops it instead of overlaying a workout already left.
                if (mode == null || state.activeActivationId != occurrence.activationId) {
                    _state.update { it.copy(occurrenceMessage = null, summary = null) }
                } else {
                    _state.update { it.copy(occurrenceMessage = null) }
                    loadWorkoutSummary(occurrence.id)
                }
            } catch (error: ScheduleException) {
                _state.update { it.copy(occurrenceMessage = error.message) }
            }
        }
    }

    /**
     * Opens the summary route for [occurrenceId] and reads it. The occurrence identity is retained so
     * a failed read can be retried without re-resolving the workout; each attempt is a fresh read, so
     * the route never shows a cached reconciliation.
     */
    private suspend fun loadWorkoutSummary(occurrenceId: Long) {
        summaryOccurrenceId = occurrenceId
        _state.update { it.copy(summary = WorkoutSummaryScreen.Loading) }
        val loaded = try {
            loggingActions.summarize(occurrenceId)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            // A failed read is not an absent workout, so the two stay distinct. A later finish or a
            // block change owns the route now, and an older read must not reopen it.
            if (summaryOccurrenceId == occurrenceId) {
                _state.update { it.copy(summary = WorkoutSummaryScreen.Failed) }
            }
            return
        }
        if (summaryOccurrenceId != occurrenceId) return
        _state.update {
            it.copy(
                summary = if (loaded == null) {
                    WorkoutSummaryScreen.Unavailable
                } else {
                    WorkoutSummaryScreen.Ready(loaded)
                }
            )
        }
    }

    /** Retries a failed summary read for the workout it was opened for. */
    fun retryWorkoutSummary() {
        val occurrenceId = summaryOccurrenceId ?: return
        if (_state.value.summary !is WorkoutSummaryScreen.Failed) return
        viewModelScope.launch { loadWorkoutSummary(occurrenceId) }
    }

    fun onOccurrenceMessageShown() = _state.update { it.copy(occurrenceMessage = null) }

    /**
     * Returns from the just-finished workout's summary without keeping it. The pending history key is
     * deliberately left alone: this runs when the destination leaves composition, which happens after
     * a handoff has already recorded the key.
     */
    fun onWorkoutSummaryShown() {
        summaryOccurrenceId = null
        _state.update { it.copy(summary = null) }
    }

    /**
     * Leaves the summary for the recorded history. The browser consumes the selected entry once it
     * is on the back stack; a workout with no single session only opens the browser list.
     */
    fun onWorkoutSummaryHistoryRequested(summary: WorkoutSummary) {
        summaryOccurrenceId = null
        _state.update {
            it.copy(summary = null, pendingHistoryKey = workoutSummaryHistoryKey(summary))
        }
    }

    /** Clears the history request once the browser has opened it. */
    fun onPendingHistoryKeyConsumed() = _state.update { it.copy(pendingHistoryKey = null) }

    fun onRestDurationChanged(value: String) {
        // Pair rest belongs to the superset, not this exercise: editing it would write an override.
        if (!_state.value.restDurationIsEditable) return
        val digits = value.filter(Char::isDigit)
        _state.update { it.copy(restDurationSeconds = digits) }
        val millis = digits.toLongOrNull()?.takeIf {
            it > 0L && it <= RestCountdown.MAX_DURATION_MILLIS / 1_000L
        }
            ?.times(1_000L)
            ?: return
        restTimer.updateDuration(millis)
        val exerciseId = timerExerciseId ?: return
        viewModelScope.launch {
            settings.setExerciseRestDuration(exerciseId, millis / 1_000L)
            _state.update { it.copy(restDurationIsOverride = true) }
        }
    }

    fun resetExerciseRestDuration() {
        if (_state.value.restDurationIsPairRest) return
        val exerciseId = timerExerciseId ?: return
        viewModelScope.launch {
            settings.clearExerciseRestDuration(exerciseId)
            val seconds = settings.globalRestDurationSeconds()
            restTimer.updateDuration(seconds * 1_000L)
            _state.update {
                it.copy(restDurationSeconds = seconds.toString(), restDurationIsOverride = false)
            }
        }
    }

    fun cancelRestTimer() {
        liveTimerGeneration++
        timerOccurrenceId = null
        timerSessionId = null
        timerExerciseId = null
        restTimer.cancel()
        _state.update { it.withRestCleared() }
    }

    // ── Timed-set timer ───────────────────────────────────────────────────

    /**
     * Starts the foreground timer for [draft]'s prescribed seconds: a five-second preparation
     * countdown, then the work countdown. It records nothing by itself, and only one timer exists, so
     * this replaces any running one.
     */
    fun startSetTimer(draft: DraftSet) {
        val prescribed = draft.durationSeconds ?: return
        if (prescribed <= 0) return
        if (draft !in _state.value.draftSets) return
        // A started guided set owns the timing of its own completion; its measured seconds win.
        if (isGuidedActive && _state.value.startedSetEntryId != null) return
        val current = _state.value.setTimer
        if (current?.logInProgress == true || draftSubmissionInProgress) return
        setTimerJob?.cancel()
        val generation = ++setTimerGeneration
        setTimerWorkStartedAtElapsedMillis = null
        _state.update {
            it.copy(
                setTimer = SetTimerState(
                    draft = draft,
                    phase = SetTimerPhase.PREPARING,
                    remainingSeconds = SET_TIMER_PREPARATION_SECONDS
                ),
                setTimerNotice = null
            )
        }
        setTimerJob = viewModelScope.launch {
            var preparationRemaining = SET_TIMER_PREPARATION_SECONDS
            while (preparationRemaining > 0) {
                kotlinx.coroutines.delay(1_000L)
                if (generation != setTimerGeneration) return@launch
                preparationRemaining--
                if (preparationRemaining > 0) {
                    _state.update { state ->
                        state.setTimer?.let { timer ->
                            state.copy(
                                setTimer = timer.copy(remainingSeconds = preparationRemaining)
                            )
                        } ?: state
                    }
                }
            }
            val startedAt = runtime.elapsedRealtimeMillis()
            if (generation != setTimerGeneration) return@launch
            setTimerWorkStartedAtElapsedMillis = startedAt
            publishSetTimerPhase(SetTimerPhase.WORKING, prescribed, 0)
            while (true) {
                kotlinx.coroutines.delay(1_000L)
                if (generation != setTimerGeneration) return@launch
                val elapsed = ((runtime.elapsedRealtimeMillis() - startedAt) / 1_000L)
                    .toInt()
                    .coerceIn(0, prescribed)
                val remaining = prescribed - elapsed
                if (remaining <= 0) {
                    setTimerWorkStartedAtElapsedMillis = null
                    publishSetTimerPhase(SetTimerPhase.CONFIRMING, 0, prescribed)
                    return@launch
                }
                _state.update { state ->
                    state.setTimer?.let { timer ->
                        state.copy(
                            setTimer = timer.copy(
                                remainingSeconds = remaining,
                                measuredSeconds = elapsed
                            )
                        )
                    } ?: state
                }
            }
        }
    }

    /** Ends the work countdown early and opens the confirmation with the measured work seconds. */
    fun stopSetTimerEarly() {
        val timer = _state.value.setTimer ?: return
        if (timer.phase != SetTimerPhase.WORKING) return
        val prescribed = timer.draft.durationSeconds ?: return
        val startedAt = setTimerWorkStartedAtElapsedMillis
        // Whole seconds, floored at one: a stop during the first second still records that the set
        // was performed, and a zero-second performance would be rejected downstream.
        val measured = if (startedAt == null) {
            timer.measuredSeconds.coerceIn(1, prescribed)
        } else {
            ((runtime.elapsedRealtimeMillis() - startedAt) / 1_000L)
                .toInt()
                .coerceIn(1, prescribed)
        }
        setTimerJob?.cancel()
        setTimerJob = null
        setTimerWorkStartedAtElapsedMillis = null
        publishSetTimerPhase(
            phase = SetTimerPhase.CONFIRMING,
            remainingSeconds = (prescribed - measured).coerceAtLeast(0),
            measuredSeconds = measured
        )
    }

    /**
     * Cancels the timer and its confirmation, recording nothing. Preparation cancel, confirmation
     * discard, a context change and leaving the screen all use this one path.
     */
    fun cancelSetTimer() {
        setTimerJob?.cancel()
        setTimerJob = null
        setTimerWorkStartedAtElapsedMillis = null
        setTimerGeneration++
        _state.update { state ->
            if (state.setTimer == null) state else state.copy(setTimer = null)
        }
    }

    /**
     * Writes exactly the one measured set through the same gated submission the Confirm action uses,
     * with the prescribed target left untouched. The confirmation cannot log twice, an insert that
     * already landed is never retried, and a failed write keeps the confirmation and the measured
     * value for an explicit retry.
     */
    fun logSetFromTimer() {
        val timer = _state.value.setTimer ?: return
        if (timer.phase != SetTimerPhase.CONFIRMING || timer.logInProgress) return
        if (draftSubmissionInProgress) return
        val draft = _state.value.draftSets.firstOrNull { timer.draft.sameSlotAs(it) }
        val prescribed = draft?.durationSeconds
        if (draft == null || prescribed == null) {
            cancelSetTimer()
            return
        }
        val measured = timer.measuredSeconds.coerceIn(1, prescribed)
        val guided = isGuidedActive && draft.occurrenceEntryId != null
        val admitted = admitDraftWrite(draft, measuredSeconds = measured, guided = guided)
        if (admitted == null) {
            // A pre-write gate now owns this slot (legacy load decision, missing load, refused
            // cluster). Its own dialog resolves and writes the set from the unmodified draft, so the
            // measured seconds cannot be carried through it; close the confirmation and say so
            // rather than leaving a stale value that looks saved.
            cancelSetTimer()
            _state.update { it.copy(setTimerNotice = SetTimerNotice.GATE_TOOK_OVER) }
            return
        }
        if (guided) {
            launchGuidedTimerWrite(admitted.resolved, admitted.edit)
            return
        }
        val contextRevision = draftContextRevision
        if (!beginDraftSubmission()) return
        _state.update { state ->
            state.setTimer?.let { current ->
                state.copy(setTimer = current.copy(logInProgress = true, writeFailed = false))
            } ?: state
        }
        viewModelScope.launch {
            // A single insert either lands or does not. The callback fires only after it landed, so a
            // later failure (a context check or a refresh) can never make a retry log it twice.
            var inserted = false
            try {
                val orderedOffset = draft.setTargets
                    ?.takeIf { !draft.isWarmup }
                    ?.let { (it.sets - draft.sets).coerceAtLeast(0) }
                logDraft(
                    admitted.resolved.copy(sets = 1),
                    admitted.edit,
                    contextRevision,
                    onSetLogged = { inserted = true },
                    orderedOffsetOverride = orderedOffset
                )
                ensureDraftContext(contextRevision)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // The draft and its measured value stay pending below for an explicit retry.
            } finally {
                endDraftSubmission()
            }
            if (inserted) {
                // One measured set is consumed, so the slot keeps its remaining prescribed sets and
                // their targets. Only a spent slot is removed; the activated path republishes the
                // remaining rows from the occurrence below, and a planned row has none to rebuild
                // from.
                consumeOnePendingSet(draft)
                refreshRecentSets()
                if (activeActivation != null && contextRevision == draftContextRevision) {
                    refreshOccurrence()
                }
            } else {
                _state.update { state ->
                    state.setTimer?.let { current ->
                        state.copy(
                            setTimer = current.copy(logInProgress = false, writeFailed = true)
                        )
                    } ?: state
                }
            }
        }
    }

    /**
     * Writes the measured guided set through the guided confirmation path, so a timed set gets the
     * same success bookkeeping as a logged guided set: applied edits are consumed, the occurrence and
     * recent sets refresh, and a landed insert is never retried. A failure keeps the confirmation and
     * its measured value for an explicit retry.
     */
    private fun launchGuidedTimerWrite(resolved: DraftSet, edit: DraftEdit?) {
        if (!beginDraftSubmission()) return
        val contextRevision = draftContextRevision
        val timerGeneration = liveTimerGeneration
        _state.update { state ->
            state.setTimer?.let { current ->
                state.copy(setTimer = current.copy(logInProgress = true, writeFailed = false))
            } ?: state
        }
        viewModelScope.launch {
            var inserted = false
            try {
                val result = writeGuidedSet(
                    resolved,
                    edit,
                    contextRevision,
                    liveCompletion = false,
                    onSetLogged = { inserted = true }
                )
                if (result.succeeded) {
                    applyGuidedWriteResult(
                        resolved,
                        result,
                        contextRevision,
                        timerGeneration,
                        liveCompletion = false
                    )
                    cancelSetTimer()
                } else if (!inserted) {
                    _state.update { state ->
                        state.setTimer?.let { current ->
                            state.copy(
                                setTimer = current.copy(logInProgress = false, writeFailed = true)
                            )
                        } ?: state
                    }
                } else {
                    // The set landed; a later context failure must not offer a duplicate retry.
                    cancelSetTimer()
                }
            } catch (cancelled: CancellationException) {
                if (inserted) cancelSetTimer()
                throw cancelled
            } finally {
                endDraftSubmission()
            }
        }
    }

    /** Dismisses the timed-confirmation explanation after the user has read it. */
    fun dismissSetTimerNotice() {
        if (_state.value.setTimerNotice == null) return
        _state.update { it.copy(setTimerNotice = null) }
    }

    /**
     * Publishes a phase change without touching [SetTimerState.draft], so the prescribed target the
     * timer measures against is visibly never rewritten by the countdown.
     */
    private fun publishSetTimerPhase(
        phase: SetTimerPhase,
        remainingSeconds: Int,
        measuredSeconds: Int
    ) {
        _state.update { state ->
            state.setTimer?.let { timer ->
                state.copy(
                    setTimer = timer.copy(
                        phase = phase,
                        remainingSeconds = remainingSeconds,
                        measuredSeconds = measuredSeconds
                    )
                )
            } ?: state
        }
    }

    /** Cancels a running timer when the screen pauses; foreground-only, nothing is recorded. */
    fun onPause() {
        cancelSetTimer()
    }

    /** True when guided mode is on and a workout occurrence is active. */
    private val isGuidedActive: Boolean
        get() = _state.value.guidedEnabled && currentOccurrence != null

    /** Opens the one-off editor for an active occurrence entry's pending set. */
    fun editGuidedSet(occurrenceEntryId: Long) {
        val draft = _state.value.draftSets.firstOrNull { it.occurrenceEntryId == occurrenceEntryId }
            ?: return
        editDraft(draft)
    }

    /**
     * Expands or collapses a completed guided exercise. Only a complete exercise collapses, so an
     * unfinished one ignores this; the expansion is session-local and lasts for the recorded
     * completion it was made against.
     */
    fun toggleGuidedEntryExpanded(occurrenceEntryId: Long) {
        val entry = _state.value.guidedProgress?.exercises
            ?.firstOrNull { it.occurrenceEntryId == occurrenceEntryId } ?: return
        _state.update { it.withGuidedExpansionToggled(entry) }
    }

    /**
     * Records exactly one set for [occurrenceEntryId], leaving any remaining
     * prescribed sets pending. Reuses the same load-shape and legacy/missing-load decisions as the
     * batch path, but never writes more than one set. A live start owns the current completion, so
     * this is refused while any guided set is started: only that started entry's "Set completed now"
     * may consume it, and an ordinary log must never silently close a live start as a non-live write.
     */
    fun confirmGuidedSet(occurrenceEntryId: Long) {
        if (_state.value.startedSetEntryId != null) return
        confirmGuidedSet(occurrenceEntryId, liveCompletion = false)
    }

    fun confirmGuidedSetNow(occurrenceEntryId: Long) {
        if (_state.value.startedSetEntryId != occurrenceEntryId) return
        confirmGuidedSet(occurrenceEntryId, liveCompletion = true)
    }

    /**
     * Passes over one paired member's turn without recording a set or satisfying a target. The skip is
     * cleared by the next successful guided write, so the outstanding work returns to the rotation.
     */
    fun skipSupersetTurn(occurrenceEntryId: Long) {
        val entry = _state.value.guidedProgress?.exercises
            ?.firstOrNull { it.occurrenceEntryId == occurrenceEntryId } ?: return
        val supersetKey = entry.superset?.key ?: return
        if (entry.isComplete) return
        if (supersetKey in _state.value.supersetUnpairedKeys) return
        // Skipping the member whose set is live cancels that start intent and its elapsed timing:
        // the skipped work writes nothing and returns only after a fresh Start.
        if (_state.value.startedSetEntryId == occurrenceEntryId) {
            clearLiveSetStart()
            resetShortRestCoaching()
        }
        _state.update { it.copy(supersetSkippedEntryId = occurrenceEntryId) }
    }

    /** Clears an explicit skip so the outstanding paired work is selectable again. */
    fun resumeSupersetTurn() {
        _state.update { it.copy(supersetSkippedEntryId = null) }
    }

    /**
     * Stops alternating this pair for the rest of the workout. It changes no stored prescription and
     * no recorded set; a later occurrence still carries its frozen grouping.
     */
    fun unpairSuperset(occurrenceEntryId: Long) {
        val entry = _state.value.guidedProgress?.exercises
            ?.firstOrNull { it.occurrenceEntryId == occurrenceEntryId } ?: return
        val key = entry.superset?.key ?: return
        _state.update { it.copy(supersetUnpairedKeys = it.supersetUnpairedKeys + key) }
    }

    /** Records an explicit set-start intent without creating a performed set. */
    fun startGuidedSet(occurrenceEntryId: Long) {
        if (draftSubmissionInProgress) return
        if (_state.value.draftSets.none { it.occurrenceEntryId == occurrenceEntryId }) return
        cancelSetTimer()
        // A second Start on the already-started slot would only reset its timestamp, discarding the
        // elapsed time the live completion depends on. Starting a different untouched slot is allowed.
        if (_state.value.startedSetEntryId == occurrenceEntryId) return
        if (_state.value.performedAtMillis != null) onPerformedAtChanged(null)
        val draft = _state.value.draftSets.first { it.occurrenceEntryId == occurrenceEntryId }
        val elapsedNow = runtime.elapsedRealtimeMillis()
        if (_state.value.isWarmup) {
            resetShortRestCoaching()
        } else if (shortRestCoachingEnabled &&
            shortRestCoach.started(
                sessionId = _state.value.activeSession?.id,
                occurrenceId = requireNotNull(draft.occurrenceId),
                exerciseId = draft.exerciseId,
                bootIdentity = runtime.bootIdentity(),
                startedAtElapsedMillis = elapsedNow
            )
        ) {
            _state.update { it.copy(shortRestCoachingExerciseName = draft.name) }
        }
        // Starting work supersedes the previous completion's rest prompt: cancel its running countdown
        // and persisted row so the alert coordinator cancels the alarm. Coaching evidence is kept, and
        // the new start is recorded after this so the cancellation cannot clear it.
        if (restTimer.state.value != null) cancelRestTimer()
        liveSetIntentEntryId = occurrenceEntryId
        liveSetStartedAtElapsedMillis = elapsedNow
        liveSetCompletedAtElapsedMillis = null
        liveSetCompletedAtWallMillis = null
        _state.update { it.copy(startedSetEntryId = occurrenceEntryId) }
    }

    private fun confirmGuidedSet(occurrenceEntryId: Long, liveCompletion: Boolean) {
        if (draftSubmissionInProgress) return
        if (!liveCompletion && _state.value.startedSetEntryId != null) return
        val draft = _state.value.draftSets.firstOrNull { it.occurrenceEntryId == occurrenceEntryId }
            ?: return
        cancelSetTimer()
        // Load dialogs and failed writes retain the classification chosen for this confirmation.
        if (guidedSetIntent?.entryId != occurrenceEntryId) {
            guidedSetIntent = GuidedSetIntent(occurrenceEntryId, _state.value.isWarmup)
        }
        liveSetIntentEntryId = occurrenceEntryId.takeIf { liveCompletion }
        if (liveCompletion && liveSetCompletedAtElapsedMillis == null) {
            liveSetCompletedAtElapsedMillis = runtime.elapsedRealtimeMillis()
            liveSetCompletedAtWallMillis = runtime.nowMillis()
        }
        if (!liveCompletion) {
            liveSetStartedAtElapsedMillis = null
            _state.update { it.copy(startedSetEntryId = null) }
        }
        _state.update { it.copy(guidedSetWriteFailed = false) }
        val retry = retryFor(draft)
        if (retry == null) {
            legacyResolutionFor(listOf(draft))?.let { resolution ->
                _state.update { it.copy(legacyResolution = resolution) }
                return
            }
        }
        val openEdit = _state.value.draftEdit?.takeIf {
            it.draft == draft || editedDraft(it) == draft
        }
        // An applied editor value is pending state: logging consumes it exactly once.
        val appliedEdit = draft.occurrenceEntryId?.let { _state.value.appliedGuidedEdits[it] }
        val resolved = when {
            openEdit != null -> editedDraft(openEdit)
            appliedEdit != null -> appliedGuidedDraft(draft, appliedEdit)
            retry != null -> retry.draft
            else -> draft
        }
        if (retry == null &&
            resolved.forceNewtons == null &&
            resolved.loadKind == LoadKind.EXTERNAL &&
            resolved.weightKg == null
        ) {
            setMissingLoadPrompt(draft)
            return
        }
        launchGuidedWrite(
            resolved,
            openEdit ?: appliedEdit ?: retry?.edit,
            liveCompletion = liveSetIntentEntryId == occurrenceEntryId
        )
    }

    /** Claims the submission guard and writes a single guided set, then refreshes or reports failure. */
    private fun launchGuidedWrite(
        resolved: DraftSet,
        edit: DraftEdit?,
        liveCompletion: Boolean = false,
        forcedWarmup: Boolean? = null
    ) {
        if (refuseClusterWrite(resolved, edit)) return
        val contextRevision = draftContextRevision
        val timerGeneration = liveTimerGeneration
        if (!beginDraftSubmission()) return
        viewModelScope.launch {
            try {
                val writeResult = writeGuidedSet(
                    resolved,
                    edit,
                    contextRevision,
                    liveCompletion,
                    forcedWarmup
                )
                applyGuidedWriteResult(
                    resolved,
                    writeResult,
                    contextRevision,
                    timerGeneration,
                    liveCompletion,
                    forcedWarmup
                )
            } finally {
                endDraftSubmission()
            }
        }
    }

    /**
     * Applies the shared result of one guided-set write. Every guided resolution path routes its
     * outcome here, so a successful write always consumes the live start/completion event through
     * [clearLiveSetStart] exactly once, and starts the rest prompt only for a live working-set
     * completion whose context is still current. A failure keeps the event for an explicit retry.
     */
    private suspend fun applyGuidedWriteResult(
        resolved: DraftSet,
        writeResult: GuidedSetWriteResult,
        contextRevision: Long,
        timerGeneration: Long,
        liveCompletion: Boolean,
        forcedWarmup: Boolean? = null
    ) {
        if (writeResult.succeeded) {
            // The logged set consumed its applied values. Clear them before the refresh below
            // republishes the pending rows, so the remaining sets follow the plan again.
            _state.update {
                it.copy(
                    appliedGuidedEdits = it.appliedGuidedEdits.filterKeys { key ->
                        key != resolved.occurrenceEntryId
                    },
                    clusterDraftError = null
                )
            }
            val contextStillCurrent = contextRevision == draftContextRevision &&
                timerGeneration == liveTimerGeneration
            val supersetRest = supersetRestAfter(resolved.occurrenceEntryId)
            if (liveCompletion && !writeResult.isWarmup && contextStillCurrent && isGuidedActive) {
                startRestTimerIfDurationValid(
                    resolved.occurrenceId,
                    resolved.exerciseId,
                    writeResult.sessionId,
                    contextRevision,
                    timerGeneration,
                    restSecondsOverride = supersetRest?.first,
                    isSupersetTransition = supersetRest?.second == true
                )
            } else {
                resetShortRestCoaching()
            }
            applyGuidedWriteSuccess()
            _state.update {
                it.copy(
                    isWarmup = false,
                    supersetSkippedEntryId = null
                )
            }
            clearLiveSetStart()
        } else if (forcedWarmup == true) {
            _state.update { it.copy(warmupRampWriteFailed = true) }
        } else {
            _state.update { it.copy(guidedSetWriteFailed = true) }
        }
    }

    /**
     * Writes exactly one set for the confirmed [resolved] values. A single insert either
     * lands or does not, so a failure leaves the pending draft untouched for an explicit retry.
     * [forcedWarmup] freezes the classification for a suggested ramp, which is always a warm-up.
     */
    private suspend fun writeGuidedSet(
        resolved: DraftSet,
        edit: DraftEdit?,
        contextRevision: Long,
        liveCompletion: Boolean = false,
        forcedWarmup: Boolean? = null,
        onSetLogged: () -> Unit = {}
    ): GuidedSetWriteResult {
        val isWarmup = forcedWarmup
            ?: guidedSetIntent?.takeIf { it.entryId == resolved.occurrenceEntryId }?.isWarmup
            ?: resolved.isWarmup
        return try {
            if (contextRevision != draftContextRevision) {
                throw CancellationException("Draft context changed")
            }
            // One guided confirmation writes one set. The slot's remaining target position comes
            // from the full pending draft, not from the single written set.
            val orderedOffset = resolved.setTargets
                ?.takeIf { !resolved.isWarmup && it.sets >= resolved.sets }
                ?.let { (it.sets - resolved.sets).coerceAtLeast(0) }
            val sessionId = logDraft(
                resolved.copy(sets = 1, isWarmup = isWarmup),
                edit,
                contextRevision,
                liveCompletion = liveCompletion,
                onSetLogged = onSetLogged,
                orderedOffsetOverride = orderedOffset
            )
            ensureDraftContext(contextRevision)
            GuidedSetWriteResult(true, sessionId, isWarmup)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            ensureDraftContext(contextRevision)
            GuidedSetWriteResult(false, null, isWarmup)
        }
    }

    /**
     * Rest to start after one recorded set of paired work: the transition while the partner still owes
     * the same round, otherwise the pair rest after a completed round. Null leaves ordinary rest, and a
     * session-local unpair removes the override.
     */
    private fun supersetRestAfter(occurrenceEntryId: Long?): Pair<Int, Boolean>? =
        supersetRestSelection(
            progress = _state.value.guidedProgress,
            unpairedKeys = _state.value.supersetUnpairedKeys,
            occurrenceEntryId = occurrenceEntryId
        )

    private suspend fun startRestTimerIfDurationValid(
        occurrenceId: Long?,
        exerciseId: String,
        sessionId: String?,
        contextRevision: Long,
        timerGeneration: Long,
        restSecondsOverride: Int? = null,
        isSupersetTransition: Boolean = false
    ) {
        if (restSecondsOverride == null && !_state.value.canStartRestTimer) return
        val completedAtElapsedMillis = liveSetCompletedAtElapsedMillis ?: return
        val seconds = restSecondsOverride?.toLong() ?: settings.restDurationSeconds(exerciseId)
        if (seconds <= 0L || seconds > RestCountdown.MAX_DURATION_MILLIS / 1_000L) return
        val durationMillis = seconds * 1_000L
        val hasOverride = restSecondsOverride == null &&
            settings.hasExerciseRestDurationOverride(exerciseId)
        val currentSessionId = logMutations.observeOpenSession().first()?.id
        // Preference reads may suspend; an End/New session, dismissal or context switch must win.
        if (contextRevision != draftContextRevision ||
            timerGeneration != liveTimerGeneration ||
            currentOccurrence?.id != occurrenceId ||
            currentSessionId != sessionId ||
            !isGuidedActive ||
            !_state.value.canStartRestTimer
        ) {
            return
        }
        val boot = runtime.bootIdentity()
        if (shortRestCoachingEnabled &&
            !isSupersetTransition &&
            boot != null &&
            sessionId != null &&
            occurrenceId != null
        ) {
            shortRestCoach.completed(
                LiveRestCompletion(
                    sessionId,
                    occurrenceId,
                    exerciseId,
                    boot,
                    completedAtElapsedMillis,
                    durationMillis
                )
            )
        }
        timerOccurrenceId = occurrenceId
        timerSessionId = sessionId
        timerExerciseId = exerciseId
        _state.update {
            it.withRestStarted(
                seconds = seconds,
                hasExerciseOverride = hasOverride,
                fromPairOverride = restSecondsOverride != null,
                fromTransition = isSupersetTransition
            )
        }
        restTimer.start(
            durationMillis = durationMillis,
            completedAtElapsedMillis = completedAtElapsedMillis,
            sessionId = sessionId,
            occurrenceId = occurrenceId,
            exerciseId = exerciseId
        )
    }

    /** Clears transient guided/edit state and refreshes consumers after a single guided set lands. */
    private suspend fun applyGuidedWriteSuccess() {
        _state.update {
            it.copy(
                guidedSetWriteFailed = false,
                warmupRampWriteFailed = false,
                missingLoadPrompt = null,
                legacyResolution = null,
                draftEdit = null
            )
        }
        refreshRecentSets()
        if (activeActivation != null) refreshOccurrence()
    }

    fun dismissShortRestCoaching() {
        _state.update { it.copy(shortRestCoachingExerciseName = null) }
    }

    private fun resetShortRestCoaching() {
        shortRestCoach.resetEligibility()
        dismissShortRestCoaching()
    }

    // ── Warm-up guidance ──────────────────────────────────────────────────

    /** Checks or unchecks one preparation item; the frozen prescription is never changed. */
    fun onWarmupPreparationToggled(action: WarmupAction) {
        _state.update { state ->
            val checked = state.warmupPreparationChecked
            state.copy(
                warmupPreparationChecked = if (action in checked) {
                    checked - action
                } else {
                    checked + action
                }
            )
        }
    }

    /** Hides or reopens the current workout's warm-up guidance. In-memory only. */
    fun onToggleWarmupGuidance() {
        _state.update { it.copy(warmupGuidanceHidden = !it.warmupGuidanceHidden) }
    }

    /**
     * Records exactly one suggested ramp set as a warm-up for its frozen slot. The suggestion's reps
     * and rounded load are frozen here, and the existing guided single-write path owns the submission
     * guard, classification and failure handling, so a ramp is never recorded twice or as working work.
     */
    fun logWarmupRamp(occurrenceEntryId: Long, rampIndex: Int) {
        if (draftSubmissionInProgress) return
        // Equipment availability and ceilings are not yet known; no suggestion is trustworthy.
        if (!warmupEquipmentObserved) return
        // A refresh is resolving new guidance; the shown suggestion may no longer match its slot.
        if (warmupGuidancePublishedGeneration != warmupGuidanceGeneration) return
        val occurrence = currentOccurrence ?: return
        if (_state.value.activeOccurrence?.occurrenceId != occurrence.id) return
        val plan = _state.value.warmupGuidance?.ramps
            ?.firstOrNull { it.occurrenceEntryId == occurrenceEntryId }
            ?: return
        val row = _state.value.visibleWarmupView?.ramps
            ?.firstOrNull { it.occurrenceEntryId == occurrenceEntryId }
            ?.rows?.firstOrNull { it.key.rampIndex == rampIndex }
            ?: return
        // An edit left above the current equipment ceiling must be corrected before it can be logged.
        if (row.exceedsCeiling) return
        val entry = occurrence.entries.firstOrNull { it.id == occurrenceEntryId } ?: return
        // Reject a suggestion whose slot no longer carries the same prescription, so a value shown
        // for an earlier exercise/reps/load can never be recorded against a replaced slot. A slot the
        // user edited is compared against the edited target its suggestion was derived from.
        if (!plan.matchesPrescription(entry, rampTargetsByEntry[entry.id])) return
        val draft = DraftSet(
            exerciseId = entry.exerciseId,
            name = entry.exerciseName,
            sets = 1,
            reps = row.reps,
            weightKg = row.weightKg,
            loadKind = LoadKind.EXTERNAL,
            loadCapability = entry.loadCapability,
            occurrenceId = occurrence.id,
            occurrenceEntryId = entry.id,
            isWarmup = true
        )
        _state.update { it.copy(warmupRampWriteFailed = false) }
        launchGuidedWrite(draft, edit = null, forcedWarmup = true)
    }

    // ── Warm-up guidance editing ──────────────────────────────────────────

    /** Opens the transient editor for one preparation item with its currently shown value. */
    fun editWarmupPreparation(action: WarmupAction) {
        if (draftSubmissionInProgress) return
        val row = _state.value.visibleWarmupView?.preparation
            ?.firstOrNull { it.action == action }
            ?: return
        _state.update {
            it.copy(
                warmupEditor = WarmupGuidanceEditor(
                    target = WarmupEditTarget.Preparation(action),
                    repsInput = row.reps?.toString().orEmpty(),
                    // A timed item is entered in whole minutes and stored as seconds.
                    durationInput = row.durationSeconds?.let { seconds -> seconds / 60 }
                        ?.toString()
                        .orEmpty(),
                    weightInput = "",
                    timed = row.durationSeconds != null,
                    hasWeight = false,
                    weightUnit = it.weightUnit
                )
            )
        }
    }

    /** Opens the transient editor for one ramp row with its currently shown values. */
    fun editWarmupRamp(occurrenceEntryId: Long, rampIndex: Int) {
        if (draftSubmissionInProgress) return
        val row = _state.value.visibleWarmupView?.ramps
            ?.firstOrNull { it.occurrenceEntryId == occurrenceEntryId }
            ?.rows?.firstOrNull { it.key.rampIndex == rampIndex }
            ?: return
        _state.update {
            val shownWeight = formatWeight(it.weightUnit.kilogramsToDisplay(row.weightKg))
            it.copy(
                warmupEditor = WarmupGuidanceEditor(
                    target = WarmupEditTarget.Ramp(WarmupRampKey(occurrenceEntryId, rampIndex)),
                    repsInput = row.reps.toString(),
                    durationInput = "",
                    weightInput = shownWeight,
                    originalWeightKg = row.weightKg,
                    originalWeightInput = shownWeight,
                    timed = false,
                    hasWeight = true,
                    weightUnit = it.weightUnit,
                    ceilingKg = rampCeilingKg(occurrenceEntryId)
                )
            )
        }
    }

    fun onWarmupEditorRepsChanged(value: String) = updateWarmupEditor {
        copy(repsInput = value.filter(Char::isDigit), resetsToDefault = false)
    }

    fun onWarmupEditorDurationChanged(value: String) = updateWarmupEditor {
        copy(durationInput = value.filter(Char::isDigit), resetsToDefault = false)
    }

    fun onWarmupEditorWeightChanged(value: String) = updateWarmupEditor {
        copy(
            weightInput = value.filter { it.isDigit() || it == '.' },
            resetsToDefault = false
        )
    }

    /**
     * Applies the editor's value to the in-memory guidance and, for a preparation row, persists it as
     * the action's override. A ramp still has to be logged through its own action to become a recorded
     * warm-up. After Reset, Save applies the derived default instead of a typed value.
     */
    fun saveWarmupEditor() {
        val editor = _state.value.warmupEditor ?: return
        if (!editor.canSave) return
        when (val target = editor.target) {
            is WarmupEditTarget.Preparation -> savePreparationEditor(editor, target.action)
            is WarmupEditTarget.Ramp -> _state.update { state ->
                applyRampEdit(state, target, editor)
            }
        }
    }

    /** Applies one ramp editor's value to [state], or drops its transient edit after Reset. */
    private fun applyRampEdit(
        state: WorkoutLoggerUiState,
        target: WarmupEditTarget.Ramp,
        editor: WarmupGuidanceEditor
    ): WorkoutLoggerUiState {
        if (editor.resetsToDefault) {
            return state.copy(
                warmupRampEdits = state.warmupRampEdits - target.key,
                warmupEditor = null
            )
        }
        val weightKg = editor.enteredWeightKg
        val default = state.warmupGuidance?.ramps
            ?.firstOrNull { it.occurrenceEntryId == target.key.occurrenceEntryId }
            ?.rampSets
            ?.getOrNull(target.key.rampIndex)
        // A collapsed default leaves no row at this position, so the edit keeps the profile
        // percentage it already carried instead of being discarded.
        val percent = default?.percentOfWorkingWeight
            ?: state.warmupRampEdits[target.key]?.percentOfWorkingWeight
        val reps = editor.repsInput.toIntOrNull()
        return if (weightKg == null || reps == null || percent == null) {
            state.copy(warmupEditor = null)
        } else {
            state.copy(
                warmupRampEdits = state.warmupRampEdits + (
                    target.key to WarmupRampSet(
                        percentOfWorkingWeight = percent,
                        reps = reps,
                        weightKg = weightKg
                    )
                    ),
                warmupEditor = null
            )
        }
    }

    /**
     * Applies a preparation editor's value to the guidance and persists it as the action's override.
     * Reset drops the transient edit and clears the stored override, so the built-in default applies
     * again; the store write is launched after the state update, so the row never waits for it.
     */
    private fun savePreparationEditor(editor: WarmupGuidanceEditor, action: WarmupAction) {
        val item = when {
            editor.resetsToDefault -> null
            editor.timed -> WarmupPreparationItem(
                action = action,
                // Minutes are the entered unit; seconds are the stored unit. canSave already
                // guaranteed the product fits, so this never truncates.
                durationSeconds = editor.durationInput.toIntOrNull()
                    ?.toLong()
                    ?.times(60L)
                    ?.toInt()
            )
            else -> WarmupPreparationItem(
                action = action,
                reps = editor.repsInput.toIntOrNull()
            )
        }
        _state.update { state ->
            val overrides = if (item == null) {
                state.warmupPreparationOverrides - action
            } else {
                state.warmupPreparationOverrides + (action to item)
            }
            state.copy(
                warmupPreparationEdits = if (item == null) {
                    state.warmupPreparationEdits - action
                } else {
                    state.warmupPreparationEdits + (action to item)
                },
                warmupPreparationOverrides = overrides,
                warmupGuidance = guidanceWithOverrides(state.warmupGuidance, overrides),
                warmupEditor = null
            )
        }
        viewModelScope.launch {
            if (item == null) {
                warmupOverrideRepository.clear(action)
            } else {
                warmupOverrideRepository.set(item)
            }
        }
    }

    /**
     * Rebuilds [guidance] with [overrides] applied, keeping the occurrence, equipment and edited ramp
     * targets it was derived from. A null guidance (no active occurrence) stays null.
     */
    private fun guidanceWithOverrides(
        guidance: WarmupGuidance?,
        overrides: Map<WarmupAction, WarmupPreparationItem>
    ): WarmupGuidance? {
        if (guidance == null) return null
        val occurrence = currentOccurrence ?: return guidance
        return buildWarmupGuidance(occurrence, warmupEquipment, overrides, rampTargetsByEntry)
    }

    /**
     * Puts the derived default into the open editor's fields. Nothing is applied until Save, so Cancel
     * still preserves whatever guidance was already in effect.
     */
    fun resetWarmupEditor() {
        val editor = _state.value.warmupEditor ?: return
        val state = _state.value
        when (val target = editor.target) {
            is WarmupEditTarget.Preparation -> {
                // The built-in default, not the persisted override the row may currently show: the
                // override is exactly what Save is about to clear.
                val occurrence = currentOccurrence
                val item = occurrence
                    ?.let { buildWarmupGuidance(it, warmupEquipment) }
                    ?.preparation
                    ?.firstOrNull { it.action == target.action }
                    ?: return
                _state.update {
                    it.copy(
                        warmupEditor = editor.copy(
                            repsInput = item.reps?.toString().orEmpty(),
                            durationInput = item.durationSeconds?.let { seconds -> seconds / 60 }
                                ?.toString()
                                .orEmpty(),
                            resetsToDefault = true
                        )
                    )
                }
            }

            is WarmupEditTarget.Ramp -> {
                val ramp = state.warmupGuidance?.ramps
                    ?.firstOrNull { it.occurrenceEntryId == target.key.occurrenceEntryId }
                    ?.rampSets
                    ?.getOrNull(target.key.rampIndex)
                if (ramp == null) {
                    // This position's default collapsed into another row, so there is no value to
                    // stage; Reset only stages the removal of the transient edit for Save.
                    _state.update { it.copy(warmupEditor = editor.copy(resetsToDefault = true)) }
                    return
                }
                _state.update {
                    // The editor's frozen unit, not the current preference, so Reset shows digits in
                    // the same unit Save will interpret them in.
                    val shownWeight = formatWeight(
                        editor.weightUnit.kilogramsToDisplay(ramp.weightKg)
                    )
                    it.copy(
                        warmupEditor = editor.copy(
                            repsInput = ramp.reps.toString(),
                            weightInput = shownWeight,
                            originalWeightKg = ramp.weightKg,
                            originalWeightInput = shownWeight,
                            resetsToDefault = true
                        )
                    )
                }
            }
        }
    }

    /** Closes the editor without changing anything. */
    fun cancelWarmupEditor() = _state.update { it.copy(warmupEditor = null) }

    /** Hides or restores one preparation item for the current workout; nothing is persisted. */
    fun toggleWarmupPreparationSkipped(action: WarmupAction) {
        _state.update { state ->
            val skipped = state.warmupPreparationSkipped
            state.copy(
                warmupPreparationSkipped = if (action in skipped) {
                    skipped - action
                } else {
                    skipped + action
                }
            )
        }
    }

    /** Restores every preparation item the user skipped for the current workout. */
    fun restoreSkippedWarmupPreparation() {
        _state.update { it.copy(warmupPreparationSkipped = emptySet()) }
    }

    private fun updateWarmupEditor(transform: WarmupGuidanceEditor.() -> WarmupGuidanceEditor) {
        _state.update { state ->
            state.warmupEditor?.let { state.copy(warmupEditor = it.transform()) } ?: state
        }
    }

    /** Drops the occurrence-scoped warm-up state; used when the block or occurrence ends. */
    private fun WorkoutLoggerUiState.withWarmupCleared(): WorkoutLoggerUiState = copy(
        warmupGuidance = null,
        warmupPreparationChecked = emptySet(),
        warmupPreparationEdits = emptyMap(),
        warmupPreparationSkipped = emptySet(),
        warmupRampEdits = emptyMap(),
        warmupEditor = null,
        warmupGuidanceHidden = false,
        warmupRampWriteFailed = false
    )

    /**
     * Reconciles an open editor with a refreshed occurrence: it closes when the workout changed or its
     * slot no longer carries a ramp, and otherwise takes the slot's current equipment ceiling so an
     * equipment change cannot leave the editor validating against a stale limit.
     */
    private fun reconcileWarmupEditor(
        editor: WarmupGuidanceEditor,
        sameOccurrence: Boolean,
        changedSlots: Set<Long>,
        guidance: WarmupGuidance
    ): WarmupGuidanceEditor? {
        if (!sameOccurrence) return null
        val target = editor.target
        if (target !is WarmupEditTarget.Ramp) return editor
        if (target.key.occurrenceEntryId in changedSlots) return null
        val plan = guidance.ramps
            .firstOrNull { it.occurrenceEntryId == target.key.occurrenceEntryId }
            ?: return null
        return editor.copy(ceilingKg = plan.ceilingKg)
    }

    /** The current equipment ceiling for [occurrenceEntryId]'s required equipment, or null. */
    private fun rampCeilingKg(occurrenceEntryId: Long): Double? {
        val entry = currentOccurrence?.entries
            ?.firstOrNull { it.id == occurrenceEntryId }
            ?: return null
        val equipment = warmupEquipment ?: return null
        return EquipmentWeightLimit.ceilingFor(entry.requiredEquipment, equipment.maxWeights)
    }

    private suspend fun refreshRecentSets() {
        val rows = getWorkoutLog()
            .sortedWith(
                compareByDescending<WorkoutSet> { it.performedAtMillis }
                    .thenByDescending { it.id }
            )
            .map { set ->
                LoggedSetRow(
                    id = set.id,
                    performedAtMillis = set.performedAtMillis,
                    exerciseId = set.exerciseId,
                    exerciseName = exerciseNames[set.exerciseId] ?: set.exerciseId,
                    reps = set.reps,
                    durationSeconds = set.durationSeconds,
                    distanceMeters = set.distanceMeters,
                    forceNewtons = set.forceNewtons,
                    device = set.device,
                    weightKg = set.weightKg,
                    rir = set.rir,
                    isWarmup = set.isWarmup,
                    weekNumber = set.weekNumber,
                    dayIndex = set.dayIndex,
                    loadKind = set.loadKind,
                    occurrenceId = set.occurrenceId,
                    sessionId = set.sessionId,
                    cluster = set.cluster
                )
            }
        _state.update { it.copy(recentSets = rows, manualLogRefreshFailed = false) }
    }

    // ── Exercise setup notes ──────────────────────────────────────────────

    /** The revision of the exercise whose note is currently loaded in the editor. */
    private var noteRevision: Long = 0

    /** Loads the note for [exerciseId] into the draft editor. */
    fun onNoteExerciseSelected(exerciseId: String) {
        if (noteSaving) return
        val revision = ++noteRevision
        // Close the editor for the new selection until its note is read, so the previous exercise's
        // draft can never be shown or saved against the newly selected exercise.
        _state.update {
            it.copy(
                noteExerciseId = null,
                noteText = "",
                noteFailed = false,
                noteSaved = false,
                noteLoadFailed = false
            )
        }
        viewModelScope.launch {
            val text = try {
                exerciseNoteRepository.note(exerciseId)
            } catch (_: Exception) {
                // Only the latest selection may apply its result; an older load that fails after a
                // newer selection must not clear or error the newer note.
                if (revision == noteRevision) {
                    _state.update {
                        it.copy(
                            noteExerciseId = exerciseId,
                            noteLoadFailed = true
                        )
                    }
                }
                return@launch
            }
            if (revision != noteRevision) return@launch
            _state.update {
                it.copy(
                    noteText = text ?: "",
                    noteExerciseId = exerciseId,
                    noteLoadFailed = false
                )
            }
        }
    }

    /** Updates the draft note text. */
    fun onNoteTextChanged(text: String) {
        _state.update { it.copy(noteText = text.take(NOTE_MAX_LENGTH)) }
    }

    /** Saves the current draft note to the repository. */
    fun saveNote() {
        val current = _state.value
        val exerciseId = current.noteExerciseId
        // The editor must belong to the currently selected exercise; never write a stale draft.
        if (noteSaving || exerciseId == null || exerciseId != current.selectedExerciseId) return
        val text = current.noteText
        if (text.length > NOTE_MAX_LENGTH) return
        val revision = noteRevision
        // Claim the editor synchronously so two submissions before the write runs cannot both write.
        _state.update { it.copy(noteSaving = true, noteFailed = false, noteSaved = false) }
        viewModelScope.launch {
            try {
                exerciseNoteRepository.save(exerciseId, text)
                // A write that finished for an exercise that is no longer the editor's target must not
                // close, alter or confirm feedback in the new context.
                if (noteRevision == revision && _state.value.noteExerciseId == exerciseId) {
                    // Closing the editor invalidates any in-flight load so it cannot reopen it.
                    noteRevision++
                    val feedbackRevision = noteRevision
                    _state.update {
                        it.copy(
                            noteSaving = false,
                            noteSaved = true,
                            noteText = "",
                            noteExerciseId = null
                        )
                    }
                    // Auto-dismiss the success indicator after a short delay.
                    kotlinx.coroutines.delay(2_000)
                    // A newer editor or success bumps the revision; this delayed dismissal must not
                    // clear feedback that belongs to a later context.
                    if (noteRevision == feedbackRevision) {
                        _state.update { it.copy(noteSaved = false) }
                    }
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                if (noteRevision == revision && _state.value.noteExerciseId == exerciseId) {
                    _state.update { it.copy(noteSaving = false, noteFailed = true) }
                }
            }
        }
    }

    /** Clears the note for the currently selected exercise. */
    fun clearNote() {
        val current = _state.value
        val exerciseId = current.noteExerciseId
        if (noteSaving || exerciseId == null || exerciseId != current.selectedExerciseId) return
        val revision = noteRevision
        _state.update { it.copy(noteSaving = true, noteFailed = false, noteSaved = false) }
        viewModelScope.launch {
            try {
                exerciseNoteRepository.clear(exerciseId)
                if (noteRevision == revision && _state.value.noteExerciseId == exerciseId) {
                    // Closing the editor invalidates any in-flight load so it cannot reopen it.
                    noteRevision++
                    val feedbackRevision = noteRevision
                    _state.update {
                        it.copy(
                            noteSaving = false,
                            noteSaved = true,
                            noteText = "",
                            noteExerciseId = null
                        )
                    }
                    kotlinx.coroutines.delay(2_000)
                    if (noteRevision == feedbackRevision) {
                        _state.update { it.copy(noteSaved = false) }
                    }
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                if (noteRevision == revision && _state.value.noteExerciseId == exerciseId) {
                    _state.update { it.copy(noteSaving = false, noteFailed = true) }
                }
            }
        }
    }

    /** Cancels the note editor, discarding the draft. */
    fun cancelNote() {
        // A write in flight owns the editor state; cancelling it here would strand `noteSaving`.
        if (noteSaving) return
        // Invalidate any in-flight load so a late result cannot reopen the editor.
        noteRevision++
        _state.update {
            it.copy(
                noteText = "",
                noteExerciseId = null,
                noteFailed = false,
                noteSaved = false,
                noteLoadFailed = false
            )
        }
    }

    /** Retries loading the note for the currently selected exercise. */
    fun retryNoteLoad() {
        val current = _state.value
        if (current.noteExerciseId == null) return
        onNoteExerciseSelected(current.noteExerciseId)
    }

    private val noteSaving: Boolean
        get() = _state.value.noteSaving

    // ── Current-session notes ─────────────────────────────────────────────

    /**
     * Identifies the session-note editor state that the newest read or write belongs to, so a
     * completion from an older editor (or the previous session) can never apply to a newer one.
     */
    private var sessionNoteRevision: Long = 0

    /** Opens the current session-note editor, keyed to the active session's explicit id. */
    fun openSessionNote() {
        if (sessionNoteSaving) return
        // A session note needs a real session identity; opening never creates one.
        val sessionId = _state.value.activeSession?.id ?: return
        loadSessionNote(sessionId)
    }

    private fun loadSessionNote(sessionId: String) {
        if (sessionNoteSaving) return
        val revision = ++sessionNoteRevision
        // Keep the editor closed until its note is read, so an empty field never reads as "no note".
        _state.update {
            it.copy(
                sessionNoteSessionId = null,
                sessionNoteText = "",
                sessionNoteLoading = true,
                sessionNoteFailed = false,
                sessionNoteSaved = false,
                sessionNoteLoadFailed = false
            )
        }
        viewModelScope.launch {
            val text = try {
                sessionNoteRepository.note(sessionId)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                // Only the newest editor may report a failed read; a stale failure must not error it.
                if (revision == sessionNoteRevision) {
                    _state.update {
                        it.copy(
                            sessionNoteLoading = false,
                            sessionNoteSessionId = sessionId,
                            sessionNoteLoadFailed = true
                        )
                    }
                }
                return@launch
            }
            if (revision != sessionNoteRevision) return@launch
            _state.update {
                it.copy(
                    sessionNoteLoading = false,
                    sessionNoteText = text ?: "",
                    sessionNoteSessionId = sessionId,
                    sessionNoteLoadFailed = false
                )
            }
        }
    }

    /** Updates the session-note draft text. */
    fun onSessionNoteTextChanged(text: String) {
        val current = _state.value
        if (current.sessionNoteSaving || current.sessionNoteSessionId == null) return
        _state.update { it.copy(sessionNoteText = text.take(NOTE_MAX_LENGTH)) }
    }

    /** Saves the current session-note draft to the active session. */
    fun saveSessionNote() {
        val current = _state.value
        val sessionId = current.sessionNoteSessionId
        // The editor must belong to the currently active session; never write a stale draft.
        if (current.sessionNoteSaving ||
            sessionId == null ||
            sessionId != current.activeSession?.id
        ) {
            return
        }
        val text = current.sessionNoteText
        if (text.length > NOTE_MAX_LENGTH) return
        val revision = sessionNoteRevision
        // Claim the editor synchronously so two submissions before the write run cannot both write.
        _state.update {
            it.copy(sessionNoteSaving = true, sessionNoteFailed = false, sessionNoteSaved = false)
        }
        viewModelScope.launch {
            try {
                sessionNoteRepository.save(sessionId, text)
                // A write that finished for a session that is no longer the editor's target must not
                // close, alter or confirm feedback in the new context.
                if (sessionNoteRevision == revision &&
                    _state.value.sessionNoteSessionId == sessionId
                ) {
                    sessionNoteRevision++
                    val feedbackRevision = sessionNoteRevision
                    _state.update {
                        it.copy(
                            sessionNoteSaving = false,
                            sessionNoteSaved = true,
                            sessionNoteText = "",
                            sessionNoteSessionId = null
                        )
                    }
                    kotlinx.coroutines.delay(2_000)
                    // A newer editor or success bumps the revision; this delayed dismissal must not
                    // clear feedback that belongs to a later context.
                    if (sessionNoteRevision == feedbackRevision) {
                        _state.update { it.copy(sessionNoteSaved = false) }
                    }
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                if (sessionNoteRevision == revision &&
                    _state.value.sessionNoteSessionId == sessionId
                ) {
                    _state.update { it.copy(sessionNoteSaving = false, sessionNoteFailed = true) }
                }
            }
        }
    }

    /** Persists an explicit clear for the current session-note draft's session. */
    fun clearSessionNote() {
        val current = _state.value
        val sessionId = current.sessionNoteSessionId
        if (current.sessionNoteSaving ||
            sessionId == null ||
            sessionId != current.activeSession?.id
        ) {
            return
        }
        val revision = sessionNoteRevision
        _state.update {
            it.copy(sessionNoteSaving = true, sessionNoteFailed = false, sessionNoteSaved = false)
        }
        viewModelScope.launch {
            try {
                sessionNoteRepository.clear(sessionId)
                if (sessionNoteRevision == revision &&
                    _state.value.sessionNoteSessionId == sessionId
                ) {
                    sessionNoteRevision++
                    val feedbackRevision = sessionNoteRevision
                    _state.update {
                        it.copy(
                            sessionNoteSaving = false,
                            sessionNoteSaved = true,
                            sessionNoteText = "",
                            sessionNoteSessionId = null
                        )
                    }
                    kotlinx.coroutines.delay(2_000)
                    // A newer editor or success bumps the revision; this delayed dismissal must not
                    // clear feedback that belongs to a later context.
                    if (sessionNoteRevision == feedbackRevision) {
                        _state.update { it.copy(sessionNoteSaved = false) }
                    }
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                if (sessionNoteRevision == revision &&
                    _state.value.sessionNoteSessionId == sessionId
                ) {
                    _state.update { it.copy(sessionNoteSaving = false, sessionNoteFailed = true) }
                }
            }
        }
    }

    /** Cancels the session-note editor, discarding the draft without writing. */
    fun cancelSessionNote() {
        if (sessionNoteSaving) return
        // Invalidate any in-flight read so a late result cannot reopen the editor.
        sessionNoteRevision++
        _state.update {
            it.copy(
                sessionNoteSessionId = null,
                sessionNoteText = "",
                sessionNoteLoading = false,
                sessionNoteFailed = false,
                sessionNoteSaved = false,
                sessionNoteLoadFailed = false
            )
        }
    }

    /** Retries reading the note for the editor's current session. */
    fun retrySessionNoteLoad() {
        val sessionId = _state.value.sessionNoteSessionId ?: return
        loadSessionNote(sessionId)
    }

    /**
     * Drops all session-note editor state and invalidates in-flight reads/writes. Called when the
     * active session identity changes, so a draft or result never attaches to a different session.
     */
    private fun resetSessionNoteState() {
        sessionNoteRevision++
        _state.update {
            it.copy(
                sessionNoteSessionId = null,
                sessionNoteText = "",
                sessionNoteLoading = false,
                sessionNoteSaving = false,
                sessionNoteFailed = false,
                sessionNoteLoadFailed = false,
                sessionNoteSaved = false
            )
        }
    }

    private val sessionNoteSaving: Boolean
        get() = _state.value.sessionNoteSaving
}

/**
 * The rest a recorded set of paired work starts: a transition while the partner still owes the same
 * round, otherwise the pair rest. A completed partner (unequal set counts) makes the remaining sets
 * solo work, so they take pair rest rather than a transition. A session-local unpair returns null.
 *
 * Pure so the selection can be tested without a ViewModel or a timer.
 */
internal fun supersetRestSelection(
    progress: GuidedWorkoutProgress?,
    unpairedKeys: Set<String>,
    occurrenceEntryId: Long?
): Pair<Int, Boolean>? {
    if (occurrenceEntryId == null) return null
    val entry = progress?.exercises?.firstOrNull {
        it.occurrenceEntryId == occurrenceEntryId
    } ?: return null
    val pair = entry.superset ?: return null
    if (pair.key in unpairedKeys) return null
    val partner = progress.exercises.firstOrNull {
        it.superset?.key == pair.key && it.occurrenceEntryId != occurrenceEntryId
    } ?: return null
    val performedAfter = entry.performedWorkingSets + 1
    return if (!partner.isComplete && partner.performedWorkingSets < performedAfter) {
        pair.transitionSeconds to true
    } else {
        pair.pairRestSeconds to false
    }
}
