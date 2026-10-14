package com.hydrafit.app.feature.routines

import com.hydrafit.app.core.domain.schedule.ReminderDecision
import com.hydrafit.app.core.domain.schedule.ReminderDeliveryRepository
import com.hydrafit.app.core.domain.schedule.TrainingActivation
import com.hydrafit.app.core.domain.schedule.WorkoutOccurrence
import com.hydrafit.app.core.domain.schedule.WorkoutReminderScheduler
import com.hydrafit.app.core.domain.schedule.WorkoutScheduleRepository
import com.hydrafit.app.core.domain.schedule.decideWorkoutReminder
import com.hydrafit.app.core.domain.schedule.nextWorkoutReminderInstant
import com.hydrafit.app.core.domain.settings.WorkoutReminderPreferenceRepository
import com.hydrafit.app.core.domain.startup.StartupReadiness
import com.hydrafit.app.core.domain.time.TimeProvider
import com.hydrafit.app.core.domain.time.localEpochDay
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * App-lifetime reminder reconciliation, independent of whether a Routines screen is open.
 *
 * Every pass re-reads the current stores rather than trusting an observed snapshot or alarm extras:
 * the alarm carries no workout authority, so a stale alarm, a reboot or a clock change can only make
 * today's state be decided again. A mutex serializes the app-lifetime collector, screen changes and
 * the alarm receiver, and the ledger claim keeps delivery to at most one post per block and local
 * day.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WorkoutReminderCoordinator(
    private val preferences: WorkoutReminderPreferenceRepository,
    private val schedule: WorkoutScheduleRepository,
    private val deliveries: ReminderDeliveryRepository,
    private val reminders: WorkoutReminderScheduler,
    private val timeProvider: TimeProvider,
    private val readiness: StartupReadiness,
    dispatcher: CoroutineDispatcher = Dispatchers.Default
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val lock = Mutex()

    fun start() {
        scope.launch {
            readiness.isReady.first { it }
            combine(
                preferences.observe(),
                schedule.observeScheduleState().flatMapLatest { state ->
                    val activationId = state.activeActivationId
                    if (activationId == null) {
                        flowOf(state)
                    } else {
                        schedule.observeOccurrences(activationId).map { state }
                    }
                }
            ) { _, _ -> Unit }.collect { reconcile() }
        }
    }

    /**
     * Brings the pending alarm and any due reminder in line with current state. Safe to call from the
     * app-lifetime collector or an alarm receiver; a caller never needs the observed values.
     */
    suspend fun reconcile() = lock.withLock { reconcileLocked() }

    fun close() = scope.cancel()

    private suspend fun reconcileLocked() {
        // Posting today's reminder changes today's outcome and leaves the next eligible day to arm,
        // so one follow-up pass settles everything a single pass can change.
        repeat(MAX_PASSES) {
            if (!reconcileOnce()) return
        }
    }

    /** Returns true when today's reminder was posted, so a follow-up pass should arm the next day. */
    private suspend fun reconcileOnce(): Boolean {
        val state = schedule.scheduleState()
        val activationId = state.activeActivationId
        if (activationId == null) {
            reminders.cancel()
            return false
        }
        val activation = schedule.activeActivation()
        if (activation == null || activation.id != activationId) {
            // A half-applied block change; the schedule flow reconciles again.
            return false
        }
        val occurrences = schedule.occurrences(activationId)
        val current = preferences.get()
        val nowMillis = timeProvider.nowMillis()
        val today = localEpochDay(nowMillis, timeProvider.utcOffsetMillis())
        val decision = decideWorkoutReminder(
            preferences = current,
            state = state,
            occurrences = occurrences,
            alreadyDelivered = deliveries.isDelivered(activationId, today),
            timeProvider = timeProvider
        )
        if (decision is ReminderDecision.DeliverNow &&
            deliverToday(
                activationId = activationId,
                reminderEpochDay = today,
                occurrenceIds = decision.occurrenceIds,
                occurrences = occurrences,
                activation = activation,
                nowMillis = nowMillis
            )
        ) {
            return true
        }
        // A delivery that could not post (permission, lost claim, failed notify) also lands here, so
        // the next eligible day is still armed instead of being lost with today's. The look-ahead
        // returns only instants strictly after now, so a failed post cannot re-arm an immediate alarm.
        val atEpochMillis = if (decision is ReminderDecision.Schedule) {
            decision.atEpochMillis
        } else {
            nextWorkoutReminderInstant(
                preferences = current,
                activationId = activationId,
                occurrences = occurrences,
                timeProvider = timeProvider
            )
        }
        if (atEpochMillis == null) reminders.cancel() else reminders.schedule(atEpochMillis)
        return false
    }

    /** Posts one summary for today, claiming the block and day first so nothing can post twice. */
    private suspend fun deliverToday(
        activationId: Long,
        reminderEpochDay: Long,
        occurrenceIds: List<Long>,
        occurrences: List<WorkoutOccurrence>,
        activation: TrainingActivation,
        nowMillis: Long
    ): Boolean {
        if (!reminders.canNotify()) return false
        val names = reminderWorkoutNames(occurrenceIds, occurrences, activation)
        if (!deliveries.claim(activationId, reminderEpochDay, nowMillis)) return false
        if (reminders.notify(names)) return true
        deliveries.release(activationId, reminderEpochDay)
        return false
    }

    /** The workouts one summary describes, in queue order and without a repeated name. */
    private fun reminderWorkoutNames(
        occurrenceIds: List<Long>,
        occurrences: List<WorkoutOccurrence>,
        activation: TrainingActivation
    ): List<String> {
        val described = occurrenceIds.toSet()
        return occurrences
            .filter { it.id in described }
            .sortedBy { it.queuePosition }
            .mapNotNull { occurrence ->
                activation.workouts.firstOrNull { it.id == occurrence.activationWorkoutId }?.name
            }
            .distinct()
    }

    private companion object {
        /** One pass may post today's reminder; the next arms the following eligible day. */
        const val MAX_PASSES = 2
    }
}
