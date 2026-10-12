package com.hydrafit.app.feature.logger

import com.hydrafit.app.core.domain.equipment.Equipment
import com.hydrafit.app.core.domain.equipment.EquipmentTag
import com.hydrafit.app.core.userdata.equipment.EquipmentRepository
import com.hydrafit.app.core.userdata.equipment.EquipmentSelectionRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest

class WorkoutLoggerEquipmentTest {

    @Test
    fun mapsSelectedTagsAndOnlyRecordedCeilings() = runTest {
        val equipment = WorkoutLoggerEquipment(
            equipmentRepository = object : EquipmentRepository {
                override fun observeAll(): kotlinx.coroutines.flow.Flow<List<Equipment>> = flowOf(
                    listOf(
                        Equipment(
                            id = EquipmentTag.BARBELL,
                            name = "Barbell",
                            isBuiltIn = true,
                            maxWeightKg = 200.0
                        ),
                        Equipment(
                            id = EquipmentTag.BODYWEIGHT,
                            name = "Bodyweight",
                            isBuiltIn = true,
                            maxWeightKg = null
                        )
                    )
                )

                override suspend fun all(): List<Equipment> = emptyList()

                override suspend fun add(name: String): Equipment = error("unused")

                override suspend fun remove(id: EquipmentTag) = error("unused")

                override suspend fun rename(
                    id: EquipmentTag,
                    name: String,
                    maxWeightKg: Double?
                ): Equipment = error("unused")

                override suspend fun setMaxWeight(id: EquipmentTag, maxWeightKg: Double?) =
                    error("unused")
            },
            selectionRepository = object : EquipmentSelectionRepository {
                override suspend fun selected(): Set<EquipmentTag> = setOf(EquipmentTag.BARBELL)

                override fun selectedFlow(): kotlinx.coroutines.flow.Flow<Set<EquipmentTag>> =
                    flowOf(setOf(EquipmentTag.BARBELL))

                override suspend fun setSelected(tags: Set<EquipmentTag>) = error("unused")
            }
        )

        val constraints = equipment.equipmentFlow().first()

        assertEquals(setOf(EquipmentTag.BARBELL), constraints.selectedTags)
        assertEquals(mapOf(EquipmentTag.BARBELL to 200.0), constraints.maxWeights)
    }
}
