package com.hydrafit.app.feature.logger

import com.hydrafit.app.core.domain.equipment.Equipment
import com.hydrafit.app.core.domain.equipment.EquipmentTag
import com.hydrafit.app.core.domain.equipment.LoadingInventory
import com.hydrafit.app.core.userdata.equipment.EquipmentProfile
import com.hydrafit.app.core.userdata.equipment.EquipmentProfileContext
import com.hydrafit.app.core.userdata.equipment.EquipmentProfileRepository
import com.hydrafit.app.core.userdata.equipment.EquipmentProfileState
import com.hydrafit.app.core.userdata.equipment.EquipmentRepository
import com.hydrafit.app.core.userdata.equipment.EquipmentSelectionRepository
import kotlinx.coroutines.flow.combine

/** Keeps the existing warm-up fixtures useful; production reads one coherent profile snapshot. */
internal fun WorkoutLoggerEquipment(
    equipmentRepository: EquipmentRepository,
    selectionRepository: EquipmentSelectionRepository
): WorkoutLoggerEquipment = WorkoutLoggerEquipment(object : EquipmentProfileRepository {
    override fun observe() = combine(
        equipmentRepository.observeAll(),
        selectionRepository.selectedFlow(),
        ::snapshot
    )

    override suspend fun current() =
        snapshot(equipmentRepository.all(), selectionRepository.selected())

    private fun snapshot(equipment: List<Equipment>, selected: Set<EquipmentTag>) =
        EquipmentProfileState(
            EquipmentProfileContext(1L, 0L),
            listOf(
                EquipmentProfile(
                    1L,
                    "Test",
                    selected,
                    equipment.mapNotNull { row -> row.maxWeightKg?.let { row.id to it } }.toMap(),
                    LoadingInventory()
                )
            )
        )

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
})
