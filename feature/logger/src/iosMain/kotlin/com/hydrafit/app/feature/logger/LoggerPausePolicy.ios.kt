package com.hydrafit.app.feature.logger

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

@Composable
internal actual fun rememberConfigurationChangeCheck(): () -> Boolean = remember { { false } }
