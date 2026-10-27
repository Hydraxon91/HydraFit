package com.hydrafit.app.feature.logger

import com.hydrafit.app.core.domain.routine.ClusterPrescription
import com.hydrafit.app.core.domain.routine.ClusterSegmentTarget
import com.hydrafit.app.core.domain.unit.WeightUnit
import com.hydrafit.app.core.domain.workout.PerformedClusterSegment
import com.hydrafit.app.core.domain.workout.WorkoutCluster

/** One later cluster segment as the draft editor holds it: the entered text, not a parsed number. */
data class ClusterSegmentInput(val reps: String = "", val weightInput: String = "")

/** One next segment; confirmed earlier segments are persisted, never pending form state. */
data class ClusterLogState(
    val draft: DraftSet,
    val position: Int,
    val weightUnit: WeightUnit,
    val reps: String,
    val weightInput: String,
    val rir: String = "",
    val failed: Boolean = false
) {
    val canSave: Boolean get() = reps.toIntOrNull()?.let { it > 0 } == true &&
        weightInput.toDoubleOrNull()?.let { it.isFinite() && it > 0 } == true &&
        (rir.isBlank() || rir.toIntOrNull()?.let { it in 0..10 } == true)
}

/**
 * Why a cluster draft cannot be logged exactly as entered.
 *
 * Each value has its own message. A refusal writes nothing and leaves the draft and its editor in
 * place, so the reported segment can be corrected and submitted again.
 */
enum class ClusterDraftError {
    /** A later segment's reps or load was not entered. */
    MISSING_SEGMENT,

    /** A later segment's reps or load is present but not a finite positive number. */
    NON_POSITIVE_SEGMENT,

    /** The entered segments contradict the cluster's own load rules. */
    INVALID_CLUSTER
}

/**
 * The editable later segments seeded from their prescribed values.
 *
 * Segment 1 stays the form's existing reps/load/RIR fields, so only positions after it are listed.
 * The load is seeded as exact entry text, so an untouched editor re-reads the same kilograms instead
 * of a rounded display value.
 */
internal fun ClusterPrescription.laterSegmentInputs(
    weightUnit: WeightUnit
): List<ClusterSegmentInput> = segments.drop(1).map { segment ->
    ClusterSegmentInput(
        reps = segment.reps.toString(),
        weightInput = exactEntryText(weightUnit.kilogramsToDisplay(segment.weightKg))
    )
}

/** The performed segments a cluster draft's later positions resolve to, or why they cannot. */
internal sealed interface ClusterSegments {
    data class Ready(val segments: List<PerformedClusterSegment>) : ClusterSegments

    data class Refused(val error: ClusterDraftError) : ClusterSegments
}

/**
 * Resolves the later segments (positions 1..n) of [prescription].
 *
 * Position 0 is always the parent set's own recorded values, so it is not read here. [inputs] is
 * null when the user has not opened the draft editor: the prescription's own segment values are
 * then logged unchanged rather than inventing a number for an untouched draft.
 */
internal fun laterClusterSegments(
    prescription: ClusterPrescription,
    inputs: List<ClusterSegmentInput>?,
    weightUnit: WeightUnit
): ClusterSegments {
    val prescribed = prescription.segments.drop(1)
    if (inputs == null) {
        return ClusterSegments.Ready(
            prescribed.mapIndexed { index, segment ->
                PerformedClusterSegment(
                    position = index + 1,
                    reps = segment.reps,
                    weightKg = segment.weightKg
                )
            }
        )
    }
    if (inputs.size != prescribed.size) {
        return ClusterSegments.Refused(ClusterDraftError.MISSING_SEGMENT)
    }
    val segments = mutableListOf<PerformedClusterSegment>()
    inputs.forEachIndexed { index, input ->
        val repsText = input.reps.trim()
        val weightText = input.weightInput.trim()
        if (repsText.isEmpty() || weightText.isEmpty()) {
            return ClusterSegments.Refused(ClusterDraftError.MISSING_SEGMENT)
        }
        val reps = repsText.toIntOrNull()
        val load = weightText.toDoubleOrNull()?.let(weightUnit::displayToKilograms)
        if (reps == null || reps <= 0 || load == null || !load.isFinite() || load <= 0.0) {
            return ClusterSegments.Refused(ClusterDraftError.NON_POSITIVE_SEGMENT)
        }
        segments += PerformedClusterSegment(position = index + 1, reps = reps, weightKg = load)
    }
    return ClusterSegments.Ready(segments)
}

/**
 * The reason a cluster draft cannot be logged as entered, or null when it can.
 *
 * The parent's own measurement is the opening segment, so the whole performed cluster is checked
 * against the domain rules here: every segment keeps positive reps and a finite positive load, and
 * the entered order still satisfies the cluster kind's load rule (strictly decreasing for a drop
 * set, one repeated load for a rest-pause). [inputs] follows [laterClusterSegments].
 */
internal fun clusterDraftRefusal(
    prescription: ClusterPrescription,
    parentReps: Int?,
    parentWeightKg: Double?,
    parentRir: Int?,
    inputs: List<ClusterSegmentInput>?,
    weightUnit: WeightUnit
): ClusterDraftError? {
    val later = when (val outcome = laterClusterSegments(prescription, inputs, weightUnit)) {
        is ClusterSegments.Refused -> return outcome.error
        is ClusterSegments.Ready -> outcome.segments
    }
    if (parentReps == null || parentWeightKg == null) return ClusterDraftError.INVALID_CLUSTER
    val performed = listOf(
        PerformedClusterSegment(
            position = 0,
            reps = parentReps,
            weightKg = parentWeightKg,
            rir = parentRir
        )
    ) + later
    val valid = runCatching {
        WorkoutCluster(
            kind = prescription.kind,
            plannedSegments = prescription.segments.size,
            segments = performed
        ).requireValid()
        ClusterPrescription(
            kind = prescription.kind,
            segments = performed.map { segment ->
                ClusterSegmentTarget(
                    reps = segment.reps,
                    weightKg = segment.weightKg,
                    rir = segment.rir
                )
            },
            restSeconds = prescription.restSeconds
        ).requireValid()
    }.isSuccess
    return if (valid) null else ClusterDraftError.INVALID_CLUSTER
}
