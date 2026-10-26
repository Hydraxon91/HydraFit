package com.hydrafit.app

import com.hydrafit.app.core.domain.equipment.EquipmentStation
import com.hydrafit.app.core.domain.equipment.EquipmentTag
import com.hydrafit.app.core.domain.equipment.LoadingInventory
import com.hydrafit.app.core.userdata.equipment.EquipmentProfile
import com.hydrafit.app.core.userdata.equipment.EquipmentProfileContext
import com.hydrafit.app.core.userdata.equipment.EquipmentProfileRepository
import com.hydrafit.app.core.userdata.equipment.EquipmentProfileState
import kotlinx.coroutines.flow.flowOf

internal object FakeEquipmentProfiles : EquipmentProfileRepository {
    private val state = EquipmentProfileState(
        EquipmentProfileContext(1L, 0L),
        listOf(EquipmentProfile(1L, "Test", emptySet(), emptyMap(), LoadingInventory()))
    )
    override fun observe() = flowOf(state)
    override suspend fun current() = state
    override suspend fun create(name: String, copyFrom: Long?): Long = error("unused")
    override suspend fun rename(context: EquipmentProfileContext, name: String) = error("unused")
    override suspend fun activate(context: EquipmentProfileContext, profileId: Long) =
        error("unused")
    override suspend fun delete(
        context: EquipmentProfileContext,
        profileId: Long,
        replacementId: Long?
    ) = error("unused")
    override suspend fun saveEquipment(
        context: EquipmentProfileContext,
        selected: Set<EquipmentTag>,
        maxWeightsKg: Map<EquipmentTag, Double>
    ) = error("unused")
    override suspend fun saveLoadingInventory(
        context: EquipmentProfileContext,
        inventory: LoadingInventory
    ) = error("unused")
    override suspend fun saveStation(
        context: EquipmentProfileContext,
        station: EquipmentStation
    ): Long = error("unused")
    override suspend fun retireStation(context: EquipmentProfileContext, stationId: Long) =
        error("unused")
}
