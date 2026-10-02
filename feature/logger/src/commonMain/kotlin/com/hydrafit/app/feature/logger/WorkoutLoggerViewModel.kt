package com.hydrafit.app.feature.logger

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hydrafit.app.core.domain.engine.AcceptedDay
import com.hydrafit.app.core.domain.engine.AcceptedPlan
import com.hydrafit.app.core.domain.engine.ExerciseCatalog
import com.hydrafit.app.core.domain.engine.ObserveAcceptedPlanUseCase
import com.hydrafit.app.core.domain.equipment.EquipmentTag
import com.hydrafit.app.core.domain.equipment.Exercise
import com.hydrafit.app.core.domain.fatigue.FatigueConfig
import com.hydrafit.app.core.domain.time.TimeProvider
import com.hydrafit.app.core.domain.time.localDayOfWeek
import com.hydrafit.app.core.domain.unit.WeightUnit
import com.hydrafit.app.core.domain.unit.formatWeight
import com.hydrafit.app.core.domain.workout.DeleteWorkoutSetUseCase
import com.hydrafit.app.core.domain.workout.GetWorkoutLogUseCase
import com.hydrafit.app.core.domain.workout.LogWorkoutSetUseCase
import com.hydrafit.app.core.domain.workout.WorkoutSet
import com.hydrafit.app.core.userdata.settings.WeightUnitRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class WorkoutLoggerViewModel(
    private val logWorkoutSet: LogWorkoutSetUseCase,
    private val getWorkoutLog: GetWorkoutLogUseCase,
    private val deleteWorkoutSet: DeleteWorkoutSetUseCase,
    private val observeAcceptedPlan: ObserveAcceptedPlanUseCase,
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

    init {
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
            observeAcceptedPlan().collectLatest { plan ->
                acceptedPlan = plan
                updateTodayPlan(plan)
            }
        }
        viewModelScope.launch {
            weightUnitRepository.unitFlow().collectLatest { unit ->
                _state.update { it.copy(weightUnit = unit) }
            }
        }
        viewModelScope.launch {
            // Active-session state is derived from the persisted open session, so it survives a restart.
            logWorkoutSet.observeOpenSession().collectLatest { session ->
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
            current.copy(
                weightInput = last.weightKg
                    ?.let { formatWeight(unit.kilogramsToDisplay(it)) }
                    .orEmpty(),
                reps = last.reps.toString()
            )
        }
    }

    /**
     * Sets the explicit time to stamp new sets with, or clears it with null to log live. Returns
     * false when a future time is rejected; the current selection is left unchanged in that case.
     */
    fun onPerformedAtChanged(millis: Long?): Boolean {
        if (millis != null && millis > timeProvider.nowMillis()) return false
        _state.update { it.copy(performedAtMillis = millis) }
        return true
    }

    /** Reveals the weight field for a bodyweight exercise so a weighted variant can be logged. */
    fun onRevealWeight() {
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
            deleteWorkoutSet(id)
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
        val weightKg = if (current.showWeightField) {
            current.weightInput.toDoubleOrNull()
                ?.let { current.weightUnit.displayToKilograms(it) }
        } else {
            null
        }

        viewModelScope.launch {
            logWorkoutSet(
                WorkoutSet(
                    exerciseId = exerciseId,
                    reps = reps,
                    weightKg = weightKg,
                    performedAtMillis = current.performedAtMillis ?: timeProvider.nowMillis(),
                    isWarmup = current.isWarmup,
                    weekNumber = acceptedPlan?.weekNumber,
                    cycleNumber = acceptedPlan?.cycleNumber,
                    dayIndex = acceptedToday?.dayIndex,
                    rir = current.rir.toIntOrNull()
                ),
                timeProvider.utcOffsetMillis()
            )
            // Keep the reps and weight so repeated sets of the same exercise do not need retyping;
            // only the warm-up flag resets between sets.
            _state.update { it.copy(isWarmup = false) }
            refreshRecentSets()
        }
    }

    /** Closes the open session; the next logged set auto-starts a new one. */
    fun endSession() {
        viewModelScope.launch { logWorkoutSet.endSession(timeProvider.nowMillis()) }
    }

    /** Closes the current session and immediately starts a new one. */
    fun newSession() {
        viewModelScope.launch {
            logWorkoutSet.startNewSession(
                startedAtMillis = timeProvider.nowMillis(),
                utcOffsetMillis = timeProvider.utcOffsetMillis()
            )
        }
    }

    /** Recomputes today's focus/drafts, e.g. when the screen resumes after a local midnight. */
    fun onResume() {
        updateTodayPlan(acceptedPlan)
        // Opening the logger is an "open time": expire a session that rolled into a new day or went idle.
        viewModelScope.launch {
            logWorkoutSet.expireOpenSession(
                nowMillis = timeProvider.nowMillis(),
                utcOffsetMillis = timeProvider.utcOffsetMillis()
            )
        }
    }

    private fun updateTodayPlan(plan: AcceptedPlan?) {
        acceptedToday = plan?.dayFor(
            localDayOfWeek(timeProvider.nowMillis(), timeProvider.utcOffsetMillis())
        )
        suggestedWeightKgByExercise = acceptedToday?.exercises
            ?.mapNotNull { exercise ->
                exercise.suggestedWeightKg?.let { exercise.exerciseId to it }
            }
            ?.toMap()
            .orEmpty()
        _state.update { current ->
            current.copy(
                exercises = prioritizedByToday(exercises, acceptedToday),
                todayFocus = acceptedToday?.focus,
                // Today's planned exercises become drafts the user must confirm before they count.
                draftSets = acceptedToday?.exercises.orEmpty().map { exercise ->
                    DraftSet(
                        exerciseId = exercise.exerciseId,
                        name = exercise.name,
                        sets = exercise.sets,
                        reps = exercise.reps,
                        weightKg = exercise.suggestedWeightKg
                    )
                },
                reps = current.reps,
                weightInput = current.selectedExerciseId
                    ?.let { suggestedInputFor(it, current.weightUnit) }
                    ?: current.weightInput
            )
        }
    }

    /** Logs every set of a draft and removes it from the pending list. */
    fun confirmDraft(draft: DraftSet) {
        viewModelScope.launch {
            logDraft(draft)
            _state.update { it.copy(draftSets = it.draftSets - draft) }
            refreshRecentSets()
        }
    }

    /** Logs every pending draft and clears the list. */
    fun confirmAllDrafts() {
        val drafts = _state.value.draftSets
        if (drafts.isEmpty()) return
        viewModelScope.launch {
            drafts.forEach { logDraft(it) }
            _state.update { it.copy(draftSets = emptyList()) }
            refreshRecentSets()
        }
    }

    /** Removes a draft without logging anything. */
    fun dismissDraft(draft: DraftSet) {
        _state.update { it.copy(draftSets = it.draftSets - draft) }
    }

    private suspend fun logDraft(draft: DraftSet) {
        repeat(draft.sets.coerceAtLeast(1)) {
            logWorkoutSet(
                WorkoutSet(
                    exerciseId = draft.exerciseId,
                    reps = draft.reps,
                    weightKg = draft.weightKg,
                    performedAtMillis = _state.value.performedAtMillis ?: timeProvider.nowMillis(),
                    isWarmup = false,
                    weekNumber = acceptedPlan?.weekNumber,
                    cycleNumber = acceptedPlan?.cycleNumber,
                    dayIndex = acceptedToday?.dayIndex
                ),
                timeProvider.utcOffsetMillis()
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
                    isBodyweight = it.requiredEquipment.isEmpty() ||
                        EquipmentTag.BODYWEIGHT in it.requiredEquipment,
                    isUnilateral = it.isUnilateral
                )
            }
            .sortedWith(compareBy({ priority[it.id] ?: Int.MAX_VALUE }, { it.name }))
    }

    private suspend fun refreshRecentSets() {
        val rows = getWorkoutLog()
            .sortedByDescending { it.performedAtMillis }
            .map { set ->
                LoggedSetRow(
                    id = set.id,
                    exerciseName = exerciseNames[set.exerciseId] ?: set.exerciseId,
                    reps = set.reps,
                    weightKg = set.weightKg,
                    isWarmup = set.isWarmup,
                    weekNumber = set.weekNumber,
                    dayIndex = set.dayIndex
                )
            }
        _state.update { it.copy(recentSets = rows) }
    }
}
