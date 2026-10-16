package com.hydrafit.app.feature.routines

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import hydrafit.feature.routines.generated.resources.Res
import hydrafit.feature.routines.generated.resources.routines_prescription_export_error_io
import hydrafit.feature.routines.generated.resources.routines_prescription_export_error_over_limit
import hydrafit.feature.routines.generated.resources.routines_prescription_export_error_too_large
import hydrafit.feature.routines.generated.resources.routines_prescription_export_saved
import org.jetbrains.compose.resources.StringResource

/** Exposes test tags as Android resource ids so the bounded UI check can target the new actions. */
internal expect fun Modifier.routinesTestTagsAsResourceId(): Modifier

/**
 * Platform document surface for the Routines prescription transfer: an Android create-document picker
 * for the per-routine JSON export and an open-document picker for the import, plus the export dialog.
 * A platform without a document surface renders nothing; the screen shows an unsupported note.
 */
@Composable
expect fun PrescriptionExportSection(
    export: RoutineExportState?,
    busy: Boolean,
    onExportWeightsChanged: (Boolean) -> Unit,
    onExportConfirmed: (String) -> Unit,
    onExportDismissed: () -> Unit,
    onImportChosen: (String) -> Unit,
    modifier: Modifier = Modifier
)

/** The message for a finished export; the dialog shows it in place until dismissed. */
internal fun PrescriptionExportStatus.messageResource(): StringResource = when (this) {
    PrescriptionExportStatus.Saved -> Res.string.routines_prescription_export_saved
    is PrescriptionExportStatus.Failed -> when (failure) {
        PrescriptionExportFailure.TOO_LARGE ->
            Res.string.routines_prescription_export_error_too_large

        PrescriptionExportFailure.OVER_LIMIT ->
            Res.string.routines_prescription_export_error_over_limit

        PrescriptionExportFailure.IO -> Res.string.routines_prescription_export_error_io
    }
}
