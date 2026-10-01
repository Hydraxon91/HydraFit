package com.hydrafit.app.core.domain.fatigue

data class LoggedSet(
    val timestampMillis: Long,
    val targets: List<MuscleTarget>,
    val isWarmup: Boolean = false,
    val reps: Int = FatigueConfig.DEFAULT_REFERENCE_REPS
)
