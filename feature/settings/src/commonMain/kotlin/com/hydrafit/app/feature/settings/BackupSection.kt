package com.hydrafit.app.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Platform document pickers for the data-transfer surface. Android offers Storage Access Framework
 * create/open launchers (backup export, history-CSV export, backup import); a platform without a
 * document surface renders no pickers (the section shows a note).
 */
@Composable
expect fun BackupSection(
    isSupported: Boolean,
    busy: Boolean,
    onExportTo: (String) -> Unit,
    onExportCsvTo: (String) -> Unit,
    onImportFrom: (String) -> Unit,
    modifier: Modifier = Modifier
)
