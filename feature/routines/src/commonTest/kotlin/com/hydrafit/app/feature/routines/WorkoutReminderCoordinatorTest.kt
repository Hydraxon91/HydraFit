package com.hydrafit.app.feature.routines

import com.hydrafit.app.core.domain.engine.AcceptedPlan
import com.hydrafit.app.core.domain.schedule.ActivationStatus
import com.hydrafit.app.core.domain.schedule.ActivationWorkout
import com.hydrafit.app.core.domain.schedule.OccurrenceEntry
import com.hydrafit.app.core.domain.schedule.OccurrenceStatus
import com.hydrafit.app.core.domain.schedule.ReminderDeliveryRepository
import com.hydrafit.app.core.domain.schedule.ScheduleMode
import com.hydrafit.app.core.domain.schedule.TrainingActivation
import com.hydrafit.app.core.domain.schedule.WorkoutOccurrence
import com.hydrafit.app.core.domain.schedule.WorkoutReminderScheduler
import com.hydrafit.app.core.domain.schedule.WorkoutScheduleRepository
import com.hydrafit.app.core.domain.schedule.WorkoutScheduleState
import com.hydrafit.app.core.domain.settings.WorkoutReminderPreferenceRepository
import com.hydrafit.app.core.domain.settings.WorkoutReminderPreferences
import com.hydrafit.app.core.domain.startup.StartupReadiness
import com.hydrafit.app.core.domain.time.TimeProvider
import com.hydrafit.app.core.domain.time.daysFromCivil
import com.hydrafit.app.core.domain.time.localEpochDay
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class WorkoutReminderCoordinatorTest {

    @Test
    fun reconcilesOnceStartupReadinessIsReachedWithoutAScreen() = runTest {
        val schedule = FakeReminderSchedule().apply {
            state.value = WorkoutScheduleState(activeActivationId = blockId)
            activation.value = activation("Lower A")
            occurrences.value = listOf(occurrence(7L, workoutId = 11L))
        }
        val preferences = FakeReminderPreferences()
        val reminders = FakeReminderScheduler()
        val readiness = FakeReminderReadiness()
        val coordinator = coordinator(
            schedule = schedule,
            preferences = preferences,
            reminders = reminders,
            readiness = readiness,
            dispatcher = StandardTestDispatcher(testScheduler),
            nowMillis = at(minutes(18) + 5)
        )

        coordinator.start()
        runCurrent()
        assertEquals(emptyList(), reminders.notified)
        assertFalse(preferences.wasRead)

        readiness.isReady.value = true
        advanceUntilIdle()

        // Several observed emissions may reconcile, but the day is claimed once.
        assertEquals(listOf(listOf("Lower A")), reminders.notified)
        assertTrue(preferences.wasRead)
        coordinator.close()
    }

    @Test
    fun postsOneSummaryForTodaysWorkoutsAndClaimsTheDay() = runTest {
        val schedule = FakeReminderSchedule().apply {
            state.value = WorkoutScheduleState(activeActivationId = blockId)
            activation.value = activation("Lower A", "Upper B")
            occurrences.value = listOf(
                occurrence(7L, workoutId = 11L, queuePosition = 0),
                occurrence(8L, workoutId = 12L, queuePosition = 1),
                occurrence(
                    9L,
                    workoutId = 12L,
                    queuePosition = 2,
                    status = OccurrenceStatus.FINISHED
                )
            )
        }
        val deliveries = FakeReminderDeliveries()
        val reminders = FakeReminderScheduler()
        val coordinator = coordinator(
            schedule = schedule,
            deliveries = deliveries,
            reminders = reminders,
            nowMillis = at(minutes(18) + 5)
        )

        coordinator.reconcile()

        assertEquals(listOf(listOf("Lower A", "Upper B")), reminders.notified)
        assertTrue(deliveries.isDelivered(blockId, today))
        // Nothing else is dated, so no further alarm is armed.
        assertEquals(emptyList(), reminders.scheduled)
        assertEquals(1, reminders.cancels)
    }

    @Test
    fun armsTodaysReminderWhileItsTimeIsStillAhead() = runTest {
        val reminders = FakeReminderScheduler()
        val schedule = FakeReminderSchedule().apply {
            state.value = WorkoutScheduleState(activeActivationId = blockId)
            activation.value = activation("Lower A")
            occurrences.value = listOf(occurrence(7L, workoutId = 11L))
        }

        coordinator(schedule = schedule, reminders = reminders).reconcile()

        assertEquals(listOf(instant(today, minutes(18))), reminders.scheduled)
        assertEquals(emptyList(), reminders.notified)
        assertEquals(0, reminders.cancels)
    }

    @Test
    fun armsTheNextEligibleDayAfterTodaysReminderIsPosted() = runTest {
        val reminders = FakeReminderScheduler()
        val schedule = FakeReminderSchedule().apply {
            state.value = WorkoutScheduleState(activeActivationId = blockId)
            activation.value = activation("Lower A")
            occurrences.value = listOf(
                occurrence(7L, workoutId = 11L),
                occurrence(8L, workoutId = 11L, queuePosition = 1, scheduledEpochDay = today + 3)
            )
        }

        coordinator(
            schedule = schedule,
            reminders = reminders,
            nowMillis = at(minutes(18) + 2)
        ).reconcile()

        assertEquals(listOf(listOf("Lower A")), reminders.notified)
        assertEquals(listOf(instant(today + 3, minutes(18))), reminders.scheduled)
    }

    @Test
    fun staysSilentForAnUndatedBlockAndWhenRemindersAreOff() = runTest {
        val undated = FakeReminderSchedule().apply {
            state.value = WorkoutScheduleState(activeActivationId = blockId)
            activation.value = activation("Lower A")
            occurrences.value = listOf(occurrence(7L, workoutId = 11L, scheduledEpochDay = null))
        }
        val undatedReminders = FakeReminderScheduler()
        coordinator(schedule = undated, reminders = undatedReminders).reconcile()
        assertEquals(emptyList(), undatedReminders.notified)
        assertEquals(emptyList(), undatedReminders.scheduled)
        assertEquals(1, undatedReminders.cancels)

        val dated = FakeReminderSchedule().apply {
            state.value = WorkoutScheduleState(activeActivationId = blockId)
            activation.value = activation("Lower A")
            occurrences.value = listOf(occurrence(7L, workoutId = 11L))
        }
        val offReminders = FakeReminderScheduler()
        coordinator(
            schedule = dated,
            preferences = FakeReminderPreferences(WorkoutReminderPreferences()),
            reminders = offReminders
        ).reconcile()
        assertEquals(emptyList(), offReminders.notified)
        assertEquals(emptyList(), offReminders.scheduled)
        assertEquals(1, offReminders.cancels)
    }

    @Test
    fun skipsTheChosenMinuteInsideQuietHours() = runTest {
        val reminders = FakeReminderScheduler()
        val coordinator = coordinator(
            schedule = FakeReminderSchedule().apply {
                state.value = WorkoutScheduleState(activeActivationId = blockId)
                activation.value = activation("Lower A")
                occurrences.value = listOf(occurrence(7L, workoutId = 11L))
            },
            preferences = FakeReminderPreferences(
                WorkoutReminderPreferences(
                    remindersEnabled = true,
                    reminderMinuteOfDay = minutes(22),
                    quietHoursEnabled = true,
                    quietStartMinuteOfDay = minutes(21),
                    quietEndMinuteOfDay = minutes(7)
                )
            ),
            reminders = reminders
        )

        coordinator.reconcile()

        assertEquals(emptyList(), reminders.notified)
        assertEquals(emptyList(), reminders.scheduled)
        assertEquals(1, reminders.cancels)
    }

    @Test
    fun withoutNotificationPermissionNothingIsPostedOrClaimed() = runTest {
        val deliveries = FakeReminderDeliveries()
        val reminders = FakeReminderScheduler(notificationAllowed = false)
        val coordinator = coordinator(
            schedule = FakeReminderSchedule().apply {
                state.value = WorkoutScheduleState(activeActivationId = blockId)
                activation.value = activation("Lower A")
                occurrences.value = listOf(
                    occurrence(7L, workoutId = 11L),
                    occurrence(
                        8L,
                        workoutId = 11L,
                        queuePosition = 1,
                        scheduledEpochDay = today + 1
                    )
                )
            },
            deliveries = deliveries,
            reminders = reminders,
            nowMillis = at(minutes(18) + 5)
        )

        coordinator.reconcile()

        assertEquals(emptyList(), reminders.notified)
        assertFalse(deliveries.isDelivered(blockId, today))
        assertEquals(listOf(instant(today + 1, minutes(18))), reminders.scheduled)
    }

    @Test
    fun doesNotPostADayThatIsAlreadyClaimed() = runTest {
        val deliveries = FakeReminderDeliveries()
        deliveries.claim(blockId, today, deliveredAtMillis = at(minutes(18)))
        val reminders = FakeReminderScheduler()
        val coordinator = coordinator(
            schedule = FakeReminderSchedule().apply {
                state.value = WorkoutScheduleState(activeActivationId = blockId)
                activation.value = activation("Lower A")
                occurrences.value = listOf(occurrence(7L, workoutId = 11L))
            },
            deliveries = deliveries,
            reminders = reminders,
            nowMillis = at(minutes(18) + 5)
        )

        coordinator.reconcile()

        assertEquals(emptyList(), reminders.notified)
        assertEquals(emptyList(), reminders.scheduled)
        assertEquals(1, reminders.cancels)
    }

    @Test
    fun releasesTheClaimWhenThePostFailsSoALaterPassCanRetry() = runTest {
        val deliveries = FakeReminderDeliveries()
        val reminders = FakeReminderScheduler(notificationSucceeds = false)
        val schedule = FakeReminderSchedule().apply {
            state.value = WorkoutScheduleState(activeActivationId = blockId)
            activation.value = activation("Lower A")
            occurrences.value = listOf(occurrence(7L, workoutId = 11L))
        }
        val coordinator = coordinator(
            schedule = schedule,
            deliveries = deliveries,
            reminders = reminders,
            nowMillis = at(minutes(18) + 5)
        )

        coordinator.reconcile()
        assertEquals(emptyList(), reminders.notified)
        assertFalse(deliveries.isDelivered(blockId, today))

        reminders.notificationSucceeds = true
        coordinator.reconcile()
        assertEquals(listOf(listOf("Lower A")), reminders.notified)
        assertTrue(deliveries.isDelivered(blockId, today))
    }

    @Test
    fun aPassThatSuspendsDoesNotPostForASupersededSchedule() = runTest {
        val gate = CompletableDeferred<Unit>()
        val schedule = FakeReminderSchedule().apply {
            state.value = WorkoutScheduleState(activeActivationId = blockId)
            activation.value = activation("Lower A")
            occurrences.value = listOf(occurrence(7L, workoutId = 11L))
            occurrencesGate = gate
        }
        val reminders = FakeReminderScheduler()
        val coordinator = coordinator(schedule = schedule, reminders = reminders)

        val pass = async { coordinator.reconcile() }
        runCurrent()
        // The workout is finished while the pass is suspended on its occurrence read.
        schedule.occurrences.value = emptyList()
        gate.complete(Unit)
        pass.await()

        assertEquals(emptyList(), reminders.notified)
        assertEquals(1, reminders.cancels)
    }

    @Test
    fun aDeliveryChangeReconcilesAfterNotificationPermissionIsGranted() = runTest {
        val deliveries = FakeReminderDeliveries()
        val reminders = FakeReminderScheduler(notificationAllowed = false)
        val schedule = FakeReminderSchedule().apply {
            state.value = WorkoutScheduleState(activeActivationId = blockId)
            activation.value = activation("Lower A")
            occurrences.value = listOf(occurrence(7L, workoutId = 11L))
        }
        val readiness = FakeReminderReadiness()
        val coordinator = coordinator(
            schedule = schedule,
            deliveries = deliveries,
            reminders = reminders,
            readiness = readiness,
            dispatcher = StandardTestDispatcher(testScheduler),
            nowMillis = at(minutes(18) + 5)
        )

        coordinator.start()
        readiness.isReady.value = true
        advanceUntilIdle()
        // No permission means no post and no claim, so the day is still available.
        assertEquals(emptyList(), reminders.notified)
        assertFalse(deliveries.isDelivered(blockId, today))

        // Granting access in system settings changes nothing in the observed stores.
        reminders.notificationAllowed = true
        reminders.reportDeliveryChange()
        advanceUntilIdle()

        assertEquals(listOf(listOf("Lower A")), reminders.notified)
        assertTrue(deliveries.isDelivered(blockId, today))
        coordinator.close()
    }

    private fun coordinator(
        schedule: FakeReminderSchedule,
        preferences: FakeReminderPreferences = FakeReminderPreferences(),
        deliveries: FakeReminderDeliveries = FakeReminderDeliveries(),
        reminders: FakeReminderScheduler = FakeReminderScheduler(),
        readiness: FakeReminderReadiness = FakeReminderReadiness(),
        nowMillis: Long = at(minutes(9)),
        dispatcher: CoroutineDispatcher = Dispatchers.Default
    ) = WorkoutReminderCoordinator(
        preferences = preferences,
        schedule = schedule,
        deliveries = deliveries,
        reminders = reminders,
        timeProvider = FixedReminderClock(nowMillis),
        readiness = readiness,
        dispatcher = dispatcher
    )

    private fun activation(vararg workoutNames: String) = TrainingActivation(
        id = blockId,
        name = "Block",
        createdAtMillis = 0L,
        startEpochDay = today,
        mode = ScheduleMode.WEEKDAY,
        weekdays = emptySet(),
        status = ActivationStatus.ACTIVE,
        workouts = workoutNames.mapIndexed { index, name ->
            ActivationWorkout(id = 11L + index, position = index, name = name)
        }
    )

    private fun occurrence(
        id: Long,
        workoutId: Long,
        queuePosition: Int = 0,
        scheduledEpochDay: Long? = today,
        status: OccurrenceStatus = OccurrenceStatus.PENDING
    ) = WorkoutOccurrence(
        id = id,
        activationId = blockId,
        activationWorkoutId = workoutId,
        queuePosition = queuePosition,
        scheduledEpochDay = scheduledEpochDay,
        status = status
    )

    private fun minutes(hours: Int) = hours * 60

    private fun at(minuteOfDay: Int) = localMidnight + minuteOfDay * 60_000L

    private fun instant(localEpochDay: Long, minuteOfDay: Int) =
        localEpochDay * 86_400_000L - offset + minuteOfDay * 60_000L

    private companion object {
        /** A fixed +02:00 pretend zone; the harness's current instant is 00:00 local on 2026-06-15. */
        val offset = 2L * 60 * 60 * 1000L
        val blockId = 5L
        val localMidnight = daysFromCivil(2026, 6, 15) * 86_400_000L - offset
        val today = localEpochDay(localMidnight, offset)
    }
}

