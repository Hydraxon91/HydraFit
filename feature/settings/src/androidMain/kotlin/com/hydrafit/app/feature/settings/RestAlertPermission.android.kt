package com.hydrafit.app.feature.settings

import android.Manifest
import android.app.NotificationManager
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

@Composable
actual fun rememberRestAlertPermission(onResult: (Boolean) -> Unit): RestAlertPermission {
    val context = LocalContext.current
    val manager = context.getSystemService(NotificationManager::class.java)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        onResult(it && manager.areNotificationsEnabled())
    }
    return RestAlertPermission(supported = true) {
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            onResult(manager.areNotificationsEnabled())
        }
    }
}
