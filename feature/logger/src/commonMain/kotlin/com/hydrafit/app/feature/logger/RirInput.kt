package com.hydrafit.app.feature.logger

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import hydrafit.feature.logger.generated.resources.Res
import hydrafit.feature.logger.generated.resources.logger_rir_explanation
import hydrafit.feature.logger.generated.resources.logger_rir_label
import org.jetbrains.compose.resources.stringResource

private val rirQuickPicks = listOf(0, 1, 2, 3)

@Composable
internal fun RirInput(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onDone: (() -> Unit)? = null
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(stringResource(Res.string.logger_rir_label)) },
            singleLine = true,
            enabled = enabled,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                imeAction = if (onDone == null) ImeAction.Default else ImeAction.Done
            ),
            keyboardActions = KeyboardActions(onDone = { onDone?.invoke() })
        )
        Text(
            text = stringResource(Res.string.logger_rir_explanation),
            style = MaterialTheme.typography.bodySmall
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            rirQuickPicks.forEach { rir ->
                val pickedValue = rir.toString()
                FilterChip(
                    selected = value == pickedValue,
                    onClick = {
                        onValueChange(nextRirQuickPickValue(value, rir))
                    },
                    enabled = enabled,
                    label = { Text(pickedValue) }
                )
            }
        }
    }
}

internal fun nextRirQuickPickValue(currentValue: String, pickedRir: Int): String =
    if (currentValue == pickedRir.toString()) "" else pickedRir.toString()
