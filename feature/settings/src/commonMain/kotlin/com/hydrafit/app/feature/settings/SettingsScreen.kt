package com.hydrafit.app.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.hydrafit.app.core.domain.engine.PlannerEngineId
import com.hydrafit.app.core.navigation.FeatureDestination
import hydrafit.feature.settings.generated.resources.Res
import hydrafit.feature.settings.generated.resources.nav_label
import hydrafit.feature.settings.generated.resources.settings_engine_deterministic
import hydrafit.feature.settings.generated.resources.settings_engine_gemini
import hydrafit.feature.settings.generated.resources.settings_engine_local_llm
import hydrafit.feature.settings.generated.resources.settings_engine_section
import hydrafit.feature.settings.generated.resources.settings_gemini_unavailable
import hydrafit.feature.settings.generated.resources.settings_title
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

val settingsRoute: String = "settings"

val settingsDestination: FeatureDestination = FeatureDestination(
    route = settingsRoute,
    label = Res.string.nav_label,
    graph = { settingsGraph() }
)

fun NavGraphBuilder.settingsGraph() {
    composable(settingsRoute) { SettingsRoute() }
}

@Composable
fun SettingsRoute(modifier: Modifier = Modifier, viewModel: SettingsViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    SettingsScreen(
        state = state,
        onEngineSelected = viewModel::onEngineSelected,
        modifier = modifier
    )
}

@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onEngineSelected: (PlannerEngineId) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .safeContentPadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = stringResource(Res.string.settings_title),
            style = MaterialTheme.typography.headlineSmall
        )
        Text(
            text = stringResource(Res.string.settings_engine_section),
            style = MaterialTheme.typography.titleMedium
        )
        state.availableEngines.forEach { engine ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(
                        selected = state.selectedEngine == engine,
                        onClick = { onEngineSelected(engine) }
                    ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = state.selectedEngine == engine,
                    onClick = { onEngineSelected(engine) }
                )
                Text(stringResource(engine.labelResource()))
            }
        }
        if (!state.isGeminiAvailable) {
            Text(
                text = stringResource(Res.string.settings_gemini_unavailable),
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

private fun PlannerEngineId.labelResource(): StringResource = when (this) {
    PlannerEngineId.DETERMINISTIC -> Res.string.settings_engine_deterministic
    PlannerEngineId.GEMINI_API -> Res.string.settings_engine_gemini
    PlannerEngineId.LOCAL_LLM -> Res.string.settings_engine_local_llm
}
