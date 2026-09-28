package com.hydrafit.app.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
expect fun OnDeviceModelSection(
    installed: Boolean,
    onModelChanged: () -> Unit,
    modifier: Modifier = Modifier
)
