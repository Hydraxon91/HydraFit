package com.hydrafit.app.feature.fatigueheatmap

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.hydrafit.app.core.domain.fatigue.MuscleGroup
import com.hydrafit.app.core.navigation.FeatureDestination
import hydrafit.feature.fatigueheatmap.generated.resources.Res
import hydrafit.feature.fatigueheatmap.generated.resources.fatigue_heatmap_title
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
import hydrafit.feature.fatigueheatmap.generated.resources.muscle_lats
import hydrafit.feature.fatigueheatmap.generated.resources.muscle_lower_back
import hydrafit.feature.fatigueheatmap.generated.resources.muscle_neck
import hydrafit.feature.fatigueheatmap.generated.resources.muscle_obliques
import hydrafit.feature.fatigueheatmap.generated.resources.muscle_quads
import hydrafit.feature.fatigueheatmap.generated.resources.muscle_rear_delts
import hydrafit.feature.fatigueheatmap.generated.resources.muscle_side_delts
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
    Column(
        modifier = modifier
            .fillMaxSize()
            .safeContentPadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = stringResource(Res.string.fatigue_heatmap_title),
            style = MaterialTheme.typography.headlineSmall
        )
        state.entries.forEach { entry ->
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(text = stringResource(entry.muscle.labelResource()))
                LinearProgressIndicator(
                    progress = { entry.score.toFloat() },
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
            }
        }
    }
}

internal fun roundedFatiguePercentage(score: Double): Int = (score * 1000).roundToInt()

internal fun fatiguePercentageResource(percentageTenths: Int): StringResource =
    if (percentageTenths >= 1000) {
        Res.string.fatigue_percentage_near_limit
    } else {
        Res.string.fatigue_percentage
    }

private fun MuscleGroup.labelResource(): StringResource = when (this) {
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
}
