package com.hydrafit.app.feature.fatigueheatmap

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.hydrafit.app.core.domain.fatigue.MuscleGroup
import hydrafit.feature.fatigueheatmap.generated.resources.Res
import hydrafit.feature.fatigueheatmap.generated.resources.fatigue_map_back
import hydrafit.feature.fatigueheatmap.generated.resources.fatigue_map_front
import hydrafit.feature.fatigueheatmap.generated.resources.fatigue_map_legend_high
import hydrafit.feature.fatigueheatmap.generated.resources.fatigue_map_legend_low
import hydrafit.feature.fatigueheatmap.generated.resources.fatigue_map_region_description
import hydrafit.feature.fatigueheatmap.generated.resources.fatigue_map_schematic_note
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

private const val MAP_ASPECT_RATIO = 0.62f

/**
 * The schematic front/back map, its neutral-to-red legend and the schematic note. Every region is
 * exposed as its own semantics node (muscle label plus formatted percentage), so colour is never the
 * only way to read fatigue. It carries no animation and no persistent cache.
 */
@Composable
internal fun FatigueMapSection(state: FatigueHeatmapUiState, modifier: Modifier = Modifier) {
    val neutral = MaterialTheme.colorScheme.surfaceVariant
    val error = MaterialTheme.colorScheme.error
    val scoreByMuscle = state.entries.associate { it.muscle to it.score }
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        FatigueMapFigure(
            view = FatigueMapView.FRONT,
            label = Res.string.fatigue_map_front,
            scoreByMuscle = scoreByMuscle,
            neutral = neutral,
            error = error
        )
        FatigueMapFigure(
            view = FatigueMapView.BACK,
            label = Res.string.fatigue_map_back,
            scoreByMuscle = scoreByMuscle,
            neutral = neutral,
            error = error
        )
        FatigueMapLegend(neutral = neutral, error = error)
        Text(
            text = stringResource(Res.string.fatigue_map_schematic_note),
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun FatigueMapFigure(
    view: FatigueMapView,
    label: StringResource,
    scoreByMuscle: Map<MuscleGroup, Double>,
    neutral: Color,
    error: Color,
    modifier: Modifier = Modifier
) {
    val regions = regionsFor(view).sortedByDescending { it.boundingArea() }
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(text = stringResource(label), style = MaterialTheme.typography.titleSmall)
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(MAP_ASPECT_RATIO)
        ) {
            val width = maxWidth
            val height = maxHeight
            Canvas(Modifier.matchParentSize()) {
                regions.forEach { region ->
                    drawPath(
                        path = region.toPath(size),
                        color = lerpFatigueColor(
                            neutral = neutral,
                            error = error,
                            intensity = fatigueIntensity(scoreByMuscle[region.muscle] ?: 0.0)
                        )
                    )
                }
            }
            regions.forEach { region ->
                val description = mapRegionDescription(
                    muscle = region.muscle,
                    score = scoreByMuscle[region.muscle] ?: 0.0
                )
                val bounds = region.bounds()
                Box(
                    modifier = Modifier
                        .offset(x = width * bounds.left, y = height * bounds.top)
                        .size(width = width * bounds.width, height = height * bounds.height)
                        .semantics { contentDescription = description }
                )
            }
        }
    }
}

@Composable
private fun FatigueMapLegend(neutral: Color, error: Color, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = stringResource(Res.string.fatigue_map_legend_low),
            style = MaterialTheme.typography.labelSmall
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(8.dp)
                .background(Brush.horizontalGradient(listOf(neutral, error)))
        )
        Text(
            text = stringResource(Res.string.fatigue_map_legend_high),
            style = MaterialTheme.typography.labelSmall
        )
    }
}

@Composable
private fun mapRegionDescription(muscle: MuscleGroup, score: Double): String {
    val label = stringResource(muscle.labelResource())
    val percentageTenths = roundedFatiguePercentage(score)
    val percentage = stringResource(
        fatiguePercentageResource(percentageTenths),
        percentageTenths / 10,
        percentageTenths % 10
    )
    return stringResource(Res.string.fatigue_map_region_description, label, percentage)
}

private fun MapRegion.toPath(size: Size): Path = Path().apply {
    points.forEachIndexed { index, point ->
        val x = point.x * size.width
        val y = point.y * size.height
        if (index == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}
