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
import com.hydrafit.app.core.domain.time.TimeProvider
import com.hydrafit.app.core.domain.time.localDayOfWeek
import com.hydrafit.app.core.domain.time.localEpochDay
import com.hydrafit.app.core.domain.unit.WeightUnit
import com.hydrafit.app.core.domain.unit.formatWeight
import com.hydrafit.app.core.domain.workout.GetWorkoutLogUseCase
import com.hydrafit.app.core.domain.workout.LoadKind
import com.hydrafit.app.core.domain.workout.WorkoutLogMutations
import com.hydrafit.app.core.domain.workout.WorkoutSet
import com.hydrafit.app.core.userdata.settings.WeightUnitRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class WorkoutLoggerViewModel(
    private val logMutations: WorkoutLogMutations,
    private val getWorkoutLog: GetWorkoutLogUseCase,
    private val loggingActions: WorkoutLoggingActions,
    private val exerciseCatalog: ExerciseCatalog,
    private val timeProvider: TimeProvider,
    private val weightUnitRepository: WeightUnitRepository
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

    init {
        _state.update { it.copy(utcOffsetMillis = timeProvider.utcOffsetMillis()) }
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
                activeActivation = activation
                occurrencesJob?.cancel()
                if (activation == null) {
                    currentOccurrences = emptyList()
                    currentOccurrence = null
                    _state.update { it.copy(activeOccurrence = null) }
                    updateTodayPlan(acceptedPlan)
                } else {
                    occurrencesJob = viewModelScope.launch {
                        loggingActions.observeOccurrences(activation.id).collect { occurrences ->
                            currentOccurrences = occurrences
                            refreshOccurrence()
                        }
                    }
                }
            }
        }
        viewModelScope.launch {
            weightUnitRepository.unitFlow().collectLatest { unit ->
                _state.update { it.copy(weightUnit = unit) }
            }
        }
        viewModelScope.launch {
            // Active-session state is derived from the persisted open session, so it survives a restart.
            logMutations.observeOpenSession().collectLatest { session ->
                _state.update { it.copy(activeSession = session) }
            }
        }
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
        if (millis != null && millis > timeProvider.nowMillis()) return false
        resolvedBackdatedSessionId = null
        _state.update { it.copy(performedAtMillis = millis) }
        return true
    }

    /**
     * Forces a fresh backdated session instead of attaching to the open one. Takes effect on the
     * next log and drops any session already resolved for the previous choice.
     */
    fun onForceNewSessionChanged(forceNewSession: Boolean) {
        resolvedBackdatedSessionId = null
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
                timeProvider.utcOffsetMillis()
            )
        )

    /** The current wall-clock time, used to seed the backdated pickers. */
    fun currentTimeMillis(): Long = timeProvider.nowMillis()

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
            timeProvider.utcOffsetMillis()
        )
        if (millis > timeProvider.nowMillis()) return false
        viewModelScope.launch {
            logMutations.correctTime(setId, millis)
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
            logMutations.delete(id)
            refreshRecentSets()
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
                    performedAtMillis = current.performedAtMillis ?: timeProvider.nowMillis(),
                    isWarmup = current.isWarmup,
                    weekNumber = activeActivation?.weekNumber ?: acceptedPlan?.weekNumber,
                    cycleNumber = activeActivation?.cycleNumber ?: acceptedPlan?.cycleNumber,
                    dayIndex = acceptedToday?.dayIndex,
                    rir = current.rir.toIntOrNull(),
                    occurrenceId = link?.first,
                    occurrenceEntryId = link?.second
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
    private suspend fun logResolved(set: WorkoutSet, current: WorkoutLoggerUiState) {
        val utcOffsetMillis = timeProvider.utcOffsetMillis()
        if (current.performedAtMillis == null) {
            logMutations(set, utcOffsetMillis)
            return
        }
        val cached = resolvedBackdatedSessionId
        if (cached != null) {
            logMutations.logInto(set, cached)
            return
        }
        val session = logMutations.logBackdated(set, utcOffsetMillis, current.forceNewSession)
        if (session.id != current.activeSession?.id) {
            resolvedBackdatedSessionId = session.id
        }
    }

    /** Closes the open session; the next logged set auto-starts a new one. */
    fun endSession() {
        viewModelScope.launch { logMutations.endSession(timeProvider.nowMillis()) }
    }

    /** Closes the current session and immediately starts a new one. */
    fun newSession() {
        viewModelScope.launch {
            logMutations.startNewSession(
                startedAtMillis = timeProvider.nowMillis(),
                utcOffsetMillis = timeProvider.utcOffsetMillis()
            )
        }
    }

    /** Recomputes today's focus/drafts, e.g. when the screen resumes after a local midnight. */
    fun onResume() {
        _state.update { it.copy(utcOffsetMillis = timeProvider.utcOffsetMillis()) }
        viewModelScope.launch {
            refreshToday()
            // Opening the logger is an "open time": expire a session that rolled into a new day or went idle.
            logMutations.expireOpenSession(
                nowMillis = timeProvider.nowMillis(),
                utcOffsetMillis = timeProvider.utcOffsetMillis()
            )
        }
    }

    private suspend fun updateTodayPlan(plan: AcceptedPlan?) {
        val nowMillis = timeProvider.nowMillis()
        val utcOffsetMillis = timeProvider.utcOffsetMillis()
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
        val rebuiltDrafts = if (rebuildDrafts) {
            satisfiedDraftFree(
                drafts = acceptedToday?.exercises.orEmpty().map { exercise ->
                    DraftSet(
                        exerciseId = exercise.exerciseId,
                        name = exercise.name,
                        sets = exercise.sets,
                        reps = exercise.reps,
                        weightKg = exercise.suggestedWeightKg,
                        loadKind = exercise.loadKind
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
        val resolution = legacyResolutionFor(listOf(draft))
        if (resolution != null) {
            _state.update { it.copy(legacyResolution = resolution) }
            return
        }
        viewModelScope.launch {
            logDraft(draft)
            _state.update { it.copy(draftSets = it.draftSets - draft) }
            refreshRecentSets()
            if (activeActivation != null) refreshOccurrence()
        }
    }

    /**
     * Logs every pending draft and clears the list. A preflight stops before any write when a draft's
     * stored load meaning is unconfirmed, so a legacy number is never silently logged as external.
     */
    fun confirmAllDrafts() {
        val drafts = _state.value.draftSets
        if (drafts.isEmpty()) return
        val resolution = legacyResolutionFor(drafts)
        if (resolution != null) {
            _state.update { it.copy(legacyResolution = resolution) }
            return
        }
        viewModelScope.launch {
            drafts.forEach { logDraft(it) }
            _state.update { it.copy(draftSets = emptyList()) }
            refreshRecentSets()
            if (activeActivation != null) refreshOccurrence()
        }
    }

    /** Removes a draft without logging anything. */
    fun dismissDraft(draft: DraftSet) {
        _state.update { it.copy(draftSets = it.draftSets - draft) }
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
        _state.update { it.copy(legacyResolution = null) }
    }

    private fun resolveLegacy(kind: LoadKind, weightKg: Double?) {
        val resolution = _state.value.legacyResolution ?: return
        val item = resolution.current
        viewModelScope.launch {
            logDraft(item.draft.copy(loadKind = kind, weightKg = weightKg))
            val remaining = resolution.items.drop(1)
            _state.update { state ->
                state.copy(
                    draftSets = state.draftSets - item.draft,
                    legacyResolution = if (remaining.isEmpty()) {
                        null
                    } else {
                        LegacyResolution(remaining)
                    }
                )
            }
            refreshRecentSets()
            if (activeActivation != null) refreshOccurrence()
        }
    }

    /** The legacy drafts among [drafts] that need an explicit load decision, or null when none. */
    private fun legacyResolutionFor(drafts: List<DraftSet>): LegacyResolution? {
        val legacy = drafts.filter { it.loadKind == LoadKind.LEGACY_UNSPECIFIED }
        if (legacy.isEmpty()) return null
        return LegacyResolution(
            legacy.map { draft ->
                val capability = exercises.firstOrNull { it.id == draft.exerciseId }?.loadCapability
                    ?: ExerciseLoadCapability.UNSPECIFIED
                LegacyResolutionItem(draft, capability)
            }
        )
    }

    private suspend fun logDraft(draft: DraftSet) {
        val link = occurrenceLink(draft.exerciseId)
        repeat(draft.sets.coerceAtLeast(1)) {
            val current = _state.value
            logResolved(
                WorkoutSet(
                    exerciseId = draft.exerciseId,
                    reps = draft.reps,
                    weightKg = if (draft.loadKind == LoadKind.BODYWEIGHT) null else draft.weightKg,
                    loadKind = draft.loadKind,
                    performedAtMillis = current.performedAtMillis ?: timeProvider.nowMillis(),
                    isWarmup = false,
                    weekNumber = activeActivation?.weekNumber ?: acceptedPlan?.weekNumber,
                    cycleNumber = activeActivation?.cycleNumber ?: acceptedPlan?.cycleNumber,
                    dayIndex = acceptedToday?.dayIndex,
                    occurrenceId = link?.first,
                    occurrenceEntryId = link?.second
                ),
                current
            )
        }
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
        if (occurrence == null) {
            currentOccurrence = null
            _state.update {
                it.copy(activeOccurrence = null, draftSets = emptyList(), todayFocus = null)
            }
            return
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
                    entry.loadKind
                )
            }
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
                draftSets = drafts,
                todayFocus = workout?.focus
            )
        }
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
                    loadKind = set.loadKind
                )
            }
        _state.update { it.copy(recentSets = rows) }
    }
}
