package com.hydrafit.app.feature.splitbuilder

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import hydrafit.feature.splitbuilder.generated.resources.Res
import hydrafit.feature.splitbuilder.generated.resources.split_plan_export
import hydrafit.feature.splitbuilder.generated.resources.split_plan_export_dismiss
import hydrafit.feature.splitbuilder.generated.resources.split_plan_export_pdf_unsupported
import hydrafit.feature.splitbuilder.generated.resources.split_plan_export_print
import hydrafit.feature.splitbuilder.generated.resources.split_plan_export_weights
import org.jetbrains.compose.resources.stringResource

/** Suggested filenames; the user may rename either in the picker. */
private const val EXPORT_FILE_NAME = "hydrafit-plan.json"
private const val PDF_FILE_NAME = "hydrafit-plan.pdf"

@OptIn(ExperimentalComposeUiApi::class)
internal actual fun Modifier.planExportTestTagsAsResourceId(): Modifier =
    semantics { testTagsAsResourceId = true }

@Composable
actual fun PlanExportSection(
    state: PlanExportUiState,
    onIncludeWeightsChanged: (Boolean) -> Unit,
    onExportJson: (String) -> Unit,
    onPrintPdf: (String) -> Unit,
    onStatusDismissed: () -> Unit,
    modifier: Modifier
) {
    val exportPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> uri?.let { onExportJson(it.toString()) } }
    val pdfPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri -> uri?.let { onPrintPdf(it.toString()) } }

    Column(
        modifier = modifier.planExportTestTagsAsResourceId(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Switch(
                checked = state.includeWeights,
                onCheckedChange = onIncludeWeightsChanged,
                enabled = !state.inProgress,
                modifier = Modifier.testTag(PlanExportTestTags.INCLUDE_WEIGHTS)
            )
            Text(stringResource(Res.string.split_plan_export_weights))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { exportPicker.launch(EXPORT_FILE_NAME) },
                enabled = !state.inProgress,
                modifier = Modifier.testTag(PlanExportTestTags.EXPORT_JSON)
            ) {
                Text(stringResource(Res.string.split_plan_export))
            }
            if (state.pdfSupported) {
                OutlinedButton(
                    onClick = { pdfPicker.launch(PDF_FILE_NAME) },
                    enabled = !state.inProgress,
                    modifier = Modifier.testTag(PlanExportTestTags.PRINT_PDF)
                ) {
                    Text(stringResource(Res.string.split_plan_export_print))
                }
            }
        }
        if (!state.pdfSupported) {
            Text(
                text = stringResource(Res.string.split_plan_export_pdf_unsupported),
                style = MaterialTheme.typography.bodySmall
            )
        }
        state.status?.let { status ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = stringResource(status.messageResource()),
                    style = MaterialTheme.typography.bodySmall
                )
                TextButton(onClick = onStatusDismissed) {
                    Text(stringResource(Res.string.split_plan_export_dismiss))
                }
            }
        }
    }
}
