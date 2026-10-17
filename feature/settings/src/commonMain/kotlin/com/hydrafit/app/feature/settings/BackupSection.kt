package com.hydrafit.app.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Platform document pickers for the data-transfer surface. Android offers Storage Access Framework
 * create/open launchers (backup export, history-CSV export, backup import); a platform without a
 * document surface renders no pickers (the section shows a note). The backup export picker also
 * asks whether to encrypt and collects the password before the file is created.
 */
@Composable
expect fun BackupSection(
    isSupported: Boolean,
    busy: Boolean,
    onExportTo: (handle: String, encrypted: Boolean, password: String?) -> Unit,
    onExportCsvTo: (String) -> Unit,
    onImportFrom: (String) -> Unit,
    modifier: Modifier = Modifier
)
