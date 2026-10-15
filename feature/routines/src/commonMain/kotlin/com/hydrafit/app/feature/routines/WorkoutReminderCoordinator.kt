package com.hydrafit.app.feature.routines

import com.hydrafit.app.core.domain.schedule.ReminderDecision
import com.hydrafit.app.core.domain.schedule.ReminderDeliveryRepository
import com.hydrafit.app.core.domain.schedule.TrainingActivation
import com.hydrafit.app.core.domain.schedule.WorkoutOccurrence
import com.hydrafit.app.core.domain.schedule.WorkoutReminderDeliveryGate
import com.hydrafit.app.core.domain.schedule.WorkoutReminderScheduler
import com.hydrafit.app.core.domain.schedule.WorkoutScheduleRepository
import com.hydrafit.app.core.domain.schedule.WorkoutScheduleState
import com.hydrafit.app.core.domain.schedule.decideWorkoutReminder
import com.hydrafit.app.core.domain.schedule.nextWorkoutReminderInstant
import com.hydrafit.app.core.domain.settings.WorkoutReminderPreferenceRepository
import com.hydrafit.app.core.domain.settings.WorkoutReminderPreferences
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
 * day. A shared [WorkoutReminderDeliveryGate] additionally linearizes that post against every write
 * that could change the decision: the pass reads one gated snapshot and re-enters the gate with its
 * token before posting, so a writer that committed in between invalidates the snapshot instead of
 * letting a stale reminder post.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WorkoutReminderCoordinator(
    private val preferences: WorkoutReminderPreferenceRepository,
    private val schedule: WorkoutScheduleRepository,
    private val deliveries: ReminderDeliveryRepository,
    private val reminders: WorkoutReminderScheduler,
    private val timeProvider: TimeProvider,
    private val readiness: StartupReadiness,
    private val gate: WorkoutReminderDeliveryGate,
    dispatcher: CoroutineDispatcher = Dispatchers.Default
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val lock = Mutex()

    fun start() {
        scope.launch {
            readiness.isReady.first { it }
            // A delivery-relevant platform change (a permission result, notification access changed
            // in system settings) is invisible in the observed stores, so it reconciles on its own.
            launch { reminders.deliveryChanges.collect { reconcile() } }
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
        // A post changes today's outcome and leaves the next eligible day to arm, and a stale
        // snapshot must be re-read, so both continue to the next bounded pass.
        repeat(MAX_PASSES) {
            when (reconcileOnce()) {
                ReconcileResult.DONE -> return
                ReconcileResult.POSTED, ReconcileResult.STALE -> Unit
            }
        }
    }

    /**
     * Captures one gated snapshot of every store a decision depends on, then re-enters the gate with
     * that snapshot's token before any side effect: a writer that committed in between invalidates the
     * token, so the pass re-reads instead of acting on stale state. Returns [ReconcileResult.POSTED]
     * when today's reminder was posted, so a follow-up pass arms the next day.
     */
    private suspend fun reconcileOnce(): ReconcileResult {
        val snapshot = gate.snapshot {
            val state = schedule.scheduleState()
            val activationId = state.activeActivationId
            ReminderSnapshot(
                state = state,
                activationId = activationId,
                activation = if (activationId == null) null else schedule.activeActivation(),
                occurrences = if (activationId == null) {
                    emptyList()
                } else {
                    schedule.occurrences(activationId)
                },
                preferences = preferences.get()
            )
        }
        val activationId = snapshot.value.activationId
        if (activationId == null) {
            return gate.ifCurrent(snapshot.token) { reminders.cancel() }
                ?.let { ReconcileResult.DONE }
                ?: ReconcileResult.STALE
        }
        val activation = snapshot.value.activation
        if (activation == null || activation.id != activationId) {
            // A half-applied block change; the schedule flow reconciles again.
            return ReconcileResult.DONE
        }
        val posted = gate.ifCurrent(snapshot.token) {
            val nowMillis = timeProvider.nowMillis()
            val today = localEpochDay(nowMillis, timeProvider.utcOffsetMillis())
            val decision = decideWorkoutReminder(
                preferences = snapshot.value.preferences,
                state = snapshot.value.state,
                occurrences = snapshot.value.occurrences,
                alreadyDelivered = deliveries.isDelivered(activationId, today),
                timeProvider = timeProvider
            )
            if (decision is ReminderDecision.DeliverNow &&
                deliverToday(
                    activationId = activationId,
                    reminderEpochDay = today,
                    occurrenceIds = decision.occurrenceIds,
                    occurrences = snapshot.value.occurrences,
                    activation = activation,
                    nowMillis = nowMillis
                )
            ) {
                true
            } else {
                // A delivery that could not post (permission, lost claim, failed notify) also lands
                // here, so the next eligible day is still armed instead of being lost with today's.
                // The look-ahead returns only instants strictly after now, so a failed post cannot
                // re-arm an immediate alarm.
                val atEpochMillis = if (decision is ReminderDecision.Schedule) {
                    decision.atEpochMillis
                } else {
                    nextWorkoutReminderInstant(
                        preferences = snapshot.value.preferences,
                        activationId = activationId,
                        occurrences = snapshot.value.occurrences,
                        timeProvider = timeProvider
                    )
                }
                if (atEpochMillis != null) {
                    reminders.schedule(atEpochMillis)
                } else if (decision is ReminderDecision.AlreadyDelivered) {
                    // Already posted today with no later day to arm: drop the spent alarm but keep
                    // the posted summary. Every other no-future case still clears both.
                    reminders.cancelPendingAlarm()
                } else {
                    reminders.cancel()
                }
                false
            }
        } ?: return ReconcileResult.STALE
        return if (posted) ReconcileResult.POSTED else ReconcileResult.DONE
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

    /** A gated read of every store one reminder decision depends on. */
    private data class ReminderSnapshot(
        val state: WorkoutScheduleState,
        val activationId: Long?,
        val activation: TrainingActivation?,
        val occurrences: List<WorkoutOccurrence>,
        val preferences: WorkoutReminderPreferences
    )

    private enum class ReconcileResult { DONE, POSTED, STALE }

    private companion object {
        /** One pass may post today's reminder; the next arms the following eligible day. */
        const val MAX_PASSES = 2
    }
}
