package com.hydrafit.app.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDialog
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import hydrafit.feature.settings.generated.resources.Res
import hydrafit.feature.settings.generated.resources.settings_reminders_denied
import hydrafit.feature.settings.generated.resources.settings_reminders_description
import hydrafit.feature.settings.generated.resources.settings_reminders_enable
import hydrafit.feature.settings.generated.resources.settings_reminders_quiet_end
import hydrafit.feature.settings.generated.resources.settings_reminders_quiet_hours
import hydrafit.feature.settings.generated.resources.settings_reminders_quiet_hours_description
import hydrafit.feature.settings.generated.resources.settings_reminders_quiet_start
import hydrafit.feature.settings.generated.resources.settings_reminders_section
import hydrafit.feature.settings.generated.resources.settings_reminders_time
import hydrafit.feature.settings.generated.resources.settings_reminders_time_cancel
import hydrafit.feature.settings.generated.resources.settings_reminders_time_save
import hydrafit.feature.settings.generated.resources.settings_reminders_time_unset
import hydrafit.feature.settings.generated.resources.settings_reminders_unsupported
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/** Whether this platform can post workout reminders, and how the user grants that. */
data class WorkoutReminderPermission(val supported: Boolean, val request: () -> Unit)

/**
 * [onResult] reports a notification request's answer. [onPlatformRefresh] reports that notification
 * access may have changed outside this app's own request, so delivery is decided again.
 */
@Composable
expect fun rememberWorkoutReminderPermission(
    onResult: (Boolean) -> Unit,
    onPlatformRefresh: () -> Unit
): WorkoutReminderPermission

@Composable
fun WorkoutReminderSettingsSection(viewModel: WorkoutReminderSettingsViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val permission = rememberWorkoutReminderPermission(
        onResult = { viewModel.setEnabled(true, it) },
        onPlatformRefresh = viewModel::refreshDelivery
    )
    Column(
        modifier = Modifier.guidanceTestTagsAsResourceId(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = stringResource(Res.string.settings_reminders_section),
            style = MaterialTheme.typography.titleMedium
        )
        if (!permission.supported) {
            Text(
                text = stringResource(Res.string.settings_reminders_unsupported),
                style = MaterialTheme.typography.bodySmall
            )
        } else {
            Row(
                modifier = Modifier.fillMaxWidth().testTag("settings_reminders").toggleable(
                    value = state.preferences.remindersEnabled,
                    role = Role.Switch,
                    onValueChange = { enabled ->
                        if (enabled) permission.request() else viewModel.setEnabled(false, true)
                    }
                ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Switch(checked = state.preferences.remindersEnabled, onCheckedChange = null)
                Text(stringResource(Res.string.settings_reminders_enable))
            }
            Text(
                text = stringResource(Res.string.settings_reminders_description),
                style = MaterialTheme.typography.bodySmall
            )
            if (state.permissionDenied) {
                Text(
                    text = stringResource(Res.string.settings_reminders_denied),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            if (state.preferences.remindersEnabled) {
                ChosenTimeRow(
                    label = Res.string.settings_reminders_time,
                    minuteOfDay = state.preferences.reminderMinuteOfDay,
                    testTag = "settings_reminders_time",
                    onPicked = viewModel::setReminderMinuteOfDay
                )
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .testTag("settings_reminders_quiet_hours")
                        .toggleable(
                            value = state.preferences.quietHoursEnabled,
                            role = Role.Switch,
                            onValueChange = viewModel::setQuietHoursEnabled
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Switch(
                        checked = state.preferences.quietHoursEnabled,
                        onCheckedChange = null
                    )
                    Text(stringResource(Res.string.settings_reminders_quiet_hours))
                }
                if (state.preferences.quietHoursEnabled) {
                    Text(
                        text = stringResource(
                            Res.string.settings_reminders_quiet_hours_description
                        ),
                        style = MaterialTheme.typography.bodySmall
                    )
                    ChosenTimeRow(
                        label = Res.string.settings_reminders_quiet_start,
                        minuteOfDay = state.preferences.quietStartMinuteOfDay,
                        testTag = "settings_reminders_quiet_start",
                        onPicked = { start ->
                            viewModel.setQuietHours(start, state.preferences.quietEndMinuteOfDay)
                        }
                    )
                    ChosenTimeRow(
                        label = Res.string.settings_reminders_quiet_end,
                        minuteOfDay = state.preferences.quietEndMinuteOfDay,
                        testTag = "settings_reminders_quiet_end",
                        onPicked = { end ->
                            viewModel.setQuietHours(state.preferences.quietStartMinuteOfDay, end)
                        }
                    )
                }
            }
        }
    }
}

/**
 * One label and its chosen local minute, or the invitation to choose one. The row is the only place a
 * reminder time is set, so enabling reminders cannot adopt a time the user never picked.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChosenTimeRow(
    label: StringResource,
    minuteOfDay: Int?,
    testTag: String,
    onPicked: (Int) -> Unit
) {
    var picking by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth().testTag(testTag).clickable { picking = true },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(stringResource(label))
        Text(
            text = minuteOfDay?.let(::clockLabel)
                ?: stringResource(Res.string.settings_reminders_time_unset)
        )
    }
    if (picking) {
        val picker = rememberTimePickerState(
            initialHour = minuteOfDay?.div(60) ?: 0,
            initialMinute = minuteOfDay?.mod(60) ?: 0,
            is24Hour = true
        )
        TimePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        onPicked(picker.hour * 60 + picker.minute)
                        picking = false
                    }
                ) {
                    Text(stringResource(Res.string.settings_reminders_time_save))
                }
            },
            dismissButton = {
                TextButton(onClick = { picking = false }) {
                    Text(stringResource(Res.string.settings_reminders_time_cancel))
                }
            },
            title = { Text(stringResource(label)) }
        ) {
            TimePicker(state = picker)
        }
    }
}

/** A local minute of day as a 24-hour clock label, e.g. "07:30". */
internal fun clockLabel(minuteOfDay: Int): String {
    val hour = (minuteOfDay / 60).toString().padStart(2, '0')
    val minute = (minuteOfDay % 60).toString().padStart(2, '0')
    return "$hour:$minute"
}
