package com.hydrafit.app.core.domain.engine

import com.hydrafit.app.core.domain.equipment.LOADING_SCALE
import com.hydrafit.app.core.domain.equipment.LoadingInventory
import com.hydrafit.app.core.domain.equipment.calculateDumbbellLoading
import com.hydrafit.app.core.domain.equipment.calculatePlateLoading
import com.hydrafit.app.core.domain.workout.LoadKind
import kotlin.math.round

/** What an approved policy proposes for a slot's next prescription. */
enum class SlotProgressionAction {
    /** Stay inside the rep range: one more repetition at the same load. */
    ADVANCE_REPS,

    /** The range's top was reached: take the chosen load step and return to the range's floor. */
    ADVANCE_LOAD,

    /** No change is proposed; [SlotProgressionSuggestion.reason] explains why. */
    HOLD,

    /** Repeated comparable misses: the user reviews the slot instead of an automatic reduction. */
    REVIEW
}

/** Why a suggestion holds, advances or asks for review. Recorded so the rule, not a guess, explains. */
enum class SlotProgressionReason {
    SUCCESS_STREAK,
    MISS_STREAK,
    INSUFFICIENT_COMPARABLE_WORK,
    NOT_ELIGIBLE,
    PAUSED_DELOAD,
    LOAD_CEILING_REACHED,
    NO_REACHABLE_INCREMENT,
    EQUIPMENT_UNRESOLVED
}

/** One slot's evaluated suggestion. A hold/review carries no target change. */
data class SlotProgressionSuggestion(
    val action: SlotProgressionAction,
    val reason: SlotProgressionReason,
    val currentReps: Int?,
    val currentLoadKg: Double?,
    val suggestedReps: Int?,
    val suggestedLoadKg: Double?,
    val comparableSuccesses: Int,
    val comparableMisses: Int
) {
    /** True only when the suggestion would move the slot's prescription. */
    val changesTarget: Boolean
        get() = suggestedReps != null &&
            suggestedLoadKg != null &&
            (suggestedReps != currentReps || suggestedLoadKg != currentLoadKg)
}

/** One slot's current prescription, its approved policy and the context that bounds a step. */
data class SlotProgressionRequest(
    val exerciseId: String,
    val policy: SlotProgressionPolicy,
    val sets: Int,
    val reps: Int?,
    val loadKind: LoadKind,
    val loadKg: Double?,
    /** The exercise's heaviest reachable load, or null when this caller has no ceiling context. */
    val equipmentCeilingKg: Double? = null,
    /** The active profile's loading inventory, used only when the policy names an equipment id. */
    val loadingInventory: LoadingInventory? = null,
    /** A deload week pauses advances; recorded deload targets are never eligible history. */
    val isDeload: Boolean = false
)

/**
 * Streak thresholds for one slot. The defaults mirror the existing load-progression rule and remain
 * product defaults, not physiological findings.
 */
data class SlotProgressionConfig(
    val successStreak: Int = DEFAULT_SUCCESS_STREAK,
    val failureStreak: Int = DEFAULT_FAILURE_STREAK
) {
    init {
        require(successStreak >= 1) { "successStreak must be at least 1" }
        require(failureStreak >= 1) { "failureStreak must be at least 1" }
    }

    companion object {
        const val DEFAULT_SUCCESS_STREAK: Int = 3
        const val DEFAULT_FAILURE_STREAK: Int = 3
    }
}

/**
 * Evaluates one slot's approved policy against its own recorded, comparable history.
 *
 * Comparability is the slot's current prescription: an exposure counts only when its recorded target
 * names the same exercise, set count, rep count and load, is a typed external rep target, and is not a
 * deload. Any other exposure — a changed prescription, a legacy row with no target, a bodyweight or
 * timed target, or an unjudgeable session — breaks the run without counting as a miss, so missing
 * evidence never becomes demonstrated failure.
 *
 * A run of comparable successes advances the prescription; a run of comparable misses asks for review
 * and never removes load automatically. The load step is the user's own number, snapped to the named
 * equipment's real grid when the policy provides one and the inventory is in kilograms.
 */
