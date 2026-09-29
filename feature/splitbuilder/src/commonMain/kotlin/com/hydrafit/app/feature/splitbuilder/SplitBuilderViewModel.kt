package com.hydrafit.app.feature.splitbuilder

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hydrafit.app.core.domain.engine.AcceptWeeklyPlanUseCase
import com.hydrafit.app.core.domain.engine.AcceptedPlan
import com.hydrafit.app.core.domain.engine.ExerciseCatalog
import com.hydrafit.app.core.domain.engine.GenerateWeeklySplitUseCase
import com.hydrafit.app.core.domain.engine.ObserveWorkoutPlanInputsUseCase
import com.hydrafit.app.core.domain.engine.PlanGenerationException
import com.hydrafit.app.core.domain.engine.PlanHistoryRepository
import com.hydrafit.app.core.domain.engine.WorkoutPlanInputs
import com.hydrafit.app.core.domain.engine.toWeeklyPlan
import com.hydrafit.app.core.userdata.settings.EnginePreferenceRepository
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SplitBuilderViewModel(
    private val observeWorkoutPlanInputs: ObserveWorkoutPlanInputsUseCase,
    private val generateWeeklySplit: GenerateWeeklySplitUseCase,
    private val acceptWeeklyPlan: AcceptWeeklyPlanUseCase,
    private val planHistory: PlanHistoryRepository,
    private val exerciseCatalog: ExerciseCatalog,
    private val enginePreference: EnginePreferenceRepository
) : ViewModel() {

    private val _state = MutableStateFlow(SplitBuilderUiState())
    val state: StateFlow<SplitBuilderUiState> = _state.asStateFlow()

    private val setsPerExercise = MutableStateFlow<Int?>(null)
    private val refreshRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    init {
        viewModelScope.launch {
            planHistory.observeHistory().collectLatest { history ->
                _state.update { it.copy(history = history) }
            }
        }
        viewModelScope.launch {
            val inputs = observeWorkoutPlanInputs(setsPerExercise, refreshRequests)
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

    fun refresh() {
        refreshRequests.tryEmit(Unit)
    }

    fun onAcceptPlan() {
        val plan = _state.value.plan ?: return
        viewModelScope.launch {
            acceptWeeklyPlan(plan)
            _state.update { it.copy(isPlanAccepted = true) }
        }
    }

    fun onViewAcceptedPlan(accepted: AcceptedPlan) {
        viewModelScope.launch { showAccepted(accepted) }
    }

    private suspend fun showAccepted(accepted: AcceptedPlan) {
        val snapshotNames = accepted.days
            .flatMap { day -> day.exercises }
            .associate { it.exerciseId to it.name }
        val catalogNames = exerciseCatalog.all().associate { it.id to it.name }
        _state.update {
            it.copy(
                plan = accepted.toWeeklyPlan(),
                exerciseNames = snapshotNames + catalogNames,
                isLoading = false,
                isPlanAccepted = true,
                hasError = false,
                isTransientError = false,
                errorDetail = null,
                requestedEngine = accepted.engine,
                daysPerWeek = accepted.days.size,
                setsPerExercise = accepted.days.firstOrNull()?.exercises?.firstOrNull()?.sets
                    ?: it.setsPerExercise
            )
        }
    }

    private suspend fun generate(inputs: WorkoutPlanInputs) {
        val request = inputs.request
        _state.update {
            it.copy(
                plan = null,
                isLoading = true,
                isPlanAccepted = false,
                hasError = false,
                isTransientError = false,
                errorDetail = null,
                daysPerWeek = request.daysPerWeek,
                setsPerExercise = request.setsPerExercise,
                requestedEngine = inputs.requestedEngine
            )
        }
        try {
            val plan = generateWeeklySplit(request)
            val names = exerciseCatalog.all().associate { it.id to it.name }
            _state.update {
                it.copy(plan = plan, exerciseNames = names, isLoading = false)
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
                    errorDetail = failure.message
                )
            }
        } catch (failure: Exception) {
            _state.update {
                it.copy(
                    plan = null,
                    isLoading = false,
                    hasError = true,
                    isTransientError = false,
                    errorDetail = failure.message
                )
            }
        }
    }
}
