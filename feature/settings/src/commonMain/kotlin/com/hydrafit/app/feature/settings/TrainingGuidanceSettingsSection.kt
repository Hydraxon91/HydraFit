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
    val permission = rememberRestAlertPermission(
        onResult = { viewModel.setAlerts(true, it) },
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
                        if (enabled) permission.request() else viewModel.setAlerts(false, false)
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
    }
}
