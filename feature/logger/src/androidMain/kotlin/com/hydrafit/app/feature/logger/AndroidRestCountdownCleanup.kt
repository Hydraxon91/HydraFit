package com.hydrafit.app.feature.logger

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import com.hydrafit.app.core.domain.time.BootIdentityProvider
import org.koin.core.component.KoinComponent
import org.koin.core.component.get

internal const val COUNTDOWN_NOTIFICATION_ID = 601
internal const val COUNTDOWN_CLEANUP_ACTION =
    "com.hydrafit.app.feature.logger.action.REST_COUNTDOWN_CLEANUP"

/** Distinct from the completion alarm's request code so the two schedules never share a slot. */
private const val COUNTDOWN_CLEANUP_REQUEST_CODE = 1
private const val EXTRA_DEADLINE = "restCountdownDeadline"
private const val EXTRA_BOOT = "restCountdownBoot"
private const val EXTRA_SESSION = "restCountdownSession"
private const val EXTRA_OCCURRENCE = "restCountdownOccurrence"
private const val EXTRA_EXERCISE = "restCountdownExercise"

internal fun countdownIdentityExtras(identity: RestCountdownNotificationIdentity): Bundle =
    Bundle().apply {
        putLong(EXTRA_DEADLINE, identity.deadlineElapsedMillis)
        putString(EXTRA_BOOT, identity.bootIdentity)
        putString(EXTRA_SESSION, identity.sessionId)
        putLong(EXTRA_OCCURRENCE, identity.occurrenceId)
        putString(EXTRA_EXERCISE, identity.exerciseId)
    }

private fun Bundle.countdownIdentity(): RestCountdownNotificationIdentity? {
    if (!containsKey(EXTRA_DEADLINE)) return null
    val boot = getString(EXTRA_BOOT) ?: return null
    val session = getString(EXTRA_SESSION) ?: return null
    val exercise = getString(EXTRA_EXERCISE) ?: return null
    val deadline = getLong(EXTRA_DEADLINE, -1L)
    val occurrence = getLong(EXTRA_OCCURRENCE, -1L)
    if (deadline < 0L || boot.isBlank() || session.isBlank() || occurrence <= 0L) return null
    if (exercise.isBlank()) return null
    return RestCountdownNotificationIdentity(deadline, boot, session, occurrence, exercise)
}

private fun Intent.countdownIdentity(): RestCountdownNotificationIdentity? =
    extras?.countdownIdentity()

/**
 * Best-effort API 24/25 cleanup for the silent countdown notification. API 26+ relies on
 * `Notification.Builder.setTimeoutAfter` instead, so the alarm stays off those devices.
 */
internal fun scheduleCountdownCleanup(
    alarms: AlarmManager,
    context: Context,
    identity: RestCountdownNotificationIdentity
) {
    val intent = Intent(context, RestCountdownCleanupReceiver::class.java)
        .setAction(COUNTDOWN_CLEANUP_ACTION)
        .putExtras(countdownIdentityExtras(identity))
    alarms.setAndAllowWhileIdle(
        AlarmManager.ELAPSED_REALTIME_WAKEUP,
        identity.deadlineElapsedMillis,
        PendingIntent.getBroadcast(
            context,
            COUNTDOWN_CLEANUP_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    )
}

/**
 * Drops a pending cleanup without creating one. Extras do not participate in PendingIntent
 * matching, so the identity-less intent still targets the same slot.
 */
internal fun cancelCountdownCleanup(alarms: AlarmManager, context: Context) {
    val intent = Intent(context, RestCountdownCleanupReceiver::class.java)
        .setAction(COUNTDOWN_CLEANUP_ACTION)
    val existing = PendingIntent.getBroadcast(
        context,
        COUNTDOWN_CLEANUP_REQUEST_CODE,
        intent,
        PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
    )
    if (existing != null) alarms.cancel(existing)
}

/**
 * Serializes countdown publication and removal with cleanup deliveries. It stores no Android or
 * in-memory identity: the displayed identity is supplied to [cleanup] inside the critical section,
 * so a process started after death still matches the notification the OS kept visible. Lives in
 * androidMain only for the JVM monitor, which keeps the boundary directly host-testable.
 */
internal class RestCountdownNotificationState {
    private val lock = Any()

    /** Posts or clears a countdown while holding the publication lock. */
    fun withPublicationLock(block: () -> Unit) {
        synchronized(lock) { block() }
    }

    /**
     * Validates [expected] against the currently displayed countdown and removes it, all inside the
     * publication lock. A concurrent post or clear cannot commit between the read and the removal,
     * so a stale delivery never removes a newer countdown.
     */
    fun cleanup(
        expected: RestCountdownNotificationIdentity,
        currentBootIdentity: String?,
        elapsedNowMillis: Long,
        displayed: () -> RestCountdownNotificationIdentity?,
        remove: () -> Unit
    ) {
        synchronized(lock) {
            if (!isCountdownCleanupEligible(
                    expected = expected,
                    displayed = displayed(),
                    currentBootIdentity = currentBootIdentity,
                    elapsedNowMillis = elapsedNowMillis
                )
            ) {
                return
            }
            remove()
        }
    }
}

/** Reads the countdown identity the OS currently displays, or null when none is posted. */
internal fun displayedCountdownIdentity(
    notifications: NotificationManager
): RestCountdownNotificationIdentity? = notifications.activeNotifications
    .firstOrNull { it.id == COUNTDOWN_NOTIFICATION_ID }
    ?.notification
    ?.extras
    ?.countdownIdentity()

/**
 * Hands cleanup to the singleton scheduler so its publication lock covers the displayed-identity
 * read, the match and the removal. It never posts a rest-complete signal and never reads or writes
 * the stored timer, so a stale or replaced delivery is a no-op.
 */
class RestCountdownCleanupReceiver :
    BroadcastReceiver(),
    KoinComponent {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != COUNTDOWN_CLEANUP_ACTION) return
        val expected = intent.countdownIdentity() ?: return
        get<AndroidRestAlertScheduler>().cleanupCountdown(
            expected = expected,
            currentBootIdentity = get<BootIdentityProvider>().currentBootIdentity(),
            elapsedNowMillis = SystemClock.elapsedRealtime()
        )
    }
}
