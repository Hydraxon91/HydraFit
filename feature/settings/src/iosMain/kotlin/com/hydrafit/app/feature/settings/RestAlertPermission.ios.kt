package com.hydrafit.app.feature.settings

import androidx.compose.runtime.Composable

@Composable
actual fun rememberRestAlertPermission(onResult: (Boolean) -> Unit): RestAlertPermission =
    RestAlertPermission(supported = false) { onResult(false) }
