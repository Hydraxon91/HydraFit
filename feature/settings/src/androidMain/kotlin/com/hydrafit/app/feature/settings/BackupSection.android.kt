package com.hydrafit.app.feature.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import hydrafit.feature.settings.generated.resources.Res
import hydrafit.feature.settings.generated.resources.settings_backup_cancel
import hydrafit.feature.settings.generated.resources.settings_backup_export
import hydrafit.feature.settings.generated.resources.settings_backup_export_encrypt
import hydrafit.feature.settings.generated.resources.settings_backup_export_encrypted_disclosure
import hydrafit.feature.settings.generated.resources.settings_backup_export_history_csv
import hydrafit.feature.settings.generated.resources.settings_backup_export_options_title
import hydrafit.feature.settings.generated.resources.settings_backup_export_password_hint
import hydrafit.feature.settings.generated.resources.settings_backup_export_plain_disclosure
import hydrafit.feature.settings.generated.resources.settings_backup_password
import hydrafit.feature.settings.generated.resources.settings_backup_password_confirm
import hydrafit.feature.settings.generated.resources.settings_backup_password_mismatch
import hydrafit.feature.settings.generated.resources.settings_backup_restore
import org.jetbrains.compose.resources.stringResource

@Composable
actual fun BackupSection(
    isSupported: Boolean,
    busy: Boolean,
    onExportTo: (handle: String, encrypted: Boolean, password: String?) -> Unit,
    onExportCsvTo: (String) -> Unit,
    onImportFrom: (String) -> Unit,
    modifier: Modifier
) {
    // The chosen export options outlive the dialog so they reach the picker that opens after it.
    var showExportOptions by remember { mutableStateOf(false) }
    var encrypt by remember { mutableStateOf(false) }
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    val closeExportOptions = {
        showExportOptions = false
        encrypt = false
        password = ""
        confirm = ""
    }

    val exportPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) onExportTo(uri.toString(), encrypt, if (encrypt) password else null)
        closeExportOptions()
    }
    val csvPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri -> uri?.let { onExportCsvTo(it.toString()) } }
    val importPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { onImportFrom(it.toString()) } }

    Row(
        modifier = modifier.fillMaxWidth().guidanceTestTagsAsResourceId(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Button(
            onClick = { showExportOptions = true },
            enabled = isSupported && !busy,
            modifier = Modifier.weight(1f)
        ) {
            Text(stringResource(Res.string.settings_backup_export))
        }
        OutlinedButton(
            onClick = { csvPicker.launch("hydrafit-history.csv") },
            enabled = isSupported && !busy,
            modifier = Modifier.weight(1f).testTag(BackupTestTags.EXPORT_CSV)
        ) {
            Text(stringResource(Res.string.settings_backup_export_history_csv))
        }
        OutlinedButton(
            onClick = {
                importPicker.launch(
                    arrayOf("application/json", "text/plain", "application/octet-stream", "*/*")
                )
            },
            enabled = isSupported && !busy,
            modifier = Modifier.weight(1f)
        ) {
            Text(stringResource(Res.string.settings_backup_restore))
        }
    }

    if (showExportOptions) {
        AlertDialog(
            onDismissRequest = closeExportOptions,
            title = { Text(stringResource(Res.string.settings_backup_export_options_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().toggleable(
                            value = encrypt,
                            role = Role.Checkbox,
                            onValueChange = { encrypt = it }
                        ),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Checkbox(checked = encrypt, onCheckedChange = null)
                        Text(stringResource(Res.string.settings_backup_export_encrypt))
                    }
                    if (encrypt) {
                        Text(
                            text = stringResource(
                                Res.string.settings_backup_export_password_hint
                            ),
                            style = MaterialTheme.typography.bodySmall
                        )
                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            label = { Text(stringResource(Res.string.settings_backup_password)) },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = confirm,
                            onValueChange = { confirm = it },
                            label = {
                                Text(stringResource(Res.string.settings_backup_password_confirm))
                            },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (confirm.isNotEmpty() && password != confirm) {
                            Text(
                                text = stringResource(Res.string.settings_backup_password_mismatch),
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Text(
                            text = stringResource(
                                Res.string.settings_backup_export_encrypted_disclosure
                            ),
                            style = MaterialTheme.typography.bodySmall
                        )
                    } else {
                        Text(
                            text = stringResource(
                                Res.string.settings_backup_export_plain_disclosure
                            ),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showExportOptions = false
                        exportPicker.launch("hydrafit-backup.json")
                    },
                    enabled = !encrypt || (password.isNotEmpty() && password == confirm)
                ) {
                    Text(stringResource(Res.string.settings_backup_export))
                }
            },
            dismissButton = {
                TextButton(onClick = closeExportOptions) {
                    Text(stringResource(Res.string.settings_backup_cancel))
                }
            }
        )
    }
}
