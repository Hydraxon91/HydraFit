package com.hydrafit.app.feature.logger

import androidx.compose.runtime.Composable

/** Read at the pause event, not at composition, because relaunch starts after composition. */
@Composable
internal expect fun rememberConfigurationChangeCheck(): () -> Boolean

internal fun handleLoggerPause(configurationChange: () -> Boolean, onPause: () -> Unit) {
    if (!configurationChange()) onPause()
}