private class FixedReminderClock(private val nowMillis: Long) : TimeProvider {
    override fun nowMillis() = nowMillis

    override fun utcOffsetMillis() = 2L * 60 * 60 * 1000L
}

private class FakeReminderReadiness : StartupReadiness {
    override val isReady = MutableStateFlow(false)
}

private class FakeReminderPreferences(
    initial: WorkoutReminderPreferences = WorkoutReminderPreferences(
        remindersEnabled = true,
        reminderMinuteOfDay = 18 * 60
    )
) : WorkoutReminderPreferenceRepository {
    val values = MutableStateFlow(initial)
    var wasRead = false

    override fun observe(): Flow<WorkoutReminderPreferences> = values

    override suspend fun get(): WorkoutReminderPreferences {
        wasRead = true
        return values.value
    }

    override suspend fun setEnabled(enabled: Boolean) {
        values.value = values.value.copy(remindersEnabled = enabled)
    }

    override suspend fun setReminderMinuteOfDay(minuteOfDay: Int?) {
        values.value = values.value.copy(reminderMinuteOfDay = minuteOfDay)
    }

    override suspend fun setQuietHoursEnabled(enabled: Boolean) {
        values.value = values.value.copy(quietHoursEnabled = enabled)
    }

    override suspend fun setQuietHours(startMinuteOfDay: Int, endMinuteOfDay: Int) {
        values.value = values.value.copy(
            quietStartMinuteOfDay = startMinuteOfDay,
            quietEndMinuteOfDay = endMinuteOfDay
        )
    }
}

