package com.hydrafit.app.core.domain.equipment

import kotlin.test.Test
import kotlin.test.assertEquals

class EquipmentIdentityTest {
    @Test
    fun customAllocationSkipsReservedAndOccupiedSuffixes() {
        assertEquals(
            "EZ_BAR_4",
            EquipmentIdentity.availableCustomId("EZ_BAR", setOf("EZ_BAR", "EZ_BAR_2", "EZ_BAR_3"))
        )
        assertEquals("MY_RACK", EquipmentIdentity.availableCustomId("MY_RACK", setOf("EZ_BAR")))
    }

    @Test
    fun repairMappingIsOrderedAndNeverReusesAnOccupiedIdentity() {
        val reserved = setOf("EZ_BAR", "AB_ROLLER")
        assertEquals(
            linkedMapOf("AB_ROLLER" to "AB_ROLLER_2", "EZ_BAR" to "EZ_BAR_3"),
            EquipmentIdentity.collisionMapping(
                setOf("EZ_BAR", "AB_ROLLER", "CUSTOM"),
                setOf("EZ_BAR_2"),
                reserved
            )
        )
    }

    @Test
    fun csvRemappingUsesWholeTokensWithoutChangingOrderOrEmptyValues() {
        assertEquals(
            "BARBELL,EZ_BAR_2,EZ_BAR_20,",
            EquipmentIdentity.remapCsv("BARBELL,EZ_BAR,EZ_BAR_20,", mapOf("EZ_BAR" to "EZ_BAR_2"))
        )
        assertEquals("", EquipmentIdentity.remapCsv("", mapOf("EZ_BAR" to "EZ_BAR_2")))
    }
}
