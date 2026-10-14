package com.hydrafit.app.core.domain.workout

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

enum class RestAlertTiming { PRECISE, INEXACT, UNSUPPORTED }

/** One local, best-effort alert. Implementations replace the previous alarm and notification. */
interface RestAlertScheduler {
    val timing: Flow<RestAlertTiming> get() = flowOf(RestAlertTiming.INEXACT)

    /** Rechecks platform access without changing the persisted countdown or alert preference. */
    fun refreshTiming() = Unit

    suspend fun schedule(countdown: PersistedRestCountdown)

    fun cancel()
}

class UnsupportedRestAlertScheduler : RestAlertScheduler {
    override val timing: Flow<RestAlertTiming> = flowOf(RestAlertTiming.UNSUPPORTED)

    override suspend fun schedule(countdown: PersistedRestCountdown) = Unit

    override fun cancel() = Unit
}

/** An alarm remains eligible only for the exact countdown and still-open workout context. */
fun isRestAlertEligible(
    stored: PersistedRestCountdown?,
    alarm: PersistedRestCountdown,
    bootIdentity: String?,
    elapsedNowMillis: Long,
    enabled: Boolean,
    guidedEnabled: Boolean,
    openSessionId: String?,
    activeOccurrenceId: Long?
): Boolean = enabled &&
    guidedEnabled &&
    stored == alarm &&
    bootIdentity == alarm.bootIdentity &&
    openSessionId == alarm.sessionId &&
    activeOccurrenceId == alarm.occurrenceId &&
    elapsedNowMillis >= alarm.deadlineElapsedMillis
