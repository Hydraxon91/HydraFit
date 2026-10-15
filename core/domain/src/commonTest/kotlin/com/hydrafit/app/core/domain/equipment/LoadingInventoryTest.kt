package com.hydrafit.app.core.domain.equipment

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class LoadingInventoryTest {
    @Test
    fun nativeUnitAndFractionalAmountsRoundTripWithoutRounding() {
        val inventory = LoadingInventory(
            unit = "LB",
            plates = listOf(OwnedPlate(125, 3)),
            bars = listOf(LoadingBar("bar", "Light bar", 15_000, 500)),
            dumbbells = listOf(LoadingDumbbell("db", "Fixed", listOf(1_125, 2_250)))
        )
        assertEquals(inventory, LoadingInventoryJson.decode(LoadingInventoryJson.encode(inventory)))
    }

    @Test
    fun inventoryBoundsAndInvalidShapesAreRejected() {
        val invalid = listOf(
            LoadingInventory(unit = "STONE"),
            LoadingInventory(plates = listOf(OwnedPlate(0, 2))),
            LoadingInventory(plates = listOf(OwnedPlate(1, 101))),
            LoadingInventory(plates = listOf(OwnedPlate(1, -1))),
            LoadingInventory(plates = listOf(OwnedPlate(125, 2), OwnedPlate(125, 2))),
            LoadingInventory(plates = (1..17).map { OwnedPlate(it, 2) }),
            LoadingInventory(plates = listOf(OwnedPlate(1_000_001, 2))),
            LoadingInventory(
                plates = listOf(OwnedPlate(1_000_000, 2)),
                bars = listOf(LoadingBar("bar", "Bar", 1))
            ),
            LoadingInventory(bars = listOf(LoadingBar("bar", " ", 0))),
            LoadingInventory(dumbbells = listOf(LoadingDumbbell("db", "Missing range"))),
            LoadingInventory(
                dumbbells = listOf(LoadingDumbbell("db", "Mixed", listOf(1), 1, 2, 1))
            ),
            LoadingInventory(
                dumbbells = listOf(
                    LoadingDumbbell(
                        "db",
                        "Uneven",
                        minimumTicks = 1,
                        maximumTicks = 10,
                        incrementTicks = 2
                    )
                )
            )
        )
        invalid.forEach { inventory ->
            assertFailsWith<IllegalArgumentException>(inventory.toString()) {
                requireLoadingInventory(inventory)
            }
        }
    }

    @Test
    fun adjustableDumbbellsIncludeBothEndpoints() {
        val inventory = LoadingInventory(
            dumbbells = listOf(
                LoadingDumbbell(
                    "db",
                    "Adjustable",
                    minimumTicks = 2_000,
                    maximumTicks = 20_000,
                    incrementTicks = 2_000
                )
            )
        )
        assertEquals(inventory, LoadingInventoryJson.decode(LoadingInventoryJson.encode(inventory)))
    }
}
