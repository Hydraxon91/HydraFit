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
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
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
    private val elapsedNow: () -> Long = ::elapsedRealtimeMillis
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)

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
                val isEligible = countdown?.let { value ->
                    preference.restAlertsEnabled &&
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
                countdown to isEligible
            }.combine(alerts.timing) { countdown, timing ->
                Triple(countdown.first, countdown.second, timing)
            }.collect { countdown ->
                val value = countdown.first
                val isEligible = countdown.second
                val timing = countdown.third
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
