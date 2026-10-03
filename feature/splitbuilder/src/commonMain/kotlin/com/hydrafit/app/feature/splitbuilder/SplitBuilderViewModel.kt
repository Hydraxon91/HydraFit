package com.hydrafit.app.feature.splitbuilder

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hydrafit.app.core.domain.engine.AcceptWeeklyPlanUseCase
import com.hydrafit.app.core.domain.engine.AcceptedPlan
import com.hydrafit.app.core.domain.engine.ExerciseCatalog
import com.hydrafit.app.core.domain.engine.GenerateWeeklySplitUseCase
import com.hydrafit.app.core.domain.engine.ObserveWorkoutPlanInputsUseCase
import com.hydrafit.app.core.domain.engine.PlanFailureReason
import com.hydrafit.app.core.domain.engine.PlanGenerationException
import com.hydrafit.app.core.domain.engine.PlanHistoryRepository
import com.hydrafit.app.core.domain.engine.PlanRequest
import com.hydrafit.app.core.domain.engine.PlannerEngineId
import com.hydrafit.app.core.domain.engine.WorkoutPlanInputs
import com.hydrafit.app.core.domain.engine.toWeeklyPlan
import com.hydrafit.app.core.domain.equipment.Exercise
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
    private val accessorySetsPerExercise = MutableStateFlow<Int?>(null)
    private val refreshRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** Fingerprint of the request that produced the currently shown plan; null until one exists. */
    private var lastGeneratedFingerprint: Int? = null

    init {
        viewModelScope.launch {
            planHistory.observeHistory().collectLatest { history ->
                _state.update { it.copy(history = history) }
            }
        }
        viewModelScope.launch {
            val inputs = observeWorkoutPlanInputs(
                setsPerExercise = setsPerExercise,
                accessorySetsPerExercise = accessorySetsPerExercise,
                refreshRequests = refreshRequests
            )
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
            acceptWeeklyPlan(plan)
            lastGeneratedFingerprint = null
            _state.update { it.copy(isPlanAccepted = true, canRegenerate = true) }
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
                daysPerWeek = request.daysPerWeek,
                setsPerExercise = request.setsPerExercise,
                accessorySetsPerExercise = request.accessorySetsPerExercise,
                requestedEngine = inputs.requestedEngine
            )
        }
        try {
            val plan = generateWeeklySplit(request)
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
                    exercise.isCustom.toString()
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
