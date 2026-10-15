package com.hydrafit.app.core.userdata.equipment

import com.hydrafit.app.core.domain.equipment.EquipmentTag
import com.hydrafit.app.core.domain.equipment.LoadingInventory
import kotlinx.coroutines.flow.Flow

/** The revision covers switching as well as edits, including switching away and back. */
typealias EquipmentProfileContext = com.hydrafit.app.core.domain.equipment.EquipmentProfileContext

data class EquipmentProfile(
    val id: Long,
    val name: String,
    val selected: Set<EquipmentTag>,
    val maxWeightsKg: Map<EquipmentTag, Double>,
    val loadingInventory: LoadingInventory
)

data class EquipmentProfileState(
    val context: EquipmentProfileContext,
    val profiles: List<EquipmentProfile>
) {
    val active: EquipmentProfile get() = profiles.single { it.id == context.profileId }
}

interface EquipmentProfileRepository {
    fun observe(): Flow<EquipmentProfileState>

    suspend fun current(): EquipmentProfileState

    suspend fun create(name: String, copyFrom: Long? = null): Long

    suspend fun rename(context: EquipmentProfileContext, name: String)

    suspend fun activate(context: EquipmentProfileContext, profileId: Long)

    suspend fun delete(
        context: EquipmentProfileContext,
        profileId: Long,
        replacementId: Long? = null
    )

    suspend fun saveEquipment(
        context: EquipmentProfileContext,
        selected: Set<EquipmentTag>,
        maxWeightsKg: Map<EquipmentTag, Double>
    )

    suspend fun saveLoadingInventory(context: EquipmentProfileContext, inventory: LoadingInventory)
}

class StaleEquipmentProfileException : IllegalStateException("Equipment profile context changed")
