package com.hydrafit.app.core.domain.equipment

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class DumbbellLoadingCalculatorTest {

    @Test
    fun fixedDumbbellResolvesExactValueWithoutDoubling() {
        val dumbbell = LoadingDumbbell("db", "Fixed", fixedTicks = listOf(20_000, 25_000))
        val result = calculateDumbbellLoading(dumbbell, 25_000)
        assertEquals(DumbbellSelection("db", 25_000), result.exact)
        assertEquals(25_000, result.exact?.perDumbbellTicks)
        assertNull(result.lower)
        assertNull(result.upper)
    }

    @Test
    fun fixedDumbbellReportsNearestValuesAroundTheTarget() {
        val dumbbell = LoadingDumbbell("db", "Fixed", fixedTicks = listOf(25_000, 20_000))

        val between = calculateDumbbellLoading(dumbbell, 22_000)
        assertNull(between.exact)
        assertEquals(20_000, between.lower?.perDumbbellTicks)
        assertEquals(25_000, between.upper?.perDumbbellTicks)

        val below = calculateDumbbellLoading(dumbbell, 10_000)
        assertNull(below.lower)
        assertEquals(20_000, below.upper?.perDumbbellTicks)

        val above = calculateDumbbellLoading(dumbbell, 30_000)
        assertEquals(25_000, above.lower?.perDumbbellTicks)
        assertNull(above.upper)
    }

    @Test
    fun adjustableDumbbellReachesItsEndpointsAndIncrementGrid() {
        val dumbbell = LoadingDumbbell(
            "db",
            "Adjustable",
            minimumTicks = 2_000,
            maximumTicks = 20_000,
            incrementTicks = 2_000
        )
        assertEquals(2_000, calculateDumbbellLoading(dumbbell, 2_000).exact?.perDumbbellTicks)
        assertEquals(20_000, calculateDumbbellLoading(dumbbell, 20_000).exact?.perDumbbellTicks)
        assertEquals(6_000, calculateDumbbellLoading(dumbbell, 6_000).exact?.perDumbbellTicks)

        val gap = calculateDumbbellLoading(dumbbell, 7_000)
        assertNull(gap.exact)
        assertEquals(6_000, gap.lower?.perDumbbellTicks)
        assertEquals(8_000, gap.upper?.perDumbbellTicks)
    }

    @Test
    fun adjustableDumbbellHandlesNonUniformIncrementGaps() {
        val dumbbell = LoadingDumbbell(
            "db",
            "Adjustable",
            minimumTicks = 1_000,
            maximumTicks = 10_000,
            incrementTicks = 3_000
        )
        val gap = calculateDumbbellLoading(dumbbell, 5_500)
        assertNull(gap.exact)
        assertEquals(4_000, gap.lower?.perDumbbellTicks)
        assertEquals(7_000, gap.upper?.perDumbbellTicks)

        val below = calculateDumbbellLoading(dumbbell, 500)
        assertNull(below.lower)
        assertEquals(1_000, below.upper?.perDumbbellTicks)

        val above = calculateDumbbellLoading(dumbbell, 20_000)
        assertEquals(10_000, above.lower?.perDumbbellTicks)
        assertNull(above.upper)
    }

    @Test
    fun inventoryLookupAndUnitLabelDoNotChangeValues() {
        val kilograms = LoadingInventory(
            unit = "KG",
            dumbbells = listOf(LoadingDumbbell("fixed", "Fixed", fixedTicks = listOf(1_250, 2_500)))
        )
        val pounds = LoadingInventory(
            unit = "LB",
            dumbbells = listOf(LoadingDumbbell("fixed", "Fixed", fixedTicks = listOf(1_250, 2_500)))
        )
        val direct = calculateDumbbellLoading(kilograms.dumbbells.first(), 1_250)
        assertEquals(direct, calculateDumbbellLoading(kilograms, "fixed", 1_250))
        assertEquals(
            listOf(1_250, 2_500),
            listOf(
                calculateDumbbellLoading(pounds, "fixed", 1_250).exact?.perDumbbellTicks,
                calculateDumbbellLoading(pounds, "fixed", 2_500).exact?.perDumbbellTicks
            )
        )
    }

    @Test
    fun invalidTargetsAndUnknownDumbbellsAreRejected() {
        val inventory = LoadingInventory(
            dumbbells = listOf(LoadingDumbbell("db", "Fixed", fixedTicks = listOf(20_000)))
        )
        val dumbbell = inventory.dumbbells.first()
        assertFailsWith<IllegalArgumentException> { calculateDumbbellLoading(dumbbell, -1) }
        assertFailsWith<IllegalArgumentException> {
            calculateDumbbellLoading(dumbbell, MAX_LOADING_TICKS + 1)
        }
        assertFailsWith<IllegalArgumentException> {
            calculateDumbbellLoading(inventory, "missing", 20_000)
        }
        assertFailsWith<IllegalArgumentException> {
            calculateDumbbellLoading(LoadingDumbbell("bad", "Missing range"), 20_000)
        }
    }
}
