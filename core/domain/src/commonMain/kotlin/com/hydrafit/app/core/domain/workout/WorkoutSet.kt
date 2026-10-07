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
    val dayIndex: Int? = null,
    /** Optional reps in reserve (0..10); null when the user did not record effort. */
    val rir: Int? = null,
    /** The explicit workout session this set belongs to; null for legacy/unsegmented rows. */
    val sessionId: String? = null,
    /** The scheduled occurrence this set fulfils, when it was logged against one. */
    val occurrenceId: Long? = null,
    /** The occurrence's prescription slot this set fulfils, when it was logged against one. */
    val occurrenceEntryId: Long? = null
)
