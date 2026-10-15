package com.hydrafit.app.core.domain.equipment

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.TimeSource

class PlateLoadingCalculatorTest {

    @Test
    fun barOnlyInventoryResolvesTheExactBaseTotal() {
        val inventory = LoadingInventory(bars = listOf(LoadingBar("bar", "Bar", 15_000, 500)))
        val result = calculatePlateLoading(inventory, "bar", 15_500)
        assertTrue(result.isExact)
        assertEquals(15_500, result.exact?.baseTicks)
        assertEquals(0, result.exact?.perSideTicks)
        assertEquals(15_500, result.exact?.totalTicks)
        assertEquals(0, result.exact?.perSidePlates?.size)
        assertNull(result.lower)
        assertNull(result.upper)
    }

    @Test
    fun exactTargetLoadsTheSamePlatesOnEachSide() {
        val inventory = LoadingInventory(
            plates = listOf(OwnedPlate(10_000, 4)),
            bars = listOf(LoadingBar("bar", "Bar", 20_000))
        )
        val result = calculatePlateLoading(inventory, "bar", 40_000)
        assertEquals(listOf(PlateCount(10_000, 1)), result.exact?.perSidePlates)
        assertEquals(10_000, result.exact?.perSideTicks)
        assertEquals(40_000, result.exact?.totalTicks)
    }

    @Test
    fun oddIndividualCountsLoadOnlyPairs() {
        val inventory = LoadingInventory(
            plates = listOf(OwnedPlate(10_000, 3)),
            bars = listOf(LoadingBar("bar", "Bar", 20_000))
        )
        val above = calculatePlateLoading(inventory, "bar", 60_000)
        assertNull(above.exact)
        assertEquals(40_000, above.lower?.totalTicks)
        assertNull(above.upper)

        val between = calculatePlateLoading(inventory, "bar", 30_000)
        assertNull(between.exact)
        assertEquals(20_000, between.lower?.totalTicks)
        assertEquals(40_000, between.upper?.totalTicks)
    }

    @Test
    fun emptyPlateInventoryReachesOnlyTheBar() {
        val inventory = LoadingInventory(bars = listOf(LoadingBar("bar", "Bar", 20_000)))
        assertEquals(20_000, calculatePlateLoading(inventory, "bar", 20_000).exact?.totalTicks)

        val missed = calculatePlateLoading(inventory, "bar", 25_000)
        assertNull(missed.exact)
        assertEquals(20_000, missed.lower?.totalTicks)
        assertNull(missed.upper)
    }

    @Test
    fun belowBarTargetHasOnlyAnUpperNeighbour() {
        val inventory = LoadingInventory(
            plates = listOf(OwnedPlate(10_000, 4)),
            bars = listOf(LoadingBar("bar", "Bar", 20_000))
        )
        val result = calculatePlateLoading(inventory, "bar", 5_000)
        assertNull(result.exact)
        assertNull(result.lower)
        assertEquals(20_000, result.upper?.totalTicks)
    }

    @Test
    fun aboveMaximumTargetHasOnlyALowerNeighbour() {
        val inventory = LoadingInventory(
            plates = listOf(OwnedPlate(10_000, 4)),
            bars = listOf(LoadingBar("bar", "Bar", 20_000))
        )
        val result = calculatePlateLoading(inventory, "bar", 100_000)
        assertNull(result.exact)
        assertEquals(60_000, result.lower?.totalTicks)
        assertNull(result.upper)
    }

    @Test
    fun specialtyBarAndCollarAddOnceAsTheBaseWeight() {
        val inventory = LoadingInventory(
            plates = listOf(OwnedPlate(1_250, 4)),
            bars = listOf(LoadingBar("specialty", "Specialty", 15_000, 500))
        )
        val result = calculatePlateLoading(inventory, "specialty", 18_000)
        assertEquals(15_500, result.exact?.baseTicks)
        assertEquals(listOf(PlateCount(1_250, 1)), result.exact?.perSidePlates)
        assertEquals(18_000, result.exact?.totalTicks)
    }

    @Test
    fun fractionalTicksStayExact() {
        val inventory = LoadingInventory(
            plates = listOf(OwnedPlate(1_250, 2)),
            bars = listOf(LoadingBar("bar", "Bar", 20_000))
        )
        val result = calculatePlateLoading(inventory, "bar", 22_500)
        assertEquals(1_250, result.exact?.perSideTicks)
        assertEquals(22_500, result.exact?.totalTicks)
    }

