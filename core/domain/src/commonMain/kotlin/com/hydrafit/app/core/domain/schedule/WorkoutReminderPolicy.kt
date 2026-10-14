package com.hydrafit.app.core.domain.schedule

import com.hydrafit.app.core.domain.settings.WorkoutReminderPreferences
import com.hydrafit.app.core.domain.time.TimeProvider
import com.hydrafit.app.core.domain.time.localEpochDay

/**
 * How late a reminder may still be posted on its own local date. A product choice for best-effort
 * local delivery, not an Android or alarm guarantee.
 */
const val REMINDER_MAX_LATE_MINUTES: Int = 60

private const val MILLIS_PER_MINUTE = 60L * 1000L

/** What the app should do about today's workout reminders. */
sealed interface ReminderDecision {
    /**
     * Arm a reminder for a future instant on today's local date. [occurrenceIds] are the pending
     * dated workouts the single summary describes.
     */
    data class Schedule(val atEpochMillis: Long, val occurrenceIds: List<Long>) : ReminderDecision

    /** The reminder instant has arrived but is still inside the lateness window: post one summary now. */
    data class DeliverNow(val occurrenceIds: List<Long>) : ReminderDecision

    /** Reminders are turned off, or no local time has been chosen yet. */
    data object Disabled : ReminderDecision

    /** No active block, or no pending workout dated today in it. */
    data object NothingToday : ReminderDecision

    /** A reminder has already been posted for this block and local day. */
    data object AlreadyDelivered : ReminderDecision

    /** The chosen reminder time falls inside the enabled quiet interval, so today is skipped. */
    data object SuppressedQuietHours : ReminderDecision

    /** The reminder instant is more than [REMINDER_MAX_LATE_MINUTES] in the past. */
    data object SuppressedExpired : ReminderDecision
}

/**
 * Decides whether today's dated, pending workouts in the active block warrant one local reminder.
 *
 * Only [OccurrenceStatus.PENDING] occurrences scheduled for today's local civil day count, so
 * in-progress and resolved workouts and undated (sequence-mode) occurrences never produce a
 * reminder; a block whose occurrences carry no date therefore stays silent. Resolved-ness is read
 * from the occurrence status, and nothing here advances the queue, edits a plan or writes a set.
 *
 * [timeProvider] supplies the current instant and local-day/minute resolution. A platform that can
 * resolve its current zone overrides `epochMillisForLocalMinute`, so a skipped local time resolves
 * forward to the first valid instant. A repeated local time still yields at most one reminder,
 * because [alreadyDelivered] is supplied per active block and local day.
 */
fun decideWorkoutReminder(
    preferences: WorkoutReminderPreferences,
    state: WorkoutScheduleState,
    occurrences: List<WorkoutOccurrence>,
    alreadyDelivered: Boolean,
    timeProvider: TimeProvider
): ReminderDecision {
    val reminderMinute = preferences.reminderMinuteOfDay
    if (!preferences.remindersEnabled || reminderMinute == null) return ReminderDecision.Disabled
    val activationId = state.activeActivationId ?: return ReminderDecision.NothingToday
    val nowMillis = timeProvider.nowMillis()
    val today = localEpochDay(nowMillis, timeProvider.utcOffsetMillis())
    val pendingToday = occurrences.filter {
        it.activationId == activationId &&
            it.status == OccurrenceStatus.PENDING &&
            it.scheduledEpochDay == today
    }
    if (pendingToday.isEmpty()) return ReminderDecision.NothingToday
    if (alreadyDelivered) return ReminderDecision.AlreadyDelivered
    if (isWithinQuietHours(preferences)) return ReminderDecision.SuppressedQuietHours
    val atEpochMillis = timeProvider.epochMillisForLocalMinute(today, reminderMinute)
    val lateMillis = nowMillis - atEpochMillis
    if (lateMillis > REMINDER_MAX_LATE_MINUTES * MILLIS_PER_MINUTE) {
        return ReminderDecision.SuppressedExpired
    }
    val occurrenceIds = pendingToday.map { it.id }
    return if (lateMillis >= 0L) {
        ReminderDecision.DeliverNow(occurrenceIds)
    } else {
        ReminderDecision.Schedule(atEpochMillis, occurrenceIds)
    }
}

/**
 * True when the chosen reminder minute falls inside the enabled quiet interval. An interval whose
 * start equals its end is empty; otherwise it wraps past midnight when the end is not later than the
 * start.
 */
private fun isWithinQuietHours(preferences: WorkoutReminderPreferences): Boolean {
    if (!preferences.quietHoursEnabled) return false
    val minute = preferences.reminderMinuteOfDay ?: return false
    val start = preferences.quietStartMinuteOfDay
    val end = preferences.quietEndMinuteOfDay
    return when {
        start == end -> false
        start < end -> minute in start until end
        else -> minute >= start || minute < end
    }
}
