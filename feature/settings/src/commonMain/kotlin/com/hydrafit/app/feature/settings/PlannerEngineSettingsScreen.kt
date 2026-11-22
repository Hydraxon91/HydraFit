package com.hydrafit.app.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hydrafit.app.core.domain.engine.PlannerEngineId
import hydrafit.feature.settings.generated.resources.Res
import hydrafit.feature.settings.generated.resources.settings_api_key_clear
import hydrafit.feature.settings.generated.resources.settings_api_key_clear_cancel
import hydrafit.feature.settings.generated.resources.settings_api_key_clear_message
import hydrafit.feature.settings.generated.resources.settings_api_key_clear_title
import hydrafit.feature.settings.generated.resources.settings_api_key_configured
import hydrafit.feature.settings.generated.resources.settings_api_key_label
import hydrafit.feature.settings.generated.resources.settings_api_key_save
import hydrafit.feature.settings.generated.resources.settings_api_key_section
import hydrafit.feature.settings.generated.resources.settings_cloud_unavailable
import hydrafit.feature.settings.generated.resources.settings_groq_tutorial_open
import hydrafit.feature.settings.generated.resources.settings_groq_tutorial_privacy
import hydrafit.feature.settings.generated.resources.settings_groq_tutorial_step_1
import hydrafit.feature.settings.generated.resources.settings_groq_tutorial_step_2
import hydrafit.feature.settings.generated.resources.settings_groq_tutorial_step_3
import hydrafit.feature.settings.generated.resources.settings_groq_tutorial_step_4
import hydrafit.feature.settings.generated.resources.settings_groq_tutorial_toggle
import hydrafit.feature.settings.generated.resources.settings_home_planner_engine
import hydrafit.feature.settings.generated.resources.settings_share_data
import hydrafit.feature.settings.generated.resources.settings_share_data_description
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun PlannerEngineRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    PlannerEngineScreen(
        state = state,
        onEngineSelected = viewModel::onEngineSelected,
        onApiKeyChanged = viewModel::onApiKeyChanged,
        onSaveApiKey = viewModel::saveApiKey,
        onClearApiKey = viewModel::clearApiKey,
        onWorkoutDataSharingToggled = viewModel::onWorkoutDataSharingToggled,
        onBack = onBack,
        modifier = modifier
    )
}

private const val GROQ_KEYS_URL = "https://console.groq.com/keys"

@Composable
private fun GroqKeyTutorial() {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val uriHandler = LocalUriHandler.current
    TextButton(onClick = { expanded = !expanded }) {
        Text(stringResource(Res.string.settings_groq_tutorial_toggle))
    }
    if (expanded) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf(
                Res.string.settings_groq_tutorial_step_1,
                Res.string.settings_groq_tutorial_step_2,
                Res.string.settings_groq_tutorial_step_3,
                Res.string.settings_groq_tutorial_step_4
            ).forEach { step ->
                Text(
                    text = stringResource(step),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Text(
                text = stringResource(Res.string.settings_groq_tutorial_privacy),
                style = MaterialTheme.typography.bodySmall
            )
            OutlinedButton(onClick = { uriHandler.openUri(GROQ_KEYS_URL) }) {
                Text(stringResource(Res.string.settings_groq_tutorial_open))
            }
        }
    }
}

@Composable
fun PlannerEngineScreen(
    state: SettingsUiState,
    onEngineSelected: (PlannerEngineId) -> Unit,
    onApiKeyChanged: (String) -> Unit,
    onSaveApiKey: () -> Unit,
    onClearApiKey: () -> Unit,
    onWorkoutDataSharingToggled: (Boolean) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var confirmClearKey by remember { mutableStateOf(false) }
    if (confirmClearKey) {
        AlertDialog(
            onDismissRequest = { confirmClearKey = false },
            title = { Text(stringResource(Res.string.settings_api_key_clear_title)) },
            text = { Text(stringResource(Res.string.settings_api_key_clear_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmClearKey = false
                        onClearApiKey()
                    }
                ) { Text(stringResource(Res.string.settings_api_key_clear)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmClearKey = false }) {
                    Text(stringResource(Res.string.settings_api_key_clear_cancel))
                }
            }
        )
    }
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SettingsPageHeader(
            title = stringResource(Res.string.settings_home_planner_engine),
            onBack = onBack
        )
        PlannerEngineId.entries
            .filterNot { it == PlannerEngineId.GEMINI_API || it == PlannerEngineId.LOCAL_LLM }
            .forEach { engine ->
                SettingsRadioRow(
                    label = stringResource(engine.labelResource()),
                    selected = state.selectedEngine == engine,
                    enabled = engine in state.availableEngines,
                    onSelected = { onEngineSelected(engine) }
                )
            }
        if (!state.isGroqAvailable) {
            Text(
                text = stringResource(Res.string.settings_cloud_unavailable),
                style = MaterialTheme.typography.bodySmall
            )
        }

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
                OutlinedButton(onClick = { confirmClearKey = true }) {
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
        GroqKeyTutorial()

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
    }
}