    @Test
    fun plateInputOrderDoesNotChangeResults() {
        val plates = listOf(OwnedPlate(5, 4), OwnedPlate(20, 4), OwnedPlate(10, 4))
        val forward = LoadingInventory(
            plates = plates,
            bars = listOf(LoadingBar("bar", "Bar", 7, 2))
        )
        val reversed = LoadingInventory(
            plates = plates.reversed(),
            bars = listOf(LoadingBar("bar", "Bar", 7, 2))
        )
        for (target in 0..200) {
            assertEquals(
                summarize(calculatePlateLoading(forward, "bar", target)),
                summarize(calculatePlateLoading(reversed, "bar", target)),
                "target $target"
            )
        }
    }

    @Test
    fun fewerPlatesThenLargerDenominationsBreakTies() {
        val inventory = LoadingInventory(
            plates = listOf(
                OwnedPlate(20_000, 4),
                OwnedPlate(15_000, 4),
                OwnedPlate(10_000, 4),
                OwnedPlate(5_000, 4)
            ),
            bars = listOf(LoadingBar("bar", "Bar", 20_000))
        )
        // 30_000 per side: {20_000, 10_000} and {15_000, 15_000} both use two plates.
        val result = calculatePlateLoading(inventory, "bar", 80_000)
        assertEquals(
            listOf(PlateCount(20_000, 1), PlateCount(10_000, 1)),
            result.exact?.perSidePlates
        )
    }

    @Test
    fun invalidTargetsBarsAndInventoriesAreRejected() {
        val inventory = LoadingInventory(
            plates = listOf(OwnedPlate(10_000, 4)),
            bars = listOf(LoadingBar("bar", "Bar", 20_000))
        )
        assertFailsWith<IllegalArgumentException> { calculatePlateLoading(inventory, "bar", -1) }
        assertFailsWith<IllegalArgumentException> {
            calculatePlateLoading(inventory, "bar", MAX_LOADING_TICKS + 1)
        }
        assertFailsWith<IllegalArgumentException> {
            calculatePlateLoading(inventory, "missing", 20_000)
        }
        assertFailsWith<IllegalArgumentException> {
            calculatePlateLoading(
                LoadingInventory(plates = listOf(OwnedPlate(1, 1), OwnedPlate(1, 1))),
                "bar",
                0
            )
        }
    }

    @Test
    fun unitLabelDoesNotRescaleNativeTicks() {
        val kilograms = LoadingInventory(
            unit = "KG",
            plates = listOf(OwnedPlate(5, 4), OwnedPlate(20, 4)),
            bars = listOf(LoadingBar("bar", "Bar", 7, 2))
        )
        val pounds = LoadingInventory(
            unit = "LB",
            plates = listOf(OwnedPlate(5, 4), OwnedPlate(20, 4)),
            bars = listOf(LoadingBar("bar", "Bar", 7, 2))
        )
        for (target in 0..200) {
            assertEquals(
                summarize(calculatePlateLoading(kilograms, "bar", target)),
                summarize(calculatePlateLoading(pounds, "bar", target)),
                "target $target"
            )
        }
    }

    @Test
    fun bruteForceOracleMatchesExactAndNearestResults() {
        val bar = LoadingBar("bar", "Bar", 5, 2)
        val plates = listOf(
            OwnedPlate(20, 3),
            OwnedPlate(15, 3),
            OwnedPlate(10, 5),
            OwnedPlate(5, 4)
        )
        val inventory = LoadingInventory(plates = plates, bars = listOf(bar))
        val oracle = PlateOracle(
            baseTicks = bar.weightTicks + bar.collarTicks,
            perSideDescending = plates
                .mapNotNull { plate ->
                    val perSide = plate.count / 2
                    if (perSide == 0) null else PlateCount(plate.weightTicks, perSide)
                }
                .sortedByDescending { it.weightTicks }
        )
        for (target in 0..(oracle.maxTotalTicks + 20)) {
            assertEquals(
                oracle.resolve(target),
                summarize(calculatePlateLoading(inventory, "bar", target)),
                "target $target"
            )
        }
    }

