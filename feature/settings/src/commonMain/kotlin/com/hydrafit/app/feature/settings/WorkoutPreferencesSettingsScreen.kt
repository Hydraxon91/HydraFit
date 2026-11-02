package com.hydrafit.app.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hydrafit.app.core.domain.engine.TrainingGoal
import hydrafit.feature.settings.generated.resources.Res
import hydrafit.feature.settings.generated.resources.settings_goal_section
import hydrafit.feature.settings.generated.resources.settings_guided_workout
import hydrafit.feature.settings.generated.resources.settings_guided_workout_description
import hydrafit.feature.settings.generated.resources.settings_home_workout_preferences
import hydrafit.feature.settings.generated.resources.settings_rest_default_description
import hydrafit.feature.settings.generated.resources.settings_rest_default_save
import hydrafit.feature.settings.generated.resources.settings_rest_default_seconds
import hydrafit.feature.settings.generated.resources.settings_rest_default_section
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun WorkoutPreferencesRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = koinViewModel(),
    restDurationViewModel: RestDurationSettingsViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val restDurationState by restDurationViewModel.state.collectAsStateWithLifecycle()
    WorkoutPreferencesScreen(
        state = state,
        restDurationState = restDurationState,
        onGoalSelected = viewModel::onGoalSelected,
        onGuidedWorkoutToggled = viewModel::onGuidedWorkoutToggled,
        onRestDurationChanged = restDurationViewModel::onSecondsChanged,
        onSaveRestDuration = restDurationViewModel::save,
        onBack = onBack,
        modifier = modifier
    )
}

@Composable
fun WorkoutPreferencesScreen(
    state: SettingsUiState,
    restDurationState: RestDurationSettingsUiState,
    onGoalSelected: (TrainingGoal) -> Unit,
    onGuidedWorkoutToggled: (Boolean) -> Unit,
    onRestDurationChanged: (String) -> Unit,
    onSaveRestDuration: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SettingsPageHeader(
            title = stringResource(Res.string.settings_home_workout_preferences),
            onBack = onBack
        )
        Text(
            text = stringResource(Res.string.settings_goal_section),
            style = MaterialTheme.typography.titleMedium
        )
        TrainingGoal.entries.forEach { goal ->
            SettingsRadioRow(
                label = stringResource(goal.labelResource()),
                selected = state.selectedGoal == goal,
                onSelected = { onGoalSelected(goal) }
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .toggleable(
                    value = state.guidedWorkoutEnabled,
                    role = Role.Switch,
                    onValueChange = onGuidedWorkoutToggled
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Switch(
                checked = state.guidedWorkoutEnabled,
                onCheckedChange = null
            )
            Text(stringResource(Res.string.settings_guided_workout))
        }
        Text(
            text = stringResource(Res.string.settings_guided_workout_description),
            style = MaterialTheme.typography.bodySmall
        )
        Text(
            text = stringResource(Res.string.settings_rest_default_section),
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            text = stringResource(Res.string.settings_rest_default_description),
            style = MaterialTheme.typography.bodySmall
        )
        OutlinedTextField(
            value = restDurationState.seconds,
            onValueChange = onRestDurationChanged,
            label = { Text(stringResource(Res.string.settings_rest_default_seconds)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )
        Button(onClick = onSaveRestDuration, enabled = restDurationState.canSave) {
            Text(stringResource(Res.string.settings_rest_default_save))
        }
    }
}
