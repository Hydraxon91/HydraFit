package com.hydrafit.app.feature.logger

import android.Manifest
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import android.util.Log
import com.hydrafit.app.core.domain.schedule.WorkoutScheduleRepository
import com.hydrafit.app.core.domain.settings.TrainingGuidancePreferenceRepository
import com.hydrafit.app.core.domain.startup.StartupReadiness
import com.hydrafit.app.core.domain.time.BootIdentityProvider
import com.hydrafit.app.core.domain.workout.PersistedRestCountdown
import com.hydrafit.app.core.domain.workout.RestAlertScheduler
import com.hydrafit.app.core.domain.workout.RestAlertTiming
import com.hydrafit.app.core.domain.workout.RestCountdownRepository
import com.hydrafit.app.core.domain.workout.WorkoutSessionRepository
import com.hydrafit.app.core.domain.workout.isRestAlertEligible
import com.hydrafit.app.core.userdata.settings.GuidedWorkoutPreferenceRepository
import hydrafit.feature.logger.generated.resources.Res
import hydrafit.feature.logger.generated.resources.logger_rest_alert_body
import hydrafit.feature.logger.generated.resources.logger_rest_alert_channel
import hydrafit.feature.logger.generated.resources.logger_rest_alert_title
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.jetbrains.compose.resources.getString
import org.koin.core.component.KoinComponent
import org.koin.core.component.get

class AndroidRestAlertScheduler(context: Context) : RestAlertScheduler {
    private val context = context.applicationContext
    private val alarms = this.context.getSystemService(AlarmManager::class.java)
    private val notifications = this.context.getSystemService(NotificationManager::class.java)
    private val deliveryLock = Any()
    private var deliveryRevision = 0L
    private val _timing = MutableStateFlow(currentTiming())
    override val timing = _timing.asStateFlow()

    private fun currentTiming(): RestAlertTiming = if (
        Build.VERSION.SDK_INT < 31 || alarms.canScheduleExactAlarms()
    ) {
        RestAlertTiming.PRECISE
    } else {
        RestAlertTiming.INEXACT
    }

    override fun refreshTiming() {
        synchronized(deliveryLock) {
            _timing.value = currentTiming()
        }
    }

    fun currentDeliveryRevision(): Long = synchronized(deliveryLock) { deliveryRevision }

    override suspend fun schedule(countdown: PersistedRestCountdown) {
        synchronized(deliveryLock) {
            cancel()
            if (!canNotify()) return
            val operation = pendingIntent(
                Intent(context, RestAlertReceiver::class.java)
                    .putExtra("deadline", countdown.deadlineElapsedMillis)
                    .putExtra("duration", countdown.durationMillis)
                    .putExtra("boot", countdown.bootIdentity)
                    .putExtra("session", countdown.sessionId)
                    .putExtra("occurrence", countdown.occurrenceId)
                    .putExtra("exercise", countdown.exerciseId)
            )
            val precise = scheduleRestAlarm(
                exactAllowed = currentTiming() == RestAlertTiming.PRECISE,
                scheduleExact = {
                    alarms.setExactAndAllowWhileIdle(
                        AlarmManager.ELAPSED_REALTIME_WAKEUP,
                        countdown.deadlineElapsedMillis,
                        operation
                    )
                },
                scheduleInexact = {
                    alarms.setAndAllowWhileIdle(
                        AlarmManager.ELAPSED_REALTIME_WAKEUP,
                        countdown.deadlineElapsedMillis,
                        operation
                    )
                }
            )
            _timing.value = if (precise) RestAlertTiming.PRECISE else RestAlertTiming.INEXACT
            logRestAlertTiming(context, "scheduled", countdown.deadlineElapsedMillis, precise)
        }
    }

    override fun cancel() {
        synchronized(deliveryLock) {
            deliveryRevision++
            alarms.cancel(pendingIntent(Intent(context, RestAlertReceiver::class.java)))
            notifications.cancel(NOTIFICATION_ID)
        }
    }

