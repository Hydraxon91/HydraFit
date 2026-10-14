package com.hydrafit.app.feature.settings

import androidx.compose.runtime.Composable

@Composable
actual fun rememberWorkoutReminderPermission(
    onResult: (Boolean) -> Unit,
    onPlatformRefresh: () -> Unit
): WorkoutReminderPermission = WorkoutReminderPermission(
    supported = false,
    request = { onResult(false) }
)
