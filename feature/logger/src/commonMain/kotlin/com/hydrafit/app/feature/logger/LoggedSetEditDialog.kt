package com.hydrafit.app.feature.logger

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
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
import hydrafit.feature.logger.generated.resources.logger_edit_set_failed
import hydrafit.feature.logger.generated.resources.logger_edit_set_invalid
import hydrafit.feature.logger.generated.resources.logger_edit_set_load_fixed
import hydrafit.feature.logger.generated.resources.logger_edit_set_title
import hydrafit.feature.logger.generated.resources.logger_future_time_error
import hydrafit.feature.logger.generated.resources.logger_pick_time_title
import hydrafit.feature.logger.generated.resources.logger_recorded_at
import hydrafit.feature.logger.generated.resources.logger_reps_label
import hydrafit.feature.logger.generated.resources.logger_save_set
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
                OutlinedTextField(
                    value = edit.reps,
                    onValueChange = onRepsChanged,
                    label = { Text(stringResource(Res.string.logger_reps_label)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    enabled = !saving
                )
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
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
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
                        stringResource(Res.string.logger_edit_set_invalid),
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
