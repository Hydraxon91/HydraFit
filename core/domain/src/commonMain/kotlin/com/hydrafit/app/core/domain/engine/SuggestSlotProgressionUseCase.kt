package com.hydrafit.app.core.domain.engine

import com.hydrafit.app.core.domain.equipment.EquipmentTag
import com.hydrafit.app.core.domain.equipment.LoadingInventory
import com.hydrafit.app.core.domain.schedule.GuidedWorkoutTargetOverride
import com.hydrafit.app.core.domain.schedule.OccurrenceEntry
import com.hydrafit.app.core.domain.schedule.WorkoutScheduleRepository
import com.hydrafit.app.core.domain.time.TimeProvider
import com.hydrafit.app.core.domain.workout.LoadKind

/**
 * One slot's read-only progression decision, including whether the caller may apply it.
 *
 * [canApply] is true only for an actual advance of the slot's working target. A hold, review or an
 * advance with no representable change never authorizes a write.
 */
data class SlotProgressionDecision(
    val action: SlotProgressionAction,
    val reason: SlotProgressionReason,
    val currentReps: Int? = null,
    val currentLoadKg: Double? = null,
    val suggestedReps: Int? = null,
    val suggestedLoadKg: Double? = null,
    /** The current ordered prescription when the slot carries one, otherwise null. */
    val currentOrdered: SlotSetTargets? = null,
    val ordered: OrderedSlotProgressionSuggestion? = null,
    val comparableSuccesses: Int = 0,
    val comparableMisses: Int = 0
) {
    val canApply: Boolean
        get() = when {
            action != SlotProgressionAction.ADVANCE_REPS &&
                action != SlotProgressionAction.ADVANCE_LOAD -> false

            // Compare the target values only: the recipe is inert context and an untouched recipe
            // must not make an unchanged prescription look like an applicable advance.
            ordered != null ->
                ordered.suggested != null &&
                    ordered.suggested.targets != currentOrdered?.targets

            else ->
                suggestedReps != null &&
                    suggestedLoadKg != null &&
                    (suggestedReps != currentReps || suggestedLoadKg != currentLoadKg)
        }
}

/**
 * Proposes each slot's next working target from the slot's own approved policy and its own
 * comparable, same-slot history.
 *
 * The policy owns progression for its slot: this use case never reads or stacks the planner's
 * exercise-level replay, and never writes anything. One batched context read per occurrence supplies
 * every entry's filtered history and the equipment bounds, so a caller cannot turn a card list into
 * one history scan per slot. Evidence comes only from the same prescription slot; a slot with no
 * stable identity, a cluster, a non-weighted metric, mixed hand-edited targets, a deload week or an
 * equipment identity absent from the active inventory holds with a recorded reason rather than
 * borrowing another slot's work or a fallback rule.
 */
