package com.hydrafit.app.core.domain.equipment

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class EquipmentStationTest {

    @Test
    fun aNamedStationRequiresATrimmedNameAndACompleteGrid() {
        val station = EquipmentStation(
            profileId = 1L,
            equipmentId = EquipmentTag.CABLE_MACHINE,
            name = "Cable row A",
            loadBasis = StationLoadBasis.PER_HAND,
            minWeightKg = 5.0,
            maxWeightKg = 120.0,
            incrementKg = 2.5
        )

        assertEquals("Cable row A", station.name)
        assertEquals(StationLoadBasis.PER_HAND, station.loadBasis)
        assertEquals(
            StationAttribution(9L, "Cable row A", StationLoadBasis.PER_HAND),
            station.copy(id = 9L).attribution()
        )
        assertFailsWith<IllegalArgumentException> {
            station.copy(name = " ")
        }
        assertFailsWith<IllegalArgumentException> {
            station.copy(minWeightKg = 5.0, maxWeightKg = null, incrementKg = 2.5)
        }
    }

    @Test
    fun anInvalidGridIsRejected() {
        val station = EquipmentStation(
            profileId = 1L,
            equipmentId = EquipmentTag.CABLE_MACHINE,
            name = "Cable row A",
            minWeightKg = 0.0,
            maxWeightKg = 100.0,
            incrementKg = 2.5
        )

        assertFailsWith<IllegalArgumentException> {
            station.copy(maxWeightKg = -1.0)
        }
        assertFailsWith<IllegalArgumentException> {
            station.copy(incrementKg = 0.0)
        }
        assertFailsWith<IllegalArgumentException> {
            station.copy(maxWeightKg = EquipmentStation.MAX_STATION_WEIGHT_KG + 1.0)
        }
    }

    @Test
    fun aDraftHasNoPersistedAttributionAndAPersistedStationDoes() {
        val draft = EquipmentStation(
            profileId = 1L,
            equipmentId = EquipmentTag.CABLE_MACHINE,
            name = "Draft"
        )
        val persisted = draft.copy(id = 7L)

        assertNull(draft.attribution())
        assertEquals(
            StationAttribution(7L, "Draft", StationLoadBasis.TOTAL),
            persisted.attribution()
        )
    }
}