private class FakeReminderDeliveries : ReminderDeliveryRepository {
    private val claimed = mutableSetOf<Pair<Long, Long>>()

    override suspend fun isDelivered(activationId: Long, reminderEpochDay: Long): Boolean =
        (activationId to reminderEpochDay) in claimed

    override suspend fun claim(
        activationId: Long,
        reminderEpochDay: Long,
        deliveredAtMillis: Long
    ): Boolean = claimed.add(activationId to reminderEpochDay)

    override suspend fun release(activationId: Long, reminderEpochDay: Long) {
        claimed.remove(activationId to reminderEpochDay)
    }
}

private class FakeReminderScheduler(
    var notificationAllowed: Boolean = true,
    var notificationSucceeds: Boolean = true
) : WorkoutReminderScheduler {
    val scheduled = mutableListOf<Long>()
    val notified = mutableListOf<List<String>>()
    var cancels = 0
    private val changes = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    override val deliveryChanges: Flow<Unit> = changes.asSharedFlow()

    override fun reportDeliveryChange() {
        changes.tryEmit(Unit)
    }

    override fun canNotify() = notificationAllowed

    override suspend fun schedule(atEpochMillis: Long) {
        scheduled += atEpochMillis
    }

    override fun cancel() {
        cancels++
    }

    override suspend fun notify(workoutNames: List<String>): Boolean {
        if (!notificationSucceeds) return false
        notified += workoutNames
        return true
    }
}

