package com.hydrafit.app.core.domain.fatigue

data class LoggedSet(
    val timestampMillis: Long,
    val targets: List<MuscleTarget>,
    val isWarmup: Boolean = false
)
