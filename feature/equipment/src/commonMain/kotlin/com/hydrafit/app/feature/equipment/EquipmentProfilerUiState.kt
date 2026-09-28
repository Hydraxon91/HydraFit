package com.hydrafit.app.feature.equipment

import com.hydrafit.app.core.domain.equipment.EquipmentTag

data class EquipmentProfilerUiState(
    val availableTags: List<EquipmentTag> = EquipmentTag.entries,
    val selectedTags: Set<EquipmentTag> = emptySet(),
    val isLoading: Boolean = true
)
