package com.hydrafit.app.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hydrafit.app.core.domain.engine.ExerciseCatalog
import com.hydrafit.app.core.domain.engine.PersonalRecord
import com.hydrafit.app.core.domain.equipment.ExerciseLoadCapability
import com.hydrafit.app.core.domain.setup.SaveSetupStepUseCase
import com.hydrafit.app.core.domain.setup.SetupDisposition
import com.hydrafit.app.core.domain.setup.SetupStep
import com.hydrafit.app.core.domain.setup.SetupStepInput
import com.hydrafit.app.core.domain.setup.SkipSetupUseCase
import com.hydrafit.app.core.domain.setup.StaleSetupContextException
import com.hydrafit.app.core.domain.setup.snapshot
import com.hydrafit.app.core.domain.startup.StartupReadiness
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

    /** The displayed unit is frozen when the PR step opens, not read again after submission. */
    fun editPersonalRecord(exerciseId: String, displayWeight: Double, reps: Int) {
        val current = _state.value
        val draft = current.input as? SetupStepInput.PersonalRecords ?: return
        if (current.eligibleExercises.none { it.id == exerciseId }) return
        val record = PersonalRecord(
            exerciseId,
            current.entryUnit.displayToKilograms(displayWeight),
            reps
        )
        edit(draft.copy(edits = draft.edits.filterNot { it.exerciseId == exerciseId } + record))
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
        val input = current.input?.snapshot()
        if (!skip && input == null) return
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
                    _state.update { it.copy(failure = reason, loading = false) }
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
                existingRecords = emptyList()
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
