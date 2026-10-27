package com.hydrafit.app.feature.fatigueheatmap

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.hydrafit.app.core.domain.fatigue.MuscleGroup
import com.hydrafit.app.core.domain.time.isoLocalDateTime
import com.hydrafit.app.core.navigation.FeatureDestination
import hydrafit.feature.fatigueheatmap.generated.resources.Res
import hydrafit.feature.fatigueheatmap.generated.resources.fatigue_coverage_clusters
import hydrafit.feature.fatigueheatmap.generated.resources.fatigue_coverage_credits
import hydrafit.feature.fatigueheatmap.generated.resources.fatigue_coverage_heading
import hydrafit.feature.fatigueheatmap.generated.resources.fatigue_coverage_last_trained
import hydrafit.feature.fatigueheatmap.generated.resources.fatigue_coverage_never_trained
import hydrafit.feature.fatigueheatmap.generated.resources.fatigue_coverage_no_sets
import hydrafit.feature.fatigueheatmap.generated.resources.fatigue_coverage_section_note
import hydrafit.feature.fatigueheatmap.generated.resources.fatigue_coverage_sets
import hydrafit.feature.fatigueheatmap.generated.resources.fatigue_coverage_unclassified_window_note
import hydrafit.feature.fatigueheatmap.generated.resources.fatigue_coverage_unknown_history
import hydrafit.feature.fatigueheatmap.generated.resources.fatigue_estimate_note
import hydrafit.feature.fatigueheatmap.generated.resources.fatigue_heatmap_title
import hydrafit.feature.fatigueheatmap.generated.resources.fatigue_map_close
import hydrafit.feature.fatigueheatmap.generated.resources.fatigue_map_open
import hydrafit.feature.fatigueheatmap.generated.resources.fatigue_percentage
import hydrafit.feature.fatigueheatmap.generated.resources.fatigue_percentage_near_limit
import hydrafit.feature.fatigueheatmap.generated.resources.muscle_abs
import hydrafit.feature.fatigueheatmap.generated.resources.muscle_adductors
import hydrafit.feature.fatigueheatmap.generated.resources.muscle_biceps
import hydrafit.feature.fatigueheatmap.generated.resources.muscle_calves
import hydrafit.feature.fatigueheatmap.generated.resources.muscle_chest_lower
import hydrafit.feature.fatigueheatmap.generated.resources.muscle_chest_upper
import hydrafit.feature.fatigueheatmap.generated.resources.muscle_forearms
import hydrafit.feature.fatigueheatmap.generated.resources.muscle_front_delts
import hydrafit.feature.fatigueheatmap.generated.resources.muscle_glutes
import hydrafit.feature.fatigueheatmap.generated.resources.muscle_hamstrings
import hydrafit.feature.fatigueheatmap.generated.resources.muscle_hip_abductors
import hydrafit.feature.fatigueheatmap.generated.resources.muscle_hip_flexors
import hydrafit.feature.fatigueheatmap.generated.resources.muscle_lats
import hydrafit.feature.fatigueheatmap.generated.resources.muscle_lower_back
import hydrafit.feature.fatigueheatmap.generated.resources.muscle_neck
import hydrafit.feature.fatigueheatmap.generated.resources.muscle_obliques
import hydrafit.feature.fatigueheatmap.generated.resources.muscle_quads
import hydrafit.feature.fatigueheatmap.generated.resources.muscle_rear_delts
import hydrafit.feature.fatigueheatmap.generated.resources.muscle_serratus_anterior
import hydrafit.feature.fatigueheatmap.generated.resources.muscle_side_delts
import hydrafit.feature.fatigueheatmap.generated.resources.muscle_tibialis_anterior
import hydrafit.feature.fatigueheatmap.generated.resources.muscle_traps
import hydrafit.feature.fatigueheatmap.generated.resources.muscle_triceps
import hydrafit.feature.fatigueheatmap.generated.resources.muscle_upper_back
import hydrafit.feature.fatigueheatmap.generated.resources.nav_label
import kotlin.math.roundToInt
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

val fatigueHeatmapRoute: String = "fatigue"

val fatigueHeatmapDestination: FeatureDestination = FeatureDestination(
    route = fatigueHeatmapRoute,
    label = Res.string.nav_label,
    graph = { { fatigueHeatmapGraph() } }
)

fun NavGraphBuilder.fatigueHeatmapGraph() {
    composable(fatigueHeatmapRoute) { FatigueHeatmapRoute() }
}

@Composable
fun FatigueHeatmapRoute(
    modifier: Modifier = Modifier,
    viewModel: FatigueHeatmapViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onResume() }
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { viewModel.onPause() }
    DisposableEffect(viewModel) {
        onDispose { viewModel.onPause() }
    }
    FatigueHeatmapScreen(state = state, modifier = modifier)
}

@Composable
fun FatigueHeatmapScreen(state: FatigueHeatmapUiState, modifier: Modifier = Modifier) {
    var mapVisible by rememberSaveable { mutableStateOf(false) }
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
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(Res.string.fatigue_heatmap_title),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = { mapVisible = true }) {
                Text(text = stringResource(Res.string.fatigue_map_open))
            }
        }
        Text(
            text = stringResource(Res.string.fatigue_estimate_note),
            style = MaterialTheme.typography.bodySmall
        )
        if (state.coverageComputed) {
            FatigueCoverageHeading(state = state)
        }
        state.entries.forEach { entry ->
            MuscleFatigueRow(entry = entry, state = state)
        }
    }
    if (mapVisible) {
        BodyMapDialog(state = state, onDismiss = { mapVisible = false })
    }
}

/**
 * The muscle map as an overlay card. The content is height-capped and scrollable so the back figure,
 * legend and schematic note stay reachable at larger font scales, independent of the bar list.
 */
