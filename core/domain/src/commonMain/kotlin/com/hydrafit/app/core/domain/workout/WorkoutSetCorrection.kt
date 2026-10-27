package com.hydrafit.app.core.domain.workout

/** Editable performed values; identity, load meaning and prescription snapshots stay unchanged. */
data class WorkoutSetCorrection(
    /** Repetitions for counted work, or null when the set is timed by [durationSeconds]. */
    val reps: Int? = null,
    val weightKg: Double?,
    val rir: Int?,
    val performedAtMillis: Long,
    /** Elapsed whole seconds for timed work, or null when the set is counted by [reps]. */
    val durationSeconds: Int? = null,
    /** Whole metres for distance work, or null for reps and timed work. */
    val distanceMeters: Int? = null,
    /** Measured force in newtons for force work, or null for every other metric. */
    val forceNewtons: Double? = null,
    /**
     * The desired measuring-device identity for a force set, or null for none. A correction carries
     * the complete desired state, so null clears a device the same way a null [weightKg] clears a
     * load; the rating stays descriptive and is never converted into force.
     */
    val device: DeviceAttribution? = null,
    /** Complete performed segment values, including the parent at position zero, for cluster edits. */
    val clusterSegments: List<PerformedClusterSegment>? = null
)