private class FakeReminderSchedule : WorkoutScheduleRepository {
    val state = MutableStateFlow(WorkoutScheduleState())
    val activation = MutableStateFlow<TrainingActivation?>(null)
    val occurrences = MutableStateFlow<List<WorkoutOccurrence>>(emptyList())

    /** When set, an occurrence read suspends until it is completed. */
    var occurrencesGate: CompletableDeferred<Unit>? = null

    override fun observeScheduleState(): Flow<WorkoutScheduleState> = state

    override fun observeActiveActivation(): Flow<TrainingActivation?> = activation

    override fun observeOccurrences(activationId: Long): Flow<List<WorkoutOccurrence>> =
        occurrences.map { list -> list.filter { it.activationId == activationId } }

    override suspend fun scheduleState(): WorkoutScheduleState = state.value

    override suspend fun setScheduleState(state: WorkoutScheduleState) {
        this.state.value = state
    }

    override suspend fun activeActivation(): TrainingActivation? = activation.value

    override suspend fun getActivation(id: Long): TrainingActivation? =
        activation.value?.takeIf { it.id == id }

    override suspend fun occurrences(activationId: Long): List<WorkoutOccurrence> {
        occurrencesGate?.await()
        return occurrences.value.filter { it.activationId == activationId }
    }

