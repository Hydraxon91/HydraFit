package com.hydrafit.app.core.userdata.equipment

import com.hydrafit.app.core.domain.equipment.Equipment
import com.hydrafit.app.core.domain.equipment.EquipmentTag
import kotlinx.coroutines.flow.Flow

/** The user's equipment catalog: the seeded built-ins plus any equipment they have added. */
interface EquipmentRepository {
    fun observeAll(): Flow<List<Equipment>>

    suspend fun all(): List<Equipment>

    suspend fun add(name: String): Equipment

    suspend fun remove(id: EquipmentTag)
}