class ResolveSlotProgressionUseCase(
    private val config: SlotProgressionConfig = SlotProgressionConfig()
) {

    operator fun invoke(
        request: SlotProgressionRequest,
        history: ProgressionHistory,
        nowMillis: Long
    ): SlotProgressionSuggestion {
        val current = request.currentPrescriptionOrNull()
            ?: return hold(request, SlotProgressionReason.NOT_ELIGIBLE)
        if (request.isDeload) return hold(request, SlotProgressionReason.PAUSED_DELOAD)

        val run = trailingRun(request, current, history, nowMillis)
        return when {
            run.successes >= config.successStreak -> advance(request, current, run)
            run.misses >= config.failureStreak -> hold(request, SlotProgressionReason.MISS_STREAK)
                .copy(
                    action = SlotProgressionAction.REVIEW,
                    comparableSuccesses = run.successes,
                    comparableMisses = run.misses
                )

            else -> hold(request, SlotProgressionReason.INSUFFICIENT_COMPARABLE_WORK).copy(
                comparableSuccesses = run.successes,
                comparableMisses = run.misses
            )
        }
    }

    /** The trailing comparable run, reset by any non-comparable or unjudgeable exposure. */
    private fun trailingRun(
        request: SlotProgressionRequest,
        current: CurrentPrescription,
        history: ProgressionHistory,
        nowMillis: Long
    ): Run {
        var successes = 0
        var misses = 0
        for (exposure in history.exposures(request.exerciseId, nowMillis)) {
            if (!exposure.isComparableWith(current, request)) {
                successes = 0
                misses = 0
                continue
            }
            when (exposure.outcome) {
                ProgressionExposureOutcome.SUCCESS -> {
                    successes++
                    misses = 0
                }

                ProgressionExposureOutcome.MISS -> {
                    misses++
                    successes = 0
                }

                ProgressionExposureOutcome.UNKNOWN -> {
                    successes = 0
                    misses = 0
                }
            }
        }
        return Run(successes, misses)
    }

    /** Inside the range: one more repetition. At its top: the load step, back to the range floor. */
    private fun advance(
        request: SlotProgressionRequest,
        current: CurrentPrescription,
        run: Run
    ): SlotProgressionSuggestion {
        if (current.reps < request.policy.maxReps) {
            return suggestion(
                request = request,
                action = SlotProgressionAction.ADVANCE_REPS,
                reason = SlotProgressionReason.SUCCESS_STREAK,
                current = current,
                reps = current.reps + 1,
                loadKg = current.loadKg,
                run = run
            )
        }
        val next = nextLoad(request, current.loadKg)
        return when (next.outcome) {
            LoadStepOutcome.REACHABLE -> suggestion(
                request = request,
                action = SlotProgressionAction.ADVANCE_LOAD,
                reason = SlotProgressionReason.SUCCESS_STREAK,
                current = current,
                reps = request.policy.minReps,
                loadKg = requireNotNull(next.loadKg),
                run = run
            )

            LoadStepOutcome.CEILING_BLOCKED ->
                hold(request, SlotProgressionReason.LOAD_CEILING_REACHED).copy(run = run)

            LoadStepOutcome.EQUIPMENT_UNRESOLVED ->
                hold(request, SlotProgressionReason.EQUIPMENT_UNRESOLVED).copy(run = run)

            LoadStepOutcome.NO_REACHABLE_VALUE ->
                hold(request, SlotProgressionReason.NO_REACHABLE_INCREMENT).copy(run = run)
        }
    }

    /**
     * The next loadable weight for the policy's step. A named equipment identity is resolved against
     * the active inventory's own grid; without one the user's numeric step applies directly. Either way
     * a known ceiling blocks an increase the equipment cannot provide.
     */
    private fun nextLoad(request: SlotProgressionRequest, currentLoadKg: Double): LoadStep {
        val target = roundToGram(currentLoadKg + request.policy.incrementKg)
        val equipmentId = request.policy.equipmentId
        val resolved = if (equipmentId == null) {
            LoadStep(LoadStepOutcome.REACHABLE, target)
        } else {
            resolveOnEquipmentGrid(request, equipmentId, target)
        }
        if (resolved.outcome != LoadStepOutcome.REACHABLE) return resolved
        val next = requireNotNull(resolved.loadKg)
        return when {
            next <= currentLoadKg -> LoadStep(LoadStepOutcome.NO_REACHABLE_VALUE, null)
            request.equipmentCeilingKg != null && next > request.equipmentCeilingKg ->
                LoadStep(LoadStepOutcome.CEILING_BLOCKED, null)

            else -> LoadStep(LoadStepOutcome.REACHABLE, next)
        }
    }

    /**
     * Snaps [targetKg] onto the named bar's or dumbbell's real grid: the exact weight when it is
     * loadable, otherwise the nearest loadable weight above it. An identity absent from the inventory,
     * or an inventory whose native unit is not kilograms, is [LoadStepOutcome.EQUIPMENT_UNRESOLVED]
     * because a kilogram target cannot be compared against that grid. A grid that simply offers no
     * value at or above the target is [LoadStepOutcome.NO_REACHABLE_VALUE] instead, so the recorded
     * reason names the real cause.
     */
    private fun resolveOnEquipmentGrid(
        request: SlotProgressionRequest,
        equipmentId: String,
        targetKg: Double
    ): LoadStep {
        val inventory = request.loadingInventory
            ?: return LoadStep(LoadStepOutcome.EQUIPMENT_UNRESOLVED, null)
        if (inventory.unit != KILOGRAM_UNIT) {
            return LoadStep(LoadStepOutcome.EQUIPMENT_UNRESOLVED, null)
        }
        val isBar = inventory.bars.any { it.id == equipmentId }
        val isDumbbell = inventory.dumbbells.any { it.id == equipmentId }
        if (!isBar && !isDumbbell) return LoadStep(LoadStepOutcome.EQUIPMENT_UNRESOLVED, null)
        val targetTicks = round(targetKg * LOADING_SCALE).toInt()
        if (targetTicks !in 0..MAX_TICKS) {
            return LoadStep(LoadStepOutcome.NO_REACHABLE_VALUE, null)
        }
        val ticks = if (isBar) {
            calculatePlateLoading(inventory, equipmentId, targetTicks)
                .let { it.exact?.totalTicks ?: it.upper?.totalTicks }
        } else {
            calculateDumbbellLoading(inventory, equipmentId, targetTicks)
                .let { it.exact?.perDumbbellTicks ?: it.upper?.perDumbbellTicks }
        }
        return ticks
            ?.let {
                LoadStep(LoadStepOutcome.REACHABLE, roundToGram(it.toDouble() / LOADING_SCALE))
            }
            ?: LoadStep(LoadStepOutcome.NO_REACHABLE_VALUE, null)
    }

    private fun suggestion(
        request: SlotProgressionRequest,
        action: SlotProgressionAction,
        reason: SlotProgressionReason,
        current: CurrentPrescription,
        reps: Int,
        loadKg: Double,
        run: Run
    ): SlotProgressionSuggestion = SlotProgressionSuggestion(
        action = action,
        reason = reason,
        currentReps = current.reps,
        currentLoadKg = current.loadKg,
        suggestedReps = reps,
        suggestedLoadKg = loadKg,
        comparableSuccesses = run.successes,
        comparableMisses = run.misses
    )

    private fun hold(
        request: SlotProgressionRequest,
        reason: SlotProgressionReason
    ): SlotProgressionSuggestion = SlotProgressionSuggestion(
        action = SlotProgressionAction.HOLD,
        reason = reason,
        currentReps = request.reps,
        currentLoadKg = request.loadKg,
        suggestedReps = null,
        suggestedLoadKg = null,
        comparableSuccesses = 0,
        comparableMisses = 0
    )

    /** Restates the observed run on a hold/review suggestion, so the reason carries its evidence. */
    private fun SlotProgressionSuggestion.copy(run: Run): SlotProgressionSuggestion =
        copy(comparableSuccesses = run.successes, comparableMisses = run.misses)

    /**
     * The slot's judgeable current prescription, or null when the policy cannot apply: a timed slot, a
     * non-external load kind, or a blank/explicit-zero load that cannot establish weighted success.
     */
    private fun SlotProgressionRequest.currentPrescriptionOrNull(): CurrentPrescription? {
        if (reps == null || sets < 1) return null
        if (loadKind != LoadKind.EXTERNAL) return null
        val load = loadKg ?: return null
        if (!load.isFinite() || load <= 0.0) return null
        return CurrentPrescription(reps = reps, loadKg = roundToGram(load))
    }

    private fun ProgressionExposure.isComparableWith(
        current: CurrentPrescription,
        request: SlotProgressionRequest
    ): Boolean {
        val recorded = target ?: return false
        return recorded.exerciseId == request.exerciseId &&
            recorded.sets == request.sets &&
            recorded.reps == current.reps &&
            recorded.durationSeconds == null &&
            recorded.loadKind == LoadKind.EXTERNAL &&
            !recorded.isDeload &&
            recorded.weightKg != null &&
            // A different approved rule is a different prescription context: work earned under
            // another policy is never stitched into this one's streak.
            recorded.progressionPolicy == request.policy &&
            roundToGram(requireNotNull(recorded.weightKg)) == current.loadKg
    }

    private data class CurrentPrescription(val reps: Int, val loadKg: Double)

    private data class Run(val successes: Int, val misses: Int)

    private data class LoadStep(val outcome: LoadStepOutcome, val loadKg: Double?)

    private enum class LoadStepOutcome {
        REACHABLE,
        CEILING_BLOCKED,
        NO_REACHABLE_VALUE,
        EQUIPMENT_UNRESOLVED
    }

    private companion object {
        const val KILOGRAM_UNIT = "KG"
        const val MAX_TICKS = 2_000 * LOADING_SCALE

        /** Keeps decimal steps free of binary-float noise without changing the user's number. */
        fun roundToGram(value: Double): Double = round(value * 1_000.0) / 1_000.0
    }
}
