package com.hydrafit.app.core.userdata.equipment

import com.hydrafit.app.core.domain.equipment.EquipmentTag
import kotlinx.coroutines.flow.Flow

interface EquipmentSelectionRepository {
    suspend fun selected(): Set<EquipmentTag>

    fun selectedFlow(): Flow<Set<EquipmentTag>>

    suspend fun setSelected(tags: Set<EquipmentTag>)
}
