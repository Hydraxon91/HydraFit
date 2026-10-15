package com.hydrafit.app.feature.logger

import com.hydrafit.app.core.domain.schedule.WorkoutScheduleRepository
import com.hydrafit.app.core.domain.settings.TrainingGuidancePreferenceRepository
import com.hydrafit.app.core.domain.startup.StartupReadiness
import com.hydrafit.app.core.domain.time.BootIdentityProvider
import com.hydrafit.app.core.domain.workout.PersistedRestCountdown
import com.hydrafit.app.core.domain.workout.RestAlertScheduler
import com.hydrafit.app.core.domain.workout.RestAlertTiming
import com.hydrafit.app.core.domain.workout.RestCountdownRepository
import com.hydrafit.app.core.domain.workout.WorkoutSessionRepository
import com.hydrafit.app.core.userdata.settings.GuidedWorkoutPreferenceRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** App-lifetime alarm reconciliation, independent of whether a Logger ViewModel is alive. */
@OptIn(ExperimentalCoroutinesApi::class)
class RestAlertCoordinator(
    private val countdowns: RestCountdownRepository,
    private val preferences: TrainingGuidancePreferenceRepository,
    private val guided: GuidedWorkoutPreferenceRepository,
    private val sessions: WorkoutSessionRepository,
    private val schedule: WorkoutScheduleRepository,
    private val boot: BootIdentityProvider,
    private val readiness: StartupReadiness,
    private val alerts: RestAlertScheduler,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val elapsedNow: () -> Long = ::elapsedRealtimeMillis,
    private val completion: RestCompletionPresentation = RestCompletionPresentation(elapsedNow)
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private var countdownPresentation: Job? = null
    private var watchedCountdown: PersistedRestCountdown? = null

    fun start() {
        scope.launch {
            readiness.isReady.first { it }
            var scheduled: PersistedRestCountdown? = null
            var scheduledTiming: RestAlertTiming? = null
            var firstSnapshot = true
            combine(
                countdowns.observe(),
                preferences.observe(),
                guided.guidedWorkoutFlow(),
                sessions.openFlow(),
                schedule.observeScheduleState().flatMapLatest { state ->
                    val activationId = state.activeActivationId
                    if (activationId == null) {
                        flowOf(state to emptyList())
                    } else {
                        schedule.observeOccurrences(activationId).map { state to it }
                    }
                }
            ) { countdown, preference, guidedEnabled, session, context ->
                val state = context.first
                val occurrence = context.second.firstOrNull { it.id == countdown?.occurrenceId }
                val validContext = countdown?.let { value ->
                    guidedEnabled &&
                        value.bootIdentity == boot.currentBootIdentity() &&
                        value.sessionId == session?.id &&
                        occurrence != null &&
                        !occurrence.isResolved &&
                        occurrence.activationId == state.activeActivationId &&
                        (
                            state.selectedOccurrenceId == null ||
                                state.selectedOccurrenceId == occurrence.id
                            ) &&
                        occurrence.entries.any { entry -> entry.exerciseId == value.exerciseId }
                } ?: false
                RestPresentationSnapshot(
                    countdown,
                    validContext && preference.restAlertsEnabled,
                    validContext && preference.restCountdownEnabled,
                    validContext && preference.restCompletionSignalEnabled
                )
            }.combine(alerts.timing) { countdown, timing ->
                countdown to timing
            }.collect { countdown ->
                val value = countdown.first.countdown
                val isEligible = countdown.first.alertEligible
                val timing = countdown.second
                completion.update(value, countdown.first.foregroundEnabled)
                val visibleCountdown = value?.takeIf {
                    countdown.first.countdownVisible && it.deadlineElapsedMillis > elapsedNow()
                }
                // Alarm cancellation below must not affect this independent presentation.
                alerts.showCountdown(visibleCountdown)
                val live = value?.takeIf {
                    countdown.first.countdownVisible || countdown.first.foregroundEnabled
                }
                // One deadline job per live countdown identity. An unrelated preference/timing/context
                // emission keeps it, including when the clock has reached the deadline but the expiry
                // work has not run yet, so a foreground completion is not dropped.
                val retainsWatchedJob = live != null &&
                    live == watchedCountdown &&
                    countdownPresentation?.isActive == true
                if (!retainsWatchedJob) {
                    countdownPresentation?.cancel()
                    if (live != null && live.deadlineElapsedMillis > elapsedNow()) {
                        watchedCountdown = live
                        countdownPresentation = scope.launch {
                            delay((live.deadlineElapsedMillis - elapsedNow()).coerceAtLeast(0L))
                            alerts.showCountdown(null)
                            // Re-read after the delay; observed context is not authority for delivery.
                            if (countdowns.load() == live &&
                                preferences.get().restCompletionSignalEnabled &&
                                guided.isGuidedWorkoutEnabled() &&
                                sessions.open()?.id == live.sessionId &&
                                boot.currentBootIdentity() == live.bootIdentity
                            ) {
                                val state = schedule.scheduleState()
                                val occurrence = schedule.getOccurrence(live.occurrenceId)
                                if (occurrence != null &&
                                    !occurrence.isResolved &&
                                    occurrence.activationId == state.activeActivationId &&
                                    (
                                        state.selectedOccurrenceId == null ||
                                            state.selectedOccurrenceId == occurrence.id
                                        ) &&
                                    occurrence.entries.any { it.exerciseId == live.exerciseId } &&
                                    countdowns.load() == live
                                ) {
                                    completion.complete(live, foregroundEnabled = true)
                                }
                            }
                        }
                    } else {
                        watchedCountdown = null
                        countdownPresentation = null
                    }
                }
                val isInitialEligibleSnapshot = firstSnapshot && isEligible
                firstSnapshot = false
                if (value == null || !isEligible) {
                    alerts.cancel()
                    scheduled = null
                    scheduledTiming = null
                } else if (value != scheduled || timing != scheduledTiming) {
                    if (value.deadlineElapsedMillis <= elapsedNow()) {
                        // Startup can race an alarm receiver that has already started this
                        // process. Preserve only an initially observed due countdown for delivery.
                        if (isInitialEligibleSnapshot || value == scheduled) {
                            scheduled = value
                        } else {
                            alerts.cancel()
                            scheduled = null
                        }
                    } else {
                        alerts.schedule(value)
                        scheduled = value
                    }
                    scheduledTiming = timing
                }
            }
        }
    }

    fun close() = scope.cancel()
}

private data class RestPresentationSnapshot(
    val countdown: PersistedRestCountdown?,
    val alertEligible: Boolean,
    val countdownVisible: Boolean,
    val foregroundEnabled: Boolean
)
