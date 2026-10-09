package com.hydrafit.app.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

// iOS has no backup document surface in 0.5.0, so BackupRoute shows a note instead of these pickers.
@Composable
actual fun BackupSection(
    isSupported: Boolean,
    busy: Boolean,
    onExportTo: (String) -> Unit,
    onImportFrom: (String) -> Unit,
    modifier: Modifier
) = Unit
