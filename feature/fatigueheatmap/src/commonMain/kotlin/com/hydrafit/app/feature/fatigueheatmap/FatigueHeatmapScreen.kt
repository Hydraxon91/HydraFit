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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.hydrafit.app.core.domain.fatigue.MuscleGroup
import com.hydrafit.app.core.navigation.FeatureDestination
import hydrafit.feature.fatigueheatmap.generated.resources.Res
import hydrafit.feature.fatigueheatmap.generated.resources.fatigue_heatmap_title
import hydrafit.feature.fatigueheatmap.generated.resources.muscle_back
import hydrafit.feature.fatigueheatmap.generated.resources.muscle_biceps
import hydrafit.feature.fatigueheatmap.generated.resources.muscle_calves
import hydrafit.feature.fatigueheatmap.generated.resources.muscle_chest
import hydrafit.feature.fatigueheatmap.generated.resources.muscle_core
import hydrafit.feature.fatigueheatmap.generated.resources.muscle_glutes
import hydrafit.feature.fatigueheatmap.generated.resources.muscle_hamstrings
import hydrafit.feature.fatigueheatmap.generated.resources.muscle_quads
import hydrafit.feature.fatigueheatmap.generated.resources.muscle_shoulders
import hydrafit.feature.fatigueheatmap.generated.resources.muscle_triceps
import hydrafit.feature.fatigueheatmap.generated.resources.nav_label
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

val fatigueHeatmapRoute: String = "fatigue"

val fatigueHeatmapDestination: FeatureDestination = FeatureDestination(
    route = fatigueHeatmapRoute,
    label = Res.string.nav_label,
    graph = { fatigueHeatmapGraph() }
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
                Text(text = "${(entry.score * 100).toInt()}%")
            }
        }
    }
}

private fun MuscleGroup.labelResource(): StringResource = when (this) {
    MuscleGroup.CHEST -> Res.string.muscle_chest
    MuscleGroup.BACK -> Res.string.muscle_back
    MuscleGroup.SHOULDERS -> Res.string.muscle_shoulders
    MuscleGroup.BICEPS -> Res.string.muscle_biceps
    MuscleGroup.TRICEPS -> Res.string.muscle_triceps
    MuscleGroup.QUADS -> Res.string.muscle_quads
    MuscleGroup.HAMSTRINGS -> Res.string.muscle_hamstrings
    MuscleGroup.GLUTES -> Res.string.muscle_glutes
    MuscleGroup.CALVES -> Res.string.muscle_calves
    MuscleGroup.CORE -> Res.string.muscle_core
}
