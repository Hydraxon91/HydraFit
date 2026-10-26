package com.hydrafit.app.feature.logger

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDialog
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.hydrafit.app.core.domain.workout.LoadKind
import hydrafit.feature.logger.generated.resources.Res
import hydrafit.feature.logger.generated.resources.logger_cancel
import hydrafit.feature.logger.generated.resources.logger_confirm
import hydrafit.feature.logger.generated.resources.logger_device_name_label
import hydrafit.feature.logger.generated.resources.logger_device_rating_label
import hydrafit.feature.logger.generated.resources.logger_distance_input_label
import hydrafit.feature.logger.generated.resources.logger_distance_label
import hydrafit.feature.logger.generated.resources.logger_edit_set_failed
import hydrafit.feature.logger.generated.resources.logger_edit_set_invalid
import hydrafit.feature.logger.generated.resources.logger_edit_set_invalid_force
import hydrafit.feature.logger.generated.resources.logger_edit_set_load_fixed
import hydrafit.feature.logger.generated.resources.logger_edit_set_title
import hydrafit.feature.logger.generated.resources.logger_force_input_label
import hydrafit.feature.logger.generated.resources.logger_force_label
import hydrafit.feature.logger.generated.resources.logger_future_time_error
import hydrafit.feature.logger.generated.resources.logger_pick_time_title
import hydrafit.feature.logger.generated.resources.logger_recorded_at
import hydrafit.feature.logger.generated.resources.logger_reps_label
import hydrafit.feature.logger.generated.resources.logger_save_set
import hydrafit.feature.logger.generated.resources.logger_seconds_label
import hydrafit.feature.logger.generated.resources.logger_set_time
import hydrafit.feature.logger.generated.resources.logger_use_now
import hydrafit.feature.logger.generated.resources.logger_weight_label
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LoggedSetEditDialog(
    edit: LoggedSetEdit,
    saving: Boolean,
    failed: Boolean,
    utcOffsetMillis: Long,
    nowMillis: () -> Long,
    onRepsChanged: (String) -> Unit,
    onDurationChanged: (String) -> Unit,
    onDistanceChanged: (String) -> Unit,
    onForceChanged: (String) -> Unit,
    onDeviceNameChanged: (String) -> Unit,
    onDeviceRatingChanged: (String) -> Unit,
    onMetricChanged: (SetMetric) -> Unit,
    onWeightChanged: (String) -> Unit,
    onRirChanged: (String) -> Unit,
    onTimeChanged: (Long) -> Boolean,
    onCancel: () -> Unit,
    onSave: () -> Unit
) {
    var showDate by remember(edit.row.id) { mutableStateOf(false) }
    var showTime by remember(edit.row.id) { mutableStateOf(false) }
    var selectedDate by remember(edit.row.id) { mutableStateOf<Long?>(null) }
    var timeError by remember(edit.row.id) { mutableStateOf(false) }
    val parts = localDateTimeParts(edit.performedAtMillis, utcOffsetMillis)
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(Res.string.logger_edit_set_title, edit.row.exerciseName)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = edit.metric == SetMetric.REPETITIONS,
                        onClick = { onMetricChanged(SetMetric.REPETITIONS) },
                        enabled = !saving,
                        label = { Text(stringResource(Res.string.logger_reps_label)) }
                    )
                    FilterChip(
                        selected = edit.metric == SetMetric.DURATION,
                        onClick = { onMetricChanged(SetMetric.DURATION) },
                        enabled = !saving,
                        label = { Text(stringResource(Res.string.logger_seconds_label)) }
                    )
                    FilterChip(
                        selected = edit.metric == SetMetric.DISTANCE,
                        onClick = { onMetricChanged(SetMetric.DISTANCE) },
                        enabled = !saving,
                        label = { Text(stringResource(Res.string.logger_distance_label)) }
                    )
                    FilterChip(
                        selected = edit.metric == SetMetric.FORCE,
                        onClick = { onMetricChanged(SetMetric.FORCE) },
                        enabled = !saving,
                        label = { Text(stringResource(Res.string.logger_force_label)) }
                    )
                }
                // Only the selected metric's field is shown; the others are never submitted.
                when (edit.metric) {
                    SetMetric.FORCE -> OutlinedTextField(
                        value = edit.forceNewtons,
                        onValueChange = onForceChanged,
                        label = { Text(stringResource(Res.string.logger_force_input_label)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        enabled = !saving
                    )
                    SetMetric.DISTANCE -> OutlinedTextField(
                        value = edit.distanceMeters,
                        onValueChange = onDistanceChanged,
                        label = {
                            Text(
                                stringResource(
                                    Res.string.logger_distance_input_label,
                                    edit.distanceUnit.label
                                )
                            )
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        enabled = !saving
                    )
                    SetMetric.DURATION -> OutlinedTextField(
                        value = edit.durationSeconds,
                        onValueChange = onDurationChanged,
                        label = { Text(stringResource(Res.string.logger_seconds_label)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        enabled = !saving
                    )
                    SetMetric.REPETITIONS -> OutlinedTextField(
                        value = edit.reps,
                        onValueChange = onRepsChanged,
                        label = { Text(stringResource(Res.string.logger_reps_label)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        enabled = !saving
                    )
                }
                if (edit.metric == SetMetric.FORCE) {
                    // A force reading is load-free; its optional device is descriptive identity only.
                    OutlinedTextField(
                        value = edit.deviceName,
                        onValueChange = onDeviceNameChanged,
                        label = { Text(stringResource(Res.string.logger_device_name_label)) },
                        singleLine = true,
                        enabled = !saving
                    )
                    OutlinedTextField(
                        value = edit.deviceRatingKg,
                        onValueChange = onDeviceRatingChanged,
                        label = { Text(stringResource(Res.string.logger_device_rating_label)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        enabled = !saving
                    )
                } else {
                    if (edit.row.loadKind != LoadKind.BODYWEIGHT) {
                        OutlinedTextField(
                            value = edit.weightInput,
                            onValueChange = onWeightChanged,
                            label = {
                                Text(
                                    stringResource(
                                        Res.string.logger_weight_label,
                                        edit.weightUnit.label
                                    )
                                )
                            },
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Decimal
                            ),
                            singleLine = true,
                            enabled = !saving
                        )
                    }
                    Text(
                        stringResource(
                            Res.string.logger_edit_set_load_fixed,
                            loadWeightText(edit.row.loadKind, edit.row.weightKg, edit.weightUnit)
                        )
                    )
                }
                RirInput(
                    value = edit.rir,
                    onValueChange = onRirChanged,
                    enabled = !saving
                )
                Text(
                    stringResource(
                        Res.string.logger_recorded_at,
                        localDateLabel(parts),
                        localTimeLabel(parts)
                    )
                )
                TextButton(onClick = { showDate = true }, enabled = !saving) {
                    Text(stringResource(Res.string.logger_set_time))
                }
                TextButton(
                    onClick = { timeError = !onTimeChanged(nowMillis()) },
                    enabled = !saving
                ) {
                    Text(stringResource(Res.string.logger_use_now))
                }
                if (!edit.canSave) {
                    Text(
                        stringResource(
                            if (edit.metric == SetMetric.FORCE) {
                                Res.string.logger_edit_set_invalid_force
                            } else {
                                Res.string.logger_edit_set_invalid
                            }
                        ),
                        color = MaterialTheme.colorScheme.error
                    )
                }
                if (timeError) {
                    Text(
                        stringResource(Res.string.logger_future_time_error),
                        color = MaterialTheme.colorScheme.error
                    )
                }
                if (failed) {
                    Text(
                        stringResource(Res.string.logger_edit_set_failed),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onSave, enabled = edit.canSave && !saving) {
                Text(stringResource(Res.string.logger_save_set))
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel, enabled = !saving) {
                Text(stringResource(Res.string.logger_cancel))
            }
        }
    )
    if (showDate) {
        val picker = rememberDatePickerState(
            initialSelectedDateMillis = localDateStartOfDayUtcMillis(
                edit.performedAtMillis,
                utcOffsetMillis
            )
        )
        DatePickerDialog(
            onDismissRequest = { showDate = false },
            confirmButton = {
                TextButton(onClick = {
                    picker.selectedDateMillis?.let {
                        selectedDate = it
                        showDate = false
                        showTime = true
                    }
                }) { Text(stringResource(Res.string.logger_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showDate = false }) {
                    Text(stringResource(Res.string.logger_cancel))
                }
            }
        ) { DatePicker(state = picker) }
    }
    if (showTime) {
        val picker = rememberTimePickerState(
            initialHour = parts.hour,
            initialMinute = parts.minute,
            is24Hour = true
        )
        TimePickerDialog(
            onDismissRequest = { showTime = false },
            title = { Text(stringResource(Res.string.logger_pick_time_title)) },
            confirmButton = {
                TextButton(onClick = {
                    selectedDate?.let {
                        timeError = !onTimeChanged(
                            pickedLocalDateTimeToEpochMillis(
                                it,
                                picker.hour,
                                picker.minute,
                                utcOffsetMillis
                            )
                        )
                    }
                    showTime = false
                }) { Text(stringResource(Res.string.logger_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showTime = false }) {
                    Text(stringResource(Res.string.logger_cancel))
                }
            }
        ) { TimePicker(state = picker) }
    }
}
