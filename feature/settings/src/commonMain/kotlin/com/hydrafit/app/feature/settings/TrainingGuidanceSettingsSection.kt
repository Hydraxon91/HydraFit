package com.hydrafit.app.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hydrafit.app.core.domain.workout.RestAlertTiming
import hydrafit.feature.settings.generated.resources.Res
import hydrafit.feature.settings.generated.resources.settings_rest_alerts
import hydrafit.feature.settings.generated.resources.settings_rest_alerts_denied
import hydrafit.feature.settings.generated.resources.settings_rest_alerts_description
import hydrafit.feature.settings.generated.resources.settings_rest_alerts_late
import hydrafit.feature.settings.generated.resources.settings_rest_alerts_precise_action
import hydrafit.feature.settings.generated.resources.settings_rest_alerts_unsupported
import hydrafit.feature.settings.generated.resources.settings_rest_completion
import hydrafit.feature.settings.generated.resources.settings_rest_completion_description
import hydrafit.feature.settings.generated.resources.settings_rest_countdown
import hydrafit.feature.settings.generated.resources.settings_rest_countdown_denied
import hydrafit.feature.settings.generated.resources.settings_rest_countdown_description
import hydrafit.feature.settings.generated.resources.settings_short_rest_coaching
import hydrafit.feature.settings.generated.resources.settings_short_rest_coaching_description
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

data class RestAlertPermission(
    val supported: Boolean,
    val request: () -> Unit,
    val requestPreciseTiming: () -> Unit = {}
)

@Composable
expect fun rememberRestAlertPermission(
    onResult: (Boolean) -> Unit,
    onTimingRefresh: () -> Unit
): RestAlertPermission

internal expect fun Modifier.guidanceTestTagsAsResourceId(): Modifier

@Composable
fun TrainingGuidanceSettingsSection(
    viewModel: TrainingGuidanceSettingsViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val requests = remember { RestNotificationPermissionRequests() }
    val permission = rememberRestAlertPermission(
        onResult = { granted ->
            when (requests.takeResult()) {
                RestNotificationPermissionTarget.ALERTS -> viewModel.setAlerts(true, granted)
                RestNotificationPermissionTarget.COUNTDOWN ->
                    viewModel.setRestCountdown(true, granted)
                null -> Unit
            }
        },
        onTimingRefresh = viewModel::refreshAlertTiming
    )
    Column(
        modifier = Modifier.guidanceTestTagsAsResourceId(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (permission.supported) {
            Row(
                modifier = Modifier.fillMaxWidth().testTag("settings_rest_alerts").toggleable(
                    value = state.preferences.restAlertsEnabled,
                    role = Role.Switch,
                    onValueChange = { enabled ->
                        if (enabled) {
                            if (requests.begin(RestNotificationPermissionTarget.ALERTS)) {
                                permission.request()
                            }
                        } else {
                            viewModel.setAlerts(false, false)
                        }
                    }
                ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Switch(checked = state.preferences.restAlertsEnabled, onCheckedChange = null)
                Text(stringResource(Res.string.settings_rest_alerts))
            }
            Text(
                stringResource(Res.string.settings_rest_alerts_description),
                style = MaterialTheme.typography.bodySmall
            )
            if (state.permissionDenied) {
                Text(stringResource(Res.string.settings_rest_alerts_denied))
            }
            if (state.preferences.restAlertsEnabled &&
                state.alertTiming == RestAlertTiming.INEXACT
            ) {
                Text(stringResource(Res.string.settings_rest_alerts_late))
                TextButton(
                    onClick = permission.requestPreciseTiming,
                    modifier = Modifier.testTag("settings_rest_alerts_precise")
                ) {
                    Text(stringResource(Res.string.settings_rest_alerts_precise_action))
                }
            }
        } else {
            Text(stringResource(Res.string.settings_rest_alerts_unsupported))
        }
        Row(
            modifier = Modifier.fillMaxWidth().testTag("settings_short_rest_coaching").toggleable(
                value = state.preferences.shortRestCoachingEnabled,
                role = Role.Switch,
                onValueChange = viewModel::setCoaching
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Switch(checked = state.preferences.shortRestCoachingEnabled, onCheckedChange = null)
            Text(stringResource(Res.string.settings_short_rest_coaching))
        }
        Text(
            stringResource(Res.string.settings_short_rest_coaching_description),
            style = MaterialTheme.typography.bodySmall
        )
        if (permission.supported) {
            Row(
                modifier = Modifier.fillMaxWidth()
                    .testTag("settings_rest_countdown")
                    .toggleable(
                        value = state.preferences.restCountdownEnabled,
                        role = Role.Switch,
                        onValueChange = { enabled ->
                            if (enabled) {
                                if (requests.begin(RestNotificationPermissionTarget.COUNTDOWN)) {
                                    permission.request()
                                }
                            } else {
                                viewModel.setRestCountdown(false, false)
                            }
                        }
                    ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Switch(checked = state.preferences.restCountdownEnabled, onCheckedChange = null)
                Text(stringResource(Res.string.settings_rest_countdown))
            }
            Text(
                stringResource(Res.string.settings_rest_countdown_description),
                style = MaterialTheme.typography.bodySmall
            )
            if (state.countdownPermissionDenied) {
                Text(stringResource(Res.string.settings_rest_countdown_denied))
            }
            Row(
                modifier = Modifier.fillMaxWidth()
                    .testTag("settings_rest_completion")
                    .toggleable(
                        value = state.preferences.restCompletionSignalEnabled,
                        role = Role.Switch,
                        onValueChange = viewModel::setRestCompletionSignal
                    ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Switch(
                    checked = state.preferences.restCompletionSignalEnabled,
                    onCheckedChange = null
                )
                Text(stringResource(Res.string.settings_rest_completion))
            }
            Text(
                stringResource(Res.string.settings_rest_completion_description),
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
