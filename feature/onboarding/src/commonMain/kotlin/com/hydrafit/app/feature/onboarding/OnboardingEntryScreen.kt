package com.hydrafit.app.feature.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hydrafit.app.core.domain.setup.SetupStep
import hydrafit.feature.onboarding.generated.resources.Res
import hydrafit.feature.onboarding.generated.resources.onboarding_close
import hydrafit.feature.onboarding.generated.resources.onboarding_editor_unavailable
import hydrafit.feature.onboarding.generated.resources.onboarding_error
import hydrafit.feature.onboarding.generated.resources.onboarding_load_error
import hydrafit.feature.onboarding.generated.resources.onboarding_loading
import hydrafit.feature.onboarding.generated.resources.onboarding_retry
import hydrafit.feature.onboarding.generated.resources.onboarding_skip
import hydrafit.feature.onboarding.generated.resources.onboarding_step_equipment
import hydrafit.feature.onboarding.generated.resources.onboarding_step_first_workout
import hydrafit.feature.onboarding.generated.resources.onboarding_step_goal
import hydrafit.feature.onboarding.generated.resources.onboarding_step_personal_records
import hydrafit.feature.onboarding.generated.resources.onboarding_step_units
import hydrafit.feature.onboarding.generated.resources.onboarding_step_weekdays
import hydrafit.feature.onboarding.generated.resources.onboarding_step_welcome
import hydrafit.feature.onboarding.generated.resources.onboarding_title
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * The minimal setup entry. It owns the destination-scoped journey: opening is explicit and writes
 * nothing, and the draft is discarded whenever the route leaves, whether by Close or system Back.
 */
@Composable
fun OnboardingEntryRoute(
    revisit: Boolean,
    onClose: () -> Unit,
    viewModel: OnboardingViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // One explicit open per destination instance; re-entering a route builds a new ViewModel.
    LaunchedEffect(viewModel, revisit) { viewModel.open(revisit) }
    // A successful skip (or a first run whose setup is no longer pending) leaves the entry.
    LaunchedEffect(state.finished) { if (state.finished) onClose() }
    DisposableEffect(viewModel) { onDispose { viewModel.close() } }
    OnboardingEntryScreen(
        state = state,
        onSkip = viewModel::skip,
        onRetryLoad = viewModel::retryLoad,
        onClose = onClose
    )
}

@Composable
fun OnboardingEntryScreen(
    state: OnboardingUiState,
    onSkip: () -> Unit,
    onRetryLoad: () -> Unit,
    onClose: () -> Unit
) {
    when (state.entryPhase) {
        OnboardingEntryPhase.LOADING -> OnboardingLoading()
        OnboardingEntryPhase.FAILURE, OnboardingEntryPhase.READY -> OnboardingJourney(
            state = state,
            onSkip = onSkip,
            onRetryLoad = onRetryLoad,
            onClose = onClose
        )
    }
}

@Composable
private fun OnboardingLoading() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator()
        Text(
            text = stringResource(Res.string.onboarding_loading),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 12.dp)
        )
    }
}

/**
 * The setup journey body. Skip and the step heading need a committed boundary: without one the
 * screen shows only the failure (with a retry for a read) and a Close that writes nothing.
 */
@Composable
private fun OnboardingJourney(
    state: OnboardingUiState,
    onSkip: () -> Unit,
    onRetryLoad: () -> Unit,
    onClose: () -> Unit
) {
    val committed = state.committed
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = stringResource(Res.string.onboarding_title),
            style = MaterialTheme.typography.headlineSmall
        )
        if (committed != null) {
            Text(
                text = stringResource(state.step.headingResource()),
                style = MaterialTheme.typography.titleLarge
            )
        }
        state.failure?.let { failure ->
            Text(
                text = stringResource(failure.messageResource()),
                style = MaterialTheme.typography.bodyMedium
            )
            if (failure == OnboardingFailure.LOAD) {
                Button(onClick = onRetryLoad) {
                    Text(stringResource(Res.string.onboarding_retry))
                }
            }
        }
        if (committed != null) {
            Text(
                text = stringResource(Res.string.onboarding_editor_unavailable),
                style = MaterialTheme.typography.bodySmall
            )
            Button(
                onClick = onSkip,
                enabled = !state.saving && !state.finished
            ) {
                Text(stringResource(Res.string.onboarding_skip))
            }
        }
        OutlinedButton(
            onClick = onClose,
            enabled = !state.saving && !state.finished
        ) {
            Text(stringResource(Res.string.onboarding_close))
        }
    }
}

private fun SetupStep.headingResource(): StringResource = when (this) {
    SetupStep.WELCOME -> Res.string.onboarding_step_welcome
    SetupStep.UNITS -> Res.string.onboarding_step_units
    SetupStep.EQUIPMENT -> Res.string.onboarding_step_equipment
    SetupStep.GOAL -> Res.string.onboarding_step_goal
    SetupStep.WEEKDAYS -> Res.string.onboarding_step_weekdays
    SetupStep.PERSONAL_RECORDS -> Res.string.onboarding_step_personal_records
    SetupStep.FIRST_WORKOUT -> Res.string.onboarding_step_first_workout
}

private fun OnboardingFailure.messageResource(): StringResource = when (this) {
    OnboardingFailure.LOAD -> Res.string.onboarding_load_error
    OnboardingFailure.SAVE,
    OnboardingFailure.INVALID_INPUT,
    OnboardingFailure.STALE_CONTEXT -> Res.string.onboarding_error
}
