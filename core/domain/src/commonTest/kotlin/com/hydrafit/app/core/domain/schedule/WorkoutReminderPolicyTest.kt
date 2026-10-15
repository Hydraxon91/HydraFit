package com.hydrafit.app.core.domain.schedule

import com.hydrafit.app.core.domain.settings.WorkoutReminderPreferences
import com.hydrafit.app.core.domain.time.TimeProvider
import com.hydrafit.app.core.domain.time.daysFromCivil
import com.hydrafit.app.core.domain.time.localEpochDay
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class WorkoutReminderPolicyTest {

    @Test
    fun isDisabledWhenRemindersAreOffOrNoTimeIsChosen() {
        assertEquals(
            ReminderDecision.Disabled,
            decide(preferences = WorkoutReminderPreferences())
        )
        assertEquals(
            ReminderDecision.Disabled,
            decide(
                preferences = WorkoutReminderPreferences(
                    remindersEnabled = true,
                    reminderMinuteOfDay = null
                )
            )
        )
    }

    @Test
    fun isQuietWithoutAnActiveBlockOrAPendingWorkoutDatedToday() {
        assertEquals(
            ReminderDecision.NothingToday,
            decide(state = WorkoutScheduleState(activeActivationId = null))
        )
        assertEquals(ReminderDecision.NothingToday, decide(occurrences = emptyList()))
        assertEquals(
            ReminderDecision.NothingToday,
            decide(occurrences = listOf(pending(7L, scheduledEpochDay = today + 1)))
        )
        // An undated occurrence cannot say which day it belongs to, so sequence mode stays silent.
        assertEquals(
            ReminderDecision.NothingToday,
            decide(occurrences = listOf(pending(7L, scheduledEpochDay = null)))
        )
    }

    @Test
    fun schedulesOneSummaryForEveryPendingWorkoutDatedToday() {
        val decision = decide(
            preferences = armed(reminderMinuteOfDay = minutes(18)),
            occurrences = listOf(
                pending(7L),
                pending(9L),
                pending(11L, status = OccurrenceStatus.IN_PROGRESS),
                pending(12L, status = OccurrenceStatus.FINISHED),
                pending(13L, status = OccurrenceStatus.SKIPPED),
                pending(14L, scheduledEpochDay = today - 1),
                pending(15L, activationId = 99L)
            )
        )

        assertEquals(
            ReminderDecision.Schedule(instant(today, minutes(18)), listOf(7L, 9L)),
            decision
        )
    }

    @Test
    fun deliversWithinTheLatenessWindowAndExpiresBeyondIt() {
        val preferences = armed(reminderMinuteOfDay = minutes(18))
        val workouts = listOf(pending(7L))
        val due = at(minutes(18))

        assertEquals(
            ReminderDecision.DeliverNow(listOf(7L)),
            decide(preferences, workouts, nowMillis = due)
        )
        assertEquals(
            ReminderDecision.DeliverNow(listOf(7L)),
            decide(
                preferences,
                workouts,
                nowMillis = due + (REMINDER_MAX_LATE_MINUTES - 1) * 60_000L
            )
        )
        assertEquals(
            ReminderDecision.DeliverNow(listOf(7L)),
            decide(
                preferences,
                workouts,
                nowMillis = due + REMINDER_MAX_LATE_MINUTES * 60_000L
            )
        )
        assertEquals(
            ReminderDecision.SuppressedExpired,
            decide(
                preferences,
                workouts,
                nowMillis = due + REMINDER_MAX_LATE_MINUTES * 60_000L + 1L
            )
        )
        assertEquals(
            ReminderDecision.SuppressedExpired,
            decide(
                preferences,
                workouts,
                nowMillis = due + (REMINDER_MAX_LATE_MINUTES + 1) * 60_000L
            )
        )
    }

    @Test
    fun skipsAReminderInsideQuietHoursWithoutPostponingIt() {
        val workouts = listOf(pending(7L))
        val overnight = armed(
            reminderMinuteOfDay = minutes(22),
            quietHoursEnabled = true,
            quietStartMinuteOfDay = minutes(21),
            quietEndMinuteOfDay = minutes(7)
        )

        assertEquals(ReminderDecision.SuppressedQuietHours, decide(overnight, workouts))
        // 06:00 is still inside the overnight interval.
        assertEquals(
            ReminderDecision.SuppressedQuietHours,
            decide(overnight.copy(reminderMinuteOfDay = minutes(6)), workouts)
        )
        // 12:00 is outside it, so the reminder is armed normally.
        assertEquals(
            ReminderDecision.Schedule(instant(today, minutes(12)), listOf(7L)),
            decide(overnight.copy(reminderMinuteOfDay = minutes(12)), workouts)
        )

        val sameDay = overnight.copy(
            reminderMinuteOfDay = minutes(12) + 30,
            quietStartMinuteOfDay = minutes(12),
            quietEndMinuteOfDay = minutes(13)
        )
        assertEquals(ReminderDecision.SuppressedQuietHours, decide(sameDay, workouts))
        // The interval is half-open, so its end minute is not inside it.
        assertEquals(
            ReminderDecision.Schedule(instant(today, minutes(13)), listOf(7L)),
            decide(sameDay.copy(reminderMinuteOfDay = minutes(13)), workouts)
        )
        // Disabled quiet hours never suppress.
        assertEquals(
            ReminderDecision.Schedule(instant(today, minutes(22)), listOf(7L)),
            decide(overnight.copy(quietHoursEnabled = false), workouts)
        )
    }

    @Test
    fun suppressesALocalDayItHasAlreadyDelivered() {
        assertEquals(
            ReminderDecision.AlreadyDelivered,
            decide(
                preferences = armed(reminderMinuteOfDay = minutes(18)),
                occurrences = listOf(pending(7L)),
                alreadyDelivered = true
            )
        )
    }

    @Test
    fun aSkippedLocalTimeResolvesToTheFirstValidInstant() {
        // A synthetic zone whose 02:00-02:59 does not exist: the zone moves those minutes forward.
        val firstValidInstant = instant(today, minutes(3))
        val zone = object : TimeProvider {
            override fun nowMillis() = at(minutes(0))
            override fun utcOffsetMillis() = offset
            override fun epochMillisForLocalMinute(localEpochDay: Long, minuteOfDay: Int): Long =
                if (localEpochDay == today && minuteOfDay in minutes(2) until minutes(3)) {
                    firstValidInstant
                } else {
                    instant(localEpochDay, minuteOfDay)
                }
        }

        val decision = decideWorkoutReminder(
            preferences = armed(reminderMinuteOfDay = minutes(2) + 30),
            state = activeState(),
            occurrences = listOf(pending(7L)),
            alreadyDelivered = false,
            timeProvider = zone
        )

        assertEquals(ReminderDecision.Schedule(firstValidInstant, listOf(7L)), decision)
    }

    @Test
    fun aRepeatedLocalTimeStillYieldsOneReminderPerLocalDay() {
        // A synthetic zone whose 02:30 occurs twice; the zone reports its first occurrence.
        val firstOccurrence = instant(today, minutes(2) + 30) - 3_600_000L
        val zone = object : TimeProvider {
            override fun nowMillis() = at(minutes(0))
            override fun utcOffsetMillis() = offset
            override fun epochMillisForLocalMinute(localEpochDay: Long, minuteOfDay: Int): Long =
                if (localEpochDay == today && minuteOfDay == minutes(2) + 30) {
                    firstOccurrence
                } else {
                    instant(localEpochDay, minuteOfDay)
                }
        }
        val preferences = armed(reminderMinuteOfDay = minutes(2) + 30)
        val workouts = listOf(pending(7L))

        assertEquals(
            ReminderDecision.Schedule(firstOccurrence, listOf(7L)),
            decideWorkoutReminder(preferences, activeState(), workouts, false, zone)
        )
        // Once that local day is marked delivered, the second pass of the hour adds nothing.
        assertEquals(
            ReminderDecision.AlreadyDelivered,
            decideWorkoutReminder(preferences, activeState(), workouts, true, zone)
        )
    }

    @Test
    fun hasNoInstantWhenRemindersAreOffOrQuietHoursSkipTheChosenMinute() {
        assertNull(next(enabled = false))
        assertNull(next(minuteOfDay = null))
        assertNull(next(minuteOfDay = minutes(22), quietHoursEnabled = true))
        // A quiet interval that does not cover the chosen minute still arms the reminder.
        assertNotNull(next(minuteOfDay = minutes(18), quietHoursEnabled = true))
    }

    @Test
    fun armsTodaysInstantWhileItIsStillAhead() {
        assertEquals(
            instant(today, minutes(18)),
            next(nowMillis = at(minutes(9)))
        )
    }

    @Test
    fun fallsThroughToTheNextDatedDayOnceTodaysInstantHasPassed() {
        // 18:00 chosen and 19:30 now: today's instant is consumed, the later date is still armed.
        assertEquals(
            instant(today + 2, minutes(18)),
            next(
                occurrences = listOf(
                    pending(7L),
                    pending(9L, scheduledEpochDay = today + 2)
                ),
                nowMillis = at(minutes(19) + 30)
            )
        )
    }

    @Test
    fun ignoresUndatedResolvedPastAndForeignOccurrences() {
        assertNull(next(occurrences = listOf(pending(7L, scheduledEpochDay = null))))
        assertNull(next(occurrences = listOf(pending(7L, status = OccurrenceStatus.FINISHED))))
        assertNull(next(occurrences = listOf(pending(7L, scheduledEpochDay = today - 1))))
        assertNull(next(occurrences = listOf(pending(7L, activationId = 99L))))
    }

    private fun decide(
        preferences: WorkoutReminderPreferences = armed(reminderMinuteOfDay = minutes(18)),
        occurrences: List<WorkoutOccurrence> = listOf(pending(7L)),
        state: WorkoutScheduleState = activeState(),
        alreadyDelivered: Boolean = false,
        nowMillis: Long = at(minutes(9))
    ) = decideWorkoutReminder(
        preferences = preferences,
        state = state,
        occurrences = occurrences,
        alreadyDelivered = alreadyDelivered,
        timeProvider = fixedOffsetProvider(nowMillis)
    )

    private fun next(
        enabled: Boolean = true,
        minuteOfDay: Int? = minutes(18),
        occurrences: List<WorkoutOccurrence> = listOf(pending(7L)),
        nowMillis: Long = at(minutes(9)),
        quietHoursEnabled: Boolean = false,
        quietStartMinuteOfDay: Int = minutes(21),
        quietEndMinuteOfDay: Int = minutes(7)
    ) = nextWorkoutReminderInstant(
        preferences = if (enabled) {
            armed(minuteOfDay, quietHoursEnabled, quietStartMinuteOfDay, quietEndMinuteOfDay)
        } else {
            WorkoutReminderPreferences()
        },
        activationId = blockId,
        occurrences = occurrences,
        timeProvider = fixedOffsetProvider(nowMillis)
    )

    private fun fixedOffsetProvider(nowMillis: Long) = object : TimeProvider {
        override fun nowMillis() = nowMillis
        override fun utcOffsetMillis() = offset
    }

    private fun armed(
        reminderMinuteOfDay: Int?,
        quietHoursEnabled: Boolean = false,
        quietStartMinuteOfDay: Int = minutes(21),
        quietEndMinuteOfDay: Int = minutes(7)
    ) = WorkoutReminderPreferences(
        remindersEnabled = true,
        reminderMinuteOfDay = reminderMinuteOfDay,
        quietHoursEnabled = quietHoursEnabled,
        quietStartMinuteOfDay = quietStartMinuteOfDay,
        quietEndMinuteOfDay = quietEndMinuteOfDay
    )

    private fun activeState() = WorkoutScheduleState(activeActivationId = blockId)

    private fun pending(
        id: Long,
        status: OccurrenceStatus = OccurrenceStatus.PENDING,
        scheduledEpochDay: Long? = today,
        activationId: Long = blockId
    ) = WorkoutOccurrence(
        id = id,
        activationId = activationId,
        activationWorkoutId = id,
        queuePosition = 0,
        scheduledEpochDay = scheduledEpochDay,
        status = status
    )

    private fun minutes(hours: Int) = hours * 60

    private fun at(minuteOfDay: Int) = localMidnight + minuteOfDay * 60_000L

    private fun instant(localEpochDay: Long, minuteOfDay: Int) =
        localEpochDay * 86_400_000L - offset + minuteOfDay * 60_000L

    /** A fixed +02:00 pretend zone; the test's current instant is 00:00 local on 2026-06-15. */
    private val offset = 2L * 60 * 60 * 1000L
    private val blockId = 5L
    private val localMidnight = daysFromCivil(2026, 6, 15) * 86_400_000L - offset
    private val today = localEpochDay(localMidnight, offset)
}
