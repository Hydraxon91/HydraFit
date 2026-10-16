package com.hydrafit.app.feature.splitbuilder

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import hydrafit.feature.splitbuilder.generated.resources.Res
import hydrafit.feature.splitbuilder.generated.resources.split_plan_export_error_io
import hydrafit.feature.splitbuilder.generated.resources.split_plan_export_error_over_limit
import hydrafit.feature.splitbuilder.generated.resources.split_plan_export_error_too_large
import hydrafit.feature.splitbuilder.generated.resources.split_plan_export_printed
import hydrafit.feature.splitbuilder.generated.resources.split_plan_export_saved
import org.jetbrains.compose.resources.StringResource

/** Exposes test tags as Android resource ids so the bounded UI check can target the new actions. */
internal expect fun Modifier.planExportTestTagsAsResourceId(): Modifier

/**
 * Platform document surface for the displayed plan: a Storage Access Framework create-document picker
 * for the JSON export and one for the PDF. A platform without a document surface renders nothing; the
 * screen shows an unsupported note instead.
 */
@Composable
expect fun PlanExportSection(
    planAvailable: Boolean,
    state: PlanExportUiState,
    onIncludeWeightsChanged: (Boolean) -> Unit,
    onExportJson: (String) -> Unit,
    onPrintPdf: (String) -> Unit,
    onStatusDismissed: () -> Unit,
    modifier: Modifier = Modifier
)

/** The message for a finished export; the section shows it until the user dismisses it. */
internal fun PlanExportStatus.messageResource(): StringResource = when (this) {
    PlanExportStatus.Saved -> Res.string.split_plan_export_saved
    PlanExportStatus.Printed -> Res.string.split_plan_export_printed
    is PlanExportStatus.Failed -> when (failure) {
        PlanExportFailure.TOO_LARGE -> Res.string.split_plan_export_error_too_large
        PlanExportFailure.OVER_LIMIT -> Res.string.split_plan_export_error_over_limit
        PlanExportFailure.IO -> Res.string.split_plan_export_error_io
    }
}
