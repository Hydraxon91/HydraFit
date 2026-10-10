package com.hydrafit.app.feature.logger

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hydrafit.app.core.domain.engine.AcceptedDay
import com.hydrafit.app.core.domain.engine.AcceptedPlan
import com.hydrafit.app.core.domain.engine.ExerciseCatalog
import com.hydrafit.app.core.domain.equipment.Exercise
import com.hydrafit.app.core.domain.equipment.ExerciseLoadCapability
import com.hydrafit.app.core.domain.fatigue.FatigueConfig
import com.hydrafit.app.core.domain.schedule.FinishMode
import com.hydrafit.app.core.domain.schedule.ScheduleException
import com.hydrafit.app.core.domain.schedule.TrainingActivation
import com.hydrafit.app.core.domain.schedule.WorkoutLoggingActions
import com.hydrafit.app.core.domain.schedule.WorkoutOccurrence
import com.hydrafit.app.core.domain.schedule.WorkoutScheduleState
import com.hydrafit.app.core.domain.time.localDayOfWeek
import com.hydrafit.app.core.domain.time.localEpochDay
import com.hydrafit.app.core.domain.unit.WeightUnit
import com.hydrafit.app.core.domain.unit.formatWeight
import com.hydrafit.app.core.domain.workout.GetWorkoutLogUseCase
import com.hydrafit.app.core.domain.workout.LoadKind
import com.hydrafit.app.core.domain.workout.RestCountdown
import com.hydrafit.app.core.domain.workout.WorkoutLogMutations
import com.hydrafit.app.core.domain.workout.WorkoutSet
import com.hydrafit.app.core.domain.workout.WorkoutSetCorrection
import com.hydrafit.app.core.domain.workout.WorkoutTimingProvenance
import com.hydrafit.app.core.domain.workout.buildGuidedWorkoutProgress
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private data class GuidedSetWriteResult(val succeeded: Boolean, val sessionId: String?)

