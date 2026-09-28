package com.hydrafit.app.core.userdata.equipment

import com.hydrafit.app.core.domain.equipment.EquipmentTag

interface EquipmentSelectionRepository {
    suspend fun selected(): Set<EquipmentTag>

    suspend fun setSelected(tags: Set<EquipmentTag>)
}
