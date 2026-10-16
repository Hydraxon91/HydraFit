package com.hydrafit.app.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

// iOS has no document surface for workout-CSV import, so WorkoutImportRoute shows a note instead.
@Composable
actual fun WorkoutImportSection(
    isSupported: Boolean,
    busy: Boolean,
    onFileChosen: (String) -> Unit,
    modifier: Modifier
) = Unit
