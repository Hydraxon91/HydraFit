package com.hydrafit.app.feature.logger

import com.hydrafit.app.core.domain.workout.PersistedRestCountdown

/**
 * Identity of one displayed countdown notification. It travels in the notification extras and the
 * cleanup alarm, never in persistence, so a delivered cleanup can be matched against what is on
 * screen. It carries no completion authority and never mutates the stored timer.
 */
data class RestCountdownNotificationIdentity(
    val deadlineElapsedMillis: Long,
    val bootIdentity: String,
    val sessionId: String,
    val occurrenceId: Long,
    val exerciseId: String
)

fun PersistedRestCountdown.notificationIdentity(): RestCountdownNotificationIdentity =
    RestCountdownNotificationIdentity(
        deadlineElapsedMillis = deadlineElapsedMillis,
        bootIdentity = bootIdentity,
        sessionId = sessionId,
        occurrenceId = occurrenceId,
        exerciseId = exerciseId
    )

/**
 * A cleanup delivery may remove only the notification it was created for: the same identity must
 * still be displayed, the boot must be unchanged and the deadline must have passed. A stale or
 * replaced countdown therefore cannot be removed, and this decision neither posts a completion
 * signal nor touches the stored countdown.
 */
fun isCountdownCleanupEligible(
    expected: RestCountdownNotificationIdentity?,
    displayed: RestCountdownNotificationIdentity?,
    currentBootIdentity: String?,
    elapsedNowMillis: Long
): Boolean = expected != null &&
    displayed == expected &&
    currentBootIdentity == expected.bootIdentity &&
    elapsedNowMillis >= expected.deadlineElapsedMillis
