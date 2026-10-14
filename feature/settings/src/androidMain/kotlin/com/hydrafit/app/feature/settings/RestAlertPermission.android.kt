package com.hydrafit.app.feature.settings

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect

@Composable
actual fun rememberRestAlertPermission(
    onResult: (Boolean) -> Unit,
    onTimingRefresh: () -> Unit
): RestAlertPermission {
    val context = LocalContext.current
    val manager = context.getSystemService(NotificationManager::class.java)
    val refresh by rememberUpdatedState(onTimingRefresh)
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { refresh() }
    val exactLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        refresh()
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        onResult(it && manager.areNotificationsEnabled())
    }
    return RestAlertPermission(
        supported = true,
        request = {
            if (Build.VERSION.SDK_INT >= 33 &&
                context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                onResult(manager.areNotificationsEnabled())
            }
        },
        requestPreciseTiming = {
            if (Build.VERSION.SDK_INT >= 31 &&
                !context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
            ) {
                try {
                    exactLauncher.launch(
                        Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                            .setData(Uri.parse("package:${context.packageName}"))
                    )
                } catch (_: ActivityNotFoundException) {
                    // Keep the disclosed inexact fallback when this system page is unavailable.
                    refresh()
                }
            } else {
                refresh()
            }
        }
    )
}
