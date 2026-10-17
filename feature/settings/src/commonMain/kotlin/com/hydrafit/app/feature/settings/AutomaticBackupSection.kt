package com.hydrafit.app.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import hydrafit.feature.settings.generated.resources.Res
import hydrafit.feature.settings.generated.resources.settings_automatic_backup_description
import hydrafit.feature.settings.generated.resources.settings_automatic_backup_provider_disclosure
import hydrafit.feature.settings.generated.resources.settings_automatic_backup_section
import hydrafit.feature.settings.generated.resources.settings_automatic_backup_unsupported
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * Platform controls for automatic backups: the folder picker, the enable switch, an explicit
 * "Back up now" and the resulting status. Android offers a persistable folder picker; a platform
 * with no folder surface renders nothing, and [AutomaticBackupRoute] shows a note instead.
 */
@Composable
expect fun AutomaticBackupSection(
    state: AutomaticBackupUiState,
    onChooseFolder: (String) -> Unit,
    onToggleEnabled: (Boolean) -> Unit,
    onRunNow: () -> Unit,
    modifier: Modifier = Modifier
)

/**
 * The Settings surface for automatic rotating local backups. It explains the folder contract before
 * the controls, discloses that the chosen provider may sync the folder, and shows an unavailable
 * note instead of controls on a platform with no folder surface.
 */
@Composable
fun AutomaticBackupRoute(
    modifier: Modifier = Modifier,
    viewModel: AutomaticBackupSettingsViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(Res.string.settings_automatic_backup_section),
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            text = stringResource(Res.string.settings_automatic_backup_description),
            style = MaterialTheme.typography.bodySmall
        )
        if (state.isSupported) {
            Text(
                text = stringResource(Res.string.settings_automatic_backup_provider_disclosure),
                style = MaterialTheme.typography.bodySmall
            )
            AutomaticBackupSection(
                state = state,
                onChooseFolder = viewModel::chooseFolder,
                onToggleEnabled = { enabled -> viewModel.setEnabled(enabled) },
                onRunNow = viewModel::runNow
            )
        } else {
            Text(
                text = stringResource(Res.string.settings_automatic_backup_unsupported),
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
