package com.hydrafit.app.core.domain.schedule

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

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
     * Cancels only the pending alarm, leaving any posted reminder notification in place. Used when
     * today's reminder was already posted and no later day remains to arm, so a spent alarm is
     * cleared without taking away the summary the user can still act on. The default keeps the
     * previous full [cancel] behavior for an implementation that cannot cancel the alarm separately.
     */
    fun cancelPendingAlarm() = cancel()

    /**
     * Posts or replaces the single reminder summary. Returns false when nothing was posted, so the
     * caller can give a delivery claim back instead of losing the day.
     */
    suspend fun notify(workoutNames: List<String>): Boolean

    /**
     * Emits when the platform's ability to deliver changed while the app's own stores did not, such
     * as a notification-permission result or notification access changed in system settings. An
     * app-lifetime observer decides delivery again from current state instead of trusting a
     * permission answer taken earlier.
     */
    val deliveryChanges: Flow<Unit>

    /** Reports one [deliveryChanges] event after a delivery-relevant platform change. */
    fun reportDeliveryChange()
}

/** iOS has no local reminder delivery: nothing is armed, posted or cancelled. */
class UnsupportedWorkoutReminderScheduler : WorkoutReminderScheduler {
    private val changes = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    override val deliveryChanges: Flow<Unit> = changes.asSharedFlow()

    override fun canNotify() = false

    override suspend fun schedule(atEpochMillis: Long) = Unit

    override fun cancel() = Unit

    override fun cancelPendingAlarm() = Unit

    override suspend fun notify(workoutNames: List<String>): Boolean = false

    override fun reportDeliveryChange() {
        changes.tryEmit(Unit)
    }
}
