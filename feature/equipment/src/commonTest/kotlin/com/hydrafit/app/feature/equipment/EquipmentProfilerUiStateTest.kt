package com.hydrafit.app.feature.equipment

import com.hydrafit.app.core.domain.fatigue.MuscleGroup
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EquipmentProfilerUiStateTest {

    @Test
    fun aStationGridAcceptsTheLocaleSeparatorAndRefusesGrouping() {
        val comma = com.hydrafit.app.core.userdata.presentation.PresentationNumberFormat(',')
        val valid = StationEditorState(
            equipmentId = com.hydrafit.app.core.domain.equipment.EquipmentTag.BARBELL,
            name = "Rack",
            minInput = "20,5",
            maxInput = "120,5",
            incrementInput = "2,5",
            numberFormat = comma
        )
        assertTrue(valid.canSave)
        assertFalse(valid.copy(maxInput = "1.234,5").canSave)
        assertFalse(valid.copy(incrementInput = "0").canSave)
    }

    private val base = ExerciseEditorState(
        isNew = true,
        isCustom = true,
        name = "My Move",
        involvements = mapOf(MuscleGroup.QUADS to 1.0)
    )

    @Test
    fun anOpenInvalidNumericDraftBlocksSave() {
        assertTrue(base.canSave)

        val explicitNone = base.copy(
            involvementEditor = InvolvementEditorState.forMuscle(MuscleGroup.GLUTES, null)
        )
        assertTrue(explicitNone.canSave)

        val outOfRange = explicitNone.copy(
            involvementEditor = requireNotNull(explicitNone.involvementEditor).copy(input = "1.5")
        )
        assertFalse(outOfRange.canSave)

        val blank = explicitNone.copy(
            involvementEditor = requireNotNull(explicitNone.involvementEditor).copy(input = "")
        )
        assertFalse(blank.canSave)
    }

    @Test
    fun saveStillRequiresANameAndAtLeastOneInvolvement() {
        assertFalse(base.copy(name = "  ").canSave)
        assertFalse(base.copy(involvements = emptyMap()).canSave)
        assertTrue(base.canSave)
    }
}
