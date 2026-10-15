package com.hydrafit.app.feature.logger

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hydrafit.app.core.domain.equipment.formatNativeTicks
import com.hydrafit.app.core.userdata.equipment.EquipmentProfileRepository
import com.hydrafit.app.core.userdata.equipment.EquipmentProfileState
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Presentation state for the Logger's read-only loading helper.
 *
 * It is backed only by [EquipmentProfileRepository]: it observes one coherent active-profile
 * snapshot, and it holds no write path, no draft and no session state. A calculation runs off the UI
 * thread on [calculationDispatcher]. Every capture — the profile snapshot, the source identity and
 * the target — is compared before a late result may publish, and any change closes the draft rather
 * than reinterpreting it: switching away, switching back and an inventory edit all change the profile
 * context and therefore close the helper. The Logger's input, draft, session and history state are
 * never touched.
 */
class LoggerLoadingCalculatorViewModel(
    private val profiles: EquipmentProfileRepository,
    private val calculationDispatcher: CoroutineDispatcher = Dispatchers.Default
) : ViewModel() {

    private val _state = MutableStateFlow(LoggerLoadingCalculatorState())
    val state: StateFlow<LoggerLoadingCalculatorState> = _state.asStateFlow()

    private var snapshot: EquipmentProfileState? = null

    /** Captured manual context; null while the helper was opened from a prescription. */
    private var manualSource: String? = null

    /** Captured prescription occurrence; null while the helper was opened manually. */
    private var occurrenceSource: Long? = null
    private var requestRevision: Long = 0L
    private var running: Job? = null

    init {
        viewModelScope.launch {
            profiles.observe().collect(::onSnapshot)
        }
    }

    /**
     * Opens the helper with a blank native target for manual entry.
     *
     * [selectedExerciseId] and [sessionId] are captured, so a later change to the manual context
     * closes the helper instead of keeping a target that belonged to the previous context. A tap
     * before the first profile snapshot arrives opens nothing, because no inventory is known yet.
     */
    fun openManual(selectedExerciseId: String?, sessionId: String?) {
        val current = snapshot ?: return
        manualSource = manualKey(selectedExerciseId, sessionId)
        occurrenceSource = null
        open(seed = null, snapshot = current)
    }

    /**
     * Opens the helper seeded from one prescribed target.
     *
     * Only a numeric external or added load may seed it; a bodyweight-only, unrecorded or legacy
     * load request closes nothing and is never treated as an assumed total.
     */
    fun openTarget(request: LoggerLoadingCalculatorRequest) {
        val loadKind = request.loadKind ?: return
        if (!loggerLoadingTargetEligible(loadKind, request.weightKg)) return
        val current = snapshot ?: return
        val unit = loggerLoadingInventoryUnit(current.active.loadingInventory.unit)
        val target = loggerLoadingTargetFor(request, unit) ?: return
        manualSource = null
        occurrenceSource = request.occurrenceId
        open(seed = target, snapshot = current)
    }

    private fun open(seed: LoggerLoadingCalculatorTarget?, snapshot: EquipmentProfileState) {
        invalidate()
        val projected = project(LoggerLoadingCalculatorState(open = true, seed = seed), snapshot)
        _state.value = projected.copy(
            targetInput = seed?.let { formatNativeTicks(it.targetTicks) } ?: ""
        )
    }

    /** Chooses the saved configuration kind; the selection is not carried across kinds. */
    fun onKindChanged(kind: LoggerLoadingCalculatorKind) {
        val current = _state.value
        if (!current.open || current.kind == kind) return
        invalidate()
        _state.value = project(
            current.copy(
                kind = kind,
                selectedId = null,
                isRunning = false,
                failure = null,
                result = null
            ),
            snapshot
        )
    }

    /** Chooses one saved bar or dumbbell explicitly; an unknown id is refused. */
    fun onEquipmentSelected(id: String) {
        val current = _state.value
        if (!current.open) return
        if (current.options.none { it.id == id }) return
        if (current.selectedId == id) return
        invalidate()
        _state.update { it.copy(selectedId = id, isRunning = false, failure = null, result = null) }
    }

    /**
     * Records a typed native target. The previous result is dropped with any in-flight calculation,
     * because it described the older target; the seed's disclosure still describes the prescribed
     * target the helper opened from.
     */
    fun onTargetChanged(text: String) {
        val current = _state.value
        if (!current.open) return
        invalidate()
        _state.update {
            it.copy(targetInput = text, isRunning = false, failure = null, result = null)
        }
    }

    /** Drops a result and any in-flight calculation that no longer describes the current draft. */
    private fun invalidate() {
        requestRevision++
        running?.cancel()
        running = null
    }

    /**
     * Resolves the typed target against the chosen saved configuration.
     *
     * A running calculation blocks a second submit, and an invalid, over-precise or out-of-range
     * target is reported without clamping. The solver runs on [calculationDispatcher], and only the
     * request that is still current when it returns may publish its result.
     */
    fun onRun() {
        val current = _state.value
        if (!current.open || current.isRunning) return
        val equipmentId = current.selectedId ?: return
        val activeSnapshot = snapshot ?: return
        val inventory = activeSnapshot.active.loadingInventory
        val inventoryOptions = loggerLoadingCalculatorOptions(inventory, current.kind)
        if (inventoryOptions.none { it.id == equipmentId }) return
        val parsed = parseLoggerLoadingTarget(current.targetInput)
        if (parsed is LoggerLoadingTargetParse.Invalid) {
            _state.update { it.copy(failure = parsed.failure, result = null) }
            return
        }
        val ticks = (parsed as LoggerLoadingTargetParse.Valid).ticks
        val kind = current.kind
        val revision = ++requestRevision
        _state.update { it.copy(isRunning = true, failure = null, result = null) }
        running = viewModelScope.launch {
            val view = withContext(calculationDispatcher) {
                runLoggerLoadingCalculation(inventory, kind, equipmentId, ticks)
            }
            if (revision != requestRevision) return@launch
            running = null
            _state.update { latest ->
                latest.copy(
                    isRunning = false,
                    result = view,
                    failure = if (view == null) LoggerLoadingCalculatorFailure.FAILED else null
                )
            }
        }
    }

    /** Closes the helper and drops its draft, selection and any in-flight result. */
    fun dismiss() {
        invalidate()
        manualSource = null
        occurrenceSource = null
        _state.value = LoggerLoadingCalculatorState()
    }

    /**
     * Closes an open helper whose own Logger context moved: the selected exercise or session for a
     * manual target, the active occurrence or the draft/guided source identity for a prescription.
     */
    fun onSourceContextChanged(context: LoggerLoadingSourceContext) {
        val current = _state.value
        if (!current.open) return
        val manual = manualSource
        if (manual != null) {
            if (manual != manualKey(context.selectedExerciseId, context.sessionId)) dismiss()
            return
        }
        val sourceId = current.seed?.sourceId
        if (occurrenceSource != context.occurrenceId ||
            (sourceId != null && sourceId !in context.availableSourceIds)
        ) {
            dismiss()
        }
    }

    /**
     * Keeps the open helper on one coherent profile snapshot. The first snapshot fills the projection
     * of a helper that opened before the observation started; any later context change — switching
     * away, switching back or an inventory edit — closes it instead of reinterpreting the target.
     */
    private fun onSnapshot(next: EquipmentProfileState) {
        val previous = snapshot
        snapshot = next
        val current = _state.value
        if (!current.open) return
        if (previous == null) {
            _state.value = project(current, next)
            return
        }
        if (previous.context != next.context) dismiss()
    }

    private fun project(
        current: LoggerLoadingCalculatorState,
        snapshot: EquipmentProfileState?
    ): LoggerLoadingCalculatorState {
        val active = snapshot?.active ?: return current
        val inventory = active.loadingInventory
        val options = loggerLoadingCalculatorOptions(inventory, current.kind)
        val selected = current.selectedId?.takeIf { id -> options.any { it.id == id } }
        return current.copy(
            profileName = active.name,
            inventoryUnit = loggerLoadingInventoryUnit(inventory.unit),
            options = options,
            selectedId = selected,
            failure = if (options.isEmpty()) {
                LoggerLoadingCalculatorFailure.NO_EQUIPMENT
            } else {
                current.failure
            }
        )
    }

    private fun manualKey(selectedExerciseId: String?, sessionId: String?): String =
        "$selectedExerciseId|$sessionId"
}
