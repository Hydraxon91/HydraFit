package com.hydrafit.app.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Platform document picker for the workout-CSV import. Android offers a Storage Access Framework
 * open launcher for a chosen CSV; a platform without a document surface renders no picker (the
 * section shows an unsupported note instead).
 */
@Composable
expect fun WorkoutImportSection(
    isSupported: Boolean,
    busy: Boolean,
    onFileChosen: (String) -> Unit,
    modifier: Modifier = Modifier
)
