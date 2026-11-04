package com.hydrafit.app.feature.logger

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

@Composable
internal actual fun rememberConfigurationChangeCheck(): () -> Boolean {
    val context = LocalContext.current
    return remember(context) {
        { context.loggerActivity()?.isChangingConfigurations == true }
    }
}

/** A preview may have no Activity; it must not gain a configuration-change exemption. */
private fun Context.loggerActivity(): Activity? {
    var context = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    return context as? Activity
}
