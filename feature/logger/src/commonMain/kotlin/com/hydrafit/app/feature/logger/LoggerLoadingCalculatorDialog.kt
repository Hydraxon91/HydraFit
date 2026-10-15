package com.hydrafit.app.feature.logger

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.hydrafit.app.core.domain.workout.LoadKind
import hydrafit.feature.logger.generated.resources.Res
import hydrafit.feature.logger.generated.resources.logger_dismiss
import hydrafit.feature.logger.generated.resources.logger_loading_calculator_approximate
import hydrafit.feature.logger.generated.resources.logger_loading_calculator_equipment_label
import hydrafit.feature.logger.generated.resources.logger_loading_calculator_exact_total
import hydrafit.feature.logger.generated.resources.logger_loading_calculator_failed
import hydrafit.feature.logger.generated.resources.logger_loading_calculator_invalid_target
import hydrafit.feature.logger.generated.resources.logger_loading_calculator_kind_dumbbell
import hydrafit.feature.logger.generated.resources.logger_loading_calculator_kind_plate
import hydrafit.feature.logger.generated.resources.logger_loading_calculator_lower
import hydrafit.feature.logger.generated.resources.logger_loading_calculator_lower_per_dumbbell
import hydrafit.feature.logger.generated.resources.logger_loading_calculator_missing_lower
import hydrafit.feature.logger.generated.resources.logger_loading_calculator_missing_upper
import hydrafit.feature.logger.generated.resources.logger_loading_calculator_no_inventory_dumbbell
import hydrafit.feature.logger.generated.resources.logger_loading_calculator_no_inventory_plate
import hydrafit.feature.logger.generated.resources.logger_loading_calculator_no_selection
import hydrafit.feature.logger.generated.resources.logger_loading_calculator_out_of_range
import hydrafit.feature.logger.generated.resources.logger_loading_calculator_per_dumbbell
import hydrafit.feature.logger.generated.resources.logger_loading_calculator_per_side
import hydrafit.feature.logger.generated.resources.logger_loading_calculator_prescribed_added
import hydrafit.feature.logger.generated.resources.logger_loading_calculator_prescribed_external
import hydrafit.feature.logger.generated.resources.logger_loading_calculator_profile
import hydrafit.feature.logger.generated.resources.logger_loading_calculator_read_only_note
import hydrafit.feature.logger.generated.resources.logger_loading_calculator_run
import hydrafit.feature.logger.generated.resources.logger_loading_calculator_running
import hydrafit.feature.logger.generated.resources.logger_loading_calculator_target_label
import hydrafit.feature.logger.generated.resources.logger_loading_calculator_title
import hydrafit.feature.logger.generated.resources.logger_loading_calculator_unit_note
import hydrafit.feature.logger.generated.resources.logger_loading_calculator_upper
import hydrafit.feature.logger.generated.resources.logger_loading_calculator_upper_per_dumbbell
import org.jetbrains.compose.resources.stringResource

/**
 * Stable external identities for the loading helper's controls. They are technical metadata for UI
 * automation: never user-facing copy and never an accessibility description.
 */
internal object LoggerLoadingCalculatorTestTags {
    /** The always-available manual action in the manual-log form. */
    const val MANUAL_ACTION = "logger_loading_calculator_manual"

    const val DIALOG = "logger_loading_calculator_dialog"
    const val TARGET = "logger_loading_calculator_target"
    const val RUN = "logger_loading_calculator_run"
    const val DISMISS = "logger_loading_calculator_dismiss"

    fun kind(kind: LoggerLoadingCalculatorKind): String =
        "logger_loading_calculator_kind_${kind.name.lowercase()}"

    fun equipment(id: String): String = "logger_loading_calculator_equipment_$id"

    /** One draft's contextual action, keyed by the draft's frozen slot identity. */
    fun draftAction(sourceId: String): String = "logger_loading_calculator_draft_$sourceId"

    /** One guided entry's contextual action, keyed by its occurrence entry id. */
    fun guidedAction(entryId: Long): String = "logger_loading_calculator_guided_$entryId"
}

/**
 * The read-only loading helper: the active profile's saved bars/dumbbells in the inventory's own
 * unit, one native target and its resolved arrangement. It offers no apply action, because nothing
 * here writes a set, edits the prescription or changes the Logger's draft.
 */
