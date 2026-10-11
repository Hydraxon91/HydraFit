package com.hydrafit.app.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import hydrafit.feature.settings.generated.resources.Res
import hydrafit.feature.settings.generated.resources.settings_rest_alerts
import hydrafit.feature.settings.generated.resources.settings_rest_alerts_denied
import hydrafit.feature.settings.generated.resources.settings_rest_alerts_description
import hydrafit.feature.settings.generated.resources.settings_rest_alerts_unsupported
import hydrafit.feature.settings.generated.resources.settings_short_rest_coaching
import hydrafit.feature.settings.generated.resources.settings_short_rest_coaching_description
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

data class RestAlertPermission(val supported: Boolean, val request: () -> Unit)

@Composable
expect fun rememberRestAlertPermission(onResult: (Boolean) -> Unit): RestAlertPermission

@Composable
fun TrainingGuidanceSettingsSection(
    viewModel: TrainingGuidanceSettingsViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val permission = rememberRestAlertPermission { viewModel.setAlerts(true, it) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (permission.supported) {
            Row(
                modifier = Modifier.fillMaxWidth().toggleable(
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
        } else {
            Text(stringResource(Res.string.settings_rest_alerts_unsupported))
        }
        Row(
            modifier = Modifier.fillMaxWidth().toggleable(
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
