package com.hydrafit.app.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hydrafit.app.core.domain.unit.DistanceUnit
import com.hydrafit.app.core.domain.unit.WeightUnit
import hydrafit.feature.settings.generated.resources.Res
import hydrafit.feature.settings.generated.resources.settings_distance_unit_section
import hydrafit.feature.settings.generated.resources.settings_home_units
import hydrafit.feature.settings.generated.resources.settings_unit_section
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun UnitsRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    UnitsScreen(
        state = state,
        onWeightUnitSelected = viewModel::onWeightUnitSelected,
        onDistanceUnitSelected = viewModel::onDistanceUnitSelected,
        onBack = onBack,
        modifier = modifier
    )
}

@Composable
fun UnitsScreen(
    state: SettingsUiState,
    onWeightUnitSelected: (WeightUnit) -> Unit,
    onDistanceUnitSelected: (DistanceUnit) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SettingsPageHeader(title = stringResource(Res.string.settings_home_units), onBack = onBack)
        Text(
            text = stringResource(Res.string.settings_unit_section),
            style = MaterialTheme.typography.titleMedium
        )
        WeightUnit.entries.forEach { unit ->
            SettingsRadioRow(
                label = stringResource(unit.labelResource()),
                selected = state.weightUnit == unit,
                onSelected = { onWeightUnitSelected(unit) }
            )
        }
        Text(
            text = stringResource(Res.string.settings_distance_unit_section),
            style = MaterialTheme.typography.titleMedium
        )
        DistanceUnit.entries.forEach { unit ->
            SettingsRadioRow(
                label = stringResource(unit.labelResource()),
                selected = state.distanceUnit == unit,
                onSelected = { onDistanceUnitSelected(unit) }
            )
        }
    }
}