    @Test
    fun worstCaseInventoryStaysWithinBoundedResources() {
        val denominations = (5..20).map { OwnedPlate(it * 100, 100) }
        val inventory = LoadingInventory(
            plates = denominations,
            bars = listOf(LoadingBar("bench", "Bench", 0))
        )
        requireLoadingInventory(inventory)
        val maxPerSide = denominations.sumOf { (it.count / 2).toLong() * it.weightTicks }
        val mark = TimeSource.Monotonic.markNow()
        val result = calculatePlateLoading(inventory, "bench", MAX_LOADING_TICKS)
        val elapsedMillis = mark.elapsedNow().inWholeMilliseconds
        println(
            "plate solver worst case: ${denominations.size} denominations, " +
                "maxPerSideTicks=$maxPerSide, elapsedMs=$elapsedMillis"
        )
        assertTrue(result.isExact)
        assertEquals(MAX_LOADING_TICKS, result.exact?.totalTicks)
        assertEquals(maxPerSide, result.exact?.perSideTicks?.toLong())
        assertEquals(800, result.exact?.perSidePlates?.sumOf { it.perSideCount })
        assertTrue(elapsedMillis < 20_000, "worst-case solve took ${elapsedMillis}ms")
    }
}

private fun summarize(result: PlateLoadingResult): List<String> = listOf(
    describe(result.exact?.totalTicks, result.exact?.perSidePlates),
    describe(result.lower?.totalTicks, result.lower?.perSidePlates),
    describe(result.upper?.totalTicks, result.upper?.perSidePlates)
)

private fun describe(totalTicks: Int?, plates: List<PlateCount>?): String {
    if (totalTicks == null || plates == null) return "none"
    val counts = plates.joinToString(",") { "${it.weightTicks}x${it.perSideCount}" }
    return "$totalTicks:$counts"
}

/** Enumerates every symmetric combination to check the solver independently. */
private class PlateOracle(baseTicks: Int, perSideDescending: List<PlateCount>) {
    private val canonicalByTotal = HashMap<Int, List<PlateCount>>()
    private val sortedTotals: IntArray

    init {
        val counts = IntArray(perSideDescending.size)
        val best = HashMap<Int, IntArray>()
        fun visit(index: Int, sum: Int) {
            if (index == perSideDescending.size) {
                val existing = best[sum]
                if (existing == null || isBetter(counts, existing)) best[sum] = counts.copyOf()
                return
            }
            val plate = perSideDescending[index]
            for (count in 0..plate.perSideCount) {
                counts[index] = count
                visit(index + 1, sum + count * plate.weightTicks)
            }
        }
        visit(0, 0)
        best.forEach { (half, chosen) ->
            canonicalByTotal[baseTicks + 2 * half] = perSideDescending.indices
                .filter { chosen[it] > 0 }
                .map { PlateCount(perSideDescending[it].weightTicks, chosen[it]) }
        }
        sortedTotals = canonicalByTotal.keys.sorted().toIntArray()
    }

    val maxTotalTicks: Int get() = sortedTotals.last()

    fun resolve(targetTicks: Int): List<String> {
        val exact = canonicalByTotal[targetTicks]
        if (exact != null) {
            return listOf(describe(targetTicks, exact), "none", "none")
        }
        val lower = floorTotal(targetTicks)
        val upper = ceilingTotal(targetTicks)
        return listOf(
            "none",
            lower?.let { describe(it, canonicalByTotal.getValue(it)) } ?: "none",
            upper?.let { describe(it, canonicalByTotal.getValue(it)) } ?: "none"
        )
    }

    private fun floorTotal(targetTicks: Int): Int? {
        var low = 0
        var high = sortedTotals.size - 1
        var found: Int? = null
        while (low <= high) {
            val mid = (low + high) / 2
            if (sortedTotals[mid] < targetTicks) {
                found = sortedTotals[mid]
                low = mid + 1
            } else {
                high = mid - 1
            }
        }
        return found
    }

    private fun ceilingTotal(targetTicks: Int): Int? {
        var low = 0
        var high = sortedTotals.size - 1
        var found: Int? = null
        while (low <= high) {
            val mid = (low + high) / 2
            if (sortedTotals[mid] > targetTicks) {
                found = sortedTotals[mid]
                high = mid - 1
            } else {
                low = mid + 1
            }
        }
        return found
    }

    private fun isBetter(candidate: IntArray, existing: IntArray): Boolean {
        val candidatePlates = candidate.sum()
        val existingPlates = existing.sum()
        if (candidatePlates != existingPlates) return candidatePlates < existingPlates
        for (index in candidate.indices) {
            if (candidate[index] != existing[index]) return candidate[index] > existing[index]
        }
        return false
    }
}
