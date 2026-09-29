package com.hydrafit.app.feature.equipment

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hydrafit.app.core.domain.equipment.EquipmentTag
import hydrafit.feature.equipment.generated.resources.Res
import hydrafit.feature.equipment.generated.resources.equipment_add_button
import hydrafit.feature.equipment.generated.resources.equipment_add_label
import hydrafit.feature.equipment.generated.resources.equipment_profiler_title
import hydrafit.feature.equipment.generated.resources.equipment_remove
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun EquipmentProfilerRoute(
    modifier: Modifier = Modifier,
    viewModel: EquipmentProfilerViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    EquipmentProfilerScreen(
        state = state,
        onTagToggled = viewModel::onTagToggled,
        onNewEquipmentNameChanged = viewModel::onNewEquipmentNameChanged,
        onAddEquipment = viewModel::onAddEquipment,
        onRemoveEquipment = viewModel::onRemoveEquipment,
        modifier = modifier
    )
}

@Composable
fun EquipmentProfilerScreen(
    state: EquipmentProfilerUiState,
    onTagToggled: (EquipmentTag) -> Unit,
    onNewEquipmentNameChanged: (String) -> Unit,
    onAddEquipment: () -> Unit,
    onRemoveEquipment: (EquipmentTag) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .safeContentPadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = stringResource(Res.string.equipment_profiler_title),
            style = MaterialTheme.typography.headlineSmall
        )
        state.equipment.forEach { equipment ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = equipment.id in state.selectedTags,
                    onClick = { onTagToggled(equipment.id) },
                    label = { Text(equipment.name) }
                )
                if (!equipment.isBuiltIn) {
                    TextButton(onClick = { onRemoveEquipment(equipment.id) }) {
                        Text(stringResource(Res.string.equipment_remove))
                    }
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = state.newEquipmentName,
                onValueChange = onNewEquipmentNameChanged,
                label = { Text(stringResource(Res.string.equipment_add_label)) },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            Button(onClick = onAddEquipment, enabled = state.canAdd) {
                Text(stringResource(Res.string.equipment_add_button))
            }
        }
    }
}
