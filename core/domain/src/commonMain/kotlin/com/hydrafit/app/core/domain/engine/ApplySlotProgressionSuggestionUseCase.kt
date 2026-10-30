package com.hydrafit.app.core.domain.engine

import com.hydrafit.app.core.domain.schedule.ApplyGuidedWorkoutTargetsUseCase
import com.hydrafit.app.core.domain.schedule.GuidedTargetField
import com.hydrafit.app.core.domain.schedule.GuidedTargetPatch
import com.hydrafit.app.core.domain.schedule.GuidedWorkoutTargetOverride
import com.hydrafit.app.core.domain.schedule.ScheduleException
import com.hydrafit.app.core.domain.schedule.WorkoutScheduleRepository
import com.hydrafit.app.core.domain.workout.WorkoutLogRepository

/**
 * Applies one slot's approved policy suggestion to the next unstarted occurrence.
 *
 * The suggestion is recomputed at apply time, so stale UI state cannot write a target the current
 * history/equipment no longer supports, and only an actual advance is accepted. The write goes
 * through the existing guided working-target transaction: the routine template, activation baseline
 * and recorded history are never rewritten. A slot with any recorded set is refused, because only an
 * unstarted occurrence's remaining working targets may change.
 */
class ApplySlotProgressionSuggestionUseCase(
    private val suggest: SuggestSlotProgressionUseCase,
    private val schedule: WorkoutScheduleRepository,
    private val log: WorkoutLogRepository,
    private val applyGuidedTargets: ApplyGuidedWorkoutTargetsUseCase
) {

    suspend operator fun invoke(occurrenceId: Long, entryId: Long) {
        val decision = suggest(occurrenceId, entryId)
            ?: throw ScheduleException("That slot has no approved progression policy")
        if (!decision.canApply) {
            throw ScheduleException("That slot has no approved progression change to apply")
        }
        val occurrence = schedule.getOccurrence(occurrenceId)
            ?: throw ScheduleException("That workout no longer exists")
        val entry = occurrence.entries.firstOrNull { it.id == entryId }
            ?: throw ScheduleException("That occurrence entry no longer exists")
        val recorded = log.setsForOccurrence(occurrenceId)
            .filter { it.occurrenceEntryId == entryId }
        if (recorded.isNotEmpty()) {
            throw ScheduleException("That slot already has recorded work")
        }
        applyGuidedTargets(
            occurrenceId = occurrenceId,
            entryId = entryId,
            resetRemaining = false,
            patches = patchesFor(decision, effectiveGuidedTargets(entry))
        )
    }

    /**
     * One patch per set position, touching only reps and/or load so the slot's metric, prescribed
     * effort and cluster absence are preserved by the merge.
     */
    private fun patchesFor(
        decision: SlotProgressionDecision,
        effective: List<GuidedWorkoutTargetOverride>
    ): Map<Int, GuidedTargetPatch> {
        val ordered = decision.ordered?.suggested
        val fields = when (decision.action) {
            SlotProgressionAction.ADVANCE_LOAD ->
                setOf(GuidedTargetField.REPS, GuidedTargetField.LOAD)

            else -> setOf(GuidedTargetField.REPS)
        }
        return (0 until effective.size).associateWith { index ->
            val current = effective[index].target
            val suggested = ordered?.targets?.getOrNull(index)
            GuidedTargetPatch(
                target = SetTarget(
                    reps = suggested?.reps ?: decision.suggestedReps ?: current.reps,
                    loadKind = suggested?.loadKind ?: current.loadKind,
                    weightKg = suggested?.weightKg ?: decision.suggestedLoadKg ?: current.weightKg,
                    targetRir = current.targetRir
                ),
                fields = fields
            )
        }
    }
}
