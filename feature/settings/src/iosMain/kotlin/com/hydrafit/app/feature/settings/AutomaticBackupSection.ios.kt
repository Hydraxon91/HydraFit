package com.hydrafit.app.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

// iOS has no folder surface for automatic backups, so AutomaticBackupRoute shows a note instead.
@Composable
actual fun AutomaticBackupSection(
    state: AutomaticBackupUiState,
    onChooseFolder: (String) -> Unit,
    onToggleEnabled: (Boolean) -> Unit,
    onRunNow: () -> Unit,
    modifier: Modifier
) = Unit