    suspend fun notifyRestEnded(expectedRevision: Long) {
        if (!canNotify()) return
        val channelName = getString(Res.string.logger_rest_alert_channel)
        val title = getString(Res.string.logger_rest_alert_title)
        val body = getString(Res.string.logger_rest_alert_body)
        synchronized(deliveryLock) {
            if (expectedRevision != deliveryRevision || !canNotify()) return
            if (Build.VERSION.SDK_INT >= 26) {
                notifications.createNotificationChannel(
                    NotificationChannel(
                        CHANNEL_ID,
                        channelName,
                        NotificationManager.IMPORTANCE_DEFAULT
                    )
                )
            }
            val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
                ?: return
            val builder = if (Build.VERSION.SDK_INT >= 26) {
                Notification.Builder(context, CHANNEL_ID)
            } else {
                Notification.Builder(context)
            }
            notifications.notify(
                NOTIFICATION_ID,
                builder.setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                    .setContentTitle(title)
                    .setContentText(body)
                    .setContentIntent(
                        PendingIntent.getActivity(
                            context,
                            0,
                            launch,
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                        )
                    )
                    .setAutoCancel(true)
                    .setOnlyAlertOnce(true)
                    .build()
            )
            logRestAlertTiming(context, "posted")
        }
    }

    private fun pendingIntent(intent: Intent): PendingIntent = PendingIntent.getBroadcast(
        context,
        0,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun canNotify(): Boolean = notifications.areNotificationsEnabled() &&
        (
            Build.VERSION.SDK_INT < 33 ||
                context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
            )

    private companion object {
        const val CHANNEL_ID = "rest-prompts"
        const val NOTIFICATION_ID = 600
    }
}

/** Access can be revoked between the capability check and the exact scheduling call. */
internal fun scheduleRestAlarm(
    exactAllowed: Boolean,
    scheduleExact: () -> Unit,
    scheduleInexact: () -> Unit
): Boolean {
    if (exactAllowed) {
        try {
            scheduleExact()
            return true
        } catch (_: SecurityException) {
            // The user can keep best-effort alerts without granting exact-alarm access.
        }
    }
    scheduleInexact()
    return false
}

private fun logRestAlertTiming(
    context: Context,
    stage: String,
    deadline: Long? = null,
    precise: Boolean? = null
) {
    if (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
        Log.d(
            "HydraFitRestAlert",
            "$stage elapsed=${SystemClock.elapsedRealtime()} deadline=$deadline precise=$precise"
        )
    }
}

/** The receiver reads current stores rather than trusting alarm extras as workout authority. */
class RestAlertReceiver :
    BroadcastReceiver(),
    KoinComponent {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED) {
            get<AndroidRestAlertScheduler>().refreshTiming()
            return
        }
        val deadline = intent.getLongExtra("deadline", -1L)
        logRestAlertTiming(context, "received", deadline)
        val duration = intent.getLongExtra("duration", -1L)
        val boot = intent.getStringExtra("boot") ?: return
        val session = intent.getStringExtra("session") ?: return
        val occurrenceId = intent.getLongExtra("occurrence", -1L)
        val exercise = intent.getStringExtra("exercise") ?: return
        if (deadline < 0L ||
            duration !in 1L..86_400_000L ||
            boot.isBlank() ||
            session.isBlank() ||
            occurrenceId <= 0L ||
            exercise.isBlank()
        ) {
            return
        }
        val alarm = PersistedRestCountdown(
            deadline,
            duration,
            boot,
            session,
            occurrenceId,
            exercise
        )
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                withTimeoutOrNull(8_000L) {
                    get<StartupReadiness>().isReady.first { it }
                    val scheduler = get<AndroidRestAlertScheduler>()
                    val deliveryRevision = scheduler.currentDeliveryRevision()
                    val state = get<WorkoutScheduleRepository>().scheduleState()
                    val occurrence = get<WorkoutScheduleRepository>().getOccurrence(occurrenceId)
                    val validOccurrence = occurrence?.takeIf {
                        !it.isResolved &&
                            it.activationId == state.activeActivationId &&
                            (
                                state.selectedOccurrenceId == null ||
                                    state.selectedOccurrenceId == it.id
                                ) &&
                            it.entries.any { entry -> entry.exerciseId == exercise }
                    }
                    val preferences = get<TrainingGuidancePreferenceRepository>().get()
                    val guided = get<GuidedWorkoutPreferenceRepository>().isGuidedWorkoutEnabled()
                    if (isRestAlertEligible(
                            stored = get<RestCountdownRepository>().load(),
                            alarm = alarm,
                            bootIdentity = get<BootIdentityProvider>().currentBootIdentity(),
                            elapsedNowMillis = elapsedRealtimeMillis(),
                            enabled = preferences.restAlertsEnabled,
                            guidedEnabled = guided,
                            openSessionId = get<WorkoutSessionRepository>().open()?.id,
                            activeOccurrenceId = validOccurrence?.id
                        )
                    ) {
                        scheduler.notifyRestEnded(deliveryRevision)
                    }
                }
            } finally {
                pending.finish()
            }
        }
    }
}
