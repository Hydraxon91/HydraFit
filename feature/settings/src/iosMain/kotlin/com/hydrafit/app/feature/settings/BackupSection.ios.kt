package com.hydrafit.app.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

// iOS has no document surface for the data-transfer flow, so BackupRoute shows a note instead.
@Composable
actual fun BackupSection(
    isSupported: Boolean,
    busy: Boolean,
    onExportTo: (String) -> Unit,
    onExportCsvTo: (String) -> Unit,
    onImportFrom: (String) -> Unit,
    modifier: Modifier
) = Unit
