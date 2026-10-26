package com.hydrafit.app.core.domain.workout

import com.hydrafit.app.core.domain.engine.SetTarget
import com.hydrafit.app.core.domain.routine.ClusterKind
import com.hydrafit.app.core.domain.routine.ClusterPrescription
import com.hydrafit.app.core.domain.routine.ClusterSegmentTarget

/**
 * The cluster identity frozen on a performed parent set, without its segment values.
 *
 * Completion and the consumers that must exclude cluster work read this directly, so deciding
 * whether a cluster satisfies its position never requires joining the segment table. A partial
 * cluster keeps its recorded work but does not satisfy its position.
 */
data class WorkoutClusterSummary(
    val kind: ClusterKind,
    val plannedSegments: Int,
    val performedSegments: Int
) {
    val isComplete: Boolean get() = performedSegments >= plannedSegments

    fun requireValid() {
        require(
            plannedSegments in ClusterPrescription.MIN_SEGMENTS..ClusterPrescription.MAX_SEGMENTS
        ) {
            "A cluster summary needs a valid planned segment count"
        }
        require(performedSegments in 1..plannedSegments) {
            "A performed cluster cannot record more segments than were prescribed"
        }
    }
}

/**
 * One performed segment of a cluster, in the order it was performed.
 *
 * Position 0 is always the parent set's own recorded values; later positions are the additional
 * segments stored beside it. A performed segment never changes the parent set's stored value, so a
 * reader that does not understand clusters still sees one valid ordinary measurement.
 */
data class PerformedClusterSegment(
    val position: Int,
    val reps: Int,
    val weightKg: Double,
    val rir: Int? = null
) {
    fun requireValid() {
        require(position >= 0) { "A cluster segment position cannot be negative" }
        require(reps > 0) { "A cluster segment needs positive reps" }
        require(weightKg.isFinite() && weightKg > 0.0) {
            "A cluster segment needs a finite positive external load"
        }
        require(rir == null || rir in SetTarget.MIN_RIR..SetTarget.MAX_RIR) {
            "A cluster RIR must be between ${SetTarget.MIN_RIR} and ${SetTarget.MAX_RIR}"
        }
    }
}

/**
 * A performed cluster: the ordered work a single logged cluster set actually produced.
 *
 * A cluster occupies one prescribed set position. [segments] always contains the parent's own values
 * at position 0, so [isComplete] compares the performed segment count with the prescription's
 * segment count rather than with any ordinary set counter.
 *
 * A partial cluster keeps the segments that were actually performed and does **not** satisfy its
 * position: the user either resumes it or deliberately finishes the workout partially.
 */
data class WorkoutCluster(
    val kind: ClusterKind,
    val plannedSegments: Int,
    val segments: List<PerformedClusterSegment>
) {
    fun requireValid() {
        require(plannedSegments in MIN_SEGMENTS..MAX_SEGMENTS) {
            "A performed cluster needs a valid planned segment count"
        }
        require(segments.isNotEmpty()) { "A performed cluster needs at least its opening segment" }
        require(segments.size <= plannedSegments) {
            "A performed cluster cannot record more segments than were prescribed"
        }
        segments.forEachIndexed { index, segment ->
            require(segment.position == index) {
                "Performed cluster segments must be contiguous from position 0"
            }
            segment.requireValid()
        }
    }

    /** Segments actually performed, including the parent's opening segment. */
    val performedSegments: Int get() = segments.size

    /** True only when every prescribed segment was performed. */
    val isComplete: Boolean get() = performedSegments >= plannedSegments

    /** Descriptive work volume; never equivalence with that many ordinary sets. */
    val volumeKg: Double get() = segments.sumOf { it.weightKg * it.reps }

    val totalReps: Int get() = segments.sumOf { it.reps }

    /**
     * The remaining segments the prescription still expects, or an empty list when complete. A
     * reader uses this to offer resume rather than inventing the missed work.
     */
    fun remainingSegments(prescription: ClusterPrescription): List<ClusterSegmentTarget> {
        require(prescription.segments.size == plannedSegments) {
            "This cluster was not performed against that prescription"
        }
        return prescription.segments.drop(performedSegments)
    }

    companion object {
        private const val MIN_SEGMENTS: Int = ClusterPrescription.MIN_SEGMENTS
        private const val MAX_SEGMENTS: Int = ClusterPrescription.MAX_SEGMENTS

        /**
         * Builds the performed cluster from a parent set and its additional segments. The parent is
         * always position 0, so the caller cannot supply a contradicting opening segment.
         */
        fun of(
            parent: WorkoutSet,
            kind: ClusterKind,
            plannedSegments: Int,
            additionalSegments: List<PerformedClusterSegment>
        ): WorkoutCluster {
            val opening = PerformedClusterSegment(
                position = 0,
                reps = requireNotNull(parent.reps) {
                    "A cluster parent must record its repetitions"
                },
                weightKg = requireNotNull(parent.weightKg) {
                    "A cluster parent must record its external load"
                },
                rir = parent.rir
            )
            return WorkoutCluster(
                kind = kind,
                plannedSegments = plannedSegments,
                segments = listOf(opening) + additionalSegments
            ).also { it.requireValid() }
        }
    }
}