    override suspend fun getOccurrence(id: Long): WorkoutOccurrence? =
        occurrences.value.firstOrNull { it.id == id }

    override suspend fun acceptAndActivate(
        acceptedPlan: AcceptedPlan?,
        activation: TrainingActivation,
        scheduledEpochDays: List<Long?>,
        replaceActive: Boolean
    ): Long = error("No reminder reconciliation writes a block")

    override suspend fun resolveOccurrence(
        occurrenceId: Long,
        expectedRevision: Int,
        status: OccurrenceStatus,
        resolvedAtMillis: Long,
        entries: List<OccurrenceEntry>
    ): WorkoutScheduleState = error("No reminder reconciliation resolves an occurrence")

    override suspend fun updateActivationHeader(activation: TrainingActivation) =
        error("No reminder reconciliation updates a block")

    override suspend fun updateOccurrence(occurrence: WorkoutOccurrence) =
        error("No reminder reconciliation updates an occurrence")

    override suspend fun replaceOccurrenceEntries(
        occurrenceId: Long,
        entries: List<OccurrenceEntry>
    ) = error("No reminder reconciliation replaces entries")

    override suspend fun deleteOccurrencesForActivation(activationId: Long) =
        error("No reminder reconciliation deletes occurrences")

    override suspend fun isTemplateReferenced(templateId: Long): Boolean =
        error("No reminder reconciliation reads template references")
}
