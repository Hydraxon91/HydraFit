package com.hydrafit.app.feature.equipment

import com.hydrafit.app.core.domain.equipment.EquipmentTag
import com.hydrafit.app.core.domain.equipment.Exercise
import com.hydrafit.app.core.domain.equipment.MovementPattern
import com.hydrafit.app.core.domain.fatigue.MuscleGroup
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The Equipment indication follows the bundled caution IDs: the approved seeded hinge rows show it,
 * other seeded rows do not, and a custom exercise can never inherit it.
 */
class EquipmentTechniqueConsiderationsTest {

    @Test
    fun theApprovedSeededRowShowsTheIndication() {
        assertTrue(row("conventional-deadlift").showsTechniqueConsiderations())
        assertTrue(row("romanian-deadlift").showsTechniqueConsiderations())
        assertTrue(row("dumbbell-rdl").showsTechniqueConsiderations())
    }

    @Test
    fun anotherSeededRowShowsNoIndication() {
        assertFalse(row("back-squat").showsTechniqueConsiderations())
    }

    @Test
    fun aCustomRowNeverShowsTheIndication() {
        assertFalse(
            row("conventional-deadlift").copy(isCustom = true).showsTechniqueConsiderations(),
            "a custom exercise has no seeded identity"
        )
    }

    private fun row(id: String) = Exercise(
        id = id,
        name = "Deadlift",
        requiredEquipment = setOf(EquipmentTag.BARBELL),
        movementPattern = MovementPattern.HINGE,
        primaryMuscles = setOf(MuscleGroup.HAMSTRINGS)
    )
}
