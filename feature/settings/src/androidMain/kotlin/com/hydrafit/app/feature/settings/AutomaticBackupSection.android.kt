package com.hydrafit.app.feature.settings

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.hydrafit.app.core.domain.backup.AutomaticBackupFailureKind
import com.hydrafit.app.core.domain.backup.FolderUnavailableReason
import hydrafit.feature.settings.generated.resources.Res
import hydrafit.feature.settings.generated.resources.settings_automatic_backup_choose_folder
import hydrafit.feature.settings.generated.resources.settings_automatic_backup_enable
import hydrafit.feature.settings.generated.resources.settings_automatic_backup_failed_export_failed
import hydrafit.feature.settings.generated.resources.settings_automatic_backup_failed_folder_not_configured
import hydrafit.feature.settings.generated.resources.settings_automatic_backup_failed_folder_unavailable
import hydrafit.feature.settings.generated.resources.settings_automatic_backup_failed_folder_unsupported
import hydrafit.feature.settings.generated.resources.settings_automatic_backup_failed_too_large
import hydrafit.feature.settings.generated.resources.settings_automatic_backup_failed_unknown
import hydrafit.feature.settings.generated.resources.settings_automatic_backup_failed_write_failed
import hydrafit.feature.settings.generated.resources.settings_automatic_backup_failed_write_unverified
import hydrafit.feature.settings.generated.resources.settings_automatic_backup_folder_chosen
import hydrafit.feature.settings.generated.resources.settings_automatic_backup_run_now
import hydrafit.feature.settings.generated.resources.settings_automatic_backup_status_disabled
import hydrafit.feature.settings.generated.resources.settings_automatic_backup_status_enabled
import hydrafit.feature.settings.generated.resources.settings_automatic_backup_status_folder_missing
import hydrafit.feature.settings.generated.resources.settings_automatic_backup_status_folder_not_configured
import hydrafit.feature.settings.generated.resources.settings_automatic_backup_status_permission_lost
import hydrafit.feature.settings.generated.resources.settings_automatic_backup_status_run_succeeded
import hydrafit.feature.settings.generated.resources.settings_automatic_backup_status_unsupported_provider
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

@Composable
actual fun AutomaticBackupSection(
    state: AutomaticBackupUiState,
    onChooseFolder: (String) -> Unit,
    onToggleEnabled: (Boolean) -> Unit,
    onRunNow: () -> Unit,
    modifier: Modifier
) {
    val context = LocalContext.current
    val folderPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            // Persist the grant first, then hand the tree to configuration, which validates the real
            // capabilities. A refused persistable grant is surfaced as FolderUnavailable, so the
            // folder is reported either way instead of failing silently.
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }
            onChooseFolder(uri.toString())
        }
    }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = { folderPicker.launch(null) },
                enabled = !state.busy
            ) {
                Text(stringResource(Res.string.settings_automatic_backup_choose_folder))
            }
            if (state.folderConfigured) {
                Text(
                    text = stringResource(Res.string.settings_automatic_backup_folder_chosen),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().toggleable(
                value = state.enabled,
                enabled = !state.busy,
                role = Role.Switch,
                onValueChange = onToggleEnabled
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Switch(checked = state.enabled, onCheckedChange = null, enabled = !state.busy)
            Text(stringResource(Res.string.settings_automatic_backup_enable))
        }
        Button(
            onClick = onRunNow,
            enabled = state.folderConfigured && state.enabled && !state.busy
        ) {
            Text(stringResource(Res.string.settings_automatic_backup_run_now))
        }
        state.status?.let { status ->
            Text(
                text = stringResource(status.messageResource()),
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

private fun AutomaticBackupStatus.messageResource(): StringResource = when (this) {
    AutomaticBackupStatus.Enabled -> Res.string.settings_automatic_backup_status_enabled
    AutomaticBackupStatus.Disabled -> Res.string.settings_automatic_backup_status_disabled
    AutomaticBackupStatus.FolderNotConfigured ->
        Res.string.settings_automatic_backup_status_folder_not_configured

    AutomaticBackupStatus.RunSucceeded -> Res.string.settings_automatic_backup_status_run_succeeded
    is AutomaticBackupStatus.FolderUnavailable -> reason.messageResource()
    is AutomaticBackupStatus.RunFailed -> kind.messageResource()
}

private fun FolderUnavailableReason.messageResource(): StringResource = when (this) {
    FolderUnavailableReason.PERMISSION_LOST ->
        Res.string.settings_automatic_backup_status_permission_lost

    FolderUnavailableReason.FOLDER_MISSING ->
        Res.string.settings_automatic_backup_status_folder_missing

    FolderUnavailableReason.UNSUPPORTED_PROVIDER ->
        Res.string.settings_automatic_backup_status_unsupported_provider
}

private fun AutomaticBackupFailureKind.messageResource(): StringResource = when (this) {
    AutomaticBackupFailureKind.FOLDER_NOT_CONFIGURED ->
        Res.string.settings_automatic_backup_failed_folder_not_configured

    AutomaticBackupFailureKind.FOLDER_UNAVAILABLE ->
        Res.string.settings_automatic_backup_failed_folder_unavailable

    AutomaticBackupFailureKind.FOLDER_UNSUPPORTED ->
        Res.string.settings_automatic_backup_failed_folder_unsupported

    AutomaticBackupFailureKind.EXPORT_FAILED ->
        Res.string.settings_automatic_backup_failed_export_failed

    AutomaticBackupFailureKind.TOO_LARGE -> Res.string.settings_automatic_backup_failed_too_large
    AutomaticBackupFailureKind.WRITE_FAILED ->
        Res.string.settings_automatic_backup_failed_write_failed

    AutomaticBackupFailureKind.WRITE_UNVERIFIED ->
        Res.string.settings_automatic_backup_failed_write_unverified

    AutomaticBackupFailureKind.UNKNOWN -> Res.string.settings_automatic_backup_failed_unknown
}
