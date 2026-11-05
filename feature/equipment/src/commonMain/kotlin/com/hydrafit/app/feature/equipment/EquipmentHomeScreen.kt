package com.hydrafit.app.feature.equipment

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import hydrafit.feature.equipment.generated.resources.Res
import hydrafit.feature.equipment.generated.resources.equipment_back
import hydrafit.feature.equipment.generated.resources.equipment_profile_active
import hydrafit.feature.equipment.generated.resources.equipment_profiler_title
import org.jetbrains.compose.resources.stringResource

/**
 * The Equipment hub: the active profile context when it is available, then one row per section.
 * It reads the same graph-scoped ViewModel as the sections, so returning from a page shows the
 * current profile rather than a copy captured when the hub was first opened.
 */
@Composable
internal fun EquipmentHomeRoute(
    viewModel: EquipmentProfilerViewModel,
    onOpenSection: (EquipmentSection) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    EquipmentHomeScreen(
        state = state,
        onOpenSection = onOpenSection,
        onBack = onBack,
        modifier = modifier
    )
}

@Composable
internal fun EquipmentHomeScreen(
    state: EquipmentProfilerUiState,
    onOpenSection: (EquipmentSection) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = stringResource(Res.string.equipment_profiler_title),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onBack) {
                Text(stringResource(Res.string.equipment_back))
            }
        }
        // Only a coherent, loaded profile is named. While the first snapshot is missing the hub
        // makes no claim about which profile is active.
        state.profile?.let { summary ->
            Text(
                text = stringResource(Res.string.equipment_profile_active, summary.activeName),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        EquipmentSection.entries.forEach { section ->
            EquipmentSectionRow(
                section = section,
                onClick = { onOpenSection(section) }
            )
        }
    }
}

/** One hub row: section title, a short description and a decorative chevron. */
@Composable
private fun EquipmentSectionRow(section: EquipmentSection, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            EquipmentSectionIcon(section)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = stringResource(section.title),
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = stringResource(section.description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = "›",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.clearAndSetSemantics { }
            )
        }
    }
}

/** One original geometric glyph per section; the title and description carry its meaning. */
@Composable
private fun EquipmentSectionIcon(section: EquipmentSection) {
    val color = MaterialTheme.colorScheme.primary
    Canvas(Modifier.size(28.dp).clearAndSetSemantics { }) {
        scale(size.width / 24f, size.height / 24f, pivot = Offset.Zero) {
            val stroke = Stroke(width = 1.8f, cap = StrokeCap.Round)
            when (section) {
                EquipmentSection.PROFILE_LOADING -> {
                    drawCircle(color, 3.2f, Offset(12f, 8f), style = stroke)
                    drawLine(color, Offset(6f, 19f), Offset(8f, 15f), 1.8f, StrokeCap.Round)
                    drawLine(color, Offset(18f, 19f), Offset(16f, 15f), 1.8f, StrokeCap.Round)
                    drawLine(color, Offset(8f, 15f), Offset(16f, 15f), 1.8f, StrokeCap.Round)
                }
                EquipmentSection.INVENTORY_STATIONS -> {
                    drawLine(color, Offset(5f, 12f), Offset(19f, 12f), 1.8f)
                    listOf(3f, 7f, 17f, 21f).forEachIndexed { index, x ->
                        val half = if (index == 0 || index == 3) 3f else 5f
                        drawLine(
                            color,
                            Offset(x, 12f - half),
                            Offset(x, 12f + half),
                            1.8f,
                            StrokeCap.Round
                        )
                    }
                }
                EquipmentSection.EXERCISE_CATALOG -> {
                    drawRoundRect(
                        color = color,
                        topLeft = Offset(4f, 4f),
                        size = Size(16f, 16f),
                        cornerRadius = CornerRadius(2f, 2f),
                        style = stroke
                    )
                    drawLine(color, Offset(9f, 4f), Offset(9f, 20f), 1.8f)
                    drawLine(color, Offset(13f, 8f), Offset(17f, 8f), 1.8f)
                }
                EquipmentSection.EXCLUSIONS -> {
                    drawCircle(color, 8f, Offset(12f, 12f), style = stroke)
                    drawLine(color, Offset(6.5f, 17.5f), Offset(17.5f, 6.5f), 1.8f)
                }
                EquipmentSection.BASELINE_RECORDS -> {
                    drawLine(color, Offset(4f, 20f), Offset(20f, 20f), 1.8f)
                    drawLine(color, Offset(7f, 20f), Offset(7f, 13f), 1.8f)
                    drawLine(color, Offset(12f, 20f), Offset(12f, 8f), 1.8f)
                    drawLine(color, Offset(17f, 20f), Offset(17f, 11f), 1.8f)
                }
            }
        }
    }
}
