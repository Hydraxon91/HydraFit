package com.hydrafit.app.feature.routines

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import hydrafit.feature.routines.generated.resources.Res
import hydrafit.feature.routines.generated.resources.library_equipment_description
import hydrafit.feature.routines.generated.resources.library_routines_description
import hydrafit.feature.routines.generated.resources.library_title
import hydrafit.feature.routines.generated.resources.routines_equipment
import hydrafit.feature.routines.generated.resources.routines_title
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun LibraryHomeScreen(onOpenRoutines: () -> Unit, onOpenEquipment: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier.widthIn(max = 800.dp).fillMaxWidth()
                .verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                stringResource(Res.string.library_title),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.semantics { heading() }
            )
            LibraryCategoryRow(
                title = stringResource(Res.string.routines_title),
                description = stringResource(Res.string.library_routines_description),
                glyph = LibraryGlyph.ROUTINES,
                onClick = onOpenRoutines
            )
            LibraryCategoryRow(
                title = stringResource(Res.string.routines_equipment),
                description = stringResource(Res.string.library_equipment_description),
                glyph = LibraryGlyph.EQUIPMENT,
                onClick = onOpenEquipment
            )
        }
    }
}

@Composable
private fun LibraryCategoryRow(
    title: String,
    description: String,
    glyph: LibraryGlyph,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().heightIn(min = 88.dp),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            LibraryGlyphIcon(glyph)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleLarge)
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                "›",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.clearAndSetSemantics { }
            )
        }
    }
}

/** The two Library groups' original geometric glyphs; the text supplies their meaning. */
private enum class LibraryGlyph { ROUTINES, EQUIPMENT }

@Composable
private fun LibraryGlyphIcon(glyph: LibraryGlyph) {
    val color = MaterialTheme.colorScheme.primary
    Canvas(Modifier.size(28.dp).clearAndSetSemantics { }) {
        scale(size.width / 24f, size.height / 24f, pivot = Offset.Zero) {
            val stroke = Stroke(width = 1.8f, cap = StrokeCap.Round)
            when (glyph) {
                LibraryGlyph.ROUTINES -> {
                    drawRoundRect(
                        color = color,
                        topLeft = Offset(4f, 4f),
                        size = Size(16f, 16f),
                        cornerRadius = CornerRadius(2f, 2f),
                        style = stroke
                    )
                    drawLine(color, Offset(9f, 4f), Offset(9f, 20f), 1.8f)
                    drawLine(color, Offset(13f, 8f), Offset(17f, 8f), 1.8f)
                    drawLine(color, Offset(13f, 12f), Offset(17f, 12f), 1.8f)
                }
                LibraryGlyph.EQUIPMENT -> {
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
            }
        }
    }
}
