package com.hydrafit.app.feature.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hydrafit.app.core.domain.setup.SetupStep
import com.hydrafit.app.core.domain.setup.SetupStepInput
import com.hydrafit.app.core.navigation.AppTab
import com.hydrafit.app.core.navigation.LocalAppTabNavigator
import hydrafit.feature.onboarding.generated.resources.Res
import hydrafit.feature.onboarding.generated.resources.onboarding_back
import hydrafit.feature.onboarding.generated.resources.onboarding_close
import hydrafit.feature.onboarding.generated.resources.onboarding_error
import hydrafit.feature.onboarding.generated.resources.onboarding_load_error
import hydrafit.feature.onboarding.generated.resources.onboarding_loading
import hydrafit.feature.onboarding.generated.resources.onboarding_next
import hydrafit.feature.onboarding.generated.resources.onboarding_retry
import hydrafit.feature.onboarding.generated.resources.onboarding_saved_on_next
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
 * The setup entry. It owns the destination-scoped journey: opening is explicit and writes nothing,
 * and the draft is discarded whenever the route leaves, whether by Close or system Back.
 */
@Composable
fun OnboardingEntryRoute(
    revisit: Boolean,
    onClose: () -> Unit,
    viewModel: OnboardingViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val openTab = LocalAppTabNavigator.current
    // One explicit open per destination instance; re-entering a route builds a new ViewModel.
    LaunchedEffect(viewModel, revisit) { viewModel.open(revisit) }
    // A handoff opens its tab only after the final step's completion write succeeded. A plain finish
    // (Next or Skip) leaves the entry, and a failed completion never reaches here with a handoff set.
    LaunchedEffect(state.finished, state.pendingHandoff) {
        if (!state.finished) return@LaunchedEffect
        val tab = state.pendingHandoff
        if (tab == null) onClose() else openTab(tab)
    }
    DisposableEffect(viewModel) { onDispose { viewModel.close() } }
    OnboardingEntryScreen(
        state = state,
        onEdit = viewModel::edit,
        onSavePersonalRecordEntry = viewModel::savePersonalRecordEntry,
        onOpenPersonalRecordEntry = viewModel::openPersonalRecordEntry,
        onEditPersonalRecordEntry = viewModel::editPersonalRecordEntry,
        onDismissPersonalRecordEntry = viewModel::dismissPersonalRecordEntry,
        onNext = viewModel::next,
        onSkip = viewModel::skip,
        onBack = viewModel::back,
        onRetryLoad = viewModel::retryLoad,
        onHandoff = viewModel::handoff,
        onClose = onClose
    )
}

@Composable
fun OnboardingEntryScreen(
    state: OnboardingUiState,
    onEdit: (SetupStepInput) -> Unit,
    onSavePersonalRecordEntry: () -> Unit,
    onOpenPersonalRecordEntry: (String) -> Unit,
    onEditPersonalRecordEntry: (String, String) -> Unit,
    onDismissPersonalRecordEntry: () -> Unit,
    onNext: () -> Unit,
    onSkip: () -> Unit,
    onBack: () -> Unit,
    onRetryLoad: () -> Unit,
    onHandoff: (AppTab) -> Unit,
    onClose: () -> Unit
) {
    when (state.entryPhase) {
        OnboardingEntryPhase.LOADING -> OnboardingLoading()
        OnboardingEntryPhase.FAILURE, OnboardingEntryPhase.READY -> OnboardingJourney(
            state = state,
            onEdit = onEdit,
            onSavePersonalRecordEntry = onSavePersonalRecordEntry,
            onOpenPersonalRecordEntry = onOpenPersonalRecordEntry,
            onEditPersonalRecordEntry = onEditPersonalRecordEntry,
            onDismissPersonalRecordEntry = onDismissPersonalRecordEntry,
            onNext = onNext,
            onSkip = onSkip,
            onBack = onBack,
            onRetryLoad = onRetryLoad,
            onHandoff = onHandoff,
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
 * The setup journey body. Skip, Next and the step heading need a committed boundary: without one the
 * screen shows only the failure (with a retry for a read) and a Close that writes nothing. The step
 * content scrolls while the action bar stays visible, and the whole stack lifts above the keyboard.
 */
@Composable
private fun OnboardingJourney(
    state: OnboardingUiState,
    onEdit: (SetupStepInput) -> Unit,
    onSavePersonalRecordEntry: () -> Unit,
    onOpenPersonalRecordEntry: (String) -> Unit,
    onEditPersonalRecordEntry: (String, String) -> Unit,
    onDismissPersonalRecordEntry: () -> Unit,
    onNext: () -> Unit,
    onSkip: () -> Unit,
    onBack: () -> Unit,
    onRetryLoad: () -> Unit,
    onHandoff: (AppTab) -> Unit,
    onClose: () -> Unit
) {
    val committed = state.committed
    val controlsEnabled = !state.saving && !state.finished
    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .onboardingTestTagsAsResourceId()
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
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
                OnboardingStepEditor(
                    state = state,
                    enabled = controlsEnabled,
                    onEdit = onEdit,
                    onSavePersonalRecordEntry = onSavePersonalRecordEntry,
                    onOpenPersonalRecordEntry = onOpenPersonalRecordEntry,
                    onEditPersonalRecordEntry = onEditPersonalRecordEntry,
                    onDismissPersonalRecordEntry = onDismissPersonalRecordEntry,
                    onHandoff = onHandoff
                )
            }
        }
        OnboardingActionBar(
            state = state,
            controlsEnabled = controlsEnabled,
            onBack = onBack,
            onNext = onNext,
            onSkip = onSkip,
            onClose = onClose
        )
    }
}

/**
 * Back / Next / Skip / Close. Next is unreachable unless the current step's draft is acceptable, and
 * every control is disabled while a save is in flight; Close stays available so a read that failed
 * without a committed boundary can still be left without writing.
 */
@Composable
private fun OnboardingActionBar(
    state: OnboardingUiState,
    controlsEnabled: Boolean,
    onBack: () -> Unit,
    onNext: () -> Unit,
    onSkip: () -> Unit,
    onClose: () -> Unit
) {
    val writeEnabled = controlsEnabled && state.committed != null
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(OnboardingTestTags.ACTION_BAR)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = stringResource(Res.string.onboarding_saved_on_next),
            style = MaterialTheme.typography.bodySmall
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = onBack,
                enabled = writeEnabled && state.step != SetupStep.WELCOME
            ) {
                Text(stringResource(Res.string.onboarding_back))
            }
            Button(
                onClick = onNext,
                enabled = writeEnabled && isSubmittable(state.input)
            ) {
                Text(stringResource(Res.string.onboarding_next))
            }
            TextButton(onClick = onSkip, enabled = writeEnabled) {
                Text(stringResource(Res.string.onboarding_skip))
            }
            TextButton(onClick = onClose, enabled = controlsEnabled) {
                Text(stringResource(Res.string.onboarding_close))
            }
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
