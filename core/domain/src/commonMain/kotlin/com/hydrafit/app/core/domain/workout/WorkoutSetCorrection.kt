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
    val distanceMeters: Int? = null
)
