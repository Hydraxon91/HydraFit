package com.hydrafit.app.feature.logger

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.hydrafit.app.core.domain.equipment.EquipmentTag
import com.hydrafit.app.core.domain.schedule.OccurrenceReplacementFailure
import com.hydrafit.app.core.domain.unit.WeightUnit
import com.hydrafit.app.core.domain.workout.LoadKind
import hydrafit.feature.logger.generated.resources.Res
import hydrafit.feature.logger.generated.resources.logger_cancel
import hydrafit.feature.logger.generated.resources.logger_replace_empty
import hydrafit.feature.logger.generated.resources.logger_replace_loading
import hydrafit.feature.logger.generated.resources.logger_replace_note
import hydrafit.feature.logger.generated.resources.logger_replace_recorded
import hydrafit.feature.logger.generated.resources.logger_replace_stale
import hydrafit.feature.logger.generated.resources.logger_replace_target
import hydrafit.feature.logger.generated.resources.logger_replace_target_timed
import hydrafit.feature.logger.generated.resources.logger_replace_title
import org.jetbrains.compose.resources.stringResource

@Composable
fun OccurrenceReplacementDialog(
    state: OccurrenceReplacementUiState,
    unit: WeightUnit,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    if (!state.open) return
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.logger_replace_title)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(stringResource(Res.string.logger_replace_note))
                when {
                    state.loading -> Text(stringResource(Res.string.logger_replace_loading))
                    state.failure != null -> Text(
                        stringResource(
                            if (state.failure == OccurrenceReplacementFailure.RECORDED_SLOT) {
                                Res.string.logger_replace_recorded
                            } else {
                                Res.string.logger_replace_stale
                            }
                        )
                    )
                    state.choices?.alternatives?.isEmpty() == true ->
                        Text(stringResource(Res.string.logger_replace_empty))
                    else -> state.choices?.alternatives?.forEach { entry ->
                        TextButton(
                            onClick = { onConfirm(entry.exerciseId) },
                            enabled = !state.saving
                        ) {
                            val seconds = entry.durationSeconds
                            val basis = loadBasisFor(
                                requiresDumbbell = EquipmentTag.DUMBBELL in entry.requiredEquipment,
                                isUnilateral = entry.isUnilateral
                            )
                            val weight = loadWeightText(
                                entry.loadKind,
                                entry.weightKg,
                                unit,
                                basis
                            )
                            val noLoadReason = if (entry.loadKind == LoadKind.EXTERNAL &&
                                entry.weightKg == null
                            ) {
                                noLoadReasonText(entry.explanation)
                            } else {
                                null
                            }
                            val target = if (seconds != null) {
                                stringResource(
                                    Res.string.logger_replace_target_timed,
                                    entry.exerciseName,
                                    entry.sets,
                                    seconds,
                                    weight
                                )
                            } else {
                                stringResource(
                                    Res.string.logger_replace_target,
                                    entry.exerciseName,
                                    entry.sets,
                                    entry.reps ?: 0,
                                    weight
                                )
                            }
                            Text(
                                text = noLoadReason?.let { "$target\n$it" } ?: target
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !state.saving) {
                Text(stringResource(Res.string.logger_cancel))
            }
        }
    )
}
