package com.hydrafit.app.core.domain.workout

/**
 * An explicit workout session, the durable segmentation unit for fatigue tracking.
 *
 * [localEpochDay] is the local calendar day (days since the Unix epoch, in the user's local
 * offset) the session started on, so day rollover never spans two local days.
 */
data class WorkoutSession(
    val id: String,
    val startedAtMillis: Long,
    val endedAtMillis: Long? = null,
    val localEpochDay: Long,
    /** The scheduled occurrence this session was started for, when it belongs to one. */
    val occurrenceId: Long? = null
)
