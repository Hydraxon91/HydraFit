package com.hydrafit.app.core.domain.engine

import com.hydrafit.app.core.domain.equipment.ExerciseLoadCapability
import com.hydrafit.app.core.domain.workout.LoadKind
import com.hydrafit.app.core.domain.workout.WorkoutLoadPolicy

/**
 * The candidate exercise's own prescribed load for one rep count, with the provenance the
 * deterministic engine records for a generated target.
 *
 * [weightKg] is null when the candidate has no usable baseline for this rep count, when its
 * capability does not allow an automatic load, or when its evidence is withheld. It is never taken
 * from the slot being replaced.
 */
data class CandidatePrescribedLoad(
    val weightKg: Double?,
    val loadKind: LoadKind,
    val explanation: ExerciseTargetExplanation
)

/**
 * Converts the candidate's own progressed baseline at [reps] exactly the way generation does: the
 * suggested e1RM bound capped by any active recent-weight cap, the deload intensity scale, the
 * automatic-load capability gate and the equipment ceiling.
 *
 * Shared by accepted-plan substitution and occurrence replacement so the two cannot drift. The
 * replaced slot's stored load is not an input here.
 */
fun resolveCandidatePrescribedLoad(
    candidateId: String,
    capability: ExerciseLoadCapability,
    reps: Int?,
    request: PlanRequest,
    equipmentCeilingKg: Double?,
    intensityScale: Double,
    weightConfig: SuggestedWeightConfig
): CandidatePrescribedLoad {
    val allowed = WorkoutLoadPolicy.allowsAutomaticLoad(capability)
    val withheld = candidateId in request.withheldWeightExerciseIds
    val baseline = if (allowed && !withheld) {
        request.suggestedWeightsKg[candidateId]?.let { suggested ->
            request.recentWeightCaps[candidateId]?.let { cap -> minOf(suggested, cap) } ?: suggested
        }
    } else {
        null
    }
    val uncappedWorking = if (reps != null) {
        baseline?.let { weightConfig.workingWeightFor(it, reps, intensityScale) }
    } else {
        null
    }
    val weightKg = uncappedWorking?.let { EquipmentWeightLimit.clamp(it, equipmentCeilingKg) }
    val origin = request.loadExplanations[candidateId]
    val conversion = if (uncappedWorking != null && reps != null) {
        LoadConversionExplanation(
            reps = reps,
            intensityFraction = weightConfig.intensityForReps(reps),
            deloadIntensityScale = intensityScale,
            roundToKg = weightConfig.roundToKg,
            uncappedWorkingKg = uncappedWorking,
            equipmentCeilingKg = equipmentCeilingKg,
            equipmentCeilingBinds = equipmentCeilingKg != null &&
                uncappedWorking > equipmentCeilingKg
        )
    } else {
        null
    }
    val unavailableReason = when {
        !allowed -> LoadUnavailableReason.NOT_LOAD_TRACKED
        reps != null && baseline == null -> LoadUnavailableReason.INSUFFICIENT_RECENT_EVIDENCE
        else -> null
    }
    return CandidatePrescribedLoad(
        weightKg = weightKg,
        loadKind = if (weightKg != null) {
            LoadKind.EXTERNAL
        } else {
            WorkoutLoadPolicy.defaultKind(capability)
        },
        explanation = ExerciseTargetExplanation(
            baseline = origin?.baseline,
            progression = origin?.progression,
            conversion = conversion,
            unavailableReason = unavailableReason
        )
    )
}

/**
 * Recalculates a slot's ordered per-set targets for a replacement candidate.
 *
 * Each set keeps its own measure and prescribed effort; only the numeric external load is looked up
 * again from the candidate's baseline at that set's reps. The manual recipe is dropped because it
 * described the replaced exercise's inputs. When the candidate cannot carry the targets' load kind,
 * the whole ordered prescription is cleared rather than keeping loads that mean something else. The
 * list size always stays equal to the slot's set count.
 */
fun recalculatedOrderedTargets(
    targets: SlotSetTargets?,
    capability: ExerciseLoadCapability,
    candidateId: String,
    request: PlanRequest,
    equipmentCeilingKg: Double?,
    intensityScale: Double,
    weightConfig: SuggestedWeightConfig
): SlotSetTargets? {
    if (targets == null) return null
    if (targets.targets.any { !WorkoutLoadPolicy.permits(capability, it.loadKind) }) return null
    val rebuilt = targets.targets.map { target ->
        val weight = if (target.loadKind == LoadKind.EXTERNAL) {
            resolveCandidatePrescribedLoad(
                candidateId = candidateId,
                capability = capability,
                reps = target.reps,
                request = request,
                equipmentCeilingKg = equipmentCeilingKg,
                intensityScale = intensityScale,
                weightConfig = weightConfig
            ).weightKg
        } else {
            null
        }
        target.copy(weightKg = weight)
    }
    return SlotSetTargets.of(rebuilt)
}
