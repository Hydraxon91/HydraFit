package com.hydrafit.app.feature.splitbuilder

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hydrafit.app.core.domain.engine.ExerciseCatalog
import com.hydrafit.app.core.domain.engine.GenerateWeeklySplitUseCase
import com.hydrafit.app.core.domain.engine.ObserveWorkoutPlanInputsUseCase
import com.hydrafit.app.core.domain.engine.PlanGenerationException
import com.hydrafit.app.core.domain.engine.WorkoutPlanInputs
import com.hydrafit.app.core.userdata.settings.EnginePreferenceRepository
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SplitBuilderViewModel(
    private val observeWorkoutPlanInputs: ObserveWorkoutPlanInputsUseCase,
    private val generateWeeklySplit: GenerateWeeklySplitUseCase,
    private val exerciseCatalog: ExerciseCatalog,
    private val enginePreference: EnginePreferenceRepository
) : ViewModel() {

    private val _state = MutableStateFlow(SplitBuilderUiState())
    val state: StateFlow<SplitBuilderUiState> = _state.asStateFlow()

    private val setsPerExercise = MutableStateFlow(SplitBuilderUiState().setsPerExercise)
    private val refreshRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    init {
        viewModelScope.launch {
            observeWorkoutPlanInputs(setsPerExercise, refreshRequests)
                .collectLatest { inputs -> generate(inputs) }
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

    private suspend fun generate(inputs: WorkoutPlanInputs) {
        val request = inputs.request
        _state.update {
            it.copy(
                isLoading = true,
                hasError = false,
                isTransientError = false,
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
                    isTransientError = failure.transient
                )
            }
        } catch (_: Exception) {
            _state.update {
                it.copy(plan = null, isLoading = false, hasError = true, isTransientError = false)
            }
        }
    }
}
