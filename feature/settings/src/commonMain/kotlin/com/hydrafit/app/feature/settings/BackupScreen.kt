package com.hydrafit.app.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hydrafit.app.core.domain.backup.BackupFailure
import hydrafit.feature.settings.generated.resources.Res
import hydrafit.feature.settings.generated.resources.settings_backup_cancel
import hydrafit.feature.settings.generated.resources.settings_backup_confirm
import hydrafit.feature.settings.generated.resources.settings_backup_description
import hydrafit.feature.settings.generated.resources.settings_backup_error_duplicate_id
import hydrafit.feature.settings.generated.resources.settings_backup_error_invalid_reference
import hydrafit.feature.settings.generated.resources.settings_backup_error_invalid_value
import hydrafit.feature.settings.generated.resources.settings_backup_error_io
import hydrafit.feature.settings.generated.resources.settings_backup_error_malformed
import hydrafit.feature.settings.generated.resources.settings_backup_error_too_large
import hydrafit.feature.settings.generated.resources.settings_backup_error_unknown_catalog
import hydrafit.feature.settings.generated.resources.settings_backup_error_unsupported_version
import hydrafit.feature.settings.generated.resources.settings_backup_exported
import hydrafit.feature.settings.generated.resources.settings_backup_preview_body
import hydrafit.feature.settings.generated.resources.settings_backup_preview_title
import hydrafit.feature.settings.generated.resources.settings_backup_restored
import hydrafit.feature.settings.generated.resources.settings_backup_section
import hydrafit.feature.settings.generated.resources.settings_backup_unsupported
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun BackupRoute(modifier: Modifier = Modifier, viewModel: BackupViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(Res.string.settings_backup_section),
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            text = stringResource(Res.string.settings_backup_description),
            style = MaterialTheme.typography.bodySmall
        )
        if (state.isSupported) {
            BackupSection(
                isSupported = state.isSupported,
                busy = state.inProgress,
                onExportTo = viewModel::export,
                onImportFrom = viewModel::`import`
            )
        } else {
            Text(
                text = stringResource(Res.string.settings_backup_unsupported),
                style = MaterialTheme.typography.bodySmall
            )
        }
        state.status?.let { status ->
            val message = when (status) {
                BackupStatus.Exported -> stringResource(Res.string.settings_backup_exported)
                BackupStatus.Restored -> stringResource(Res.string.settings_backup_restored)
                is BackupStatus.Failed -> stringResource(status.failure.messageResource())
            }
            Text(text = message, style = MaterialTheme.typography.bodySmall)
        }
    }

    state.preview?.let { preview ->
        AlertDialog(
            onDismissRequest = viewModel::cancelPreview,
            title = { Text(stringResource(Res.string.settings_backup_preview_title)) },
            text = {
                Text(
                    stringResource(
                        Res.string.settings_backup_preview_body,
                        preview.totalRecords,
                        preview.workoutSets,
                        preview.customExercises
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = viewModel::confirmRestore, enabled = !state.inProgress) {
                    Text(stringResource(Res.string.settings_backup_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::cancelPreview) {
                    Text(stringResource(Res.string.settings_backup_cancel))
                }
            }
        )
    }
}

private fun BackupFailure.messageResource(): StringResource = when (this) {
    BackupFailure.MALFORMED -> Res.string.settings_backup_error_malformed
    BackupFailure.UNSUPPORTED_VERSION -> Res.string.settings_backup_error_unsupported_version
    BackupFailure.TOO_LARGE -> Res.string.settings_backup_error_too_large
    BackupFailure.UNKNOWN_CATALOG_ID -> Res.string.settings_backup_error_unknown_catalog
    BackupFailure.DUPLICATE_ID -> Res.string.settings_backup_error_duplicate_id
    BackupFailure.INVALID_REFERENCE -> Res.string.settings_backup_error_invalid_reference
    BackupFailure.INVALID_VALUE -> Res.string.settings_backup_error_invalid_value
    BackupFailure.IO -> Res.string.settings_backup_error_io
}