class SuggestSlotProgressionUseCase(
    private val schedule: WorkoutScheduleRepository,
    private val contexts: SlotProgressionContextRepository,
    private val timeProvider: TimeProvider,
    private val resolver: ResolveSlotProgressionUseCase,
    private val periodization: PeriodizationConfig = PeriodizationConfig()
) {

    /**
     * Decisions for every policy-bearing entry of [occurrenceId], keyed by entry id.
     *
     * The occurrence and active selection are validated once; the context read happens at most once
     * and only when the occurrence is eligible to advance at all. An empty map means no entry carries
     * an approved policy, or the occurrence no longer exists.
     */
    suspend fun decisionsFor(occurrenceId: Long): Map<Long, SlotProgressionDecision> {
        val occurrence = schedule.getOccurrence(occurrenceId) ?: return emptyMap()
        val policyEntries = occurrence.entries.filter { it.progressionPolicy != null }
        if (policyEntries.isEmpty()) return emptyMap()
        val state = schedule.scheduleState()
        if (occurrence.isResolved ||
            state.legacyFallbackEnabled ||
            state.activeActivationId != occurrence.activationId ||
            state.selectedOccurrenceId != occurrenceId
        ) {
            return policyEntries.associate { it.id to hold(SlotProgressionReason.NOT_ELIGIBLE) }
        }
        val isDeload = schedule.getActivation(occurrence.activationId)?.weekNumber
            ?.let(periodization::isDeload) == true
        if (isDeload) {
            return policyEntries.associate { it.id to hold(SlotProgressionReason.PAUSED_DELOAD) }
        }
        val inputs = contexts.inputsFor(occurrenceId)
        return policyEntries.associate { entry -> entry.id to decide(entry, inputs) }
    }

    /**
     * One entry's decision, or null when it carries no approved policy. A thin lookup over
     * [decisionsFor] for the apply path, not a second evidence read.
     */
    suspend operator fun invoke(occurrenceId: Long, entryId: Long): SlotProgressionDecision? =
        decisionsFor(occurrenceId)[entryId]

    private fun decide(
        entry: OccurrenceEntry,
        inputs: SlotProgressionInputs
    ): SlotProgressionDecision {
        val policy = entry.progressionPolicy ?: return hold(SlotProgressionReason.NOT_ELIGIBLE)
        if (entry.cluster != null) return hold(SlotProgressionReason.NOT_ELIGIBLE)
        if (policy.equipmentId != null &&
            inputs.loadingInventory?.containsImplement(policy.equipmentId) != true
        ) {
            return hold(SlotProgressionReason.EQUIPMENT_UNRESOLVED)
        }
        val context = inputs.contexts[entry.id]
            ?: SlotProgressionContext(trackKey = null, history = ProgressionHistory())
        if (context.trackKey == null) {
            return hold(SlotProgressionReason.INSUFFICIENT_COMPARABLE_WORK)
        }
        val effective = effectiveGuidedTargets(entry)
        return if (entry.setTargets != null) {
            ordered(entry, policy, effective, inputs, context)
        } else {
            scalar(entry, policy, effective, inputs, context)
        }
    }

    private fun scalar(
        entry: OccurrenceEntry,
        policy: SlotProgressionPolicy,
        effective: List<GuidedWorkoutTargetOverride>,
        inputs: SlotProgressionInputs,
        context: SlotProgressionContext
    ): SlotProgressionDecision {
        val first = effective.firstOrNull() ?: return hold(SlotProgressionReason.NOT_ELIGIBLE)
        val targets = effective.map { it.target }
        if (targets.any { !it.isUsableWeightedRepTarget() }) {
            return hold(SlotProgressionReason.NOT_ELIGIBLE)
        }
        val reps = requireNotNull(first.target.reps)
        val loadKind = first.target.loadKind
        val loadKg = requireNotNull(first.target.weightKg)
        // A hand-edited, mixed prescription is not one comparable target; hold instead of picking one.
        if (targets.any {
                it.reps != reps || it.loadKind != loadKind || it.weightKg != loadKg
            }
        ) {
            return hold(SlotProgressionReason.NOT_ELIGIBLE)
        }
        val suggestion = resolver(
            SlotProgressionRequest(
                exerciseId = entry.exerciseId,
                policy = policy,
                sets = entry.sets,
                reps = reps,
                loadKind = loadKind,
                loadKg = loadKg,
                equipmentCeilingKg = equipmentCeiling(
                    policy.equipmentId,
                    entry.requiredEquipment,
                    inputs.equipmentMaxWeights
                ),
                loadingInventory = inputs.loadingInventory
            ),
            context.history,
            timeProvider.nowMillis()
        )
        return SlotProgressionDecision(
            action = suggestion.action,
            reason = suggestion.reason,
            currentReps = suggestion.currentReps,
            currentLoadKg = suggestion.currentLoadKg,
            suggestedReps = suggestion.suggestedReps,
            suggestedLoadKg = suggestion.suggestedLoadKg,
            comparableSuccesses = suggestion.comparableSuccesses,
            comparableMisses = suggestion.comparableMisses
        )
    }

    private fun ordered(
        entry: OccurrenceEntry,
        policy: SlotProgressionPolicy,
        effective: List<GuidedWorkoutTargetOverride>,
        inputs: SlotProgressionInputs,
        context: SlotProgressionContext
    ): SlotProgressionDecision {
        val ordered = entry.setTargets ?: return hold(SlotProgressionReason.NOT_ELIGIBLE)
        if (effective.map { it.target }.any { !it.isUsableWeightedRepTarget() }) {
            return hold(SlotProgressionReason.NOT_ELIGIBLE)
        }
        // The effective targets are the current prescription: guided edits where present, otherwise
        // the frozen ordered targets. The recipe is inert context and is carried through unchanged.
        val current = SlotSetTargets.of(effective.map { it.target }, ordered.recipe)
        val suggestion = resolver.resolveOrdered(
            OrderedSlotProgressionRequest(
                exerciseId = entry.exerciseId,
                targets = current,
                policy = policy
            ),
            context.history,
            timeProvider.nowMillis()
        )
        return SlotProgressionDecision(
            action = suggestion.action,
            reason = suggestion.reason,
            currentOrdered = current,
            ordered = suggestion,
            comparableSuccesses = suggestion.comparableSuccesses,
            comparableMisses = suggestion.comparableMisses
        )
    }

    private fun hold(
        reason: SlotProgressionReason,
        currentReps: Int? = null,
        currentLoadKg: Double? = null
    ) = SlotProgressionDecision(
        action = SlotProgressionAction.HOLD,
        reason = reason,
        currentReps = currentReps,
        currentLoadKg = currentLoadKg
    )

    private fun SetTarget.isUsableWeightedRepTarget(): Boolean = reps != null &&
        durationSeconds == null &&
        distanceMeters == null &&
        forceNewtons == null &&
        (loadKind == LoadKind.EXTERNAL || loadKind == LoadKind.ADDED) &&
        weightKg != null &&
        weightKg.isFinite() &&
        weightKg > 0.0

    /**
     * The heaviest load this slot may be prescribed. A named implement and the required-equipment
     * tags share one id namespace, so when both name a ceiling the lower one wins: the named bar can
     * never move the slot past another piece of its own required equipment.
     */
    private fun equipmentCeiling(
        equipmentId: String?,
        requiredEquipment: Set<EquipmentTag>,
        maxWeights: Map<EquipmentTag, Double>
    ): Double? {
        val named = equipmentId?.let { id ->
            maxWeights.entries.firstOrNull { it.key.id == id }?.value
        }
        val fallback = EquipmentWeightLimit.ceilingFor(requiredEquipment, maxWeights)
        return when {
            named != null && fallback != null -> minOf(named, fallback)
            else -> named ?: fallback
        }
    }
}

/** Guided edits where present, otherwise the entry's own frozen targets. */
internal fun effectiveGuidedTargets(entry: OccurrenceEntry): List<GuidedWorkoutTargetOverride> {
    val original = com.hydrafit.app.core.domain.schedule.originalTargets(entry)
    return (0 until entry.sets).map { index ->
        entry.guidedTargets?.targetFor(entry, index) ?: original[index]
    }
}

private fun LoadingInventory.containsImplement(equipmentId: String): Boolean =
    bars.any { it.id == equipmentId } || dumbbells.any { it.id == equipmentId }
