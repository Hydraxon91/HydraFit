package com.hydrafit.app.feature.fatigueheatmap

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.hydrafit.app.core.domain.fatigue.MuscleGroup
import hydrafit.feature.fatigueheatmap.generated.resources.Res
import hydrafit.feature.fatigueheatmap.generated.resources.fatigue_map_back
import hydrafit.feature.fatigueheatmap.generated.resources.fatigue_map_female
import hydrafit.feature.fatigueheatmap.generated.resources.fatigue_map_figure_description
import hydrafit.feature.fatigueheatmap.generated.resources.fatigue_map_front
import hydrafit.feature.fatigueheatmap.generated.resources.fatigue_map_legend_high
import hydrafit.feature.fatigueheatmap.generated.resources.fatigue_map_legend_low
import hydrafit.feature.fatigueheatmap.generated.resources.fatigue_map_load_failed
import hydrafit.feature.fatigueheatmap.generated.resources.fatigue_map_male
import hydrafit.feature.fatigueheatmap.generated.resources.fatigue_map_region_description
import hydrafit.feature.fatigueheatmap.generated.resources.fatigue_map_retry
import hydrafit.feature.fatigueheatmap.generated.resources.fatigue_map_schematic_note
import kotlin.math.min
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource

private sealed interface ArtworkState {
    data object Loading : ArtworkState
    data class Ready(
        val artwork: AnatomicalArtwork,
        val model: BodyModel,
        val view: FatigueMapView
    ) : ArtworkState
    data class Failed(val cause: Exception) : ArtworkState
}

/** Fixed figure height, so switching model or view never moves the text below the map. */
private val mapFigureHeight = 260.dp

/**
 * Reads and parses one bundled figure off the main thread. Kept as a parameter of
 * [FatigueMapSection] so the loading regression test can hold a load open without the packaged
 * resources.
 */
internal suspend fun loadBundledArtwork(model: BodyModel, view: FatigueMapView): AnatomicalArtwork =
    withContext(Dispatchers.Default) {
        val source = Res.readBytes(artworkResource(model, view)).decodeToString()
        buildAnatomicalArtwork(parseArtworkSource(source), model, view)
    }

/**
 * Bundled anatomy with presentation-only model/view selection. Numeric descriptions are separate
 * text nodes, not overlapping bounds boxes that can hide one another from accessibility services.
 */
@Composable
internal fun FatigueMapSection(
    state: FatigueHeatmapUiState,
    modifier: Modifier = Modifier,
    loadArtwork: suspend (BodyModel, FatigueMapView) -> AnatomicalArtwork = ::loadBundledArtwork
) {
    var female by rememberSaveable { mutableStateOf(false) }
    var back by rememberSaveable { mutableStateOf(false) }
    var retry by rememberSaveable { mutableStateOf(0) }
    val model = if (female) BodyModel.FEMALE else BodyModel.MALE
    val view = if (back) FatigueMapView.BACK else FatigueMapView.FRONT
    val artworkState = remember { mutableStateOf<ArtworkState>(ArtworkState.Loading) }
    LaunchedEffect(model, view, retry) {
        artworkState.value = try {
            ArtworkState.Ready(loadArtwork(model, view), model, view)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            ArtworkState.Failed(failure)
        }
    }
    val neutral = lerp(MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.onSurface, 0.3f)
    val error = MaterialTheme.colorScheme.error
    val scoreByMuscle = state.entries.associate { it.muscle to it.score }
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = !female, onClick = {
                female = false
            }, label = { Text(stringResource(Res.string.fatigue_map_male)) })
            FilterChip(selected = female, onClick = {
                female = true
            }, label = { Text(stringResource(Res.string.fatigue_map_female)) })
        }
        TextButton(onClick = { back = !back }) {
            Text(
                stringResource(
                    if (back) Res.string.fatigue_map_front else Res.string.fatigue_map_back
                )
            )
        }
        Box(modifier = Modifier.fillMaxWidth().height(mapFigureHeight)) {
            (artworkState.value as? ArtworkState.Ready)?.let { loaded ->
                AnatomicalFigure(
                    artwork = loaded.artwork,
                    model = loaded.model,
                    view = loaded.view,
                    scoreByMuscle = scoreByMuscle,
                    neutral = neutral,
                    error = error
                )
            }
        }
        (artworkState.value as? ArtworkState.Failed)?.let {
            Text(stringResource(Res.string.fatigue_map_load_failed))
            TextButton(onClick = {
                retry++
            }) { Text(stringResource(Res.string.fatigue_map_retry)) }
        }
        FatigueMapLegend(neutral = neutral, error = error)
        Text(
            text = stringResource(Res.string.fatigue_map_schematic_note),
            style = MaterialTheme.typography.bodySmall
        )
        state.entries.forEach { entry ->
            Text(
                mapRegionDescription(entry.muscle, entry.score),
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun AnatomicalFigure(
    artwork: AnatomicalArtwork,
    model: BodyModel,
    view: FatigueMapView,
    scoreByMuscle: Map<MuscleGroup, Double>,
    neutral: Color,
    error: Color
) {
    val viewport = artwork.viewport
    val description = stringResource(
        Res.string.fatigue_map_figure_description,
        stringResource(
            if (model ==
                BodyModel.MALE
            ) {
                Res.string.fatigue_map_male
            } else {
                Res.string.fatigue_map_female
            }
        ),
        stringResource(
            if (view ==
                FatigueMapView.FRONT
            ) {
                Res.string.fatigue_map_front
            } else {
                Res.string.fatigue_map_back
            }
        )
    )
    val colours = artwork.muscles.keys.associateWith { muscle ->
        lerpFatigueColor(neutral, error, fatigueIntensity(scoreByMuscle[muscle] ?: 0.0))
    }
    Canvas(
        Modifier.fillMaxSize().semantics {
            contentDescription =
                description
        }
    ) {
        val fit = min(size.width / viewport.width, size.height / viewport.height)
        withTransform({
            translate((size.width - viewport.width * fit) / 2f, 0f)
            scale(fit, fit, pivot = androidx.compose.ui.geometry.Offset.Zero)
            translate(-viewport.x, -viewport.y)
        }) {
            artwork.pieces.forEach { piece ->
                val colour = artworkMuscle(piece.slug)?.let { colours.getValue(it) }
                    ?: if (piece.slug == "hair") Color.Gray else neutral
                drawPath(piece.path, colour)
            }
            anatomicalBlendPairs(view).forEach { (a, b) ->
                blendBoundary(artwork, a, b, colours, viewport.width * 0.006f)
            }
        }
    }
}

/** Clip every feather to the receiving region: neither the silhouette nor unrelated muscles blur. */
private fun DrawScope.blendBoundary(
    artwork: AnatomicalArtwork,
    a: MuscleGroup,
    b: MuscleGroup,
    colours: Map<MuscleGroup, Color>,
    radius: Float
) {
    val pathA = artwork.muscles.getValue(a)
    val pathB = artwork.muscles.getValue(b)
    val colourA = colours.getValue(a)
    val colourB = colours.getValue(b)
    if (colourA == colourB) return
    for (step in 8 downTo 1) {
        val width = radius * 2f * step / 8f
        val weight = boundaryBlendWeight(step)
        clipPath(pathB) { drawPath(pathA, lerp(colourB, colourA, weight), style = Stroke(width)) }
        clipPath(pathA) { drawPath(pathB, lerp(colourA, colourB, weight), style = Stroke(width)) }
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