@Composable
internal fun LoggerLoadingCalculatorDialog(
    state: LoggerLoadingCalculatorState,
    onKindChanged: (LoggerLoadingCalculatorKind) -> Unit,
    onEquipmentSelected: (String) -> Unit,
    onTargetChanged: (String) -> Unit,
    onRun: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier
            .testTag(LoggerLoadingCalculatorTestTags.DIALOG)
            .loggerTestTagsAsResourceId(),
        title = { Text(stringResource(Res.string.logger_loading_calculator_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = stringResource(
                        Res.string.logger_loading_calculator_profile,
                        state.profileName
                    )
                )
                Text(
                    text = stringResource(
                        Res.string.logger_loading_calculator_unit_note,
                        state.inventoryUnit.label
                    ),
                    style = MaterialTheme.typography.bodySmall
                )
                state.seed?.let { seed ->
                    Text(
                        text = stringResource(
                            if (seed.loadKind == LoadKind.ADDED) {
                                Res.string.logger_loading_calculator_prescribed_added
                            } else {
                                Res.string.logger_loading_calculator_prescribed_external
                            },
                            formatLoggerLoadingAmount(seed.targetTicks, state.inventoryUnit)
                        ),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    seed.approximation?.let { approximation ->
                        Text(
                            text = stringResource(
                                Res.string.logger_loading_calculator_approximate,
                                approximation.prescribedKg,
                                approximation.nativeAmount,
                                approximation.unitLabel
                            ),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                Column(
                    modifier = Modifier.fillMaxWidth().selectableGroup(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(stringResource(Res.string.logger_loading_calculator_equipment_label))
                    LoggerLoadingCalculatorKind.entries.forEach { kind ->
                        ChoiceRow(
                            label = stringResource(
                                if (kind == LoggerLoadingCalculatorKind.PLATE) {
                                    Res.string.logger_loading_calculator_kind_plate
                                } else {
                                    Res.string.logger_loading_calculator_kind_dumbbell
                                }
                            ),
                            selected = state.kind == kind,
                            modifier = Modifier.testTag(LoggerLoadingCalculatorTestTags.kind(kind)),
                            onSelect = { onKindChanged(kind) }
                        )
                    }
                }
                if (state.hasSavedEquipment) {
                    Column(
                        modifier = Modifier.fillMaxWidth().selectableGroup(),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        state.options.forEach { option ->
                            ChoiceRow(
                                label = option.label,
                                selected = state.selectedId == option.id,
                                modifier = Modifier.testTag(
                                    LoggerLoadingCalculatorTestTags.equipment(option.id)
                                ),
                                onSelect = { onEquipmentSelected(option.id) }
                            )
                        }
                    }
                } else {
                    Text(
                        text = stringResource(
                            if (state.kind == LoggerLoadingCalculatorKind.PLATE) {
                                Res.string.logger_loading_calculator_no_inventory_plate
                            } else {
                                Res.string.logger_loading_calculator_no_inventory_dumbbell
                            }
                        ),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                OutlinedTextField(
                    value = state.targetInput,
                    onValueChange = onTargetChanged,
                    label = {
                        Text(
                            stringResource(
                                Res.string.logger_loading_calculator_target_label,
                                state.inventoryUnit.label
                            )
                        )
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(LoggerLoadingCalculatorTestTags.TARGET)
                )
                if (state.hasSavedEquipment && state.selectedId == null) {
                    Text(
                        text = stringResource(Res.string.logger_loading_calculator_no_selection),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                state.failure?.let { failure ->
                    Text(
                        text = loadingCalculatorFailureText(failure, state),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                if (state.isRunning) {
                    Text(
                        text = stringResource(Res.string.logger_loading_calculator_running),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                state.result?.let { result ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        result.options.forEach { option ->
                            Column {
                                Text(
                                    text = loadingCalculatorOptionText(option),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                option.plateLines.forEach { line ->
                                    Text(
                                        text = stringResource(
                                            Res.string.logger_loading_calculator_per_side,
                                            line.denomination,
                                            line.perSideCount
                                        ),
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }
                        if (result.missingLower) {
                            Text(
                                text = stringResource(
                                    Res.string.logger_loading_calculator_missing_lower
                                ),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        if (result.missingUpper) {
                            Text(
                                text = stringResource(
                                    Res.string.logger_loading_calculator_missing_upper
                                ),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
                Text(
                    text = stringResource(Res.string.logger_loading_calculator_read_only_note),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onRun,
                enabled = state.canRun,
                modifier = Modifier.testTag(LoggerLoadingCalculatorTestTags.RUN)
            ) {
                Text(stringResource(Res.string.logger_loading_calculator_run))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag(LoggerLoadingCalculatorTestTags.DISMISS)
            ) {
                Text(stringResource(Res.string.logger_dismiss))
            }
        }
    )
}

/** A labelled radio row with a full-width touch target, matching the project's selection rows. */
@Composable
private fun ChoiceRow(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onSelect: () -> Unit
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(label)
    }
}

/** One option's label, keeping a dumbbell value per dumbbell instead of doubling it. */
@Composable
private fun loadingCalculatorOptionText(option: LoggerLoadingResultOption): String =
    when (option.label) {
        LoggerLoadingResultLabel.EXACT ->
            if (option.perDumbbell) {
                stringResource(
                    Res.string.logger_loading_calculator_per_dumbbell,
                    option.totalWeight
                )
            } else {
                stringResource(Res.string.logger_loading_calculator_exact_total, option.totalWeight)
            }

        LoggerLoadingResultLabel.LOWER ->
            if (option.perDumbbell) {
                stringResource(
                    Res.string.logger_loading_calculator_lower_per_dumbbell,
                    option.totalWeight
                )
            } else {
                stringResource(Res.string.logger_loading_calculator_lower, option.totalWeight)
            }

        LoggerLoadingResultLabel.UPPER ->
            if (option.perDumbbell) {
                stringResource(
                    Res.string.logger_loading_calculator_upper_per_dumbbell,
                    option.totalWeight
                )
            } else {
                stringResource(Res.string.logger_loading_calculator_upper, option.totalWeight)
            }
    }

/** A typed failure message; never a raw exception or a solver message. */
@Composable
private fun loadingCalculatorFailureText(
    failure: LoggerLoadingCalculatorFailure,
    state: LoggerLoadingCalculatorState
): String = when (failure) {
    LoggerLoadingCalculatorFailure.INVALID_TARGET ->
        stringResource(Res.string.logger_loading_calculator_invalid_target)

    LoggerLoadingCalculatorFailure.OUT_OF_RANGE -> stringResource(
        Res.string.logger_loading_calculator_out_of_range,
        loggerLoadingMaxWholeAmount().toString(),
        state.inventoryUnit.label
    )

    LoggerLoadingCalculatorFailure.FAILED ->
        stringResource(Res.string.logger_loading_calculator_failed)

    LoggerLoadingCalculatorFailure.NO_EQUIPMENT ->
        stringResource(
            if (state.kind == LoggerLoadingCalculatorKind.PLATE) {
                Res.string.logger_loading_calculator_no_inventory_plate
            } else {
                Res.string.logger_loading_calculator_no_inventory_dumbbell
            }
        )
}
