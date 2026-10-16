package com.hydrafit.app.feature.routines

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.dp
import hydrafit.feature.routines.generated.resources.Res
import hydrafit.feature.routines.generated.resources.routines_cancel
import hydrafit.feature.routines.generated.resources.routines_prescription_export_body
import hydrafit.feature.routines.generated.resources.routines_prescription_export_confirm
import hydrafit.feature.routines.generated.resources.routines_prescription_export_title
import hydrafit.feature.routines.generated.resources.routines_prescription_export_weights
import hydrafit.feature.routines.generated.resources.routines_prescription_import
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalComposeUiApi::class)
internal actual fun Modifier.routinesTestTagsAsResourceId(): Modifier =
    semantics { testTagsAsResourceId = true }

/** The suggested filename for a routine interchange file; the user may rename it in the picker. */
private const val EXPORT_FILE_NAME = "hydrafit-routines.json"

@Composable
actual fun PrescriptionExportSection(
    export: RoutineExportState?,
    busy: Boolean,
    onExportWeightsChanged: (Boolean) -> Unit,
    onExportConfirmed: (String) -> Unit,
    onExportDismissed: () -> Unit,
    onImportChosen: (String) -> Unit,
    modifier: Modifier
) {
    val exportPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> uri?.let { onExportConfirmed(it.toString()) } }
    val importPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { onImportChosen(it.toString()) } }

    Column(
        modifier = modifier.routinesTestTagsAsResourceId(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedButton(
            onClick = { importPicker.launch(arrayOf("application/json", "text/plain", "*/*")) },
            enabled = !busy,
            modifier = Modifier.testTag(PrescriptionShareTestTags.IMPORT_CHOOSE)
        ) {
            Text(stringResource(Res.string.routines_prescription_import))
        }
    }

    export?.let { state ->
        AlertDialog(
            onDismissRequest = onExportDismissed,
            title = { Text(stringResource(Res.string.routines_prescription_export_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(
                            Res.string.routines_prescription_export_body,
                            state.routineName
                        )
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Switch(
                            checked = state.includeWeights,
                            onCheckedChange = onExportWeightsChanged,
                            enabled = !state.inProgress,
                            modifier = Modifier.testTag(PrescriptionShareTestTags.EXPORT_WEIGHTS)
                        )
                        Text(stringResource(Res.string.routines_prescription_export_weights))
                    }
                    state.status?.let { status ->
                        Text(
                            text = stringResource(status.messageResource()),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { exportPicker.launch(EXPORT_FILE_NAME) },
                    enabled = !state.inProgress,
                    modifier = Modifier.testTag(PrescriptionShareTestTags.EXPORT_CONFIRM)
                ) {
                    Text(stringResource(Res.string.routines_prescription_export_confirm))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = onExportDismissed,
                    modifier = Modifier.testTag(PrescriptionShareTestTags.EXPORT_CANCEL)
                ) {
                    Text(stringResource(Res.string.routines_cancel))
                }
            }
        )
    }
}
