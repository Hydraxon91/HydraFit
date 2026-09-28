package com.hydrafit.app.core.domain.workout

data class WorkoutSet(
    val id: Long = 0,
    val exerciseId: String,
    val reps: Int,
    val weightKg: Double?,
    val performedAtMillis: Long,
    val isWarmup: Boolean = false
)
