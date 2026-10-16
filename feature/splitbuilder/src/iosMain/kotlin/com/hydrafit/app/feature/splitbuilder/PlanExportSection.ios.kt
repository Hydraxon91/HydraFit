package com.hydrafit.app.feature.splitbuilder

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

// iOS has no document surface for plan export in 0.9.x, so the screen shows a note instead.
@Composable
actual fun PlanExportSection(
    state: PlanExportUiState,
    onIncludeWeightsChanged: (Boolean) -> Unit,
    onExportJson: (String) -> Unit,
    onPrintPdf: (String) -> Unit,
    onStatusDismissed: () -> Unit,
    modifier: Modifier
) = Unit

internal actual fun Modifier.planExportTestTagsAsResourceId(): Modifier = this
