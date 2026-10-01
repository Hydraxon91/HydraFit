package com.hydrafit.app.core.domain.fatigue

data class LoggedSet(
    val timestampMillis: Long,
    val targets: List<MuscleTarget>,
    val isWarmup: Boolean = false,
    val reps: Int = FatigueConfig.DEFAULT_REFERENCE_REPS,
    /** Compound work recovers on a longer half-life; unknown/legacy types default to isolation. */
    val isCompound: Boolean = false,
    /** Identifies the exercise so the relative-load reference can be found among earlier sets. */
    val exerciseId: String? = null,
    /** Logged working load; null/<=0 leaves the relative-load factor neutral. */
    val weightKg: Double? = null,
    /** User-entered reps in reserve; null leaves the effort factor at its neutral default. */
    val rir: Int? = null
)
