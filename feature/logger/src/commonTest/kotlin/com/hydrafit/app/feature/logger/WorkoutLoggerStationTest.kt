package com.hydrafit.app.feature.logger

import com.hydrafit.app.core.domain.equipment.EquipmentStation
import com.hydrafit.app.core.domain.equipment.EquipmentTag
import com.hydrafit.app.core.domain.equipment.StationAttribution
import com.hydrafit.app.core.domain.equipment.StationLoadBasis
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WorkoutLoggerStationTest {

    @Test
    fun stationOptionsFollowTheSelectedExercisesEquipmentAndRetirement() {
        val cable = EquipmentStation(
            id = 1L,
            profileId = 1L,
            equipmentId = EquipmentTag.CABLE_MACHINE,
            name = "Cable row A"
        )
        val barbell = EquipmentStation(
            id = 2L,
            profileId = 1L,
            equipmentId = EquipmentTag.BARBELL,
            name = "Rack A"
        )
        val retired = cable.copy(id = 3L, name = "Old cable", retired = true)
        val state = WorkoutLoggerUiState(
            exercises = listOf(
                ExerciseOption(
                    id = "row",
                    name = "Cable row",
                    isBodyweight = false,
                    requiredEquipment = setOf(EquipmentTag.CABLE_MACHINE)
                )
            ),
            stations = listOf(cable, barbell, retired),
            selectedExerciseId = "row",
            selectedStationId = 1L
        )

        assertEquals(listOf(cable), state.availableStations)
        assertEquals(
            StationAttribution(1L, "Cable row A", StationLoadBasis.TOTAL),
            state.selectedStationAttribution
        )
        assertNull(state.copy(selectedStationId = 2L).selectedStationAttribution)
    }
}
