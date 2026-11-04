package com.hydrafit.app.feature.logger

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

internal fun loggerUsesTwoPane(
    availableWidth: Dp,
    availableHeight: Dp,
    hasActiveWorkout: Boolean,
    trainingStage: Boolean
): Boolean = availableWidth >= 840.dp &&
    availableHeight >= 480.dp &&
    hasActiveWorkout &&
    trainingStage
