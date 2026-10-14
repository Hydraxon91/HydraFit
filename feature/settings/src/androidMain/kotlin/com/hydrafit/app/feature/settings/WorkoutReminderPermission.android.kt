package com.hydrafit.app.feature.settings

import android.Manifest
import android.app.NotificationManager
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect

@Composable
actual fun rememberWorkoutReminderPermission(
    onResult: (Boolean) -> Unit,
    onPlatformRefresh: () -> Unit
): WorkoutReminderPermission {
    val context = LocalContext.current
    val manager = context.getSystemService(NotificationManager::class.java)
    val refresh by rememberUpdatedState(onPlatformRefresh)
    // Notification access also changes in system settings, outside this app's own request.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { refresh() }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        onResult(it && manager.areNotificationsEnabled())
    }
    return WorkoutReminderPermission(
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
        }
    )
}
