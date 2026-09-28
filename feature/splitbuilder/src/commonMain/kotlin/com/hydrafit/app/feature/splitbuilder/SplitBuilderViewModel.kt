package com.hydrafit.app.feature.splitbuilder

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hydrafit.app.core.domain.engine.ExerciseCatalog
import com.hydrafit.app.core.domain.engine.GenerateWeeklySplitUseCase
import com.hydrafit.app.core.domain.engine.PlanRequest
import com.hydrafit.app.core.domain.engine.PlannerEngineId
import com.hydrafit.app.core.domain.equipment.EquipmentTag
import com.hydrafit.app.core.domain.fatigue.CalculateMuscleFatigueUseCase
import com.hydrafit.app.core.domain.fatigue.LoggedSet
import com.hydrafit.app.core.domain.time.TimeProvider
import com.hydrafit.app.core.domain.workout.WorkoutLogRepository
import com.hydrafit.app.core.userdata.equipment.EquipmentSelectionRepository
import com.hydrafit.app.core.userdata.settings.EnginePreferenceRepository
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SplitBuilderViewModel(
    private val generateWeeklySplit: GenerateWeeklySplitUseCase,
    private val equipmentSelectionRepository: EquipmentSelectionRepository,
    private val workoutLogRepository: WorkoutLogRepository,
    private val calculateMuscleFatigue: CalculateMuscleFatigueUseCase,
    private val exerciseCatalog: ExerciseCatalog,
    private val timeProvider: TimeProvider,
    private val enginePreference: EnginePreferenceRepository
) : ViewModel() {

    private val _state = MutableStateFlow(SplitBuilderUiState())
    val state: StateFlow<SplitBuilderUiState> = _state.asStateFlow()

    private val setsPerExercise = MutableStateFlow(SplitBuilderUiState().setsPerExercise)
    private var lastInputs: PlanInputs? = null

    init {
        viewModelScope.launch {
            combine(
                equipmentSelectionRepository.selectedFlow(),
                enginePreference.engineFlow(),
                enginePreference.daysPerWeekFlow(),
                workoutLogRepository.loggedSetsFlow(),
                setsPerExercise
            ) { equipment, engine, daysPerWeek, loggedSets, sets ->
                PlanInputs(
                    availableEquipment = equipment,
                    requestedEngine = engine,
                    daysPerWeek = daysPerWeek,
                    loggedSets = loggedSets,
                    setsPerExercise = sets
                )
            }
                .distinctUntilChanged()
                .collectLatest { inputs ->
                    lastInputs = inputs
                    generate(inputs)
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
        val inputs = lastInputs ?: return
        viewModelScope.launch { generate(inputs) }
    }

    private suspend fun generate(inputs: PlanInputs) {
        _state.update {
            it.copy(
                isLoading = true,
                hasError = false,
                daysPerWeek = inputs.daysPerWeek,
                requestedEngine = inputs.requestedEngine
            )
        }
        try {
            val nowMillis = timeProvider.nowMillis()
            val request = PlanRequest(
                daysPerWeek = inputs.daysPerWeek,
                availableEquipment = inputs.availableEquipment,
                muscleFatigue = calculateMuscleFatigue(inputs.loggedSets, nowMillis),
                nowMillis = nowMillis,
                setsPerExercise = inputs.setsPerExercise
            )
            val plan = generateWeeklySplit(request)
            val names = exerciseCatalog.all().associate { it.id to it.name }
            _state.update {
                it.copy(plan = plan, exerciseNames = names, isLoading = false)
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            _state.update { it.copy(plan = null, isLoading = false, hasError = true) }
        }
    }

    private data class PlanInputs(
        val availableEquipment: Set<EquipmentTag>,
        val requestedEngine: PlannerEngineId,
        val daysPerWeek: Int,
        val loggedSets: List<LoggedSet>,
        val setsPerExercise: Int
    )
}
