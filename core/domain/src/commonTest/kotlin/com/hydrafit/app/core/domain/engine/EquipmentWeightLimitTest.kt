package com.hydrafit.app.core.domain.engine

import com.hydrafit.app.core.domain.equipment.EquipmentTag
import com.hydrafit.app.core.domain.equipment.Exercise
import com.hydrafit.app.core.domain.equipment.MovementPattern
import com.hydrafit.app.core.domain.fatigue.MuscleGroup
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class EquipmentWeightLimitTest {

    private val machineFly = Exercise(
        id = "cable-fly",
        name = "Cable Fly",
        requiredEquipment = setOf(EquipmentTag.CABLE_MACHINE),
        primaryMuscles = setOf(MuscleGroup.CHEST),
        movementPattern = MovementPattern.CHEST_FLY
    )

    @Test
    fun hasNoCeilingWhenTheEquipmentIsUnbounded() {
        assertNull(EquipmentWeightLimit.ceilingFor(machineFly, emptyMap()))
    }

    @Test
    fun ceilingIsTheLowestMaximumAmongTheRequiredEquipment() {
        val exercise = machineFly.copy(
            requiredEquipment = setOf(EquipmentTag.CABLE_MACHINE, EquipmentTag.BENCH)
        )

        val ceiling = EquipmentWeightLimit.ceilingFor(
            exercise,
            mapOf(EquipmentTag.CABLE_MACHINE to 100.0, EquipmentTag.BENCH to 80.0)
        )

        assertEquals(80.0, ceiling)
    }

    @Test
    fun clampLeavesWeightsBelowTheCeilingAndLimitsWeightsAboveIt() {
        assertEquals(120.0, EquipmentWeightLimit.clamp(120.0, null))
        assertEquals(120.0, EquipmentWeightLimit.clamp(120.0, 150.0))
        assertEquals(100.0, EquipmentWeightLimit.clamp(120.0, 100.0))
    }
}
