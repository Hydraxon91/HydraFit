package com.hydrafit.app.core.domain.schedule

/**
 * The platform's best-effort local reminder delivery: one replaceable alarm plus the single summary
 * notification.
 *
 * Workout reminders never request exact-alarm access, so an implementation arms an inexact alarm and
 * accepts that the platform may deliver it late; the posting caller still applies the policy's
 * lateness bound before showing anything. Scheduling and posting are separate so a re-arm for a later
 * day never disturbs an already-posted summary.
 */
interface WorkoutReminderScheduler {
    /** True when the platform can post notifications now (notification permission and app setting). */
    fun canNotify(): Boolean

    /** Replaces the pending alarm with one for [atEpochMillis]; a posted notification is left alone. */
    suspend fun schedule(atEpochMillis: Long)

    /** Cancels the pending alarm and removes any posted reminder notification. */
    fun cancel()

    /**
     * Posts or replaces the single reminder summary. Returns false when nothing was posted, so the
     * caller can give a delivery claim back instead of losing the day.
     */
    suspend fun notify(workoutNames: List<String>): Boolean
}

/** iOS has no local reminder delivery: nothing is armed, posted or cancelled. */
class UnsupportedWorkoutReminderScheduler : WorkoutReminderScheduler {
    override fun canNotify() = false

    override suspend fun schedule(atEpochMillis: Long) = Unit

    override fun cancel() = Unit

    override suspend fun notify(workoutNames: List<String>): Boolean = false
}
