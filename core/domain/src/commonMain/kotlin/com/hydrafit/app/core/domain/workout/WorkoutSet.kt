package com.hydrafit.app.core.domain.workout

data class WorkoutSet(
    val id: Long = 0,
    val exerciseId: String,
    val reps: Int,
    val weightKg: Double?,
    val performedAtMillis: Long,
    val isWarmup: Boolean = false,
    /** The accepted plan's week/cycle and day at log time, when a plan was active. */
    val weekNumber: Int? = null,
    val cycleNumber: Int? = null,
    val dayIndex: Int? = null
)