@Composable
private fun BodyMapDialog(state: FatigueHeatmapUiState, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(Res.string.fatigue_map_open)) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                FatigueMapSection(state = state)
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(Res.string.fatigue_map_close))
            }
        }
    )
}

@Composable
private fun FatigueCoverageHeading(state: FatigueHeatmapUiState) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = stringResource(Res.string.fatigue_coverage_heading),
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            text = stringResource(Res.string.fatigue_coverage_section_note),
            style = MaterialTheme.typography.bodySmall
        )
        if (state.unclassifiedSetsInWindow > 0) {
            Text(
                text = stringResource(
                    Res.string.fatigue_coverage_unclassified_window_note,
                    state.unclassifiedSetsInWindow
                ),
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun MuscleFatigueRow(entry: MuscleFatigueEntry, state: FatigueHeatmapUiState) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(text = stringResource(entry.muscle.labelResource()))
        LinearProgressIndicator(
            progress = { entry.score.toFloat().coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth()
        )
        val percentageTenths = roundedFatiguePercentage(entry.score)
        Text(
            text = stringResource(
                fatiguePercentageResource(percentageTenths),
                percentageTenths / 10,
                percentageTenths % 10
            )
        )
        if (state.coverageComputed) {
            CoverageDetails(entry = entry, state = state)
        }
    }
}

@Composable
private fun CoverageDetails(entry: MuscleFatigueEntry, state: FatigueHeatmapUiState) {
    val unknownHistory = state.hasUnclassifiedHistory
    val setsText = when {
        entry.workingSets > 0 ->
            stringResource(Res.string.fatigue_coverage_sets, entry.workingSets)
        unknownHistory -> stringResource(Res.string.fatigue_coverage_unknown_history)
        else -> stringResource(Res.string.fatigue_coverage_no_sets)
    }
    val (wholeCredits, tenthCredits) = creditParts(entry.estimatedInvolvementCredits)
    val lastTrained = entry.lastTrainedAtMillis
    val lastTrainedText = when {
        lastTrained != null -> stringResource(
            Res.string.fatigue_coverage_last_trained,
            coverageDateLabel(lastTrained, state.utcOffsetMillis)
        )
        unknownHistory -> stringResource(Res.string.fatigue_coverage_unknown_history)
        else -> stringResource(Res.string.fatigue_coverage_never_trained)
    }
    Text(text = setsText, style = MaterialTheme.typography.bodySmall)
    Text(
        text = stringResource(Res.string.fatigue_coverage_clusters, entry.clusters),
        style = MaterialTheme.typography.bodySmall
    )
    Text(
        text = stringResource(Res.string.fatigue_coverage_credits, wholeCredits, tenthCredits),
        style = MaterialTheme.typography.bodySmall
    )
    Text(text = lastTrainedText, style = MaterialTheme.typography.bodySmall)
}

/** Splits credits into whole and one-decimal tenths for the `%1$d.%2$d` resource. */
internal fun creditParts(credits: Double): Pair<Int, Int> {
    val tenths = (credits * 10.0).roundToInt()
    return tenths / 10 to tenths % 10
}

/** The local civil date only, from the full `YYYY-MM-DD HH:mm` local timestamp. */
internal fun coverageDateLabel(epochMillis: Long, utcOffsetMillis: Long): String =
    isoLocalDateTime(epochMillis, utcOffsetMillis).substringBefore(' ')

internal fun roundedFatiguePercentage(score: Double): Int = (score * 1000).roundToInt()

internal fun fatiguePercentageResource(percentageTenths: Int): StringResource =
    if (percentageTenths >= 1000) {
        Res.string.fatigue_percentage_near_limit
    } else {
        Res.string.fatigue_percentage
    }

internal fun MuscleGroup.labelResource(): StringResource = when (this) {
    MuscleGroup.CHEST_UPPER -> Res.string.muscle_chest_upper
    MuscleGroup.CHEST_LOWER -> Res.string.muscle_chest_lower
    MuscleGroup.LATS -> Res.string.muscle_lats
    MuscleGroup.UPPER_BACK -> Res.string.muscle_upper_back
    MuscleGroup.LOWER_BACK -> Res.string.muscle_lower_back
    MuscleGroup.FRONT_DELTS -> Res.string.muscle_front_delts
    MuscleGroup.SIDE_DELTS -> Res.string.muscle_side_delts
    MuscleGroup.REAR_DELTS -> Res.string.muscle_rear_delts
    MuscleGroup.BICEPS -> Res.string.muscle_biceps
    MuscleGroup.TRICEPS -> Res.string.muscle_triceps
    MuscleGroup.FOREARMS -> Res.string.muscle_forearms
    MuscleGroup.ABS -> Res.string.muscle_abs
    MuscleGroup.OBLIQUES -> Res.string.muscle_obliques
    MuscleGroup.QUADS -> Res.string.muscle_quads
    MuscleGroup.HAMSTRINGS -> Res.string.muscle_hamstrings
    MuscleGroup.GLUTES -> Res.string.muscle_glutes
    MuscleGroup.CALVES -> Res.string.muscle_calves
    MuscleGroup.ADDUCTORS -> Res.string.muscle_adductors
    MuscleGroup.HIP_ABDUCTORS -> Res.string.muscle_hip_abductors
    MuscleGroup.TRAPS -> Res.string.muscle_traps
    MuscleGroup.NECK -> Res.string.muscle_neck
    MuscleGroup.TIBIALIS_ANTERIOR -> Res.string.muscle_tibialis_anterior
    MuscleGroup.SERRATUS_ANTERIOR -> Res.string.muscle_serratus_anterior
    MuscleGroup.HIP_FLEXORS -> Res.string.muscle_hip_flexors
}
