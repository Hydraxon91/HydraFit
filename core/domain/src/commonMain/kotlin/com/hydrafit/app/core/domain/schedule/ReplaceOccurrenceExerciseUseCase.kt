package com.hydrafit.app.core.domain.schedule

import com.hydrafit.app.core.domain.engine.DeterministicWorkoutPlannerEngine
import com.hydrafit.app.core.domain.engine.EquipmentWeightLimit
import com.hydrafit.app.core.domain.engine.ExerciseCatalog
import com.hydrafit.app.core.domain.engine.PeriodizationConfig
import com.hydrafit.app.core.domain.engine.PlanRequest
import com.hydrafit.app.core.domain.engine.PlannerCandidateEligibility
import com.hydrafit.app.core.domain.engine.SuggestedWeightConfig
import com.hydrafit.app.core.domain.equipment.Exercise
import com.hydrafit.app.core.domain.workout.WorkoutLoadPolicy
import com.hydrafit.app.core.domain.workout.WorkoutLogRepository
import com.hydrafit.app.core.domain.workout.WorkoutSet

enum class OccurrenceReplacementFailure { RECORDED_SLOT, STALE_CONTEXT, UNAVAILABLE }

/**
 * True when [set] is recorded work that freezes an occurrence slot.
 *
 * A slot is frozen by any set attributed to that entry - working or warm-up - so a swap can never
 * discard or reinterpret logged work. The entry id is the authoritative attribution: a set that
 * names the entry freezes it even when its derived occurrence id is missing or disagrees, so an
 * entry-linked row is never mistaken for an unrelated one. A set the occurrence recorded without a
 * resolved entry also freezes the slot, but only when its exercise matches: that is the same
 * prescribed exercise whose link was lost, so the swap would orphan it. An unplanned extra exercise
 * is deliberately not attributed to this slot, and freezing it would block every replacement in the
 * occurrence.
 */
fun WorkoutSet.freezesOccurrenceSlot(
    occurrenceId: Long,
    entryId: Long,
    entryExerciseId: String
): Boolean {
    if (occurrenceEntryId != null) return occurrenceEntryId == entryId
    if (this.occurrenceId != occurrenceId) return false
    return exerciseId == entryExerciseId
}

class OccurrenceReplacementException(val reason: OccurrenceReplacementFailure) :
    IllegalStateException(reason.name)

data class OccurrenceReplacementChoices(
    val occurrenceId: Long,
    val revision: Int,
    val original: OccurrenceEntry,
    val alternatives: List<OccurrenceEntry>
)

/** Manual, occurrence-only replacement with the candidate's own load baseline, never the old load. */
class ReplaceOccurrenceExerciseUseCase(
    private val schedule: WorkoutScheduleRepository,
    private val log: WorkoutLogRepository,
    private val catalog: ExerciseCatalog,
    private val replacements: OccurrenceReplacementRepository,
    private val ranking: DeterministicWorkoutPlannerEngine,
    private val weights: SuggestedWeightConfig,
    private val periodization: PeriodizationConfig
) {
    suspend fun candidates(
        occurrenceId: Long,
        entryId: Long,
        request: PlanRequest
    ): OccurrenceReplacementChoices {
        val occurrence = schedule.getOccurrence(occurrenceId)
            ?: throw OccurrenceReplacementException(OccurrenceReplacementFailure.STALE_CONTEXT)
        val state = schedule.scheduleState()
        if (occurrence.isResolved ||
            state.activeActivationId != occurrence.activationId ||
            (state.selectedOccurrenceId != null && state.selectedOccurrenceId != occurrenceId)
        ) {
            throw OccurrenceReplacementException(OccurrenceReplacementFailure.STALE_CONTEXT)
        }
        val original = occurrence.entries.firstOrNull { it.id == entryId }
            ?: throw OccurrenceReplacementException(OccurrenceReplacementFailure.STALE_CONTEXT)
        val recorded = log.setsForOccurrence(occurrenceId)
        if (recorded.any { it.freezesOccurrenceSlot(occurrenceId, entryId, original.exerciseId) }) {
            throw OccurrenceReplacementException(OccurrenceReplacementFailure.RECORDED_SLOT)
        }
        val otherIds = occurrence.entries.filter { it.id != entryId }.map { it.exerciseId }.toSet()
        val week = schedule.getActivation(occurrence.activationId)?.weekNumber
        val deload = week?.let { periodization.isDeload(it) } == true
        val alternatives = PlannerCandidateEligibility.candidates(catalog.all(), request)
            .filter {
                it.id != original.exerciseId &&
                    it.id !in otherIds &&
                    it.movementPattern == original.movementPattern &&
                    ranking.pickFirstNonSore(listOf(it), request.muscleFatigue) != null
            }
            .sortedWith(compareBy<Exercise> { it.name }.thenBy { it.id })
            .map { candidate ->
                val baseline = request.suggestedWeightsKg[candidate.id]
                val weight = if (WorkoutLoadPolicy.allowsAutomaticLoad(candidate.loadCapability) &&
                    baseline != null &&
                    candidate.id !in request.withheldWeightExerciseIds
                ) {
                    EquipmentWeightLimit.clamp(
                        weights.workingWeightFor(
                            baseline,
                            original.reps,
                            if (deload) periodization.deloadIntensityScale else 1.0
                        ),
                        EquipmentWeightLimit.ceilingFor(candidate, request.equipmentMaxWeights)
                    )
                } else {
                    null
                }
                original.copy(
                    exerciseId = candidate.id,
                    exerciseName = candidate.name,
                    movementPattern = candidate.movementPattern,
                    requiredEquipment = candidate.requiredEquipment,
                    involvements = candidate.effectiveInvolvements,
                    isUnilateral = candidate.isUnilateral,
                    weightKg = weight,
                    loadCapability = candidate.loadCapability,
                    loadKind = WorkoutLoadPolicy.defaultKind(candidate.loadCapability)
                )
            }
        return OccurrenceReplacementChoices(
            occurrenceId,
            occurrence.revision,
            original,
            alternatives
        )
    }

    suspend operator fun invoke(
        choices: OccurrenceReplacementChoices,
        selectedExerciseId: String,
        request: PlanRequest
    ) {
        val fresh = candidates(choices.occurrenceId, choices.original.id, request)
        if (fresh.revision != choices.revision || fresh.original != choices.original) {
            throw OccurrenceReplacementException(OccurrenceReplacementFailure.STALE_CONTEXT)
        }
        val replacement = fresh.alternatives.firstOrNull { it.exerciseId == selectedExerciseId }
            ?: throw OccurrenceReplacementException(OccurrenceReplacementFailure.UNAVAILABLE)
        val preview = choices.alternatives.firstOrNull { it.exerciseId == selectedExerciseId }
        if (replacement != preview) {
            throw OccurrenceReplacementException(OccurrenceReplacementFailure.STALE_CONTEXT)
        }
        replacements.replaceUntouchedEntry(
            choices.occurrenceId,
            choices.revision,
            replacement,
            request.nowMillis
        )
    }
}
