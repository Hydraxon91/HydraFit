package com.hydrafit.app.feature.equipment

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hydrafit.app.core.domain.equipment.EquipmentTag
import hydrafit.feature.equipment.generated.resources.Res
import hydrafit.feature.equipment.generated.resources.equipment_profiler_title
import hydrafit.feature.equipment.generated.resources.equipment_tag_barbell
import hydrafit.feature.equipment.generated.resources.equipment_tag_bench
import hydrafit.feature.equipment.generated.resources.equipment_tag_bodyweight
import hydrafit.feature.equipment.generated.resources.equipment_tag_cable_machine
import hydrafit.feature.equipment.generated.resources.equipment_tag_dumbbell
import hydrafit.feature.equipment.generated.resources.equipment_tag_kettlebell
import hydrafit.feature.equipment.generated.resources.equipment_tag_pull_up_bar
import hydrafit.feature.equipment.generated.resources.equipment_tag_resistance_band
import org.jetbrains.compose.resources.StringResource
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
        modifier = modifier
    )
}

@Composable
fun EquipmentProfilerScreen(
    state: EquipmentProfilerUiState,
    onTagToggled: (EquipmentTag) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .safeContentPadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = stringResource(Res.string.equipment_profiler_title),
            style = MaterialTheme.typography.headlineSmall
        )
        state.availableTags.forEach { tag ->
            FilterChip(
                selected = tag in state.selectedTags,
                onClick = { onTagToggled(tag) },
                label = { Text(stringResource(tag.labelResource())) }
            )
        }
    }
}

private fun EquipmentTag.labelResource(): StringResource = when (this) {
    EquipmentTag.BARBELL -> Res.string.equipment_tag_barbell
    EquipmentTag.DUMBBELL -> Res.string.equipment_tag_dumbbell
    EquipmentTag.KETTLEBELL -> Res.string.equipment_tag_kettlebell
    EquipmentTag.BENCH -> Res.string.equipment_tag_bench
    EquipmentTag.PULL_UP_BAR -> Res.string.equipment_tag_pull_up_bar
    EquipmentTag.RESISTANCE_BAND -> Res.string.equipment_tag_resistance_band
    EquipmentTag.CABLE_MACHINE -> Res.string.equipment_tag_cable_machine
    EquipmentTag.BODYWEIGHT -> Res.string.equipment_tag_bodyweight
}
