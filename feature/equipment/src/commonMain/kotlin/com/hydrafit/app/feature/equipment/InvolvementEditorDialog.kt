package com.hydrafit.app.feature.equipment

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import hydrafit.feature.equipment.generated.resources.Res
import hydrafit.feature.equipment.generated.resources.equipment_cancel
import hydrafit.feature.equipment.generated.resources.equipment_involvement_apply
import hydrafit.feature.equipment.generated.resources.equipment_involvement_invalid
import hydrafit.feature.equipment.generated.resources.equipment_involvement_value
import hydrafit.feature.equipment.generated.resources.equipment_profile_none
import org.jetbrains.compose.resources.stringResource

/**
 * Per-muscle involvement editor: a numeric field for the exact stored double, with the named tier
 * shortcuts beside it. Apply commits, Cancel discards the draft, and an invalid (including blank)
 * value disables Apply so it can never be written.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun InvolvementEditorDialog(
    state: InvolvementEditorState,
    onInputChanged: (String) -> Unit,
    onTierSelected: (Double?) -> Unit,
    onApply: () -> Unit,
    onCancel: () -> Unit
) {
    val selected = state.enteredWeight
    AlertDialog(
        onDismissRequest = onCancel,
        confirmButton = {
            Button(onClick = onApply, enabled = state.isValid) {
                Text(stringResource(Res.string.equipment_involvement_apply))
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) {
                Text(stringResource(Res.string.equipment_cancel))
            }
        },
        title = { Text(stringResource(state.muscle.labelResource())) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = state.input,
                    onValueChange = onInputChanged,
                    label = { Text(stringResource(Res.string.equipment_involvement_value)) },
                    singleLine = true,
                    isError = !state.isValid,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = selected == 0.0,
                        onClick = { onTierSelected(null) },
                        label = { Text(stringResource(Res.string.equipment_profile_none)) }
                    )
                    InvolvementTier.entries.forEach { tier ->
                        FilterChip(
                            selected = selected == tier.weight,
                            onClick = { onTierSelected(tier.weight) },
                            label = { Text(stringResource(tier.labelResource())) }
                        )
                    }
                }
                if (!state.isValid) {
                    Text(
                        text = stringResource(Res.string.equipment_involvement_invalid),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    )
}
