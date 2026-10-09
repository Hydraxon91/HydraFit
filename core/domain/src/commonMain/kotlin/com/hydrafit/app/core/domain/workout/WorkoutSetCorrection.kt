package com.hydrafit.app.core.domain.workout

/** Editable performed values; identity, load meaning and prescription snapshots stay unchanged. */
data class WorkoutSetCorrection(
    val reps: Int,
    val weightKg: Double?,
    val rir: Int?,
    val performedAtMillis: Long
)
