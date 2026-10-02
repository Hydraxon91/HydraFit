package com.hydrafit.app.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.hydrafit.app.core.domain.engine.PlannerEngineId
import com.hydrafit.app.core.domain.engine.TrainingGoal
import com.hydrafit.app.core.domain.unit.WeightUnit
import com.hydrafit.app.core.navigation.FeatureDestination
import hydrafit.feature.settings.generated.resources.Res
import hydrafit.feature.settings.generated.resources.nav_label
import hydrafit.feature.settings.generated.resources.settings_api_key_clear
import hydrafit.feature.settings.generated.resources.settings_api_key_configured
import hydrafit.feature.settings.generated.resources.settings_api_key_label
import hydrafit.feature.settings.generated.resources.settings_api_key_save
import hydrafit.feature.settings.generated.resources.settings_api_key_section
import hydrafit.feature.settings.generated.resources.settings_engine_deterministic
import hydrafit.feature.settings.generated.resources.settings_engine_gemini
import hydrafit.feature.settings.generated.resources.settings_engine_local_llm
import hydrafit.feature.settings.generated.resources.settings_engine_section
import hydrafit.feature.settings.generated.resources.settings_gemini_unavailable
import hydrafit.feature.settings.generated.resources.settings_goal_balanced
import hydrafit.feature.settings.generated.resources.settings_goal_endurance
import hydrafit.feature.settings.generated.resources.settings_goal_hypertrophy
import hydrafit.feature.settings.generated.resources.settings_goal_section
import hydrafit.feature.settings.generated.resources.settings_goal_strength
import hydrafit.feature.settings.generated.resources.settings_local_llm_slow
import hydrafit.feature.settings.generated.resources.settings_share_data
import hydrafit.feature.settings.generated.resources.settings_share_data_description
import hydrafit.feature.settings.generated.resources.settings_title
import hydrafit.feature.settings.generated.resources.settings_unit_kg
import hydrafit.feature.settings.generated.resources.settings_unit_lb
import hydrafit.feature.settings.generated.resources.settings_unit_section
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
        onGoalSelected = viewModel::onGoalSelected,
        onWeightUnitSelected = viewModel::onWeightUnitSelected,
        onWorkoutDataSharingToggled = viewModel::onWorkoutDataSharingToggled,
        onApiKeyChanged = viewModel::onApiKeyChanged,
        onSaveApiKey = viewModel::saveApiKey,
        onClearApiKey = viewModel::clearApiKey,
        onModelChanged = viewModel::refresh,
        modifier = modifier
    )
}

@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onEngineSelected: (PlannerEngineId) -> Unit,
    onGoalSelected: (TrainingGoal) -> Unit,
    onWeightUnitSelected: (WeightUnit) -> Unit,
    onWorkoutDataSharingToggled: (Boolean) -> Unit,
    onApiKeyChanged: (String) -> Unit,
    onSaveApiKey: () -> Unit,
    onClearApiKey: () -> Unit,
    onModelChanged: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .safeContentPadding()
            .verticalScroll(rememberScrollState())
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
        PlannerEngineId.entries.forEach { engine ->
            val enabled = engine in state.availableEngines
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(
                        selected = state.selectedEngine == engine,
                        enabled = enabled,
                        onClick = { onEngineSelected(engine) }
                    ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = state.selectedEngine == engine,
                    onClick = { onEngineSelected(engine) },
                    enabled = enabled
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
        if (state.isLocalLlmInstalled) {
            Text(
                text = stringResource(Res.string.settings_local_llm_slow),
                style = MaterialTheme.typography.bodySmall
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Switch(
                checked = state.workoutDataSharingEnabled,
                onCheckedChange = onWorkoutDataSharingToggled
            )
            Text(stringResource(Res.string.settings_share_data))
        }
        Text(
            text = stringResource(Res.string.settings_share_data_description),
            style = MaterialTheme.typography.bodySmall
        )

        Text(
            text = stringResource(Res.string.settings_unit_section),
            style = MaterialTheme.typography.titleMedium
        )
        WeightUnit.entries.forEach { unit ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(
                        selected = state.weightUnit == unit,
                        onClick = { onWeightUnitSelected(unit) }
                    ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = state.weightUnit == unit,
                    onClick = { onWeightUnitSelected(unit) }
                )
                Text(stringResource(unit.labelResource()))
            }
        }

        Text(
            text = stringResource(Res.string.settings_goal_section),
            style = MaterialTheme.typography.titleMedium
        )
        TrainingGoal.entries.forEach { goal ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(
                        selected = state.selectedGoal == goal,
                        onClick = { onGoalSelected(goal) }
                    ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = state.selectedGoal == goal,
                    onClick = { onGoalSelected(goal) }
                )
                Text(stringResource(goal.labelResource()))
            }
        }

        OnDeviceModelSection(
            installed = state.isLocalLlmInstalled,
            onModelChanged = onModelChanged
        )

        Text(
            text = stringResource(Res.string.settings_api_key_section),
            style = MaterialTheme.typography.titleMedium
        )
        OutlinedTextField(
            value = state.apiKeyInput,
            onValueChange = onApiKeyChanged,
            label = { Text(stringResource(Res.string.settings_api_key_label)) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onSaveApiKey, enabled = state.apiKeyInput.isNotBlank()) {
                Text(stringResource(Res.string.settings_api_key_save))
            }
            if (state.apiKeyConfigured) {
                OutlinedButton(onClick = onClearApiKey) {
                    Text(stringResource(Res.string.settings_api_key_clear))
                }
            }
        }
        if (state.apiKeyConfigured) {
            Text(
                text = stringResource(Res.string.settings_api_key_configured),
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

private fun TrainingGoal.labelResource(): StringResource = when (this) {
    TrainingGoal.BALANCED -> Res.string.settings_goal_balanced
    TrainingGoal.STRENGTH -> Res.string.settings_goal_strength
    TrainingGoal.HYPERTROPHY -> Res.string.settings_goal_hypertrophy
    TrainingGoal.ENDURANCE -> Res.string.settings_goal_endurance
}

private fun WeightUnit.labelResource(): StringResource = when (this) {
    WeightUnit.KG -> Res.string.settings_unit_kg
    WeightUnit.LB -> Res.string.settings_unit_lb
}