class WorkoutLoggerViewModel(
    private val logMutations: WorkoutLogMutations,
    private val getWorkoutLog: GetWorkoutLogUseCase,
    private val loggingActions: WorkoutLoggingActions,
    private val exerciseCatalog: ExerciseCatalog,
    private val runtime: WorkoutLoggerRuntime,
    private val settings: WorkoutLoggerSettings
) : ViewModel() {

    private val _state = MutableStateFlow(WorkoutLoggerUiState())
    val state: StateFlow<WorkoutLoggerUiState> = _state.asStateFlow()

    private var exercises: List<Exercise> = emptyList()
    private var exerciseNames: Map<String, String> = emptyMap()
    private var acceptedPlan: AcceptedPlan? = null
    private var acceptedToday: AcceptedDay? = null
    private var suggestedWeightKgByExercise: Map<String, Double> = emptyMap()

    /** The active block and its queue, when the user has started one; null uses the accepted plan. */
    private var activeActivation: TrainingActivation? = null
    private var currentOccurrences: List<WorkoutOccurrence> = emptyList()
    private var scheduleState: WorkoutScheduleState? = null
    private var currentOccurrence: WorkoutOccurrence? = null
    private var occurrencesJob: Job? = null

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
    private var draftContextRevision = 0L
    private var missingLoadPromptRevision = 0L
    private val restTimer = runtime.restTimer(viewModelScope)
    private var liveSetIntentEntryId: Long? = null
    private var liveSetStartedAtElapsedMillis: Long? = null
    private var liveSetCompletedAtElapsedMillis: Long? = null
    private var liveSetCompletedAtWallMillis: Long? = null
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
            restTimer.state.collect { timer -> _state.update { it.copy(restTimer = timer) } }
        }
        viewModelScope.launch {
            // Observe the catalog so a newly added/edited custom exercise appears without a restart.
            exerciseCatalog.observeAll().collect { catalog ->
                exercises = catalog
                exerciseNames = catalog.associate { it.id to it.name }
                _state.update { current ->
                    current.copy(
                        exercises = prioritizedByToday(catalog, acceptedToday)
                    )
                }
                refreshRecentSets()
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
                    invalidateRestTimer(clearPersisted = restTimer.state.value != null)
                    liveSetIntentEntryId = null
                }
                activeActivation = activation
                activationObserved = true
                occurrencesJob?.cancel()
                if (activation == null) {
                    currentOccurrences = emptyList()
                    currentOccurrence = null
                    occurrenceContextObserved = true
                    // Leaving the active block ends any in-flight batch or partial-write retry.
                    _state.update {
                        it.copy(
                            activeOccurrence = null,
                            confirmingAllDrafts = false,
                            draftWriteRetries = emptyList(),
                            guidedProgress = null,
                            guidedSetWriteFailed = false
                        )
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
                _state.update { it.copy(weightUnit = unit) }
            }
        }
        viewModelScope.launch {
            settings.guidedWorkoutFlow().collectLatest { enabled ->
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
                _state.update { it.copy(activeSession = session) }
                restoreRestTimerIfReady()
            }
        }
    }

    override fun onCleared() {
        invalidateRestTimer(clearPersisted = false)
        super.onCleared()
    }

    private fun invalidateRestTimer(clearPersisted: Boolean = true) {
        liveTimerGeneration++
        timerOccurrenceId = null
        timerSessionId = null
        timerExerciseId = null
        restTimer.cancel(clearPersisted)
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
            _state.update {
                it.copy(
                    restDurationSeconds = restored.durationMillis.div(1_000L).toString(),
                    restDurationIsOverride = isOverride
                )
            }
        }
    }

    private fun clearLiveSetStart() {
        liveSetIntentEntryId = null
        liveSetStartedAtElapsedMillis = null
        liveSetCompletedAtElapsedMillis = null
        liveSetCompletedAtWallMillis = null
        _state.update { it.copy(startedSetEntryId = null) }
    }

    fun onExerciseSelected(exerciseId: String) {
        val unit = _state.value.weightUnit
        val planWeight = suggestedInputFor(exerciseId, unit)
        val planReps = suggestedRepsFor(exerciseId)
        _state.update {
            it.copy(
                selectedExerciseId = exerciseId,
                weightInput = planWeight,
                reps = planReps ?: it.reps,
                // Bodyweight exercises hide the weight field again on each selection.
                weightRevealed = false
            )
        }
        // The accepted plan's suggestion wins; only fall back to the last logged set when the plan
        // has no suggestion for this exercise.
        if (planWeight.isBlank()) {
            viewModelScope.launch { prefillFromLastSet(exerciseId, unit) }
        }
    }

    /** Fills reps and weight from the most recent non-warmup set logged for this exercise. */
    private suspend fun prefillFromLastSet(exerciseId: String, unit: WeightUnit) {
        val last = getWorkoutLog()
            .filter { it.exerciseId == exerciseId && !it.isWarmup }
            .maxByOrNull { it.performedAtMillis }
            ?: return
        _state.update { current ->
            if (current.selectedExerciseId != exerciseId) return@update current
            val option = current.exercises.firstOrNull { it.id == exerciseId }
            current.copy(
                weightInput = weightInputFor(last.weightKg, unit),
                reps = last.reps.toString(),
                // An added-load history reveals the field on an addable bodyweight exercise.
                weightRevealed = current.weightRevealed ||
                    (option?.canAddLoad == true && last.weightKg != null)
            )
        }
    }

    /**
     * Quick-fills the input from a past set without applying the accepted plan's suggestion, so a
     * tapped row reproduces exactly what was logged: exercise, reps, weight, warm-up flag, and RIR.
     */
    fun onRecentSetSelected(row: LoggedSetRow) {
        val unit = _state.value.weightUnit
        _state.update {
            it.copy(
                selectedExerciseId = row.exerciseId,
                weightInput = weightInputFor(row.weightKg, unit),
                reps = row.reps.toString(),
                // Reveal the weight field for a bodyweight exercise only when that row had a weight.
                weightRevealed = row.weightKg != null,
                isWarmup = row.isWarmup,
                rir = row.rir?.toString().orEmpty()
            )
        }
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
        val unit = _state.value.weightUnit
        val weight = weightInputFor(row.weightKg, unit)
        _state.update {
            it.copy(
                loggedSetEdit = LoggedSetEdit(
                    row,
                    unit,
                    row.reps.toString(),
                    weight,
                    weight,
                    row.rir?.toString().orEmpty(),
                    row.performedAtMillis
                ),
                loggedSetEditFailed = false
            )
        }
    }

    fun onLoggedSetRepsChanged(value: String) = updateLoggedSetEdit { copy(reps = value) }

    fun onLoggedSetWeightChanged(value: String) = updateLoggedSetEdit { copy(weightInput = value) }

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
                        requireNotNull(edit.reps.toIntOrNull()),
                        weight,
                        edit.rir.toIntOrNull(),
                        edit.performedAtMillis
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
            if (timerOccurrenceId != null && edit.row.occurrenceId == timerOccurrenceId) {
                cancelRestTimer()
            }
            refreshRecentSets()
            if (activeActivation != null) refreshOccurrence()
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
        val correctedOccurrenceId = _state.value.recentSets
            .firstOrNull { it.id == setId }
            ?.occurrenceId
        viewModelScope.launch {
            logMutations.correctTime(setId, millis)
            if (timerOccurrenceId != null && correctedOccurrenceId == timerOccurrenceId) {
                cancelRestTimer()
            }
            refreshRecentSets()
        }
        return true
    }

    /** Reveals the weight field for an addable bodyweight exercise so added load can be logged. */
    fun onRevealWeight() {
        val exercise = exercises.firstOrNull { it.id == _state.value.selectedExerciseId } ?: return
        if (exercise.loadCapability != ExerciseLoadCapability.BODYWEIGHT_ADDABLE) return
        _state.update { it.copy(weightRevealed = true) }
    }

    fun onExerciseSearchChanged(value: String) {
        _state.update { it.copy(exerciseSearch = value) }
    }

    fun onRepsChanged(value: String) {
        _state.update { it.copy(reps = value.filter(Char::isDigit)) }
    }

    fun onWeightChanged(value: String) {
        _state.update {
            it.copy(weightInput = value.filter { char -> char.isDigit() || char == '.' })
        }
    }

    /** RIR is optional; non-digits are dropped and values above the maximum are ignored outright. */
    fun onRirChanged(value: String) {
        val digits = value.filter(Char::isDigit)
        val withinRange = digits.toIntOrNull()
            ?.let { it <= FatigueConfig.DEFAULT_MAX_RIR.toInt() }
            ?: false
        if (digits.isEmpty() || withinRange) {
            _state.update { it.copy(rir = digits) }
        }
    }

    fun onWarmupToggled(isWarmup: Boolean) {
        _state.update { it.copy(isWarmup = isWarmup) }
    }

    fun deleteSet(id: Long) {
        viewModelScope.launch {
            val deletedOccurrenceId = _state.value.recentSets
                .firstOrNull { it.id == id }
                ?.occurrenceId
            logMutations.delete(id)
            if (timerOccurrenceId != null && deletedOccurrenceId == timerOccurrenceId) {
                cancelRestTimer()
            }
            refreshRecentSets()
            // A deleted set changes the active occurrence's remaining/progress, so refresh it too.
            if (activeActivation != null) refreshOccurrence()
        }
    }

    fun log() {
        val current = _state.value
        val exerciseId = current.selectedExerciseId ?: return
        val reps = current.reps.toIntOrNull() ?: return
        if (reps <= 0) return
        // A hidden weight field (bodyweight exercise not revealed) never logs a weight, even if a
        // value was retained from a previous exercise.
        val typedWeight = if (current.showWeightField) {
            current.weightInput.toDoubleOrNull()
                ?.let { current.weightUnit.displayToKilograms(it) }
        } else {
            null
        }
        val capability = exercises.firstOrNull { it.id == exerciseId }?.loadCapability
            ?: ExerciseLoadCapability.EXTERNAL
        val (loadKind, weightKg) = when (capability) {
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

        viewModelScope.launch {
            val link = occurrenceLink(exerciseId)
            logResolved(
                WorkoutSet(
                    exerciseId = exerciseId,
                    reps = reps,
                    weightKg = weightKg,
                    loadKind = loadKind,
                    performedAtMillis = current.performedAtMillis ?: runtime.nowMillis(),
                    isWarmup = current.isWarmup,
                    weekNumber = activeActivation?.weekNumber ?: acceptedPlan?.weekNumber,
                    cycleNumber = activeActivation?.cycleNumber ?: acceptedPlan?.cycleNumber,
                    dayIndex = acceptedToday?.dayIndex,
                    rir = current.rir.toIntOrNull(),
                    occurrenceId = link?.first,
                    occurrenceEntryId = link?.second,
                    timingProvenance = if (current.performedAtMillis != null) {
                        WorkoutTimingProvenance.CATCH_UP
                    } else {
                        WorkoutTimingProvenance.UNKNOWN
                    }
                ),
                current
            )
            // Keep the reps and weight so repeated sets of the same exercise do not need retyping;
            // only the warm-up flag resets between sets.
            _state.update { it.copy(isWarmup = false) }
            refreshRecentSets()
            if (activeActivation != null) refreshOccurrence()
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

    /** Closes the open session; the next logged set auto-starts a new one. */
    fun endSession() {
        invalidateRestTimer()
        viewModelScope.launch { logMutations.endSession(runtime.nowMillis()) }
    }

    /** Closes the current session and immediately starts a new one. */
    fun newSession() {
        invalidateRestTimer()
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
        if (rebuildDrafts) draftContextRevision++
        val rebuiltDrafts = if (rebuildDrafts) {
            satisfiedDraftFree(
                drafts = acceptedToday?.exercises.orEmpty().map { exercise ->
                    DraftSet(
                        exerciseId = exercise.exerciseId,
                        name = exercise.name,
                        sets = exercise.sets,
                        reps = exercise.reps,
                        weightKg = exercise.suggestedWeightKg,
                        loadKind = exercise.loadKind,
                        loadCapability = exercise.loadCapability
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
                draftWriteRetries = if (rebuildDrafts) emptyList() else current.draftWriteRetries,
                confirmingAllDrafts = if (rebuildDrafts) false else current.confirmingAllDrafts,
                reps = current.reps,
                weightInput = if (rebuildDrafts) {
                    current.selectedExerciseId
                        ?.let { suggestedInputFor(it, current.weightUnit) }
                        ?: current.weightInput
                } else {
                    current.weightInput
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

    /** Logs every set of a draft and removes it from the pending list. */
    fun confirmDraft(draft: DraftSet) {
        if (draftSubmissionInProgress || draft !in _state.value.draftSets) return
        val retry = retryFor(draft)
        val edit = _state.value.draftEdit?.takeIf { it.draft == draft || editedDraft(it) == draft }
        // A resolved retry already decided this draft's load; otherwise an unconfirmed legacy number
        // always needs the explicit decision, even when an editor is open.
        if (retry == null) {
            legacyResolutionFor(listOf(draft))?.let { resolution ->
                _state.update { it.copy(legacyResolution = resolution) }
                return
            }
        }
        val resolved = when {
            edit != null -> editedDraft(edit)
            retry != null -> retry.draft
            else -> draft
        }
        if (retry == null && resolved.loadKind == LoadKind.EXTERNAL && resolved.weightKg == null) {
            setMissingLoadPrompt(draft)
            return
        }
        launchDraftWrite { contextRevision ->
            val written = writeDraft(draft, resolved, edit ?: retry?.edit, contextRevision)
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
        val base = retry?.draft ?: draft
        val retryEdit = retry?.edit
        _state.update {
            it.copy(
                draftEdit = DraftEdit(
                    draft = draft,
                    reps = base.reps.toString(),
                    weightInput = weightInputFor(base.weightKg, current.weightUnit),
                    rir = retryEdit?.rir.orEmpty(),
                    weightRevealed = base.loadKind == LoadKind.ADDED,
                    performedAtMillis = if (retryEdit != null) {
                        retryEdit.performedAtMillis
                    } else {
                        current.performedAtMillis
                    },
                    performedAtExplicit = retryEdit?.performedAtExplicit ?: false
                )
            )
        }
    }

    fun onDraftRepsChanged(value: String) = updateDraftEdit {
        copy(reps = value.filter(Char::isDigit))
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
        _state.update { it.copy(draftEdit = null, confirmingAllDrafts = false) }
    }

    fun resetDraftEdit() {
        val draft = _state.value.draftEdit?.draft ?: return
        editDraft(draft)
    }

    fun confirmDraftEdit() {
        val draft = _state.value.draftEdit?.draft ?: return
        if (_state.value.draftEdit?.reps?.toIntOrNull()?.let { it > 0 } != true) return
        val entryId = draft.occurrenceEntryId
        if (isGuidedActive && entryId != null) {
            confirmGuidedSet(entryId, liveCompletion = liveSetIntentEntryId == entryId)
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
            if (liveSetIntentEntryId == draft.occurrenceEntryId) liveSetIntentEntryId = null
            if (shouldContinue) continueConfirmAllDrafts()
        }
    }

    private fun updateDraftEdit(transform: DraftEdit.() -> DraftEdit) {
        _state.update { state -> state.copy(draftEdit = state.draftEdit?.transform()) }
    }

    private fun editedDraft(edit: DraftEdit): DraftSet {
        val capability = edit.draft.loadCapability
        val typed = edit.weightInput.toDoubleOrNull()?.let {
            _state.value.weightUnit.displayToKilograms(it)
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
        return edit.draft.copy(
            reps = edit.reps.toIntOrNull() ?: 0,
            weightKg = weight,
            loadKind = kind
        )
    }

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
        var completed = 0
        return try {
            logDraft(resolved, edit, contextRevision) { completed++ }
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
        _state.update {
            it.copy(
                draftSets = it.draftSets - draft,
                draftEdit = it.draftEdit?.takeUnless { edit -> edit.draft == draft },
                missingLoadPrompt = it.missingLoadPrompt?.takeUnless { pending ->
                    pending == draft
                },
                draftWriteRetries = it.draftWriteRetries.filterNot { retry ->
                    retry.source == draft || retry.draft == draft
                }
            )
        }
    }

    /** The pending retry for [draft], if any, matched by its source row or its remaining row. */
    private fun retryFor(draft: DraftSet): DraftWriteRetry? =
        _state.value.draftWriteRetries.firstOrNull { it.source == draft || it.draft == draft }

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
                draft.loadKind == LoadKind.EXTERNAL &&
                draft.weightKg == null
        }?.let { missing ->
            setMissingLoadPrompt(missing)
            return
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
                    logDraft(resolved, edit, contextRevision) { completed++ }
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
                        confirmingAllDrafts = false
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
        _state.update {
            it.copy(
                draftSets = it.draftSets - draft,
                draftEdit = it.draftEdit?.takeUnless { edit -> edit.draft == draft },
                missingLoadPrompt = it.missingLoadPrompt?.takeUnless { pending ->
                    pending == draft
                },
                draftWriteRetries = it.draftWriteRetries.filterNot { retry ->
                    retry.source == draft || retry.draft == draft
                }
            )
        }
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
        liveSetIntentEntryId = null
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
        onSetLogged: () -> Unit = {}
    ): String? {
        val link = if (draft.occurrenceId != null) {
            draft.occurrenceId to draft.occurrenceEntryId
        } else {
            occurrenceLink(draft.exerciseId)
        }
        var sessionId: String? = null
        repeat(draft.sets.coerceAtLeast(1)) {
            if (contextRevision != draftContextRevision) {
                throw CancellationException("Draft context changed")
            }
            val current = _state.value.copy(
                performedAtMillis = if (edit?.performedAtExplicit == true) {
                    edit.performedAtMillis
                } else {
                    _state.value.performedAtMillis
                }
            )
            sessionId = logResolved(
                WorkoutSet(
                    exerciseId = draft.exerciseId,
                    reps = draft.reps,
                    weightKg = if (draft.loadKind == LoadKind.BODYWEIGHT) null else draft.weightKg,
                    loadKind = draft.loadKind,
                    performedAtMillis = if (liveCompletion) {
                        liveSetCompletedAtWallMillis ?: runtime.nowMillis()
                    } else {
                        current.performedAtMillis ?: runtime.nowMillis()
                    },
                    isWarmup = false,
                    weekNumber = activeActivation?.weekNumber ?: acceptedPlan?.weekNumber,
                    cycleNumber = activeActivation?.cycleNumber ?: acceptedPlan?.cycleNumber,
                    dayIndex = acceptedToday?.dayIndex,
                    occurrenceId = link?.first,
                    occurrenceEntryId = link?.second,
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
                ),
                current,
                returnSessionId = liveCompletion
            )
            onSetLogged()
        }
        return sessionId
    }

    /** Prefills the weight field with the accepted plan's suggestion, in the display unit. */
    private fun suggestedInputFor(exerciseId: String, unit: WeightUnit): String =
        suggestedWeightKgByExercise[exerciseId]
            ?.let { formatWeight(unit.kilogramsToDisplay(it)) }
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
                    loadCapability = it.loadCapability
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
        val selectedId = scheduleState?.selectedOccurrenceId
        val occurrence = currentOccurrences.firstOrNull { it.id == selectedId }
            ?: currentOccurrences.filterNot { it.isResolved }.minByOrNull { it.queuePosition }
        val previousOccurrenceId = currentOccurrence?.id
        if (occurrence == null) {
            if (currentOccurrence != null) {
                draftContextRevision++
                invalidateRestTimer()
                liveSetIntentEntryId = null
            }
            currentOccurrence = null
            _state.update {
                it.copy(
                    activeOccurrence = null,
                    draftSets = emptyList(),
                    draftEdit = null,
                    missingLoadPrompt = null,
                    legacyResolution = null,
                    draftWriteRetries = emptyList(),
                    confirmingAllDrafts = false,
                    todayFocus = null,
                    guidedProgress = null,
                    guidedSetWriteFailed = false
                )
            }
            return
        }
        if (currentOccurrence != occurrence) {
            draftContextRevision++
            if (currentOccurrence != null) {
                invalidateRestTimer()
                liveSetIntentEntryId = null
            }
        }
        currentOccurrence = occurrence
        val performed = performedSetsByEntry(occurrence.id)
        val drafts = occurrence.entries.mapNotNull { entry ->
            val remaining = entry.sets - (performed[entry.id] ?: 0)
            if (remaining <= 0) {
                null
            } else {
                DraftSet(
                    entry.exerciseId,
                    entry.exerciseName,
                    remaining,
                    entry.reps,
                    entry.weightKg,
                    entry.loadKind,
                    loadCapability = entry.loadCapability,
                    occurrenceId = occurrence.id,
                    occurrenceEntryId = entry.id
                )
            }
        }
        // Each partial-write retry keeps its own row and one-off values across a refresh.
        val retries = _state.value.draftWriteRetries.filter { retry ->
            drafts.any { it.sameSlotAs(retry.source) }
        }
        val displayDrafts = drafts.map { draft ->
            retries.firstOrNull { draft.sameSlotAs(it.source) }?.draft ?: draft
        }
        val workout = activation.workouts.firstOrNull { it.id == occurrence.activationWorkoutId }
        val prescribed = occurrence.entries.sumOf { it.sets }
        val performedTotal = occurrence.entries.sumOf {
            (performed[it.id] ?: 0).coerceAtMost(it.sets)
        }
        _state.update {
            it.copy(
                activeOccurrence = ActiveOccurrence(
                    occurrenceId = occurrence.id,
                    workoutName = workout?.name.orEmpty(),
                    performedSets = performedTotal,
                    prescribedSets = prescribed
                ),
                draftSets = displayDrafts,
                draftEdit = it.draftEdit?.takeIf { edit -> edit.draft in displayDrafts },
                missingLoadPrompt = it.missingLoadPrompt?.takeIf { draft ->
                    draft in displayDrafts
                },
                legacyResolution = it.legacyResolution?.takeIf { resolution ->
                    resolution.items.all { item -> item.draft in displayDrafts }
                },
                draftWriteRetries = retries,
                // A batch continuation is scoped to one occurrence: switching away ends it.
                confirmingAllDrafts = it.confirmingAllDrafts &&
                    previousOccurrenceId == occurrence.id &&
                    displayDrafts.isNotEmpty(),
                todayFocus = workout?.focus,
                guidedProgress = buildGuidedWorkoutProgress(occurrence, performed),
                guidedSetWriteFailed = it.guidedSetWriteFailed &&
                    previousOccurrenceId == occurrence.id
            )
        }
    }

    /** True when both drafts describe the same frozen slot: the same occurrence entry, else exercise. */
    private fun DraftSet.sameSlotAs(other: DraftSet): Boolean =
        if (occurrenceEntryId != null || other.occurrenceEntryId != null) {
            occurrenceEntryId == other.occurrenceEntryId
        } else {
            exerciseId == other.exerciseId
        }

    /** The occurrence + entry a set for [exerciseId] belongs to, when a block is active. */
    private fun occurrenceLink(exerciseId: String): Pair<Long, Long?>? {
        val occurrence = currentOccurrence ?: return null
        val entry = occurrence.entries.firstOrNull { it.exerciseId == exerciseId }
        return occurrence.id to entry?.id
    }

    private suspend fun performedSetsByEntry(occurrenceId: Long): Map<Long, Int> = getWorkoutLog()
        .filter { set ->
            !set.isWarmup && set.occurrenceId == occurrenceId && set.occurrenceEntryId != null
        }
        .groupingBy { requireNotNull(it.occurrenceEntryId) }
        .eachCount()

    /** Concludes the current workout occurrence: finish, finish partially, or skip. */
    fun finishWorkout() = resolveCurrentOccurrence(FinishMode.FULL)

    fun finishWorkoutPartially() = resolveCurrentOccurrence(FinishMode.PARTIAL)

    fun skipWorkout() = resolveCurrentOccurrence(null)

    private fun resolveCurrentOccurrence(mode: FinishMode?) {
        val occurrence = currentOccurrence ?: return
        viewModelScope.launch {
            try {
                if (mode == null) {
                    loggingActions.skip(occurrence.id)
                } else {
                    loggingActions.finish(occurrence.id, mode)
                }
                _state.update { it.copy(occurrenceMessage = null) }
            } catch (error: ScheduleException) {
                _state.update { it.copy(occurrenceMessage = error.message) }
            }
        }
    }

    fun onOccurrenceMessageShown() = _state.update { it.copy(occurrenceMessage = null) }

    fun onRestDurationChanged(value: String) {
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
     * Records exactly one prescribed working set for [occurrenceEntryId], leaving any remaining
     * prescribed sets pending. Reuses the same load-shape and legacy/missing-load decisions as the
     * batch path, but never writes more than one set.
     */
    fun confirmGuidedSet(occurrenceEntryId: Long) {
        confirmGuidedSet(occurrenceEntryId, liveCompletion = false)
    }

    fun confirmGuidedSetNow(occurrenceEntryId: Long) {
        if (_state.value.startedSetEntryId != occurrenceEntryId) return
        confirmGuidedSet(occurrenceEntryId, liveCompletion = true)
    }

    /** Records an explicit set-start intent without creating a performed set. */
    fun startGuidedSet(occurrenceEntryId: Long) {
        if (draftSubmissionInProgress) return
        if (_state.value.draftSets.none { it.occurrenceEntryId == occurrenceEntryId }) return
        if (_state.value.performedAtMillis != null) onPerformedAtChanged(null)
        liveSetIntentEntryId = occurrenceEntryId
        liveSetStartedAtElapsedMillis = runtime.elapsedRealtimeMillis()
        liveSetCompletedAtElapsedMillis = null
        liveSetCompletedAtWallMillis = null
        _state.update { it.copy(startedSetEntryId = occurrenceEntryId) }
    }

    private fun confirmGuidedSet(occurrenceEntryId: Long, liveCompletion: Boolean) {
        if (draftSubmissionInProgress) return
        val draft = _state.value.draftSets.firstOrNull { it.occurrenceEntryId == occurrenceEntryId }
            ?: return
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
        val edit = _state.value.draftEdit?.takeIf { it.draft == draft || editedDraft(it) == draft }
        val resolved = when {
            edit != null -> editedDraft(edit)
            retry != null -> retry.draft
            else -> draft
        }
        if (retry == null && resolved.loadKind == LoadKind.EXTERNAL && resolved.weightKg == null) {
            setMissingLoadPrompt(draft)
            return
        }
        launchGuidedWrite(
            resolved,
            edit ?: retry?.edit,
            liveCompletion = liveSetIntentEntryId == occurrenceEntryId
        )
    }

    /** Claims the submission guard and writes a single guided set, then refreshes or reports failure. */
    private fun launchGuidedWrite(
        resolved: DraftSet,
        edit: DraftEdit?,
        liveCompletion: Boolean = false
    ) {
        val contextRevision = draftContextRevision
        val timerGeneration = liveTimerGeneration
        if (!beginDraftSubmission()) return
        viewModelScope.launch {
            val writeResult = try {
                writeGuidedSet(resolved, edit, contextRevision, liveCompletion)
            } finally {
                endDraftSubmission()
            }
            applyGuidedWriteResult(
                resolved,
                writeResult,
                contextRevision,
                timerGeneration,
                liveCompletion
            )
        }
    }

    /**
     * Applies the shared result of one guided-set write. Every guided resolution path routes its
     * outcome here, so a successful write always consumes the live start/completion event through
     * [clearLiveSetStart] exactly once, and starts the rest prompt only for a live completion whose
     * context is still current. A failure keeps the event for an explicit retry.
     */
    private suspend fun applyGuidedWriteResult(
        resolved: DraftSet,
        writeResult: GuidedSetWriteResult,
        contextRevision: Long,
        timerGeneration: Long,
        liveCompletion: Boolean
    ) {
        if (writeResult.succeeded) {
            val contextStillCurrent = contextRevision == draftContextRevision &&
                timerGeneration == liveTimerGeneration
            if (liveCompletion && contextStillCurrent && isGuidedActive) {
                startRestTimerIfDurationValid(
                    resolved.occurrenceId,
                    resolved.exerciseId,
                    writeResult.sessionId
                )
            }
            applyGuidedWriteSuccess()
            clearLiveSetStart()
        } else {
            _state.update { it.copy(guidedSetWriteFailed = true) }
        }
    }

    /**
     * Writes exactly one working set for the confirmed [resolved] values. A single insert either
     * lands or does not, so a failure leaves the pending draft untouched for an explicit retry.
     */
    private suspend fun writeGuidedSet(
        resolved: DraftSet,
        edit: DraftEdit?,
        contextRevision: Long,
        liveCompletion: Boolean = false
    ): GuidedSetWriteResult = try {
        if (contextRevision != draftContextRevision) {
            throw CancellationException("Draft context changed")
        }
        val sessionId = logDraft(
            resolved.copy(sets = 1),
            edit,
            contextRevision,
            liveCompletion = liveCompletion
        )
        ensureDraftContext(contextRevision)
        GuidedSetWriteResult(true, sessionId)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        ensureDraftContext(contextRevision)
        GuidedSetWriteResult(false, null)
    }

    private suspend fun startRestTimerIfDurationValid(
        occurrenceId: Long?,
        exerciseId: String,
        sessionId: String?
    ) {
        if (!_state.value.canStartRestTimer) return
        val seconds = settings.restDurationSeconds(exerciseId).takeIf {
            it > 0L && it <= RestCountdown.MAX_DURATION_MILLIS / 1_000L
        } ?: return
        val durationMillis = seconds * 1_000L
        val hasOverride = settings.hasExerciseRestDurationOverride(exerciseId)
        timerOccurrenceId = occurrenceId
        timerSessionId = sessionId
        timerExerciseId = exerciseId
        _state.update {
            it.copy(restDurationSeconds = seconds.toString(), restDurationIsOverride = hasOverride)
        }
        restTimer.start(
            durationMillis = durationMillis,
            completedAtElapsedMillis = liveSetCompletedAtElapsedMillis
                ?: runtime.elapsedRealtimeMillis(),
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
                missingLoadPrompt = null,
                legacyResolution = null,
                draftEdit = null
            )
        }
        refreshRecentSets()
        if (activeActivation != null) refreshOccurrence()
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
                    weightKg = set.weightKg,
                    rir = set.rir,
                    isWarmup = set.isWarmup,
                    weekNumber = set.weekNumber,
                    dayIndex = set.dayIndex,
                    loadKind = set.loadKind,
                    occurrenceId = set.occurrenceId
                )
            }
        _state.update { it.copy(recentSets = rows) }
    }
}
