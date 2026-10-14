package com.hydrafit.app.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hydrafit.app.core.domain.engine.ExerciseCatalog
import com.hydrafit.app.core.domain.equipment.ExerciseLoadCapability
import com.hydrafit.app.core.domain.setup.SaveSetupStepUseCase
import com.hydrafit.app.core.domain.setup.SetupDisposition
import com.hydrafit.app.core.domain.setup.SetupStep
import com.hydrafit.app.core.domain.setup.SetupStepInput
import com.hydrafit.app.core.domain.setup.SkipSetupUseCase
import com.hydrafit.app.core.domain.setup.StaleSetupContextException
import com.hydrafit.app.core.domain.setup.snapshot
import com.hydrafit.app.core.domain.startup.StartupReadiness
import com.hydrafit.app.core.navigation.AppTab
import com.hydrafit.app.core.userdata.equipment.EquipmentRepository
import com.hydrafit.app.core.userdata.equipment.EquipmentSelectionRepository
import com.hydrafit.app.core.userdata.equipment.PersonalRecordRepository
import com.hydrafit.app.core.userdata.settings.PreferredWeekdaysRepository
import com.hydrafit.app.core.userdata.settings.SetupRepository
import com.hydrafit.app.core.userdata.settings.TrainingGoalRepository
import com.hydrafit.app.core.userdata.settings.WeightUnitRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Owns one setup editor journey; all persistent policy and atomic writes stay behind use cases. */
class OnboardingViewModel(
    private val setup: SetupRepository,
    private val saveStep: SaveSetupStepUseCase,
    private val skipSetup: SkipSetupUseCase,
    private val units: WeightUnitRepository,
    private val equipment: EquipmentRepository,
    private val selection: EquipmentSelectionRepository,
    private val goals: TrainingGoalRepository,
    private val weekdays: PreferredWeekdaysRepository,
    private val records: PersonalRecordRepository,
    private val catalog: ExerciseCatalog,
    private val readiness: StartupReadiness,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default
) : ViewModel() {
    private val _state = MutableStateFlow(OnboardingUiState())
    val state: StateFlow<OnboardingUiState> = _state.asStateFlow()
    private var contextRevision = 0L
    private var revisit = false
    private var writeJob: Job? = null
    private var inFlight = false

    /** Explicit entry only: constructing the ViewModel cannot write or start a setup journey. */
    fun open(revisit: Boolean = false) {
        val context = ++contextRevision
        this.revisit = revisit
        _state.value = OnboardingUiState(loading = true)
        viewModelScope.launch {
            try {
                writeJob?.join()
                readiness.isReady.first { it }
                val committed = withContext(dispatcher) { setup.current() }
                if (context != contextRevision) return@launch
                if (!revisit && committed.disposition != SetupDisposition.PENDING) {
                    _state.update {
                        it.copy(committed = committed, loading = false, finished = true)
                    }
                } else {
                    val step = if (revisit) SetupStep.WELCOME else committed.step
                    _state.update { it.copy(committed = committed, step = step) }
                    loadStep(step, context)
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (failure: Exception) {
                if (context == contextRevision) {
                    _state.update { it.copy(loading = false, failure = OnboardingFailure.LOAD) }
                }
            }
        }
    }

    fun edit(input: SetupStepInput) {
        val current = _state.value
        if (inFlight || current.loading || current.finished || input.step != current.step) return
        _state.update { it.copy(input = input.snapshot(), failure = null) }
    }

    /**
     * Stages the open entry through the same rule Next and a switch use, so the two can never stage
     * different records. It writes nothing to storage, keeps the entry open, and stages nothing when
     * the text is unchanged from the pre-filled origin or does not parse.
     */
    fun savePersonalRecordEntry() {
        val current = _state.value
        val draft = current.input as? SetupStepInput.PersonalRecords ?: return
        val entry = current.personalRecordEntry ?: return
        val record = decidePersonalRecordEntry(entry, current.entryUnit) ?: return
        edit(draft.copy(edits = withDraftRecord(draft.edits, record)))
    }

    /**
     * Opens the one PR entry for an eligible exercise, pre-filled from the value the row already shows
     * and remembering that value as the entry's origin. Opening writes nothing, and re-opening the
     * entry that is already open keeps what was typed. Opening a different exercise first stages the
     * entry already open by the same rule Save and Next use, so its typed text survives as that row's
     * value instead of being discarded; an untouched, blank or invalid entry stages nothing and never
     * blocks the switch.
     */
    fun openPersonalRecordEntry(exerciseId: String) {
        val current = _state.value
        if (inFlight || current.loading || current.finished) return
        if (current.personalRecordEntry?.exerciseId == exerciseId) return
        val draft = current.input as? SetupStepInput.PersonalRecords ?: return
        if (current.eligibleExercises.none { it.id == exerciseId }) return
        val staged = foldPersonalRecordEntry(draft, current.personalRecordEntry, current.entryUnit)
        val edits = (staged as? SetupStepInput.PersonalRecords)?.edits ?: draft.edits
        val shown = displayedRecord(exerciseId, edits, current.existingRecords)
        _state.update {
            it.copy(
                input = staged,
                personalRecordEntry = personalRecordEntryDraft(exerciseId, it.entryUnit, shown)
            )
        }
    }

    /** The open entry's raw text, exactly as typed; it is parsed only when Save or Next reads it. */
    fun editPersonalRecordEntry(weightText: String, repsText: String) {
        val current = _state.value
        if (inFlight || current.loading || current.finished) return
        val entry = current.personalRecordEntry ?: return
        _state.update {
            it.copy(personalRecordEntry = entry.copy(weightText = weightText, repsText = repsText))
        }
    }

    /** "I don't know": closes the entry without recording and without touching any stored record. */
    fun dismissPersonalRecordEntry() {
        val current = _state.value
        if (inFlight || current.loading || current.finished) return
        if (current.personalRecordEntry == null) return
        _state.update { it.copy(personalRecordEntry = null) }
    }

    fun back() {
        val current = _state.value
        if (inFlight ||
            current.loading ||
            current.finished ||
            current.step == SetupStep.WELCOME
        ) {
            return
        }
        val context = ++contextRevision
        val previous = SetupStep.entries[current.step.ordinal - 1]
        _state.update { it.copy(step = previous, input = null, loading = true, failure = null) }
        viewModelScope.launch {
            try {
                loadStep(previous, context)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (failure: Exception) {
                if (context == contextRevision) {
                    _state.update { it.copy(loading = false, failure = OnboardingFailure.LOAD) }
                }
            }
        }
    }

    fun next() = submit(skip = false)

    fun skip() = submit(skip = true)

    /**
     * Requests one first-workout destination. It completes the final step exactly like Next and only
     * then leaves [OnboardingUiState.pendingHandoff] set for the screen to open the tab, so a failed
     * write keeps the failure and retry state on the final step instead of navigating. Any other step
     * has no reachable handoff.
     */
    fun handoff(tab: AppTab) {
        val current = _state.value
        if (current.step != SetupStep.FIRST_WORKOUT) return
        if (current.input?.step != SetupStep.FIRST_WORKOUT) return
        if (inFlight || current.loading || current.finished) return
        _state.update { it.copy(pendingHandoff = tab) }
        submit(skip = false)
    }

    fun retryLoad() {
        val current = _state.value
        if (inFlight || current.loading || current.failure != OnboardingFailure.LOAD) return
        if (current.committed == null) {
            open(revisit)
            return
        }
        val context = ++contextRevision
        _state.update { it.copy(loading = true, failure = null) }
        viewModelScope.launch {
            try {
                loadStep(current.step, context)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (failure: Exception) {
                if (context == contextRevision) {
                    _state.update { it.copy(loading = false, failure = OnboardingFailure.LOAD) }
                }
            }
        }
    }

    fun close() {
        contextRevision++
        _state.value = OnboardingUiState()
    }

    private fun submit(skip: Boolean) {
        val current = _state.value
        val committed = current.committed ?: return
        // Skip records nothing at all, so its submission never folds the open entry; Next folds a valid
        // entry into the draft it writes, so typed values are not discarded.
        val folded = if (skip) {
            current.input
        } else {
            foldPersonalRecordEntry(current.input, current.personalRecordEntry, current.entryUnit)
        }
        val input = folded?.snapshot()
        if (!skip && !isSubmittable(input)) return
        if (inFlight || current.loading || current.finished) return
        val context = contextRevision
        val entryIsRevisit = revisit
        inFlight = true
        _state.update { it.copy(saving = true, failure = null) }
        writeJob = viewModelScope.launch {
            var writeCommitted = false
            try {
                val saved = if (skip) {
                    skipSetup(committed, entryIsRevisit)
                } else {
                    saveStep(committed, requireNotNull(input), entryIsRevisit)
                }
                writeCommitted = true
                if (context != contextRevision) return@launch
                val finished = skip || input?.step == SetupStep.FIRST_WORKOUT
                _state.update { it.copy(committed = saved, finished = finished) }
                if (!finished) {
                    _state.update { it.copy(step = saved.step, input = null, loading = true) }
                    loadStep(saved.step, context)
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (failure: Exception) {
                val reason = when {
                    writeCommitted -> OnboardingFailure.LOAD
                    failure is StaleSetupContextException -> OnboardingFailure.STALE_CONTEXT
                    failure is IllegalArgumentException -> OnboardingFailure.INVALID_INPUT
                    else -> OnboardingFailure.SAVE
                }
                if (context == contextRevision) {
                    _state.update {
                        it.copy(failure = reason, loading = false, pendingHandoff = null)
                    }
                }
            } finally {
                inFlight = false
                if (context == contextRevision) _state.update { it.copy(saving = false) }
            }
        }
    }

    private suspend fun loadStep(step: SetupStep, context: Long) {
        val loaded = withContext(dispatcher) {
            val base = _state.value.copy(
                step = step,
                failure = null,
                equipment = emptyList(),
                eligibleExercises = emptyList(),
                existingRecords = emptyList(),
                // A reloaded step never keeps a previously open PR entry.
                personalRecordEntry = null
            )
            when (step) {
                SetupStep.WELCOME, SetupStep.FIRST_WORKOUT ->
                    base.copy(input = SetupStepInput.Continue(step))
                SetupStep.UNITS -> base.copy(input = SetupStepInput.Units(units.selectedUnit()))
                SetupStep.EQUIPMENT -> base.copy(
                    equipment = equipment.all(),
                    input = SetupStepInput.Equipment(selection.selected())
                )
                SetupStep.GOAL -> base.copy(input = SetupStepInput.Goal(goals.selectedGoal()))
                SetupStep.WEEKDAYS -> base.copy(input = SetupStepInput.Weekdays(weekdays.current()))
                SetupStep.PERSONAL_RECORDS -> base.copy(
                    input = SetupStepInput.PersonalRecords(emptyList()),
                    entryUnit = units.selectedUnit(),
                    eligibleExercises = catalog.all().filter {
                        it.loadCapability == ExerciseLoadCapability.EXTERNAL
                    },
                    existingRecords = records.observe().first()
                )
            }
        }
        if (context == contextRevision) _state.value = loaded.copy(loading = false)
    }
}
