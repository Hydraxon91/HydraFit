package com.hydrafit.app.feature.routines

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
import android.util.Log
import com.hydrafit.app.core.domain.schedule.WorkoutReminderScheduler
import com.hydrafit.app.core.domain.startup.StartupReadiness
import hydrafit.feature.routines.generated.resources.Res
import hydrafit.feature.routines.generated.resources.reminder_body_generic
import hydrafit.feature.routines.generated.resources.reminder_body_named
import hydrafit.feature.routines.generated.resources.reminder_channel
import hydrafit.feature.routines.generated.resources.reminder_title
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.jetbrains.compose.resources.getString
import org.koin.core.component.KoinComponent
import org.koin.core.component.get

/**
 * Android reminder delivery: one inexact local alarm plus the single summary notification.
 *
 * Reminders never request exact-alarm access, so the alarm is best-effort and may land late; the
 * policy's lateness bound decides at posting time whether it is still worth showing. Delivery is
 * serialized so a re-arm cannot clear a notification that was just posted.
 */
class AndroidWorkoutReminderScheduler(context: Context) : WorkoutReminderScheduler {
    private val context = context.applicationContext
    private val alarms = this.context.getSystemService(AlarmManager::class.java)
    private val notifications = this.context.getSystemService(NotificationManager::class.java)
    private val deliveryLock = Any()

    override fun canNotify(): Boolean = notifications.areNotificationsEnabled() &&
        (
            Build.VERSION.SDK_INT < 33 ||
                context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
            )

    override suspend fun schedule(atEpochMillis: Long) {
        synchronized(deliveryLock) {
            alarms.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                atEpochMillis,
                pendingIntent()
            )
            logWorkoutReminder(context, "scheduled", atEpochMillis)
        }
    }

    override fun cancel() {
        synchronized(deliveryLock) {
            alarms.cancel(pendingIntent())
            notifications.cancel(NOTIFICATION_ID)
        }
    }

    override suspend fun notify(workoutNames: List<String>): Boolean {
        val channelName = getString(Res.string.reminder_channel)
        val title = getString(Res.string.reminder_title)
        val body = if (workoutNames.isEmpty()) {
            getString(Res.string.reminder_body_generic)
        } else {
            getString(Res.string.reminder_body_named, workoutNames.joinToString(", "))
        }
        synchronized(deliveryLock) {
            if (!canNotify()) return false
            val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
                ?: return false
            if (Build.VERSION.SDK_INT >= 26) {
                notifications.createNotificationChannel(
                    NotificationChannel(
                        CHANNEL_ID,
                        channelName,
                        NotificationManager.IMPORTANCE_DEFAULT
                    )
                )
            }
            val builder = if (Build.VERSION.SDK_INT >= 26) {
                Notification.Builder(context, CHANNEL_ID)
            } else {
                Notification.Builder(context)
            }
            notifications.notify(
                NOTIFICATION_ID,
                builder.setSmallIcon(android.R.drawable.ic_menu_my_calendar)
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
            logWorkoutReminder(context, "posted")
            return true
        }
    }

    private fun pendingIntent(): PendingIntent = PendingIntent.getBroadcast(
        context,
        0,
        Intent(context, WorkoutReminderReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private companion object {
        const val CHANNEL_ID = "workout-reminders"
        const val NOTIFICATION_ID = 700
    }
}

/**
 * A fired reminder, a reboot and a clock or timezone change all mean the same thing: decide again from
 * current stores. The alarm carries no workout authority, so the receiver revalidates rather than
 * posting what was true when it was armed.
 */
class WorkoutReminderReceiver :
    BroadcastReceiver(),
    KoinComponent {
    override fun onReceive(context: Context, intent: Intent) {
        logWorkoutReminder(context, "received")
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                withTimeoutOrNull(8_000L) {
                    get<StartupReadiness>().isReady.first { it }
                    get<WorkoutReminderCoordinator>().reconcile()
                }
            } finally {
                pending.finish()
            }
        }
    }
}

/** Debug timing only: no workout, block or account data. */
private fun logWorkoutReminder(context: Context, stage: String, atEpochMillis: Long? = null) {
    if (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
        Log.d(
            "HydraFitWorkoutReminder",
            "$stage at=$atEpochMillis now=${System.currentTimeMillis()}"
        )
    }
}
