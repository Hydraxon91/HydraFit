package com.hydrafit.app

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

internal fun usesNavigationRail(availableWidth: Dp): Boolean = availableWidth >= 600.dp
