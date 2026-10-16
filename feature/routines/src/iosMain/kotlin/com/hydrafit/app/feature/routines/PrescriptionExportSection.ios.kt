package com.hydrafit.app.feature.routines

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

// iOS has no document surface for prescription transfer in 0.9.x, so the screen shows a note instead.
@Composable
actual fun PrescriptionExportSection(
    export: RoutineExportState?,
    busy: Boolean,
    onExportWeightsChanged: (Boolean) -> Unit,
    onExportConfirmed: (String) -> Unit,
    onExportDismissed: () -> Unit,
    onImportChosen: (String) -> Unit,
    modifier: Modifier
) = Unit

internal actual fun Modifier.routinesTestTagsAsResourceId(): Modifier = this
