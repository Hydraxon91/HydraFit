package com.hydrafit.app.feature.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import hydrafit.feature.settings.generated.resources.Res
import hydrafit.feature.settings.generated.resources.settings_backup_export
import hydrafit.feature.settings.generated.resources.settings_backup_restore
import org.jetbrains.compose.resources.stringResource

@Composable
actual fun BackupSection(
    isSupported: Boolean,
    busy: Boolean,
    onExportTo: (String) -> Unit,
    onImportFrom: (String) -> Unit,
    modifier: Modifier
) {
    val exportPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> uri?.let { onExportTo(it.toString()) } }
    val importPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { onImportFrom(it.toString()) } }

    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
            onClick = { exportPicker.launch("hydrafit-backup.json") },
            enabled = isSupported && !busy
        ) {
            Text(stringResource(Res.string.settings_backup_export))
        }
        OutlinedButton(
            onClick = {
                importPicker.launch(
                    arrayOf("application/json", "text/plain", "application/octet-stream", "*/*")
                )
            },
            enabled = isSupported && !busy
        ) {
            Text(stringResource(Res.string.settings_backup_restore))
        }
    }
}
