package com.hydrafit.app.core.domain.equipment

/**
 * One plate denomination's symmetric count in a [PlateLoadout].
 *
 * [perSideCount] is how many plates of [weightTicks] sit on *each* side, so a loaded bar
 * carries twice that many plates of the denomination in total.
 */
data class PlateCount(val weightTicks: Int, val perSideCount: Int)

/**
 * A symmetric plate arrangement around one bar.
 *
 * [perSidePlates] lists only the denominations actually used, in descending
 * [PlateCount.weightTicks] order. [perSideTicks] is the plate weight on one side and
 * [totalTicks] is [baseTicks] + 2 * [perSideTicks]. [baseTicks] is the bar weight plus its
 * collars as one total, never a per-side value.
 */
data class PlateLoadout(
    val perSidePlates: List<PlateCount>,
    val perSideTicks: Int,
    val baseTicks: Int,
    val totalTicks: Int
)

/**
 * Resolved symmetric loading for [targetTicks].
 *
 * When the target is exactly reachable, [exact] is set and [lower]/[upper] are null.
 * Otherwise [exact] is null and the nearest reachable totals strictly below and/or above
 * the target are reported when they exist (a side with no arrangement is null). Totals are
 * native inventory ticks; no conversion or rounding is applied.
 */
data class PlateLoadingResult(
    val targetTicks: Int,
    val exact: PlateLoadout?,
    val lower: PlateLoadout?,
    val upper: PlateLoadout?
) {
    val isExact: Boolean get() = exact != null
}

/**
 * Resolves the symmetric plate arrangement for [targetTicks] on [barId].
 *
 * Only pairs of owned plates load: a denomination with `count` plates contributes
 * `count / 2` plates to each side, and bar/collar weight is added once. The result prefers
 * fewer total plates and, on a tie, larger denominations. It rejects an unknown bar, an
 * invalid inventory or a target outside `0..MAX_LOADING_TICKS` rather than clamping, and it
 * never reports an arrangement that needs more plates than the inventory owns.
 */
fun calculatePlateLoading(
    inventory: LoadingInventory,
    barId: String,
    targetTicks: Int
): PlateLoadingResult {
    requireLoadingInventory(inventory)
    require(targetTicks in 0..MAX_LOADING_TICKS) {
        "Plate loading target $targetTicks is outside 0..$MAX_LOADING_TICKS"
    }
    val bar = inventory.bars.firstOrNull { it.id == barId }
        ?: throw IllegalArgumentException("No bar '$barId' in the inventory")
    val baseTicks = bar.weightTicks + bar.collarTicks
    val perSide = inventory.plates
        .mapNotNull { plate ->
            val perSideCount = plate.count / 2
            if (perSideCount == 0) null else PlateCount(plate.weightTicks, perSideCount)
        }
        .sortedByDescending { it.weightTicks }
    val maxSum = perSide.fold(0L) { total, plate ->
        total + plate.weightTicks.toLong() * plate.perSideCount
    }.toInt()
    val solver = PlateSideSolver.from(perSide, maxSum)

    val delta = targetTicks - baseTicks
    val exact = if (delta >= 0 && delta % 2 == 0) {
        val half = delta / 2
        if (solver.isReachable(half)) solver.loadout(half, baseTicks) else null
    } else {
        null
    }
    if (exact != null) {
        return PlateLoadingResult(targetTicks, exact, lower = null, upper = null)
    }
    return PlateLoadingResult(
        targetTicks = targetTicks,
        exact = null,
        lower = lowerReachableHalf(solver, delta)?.let { solver.loadout(it, baseTicks) },
        upper = upperReachableHalf(solver, delta)?.let { solver.loadout(it, baseTicks) }
    )
}

/** Largest per-side sum whose total stays strictly below the target, or null. */
private fun lowerReachableHalf(solver: PlateSideSolver, delta: Int): Int? {
    if (delta <= 0) return null
    var half = minOf((delta - 1) / 2, solver.maxSum)
    while (half >= 0 && !solver.isReachable(half)) half--
    return if (half >= 0) half else null
}

/** Smallest per-side sum whose total stays strictly above the target, or null. */
private fun upperReachableHalf(solver: PlateSideSolver, delta: Int): Int? {
    var half = if (delta < 0) 0 else delta / 2 + 1
    while (half <= solver.maxSum && !solver.isReachable(half)) half++
    return if (half <= solver.maxSum) half else null
}

/** Sentinel for an unreachable sum in [PlateSideSolver.minPlates]. */
private const val UNREACHABLE = 1 shl 25

/**
 * Bounded solver for symmetric per-side plate sums.
 *
 * A bounded min-plate DP over the denominations (ascending weight, so each newly added
 * denomination is the most significant one for the tie-break) records the chosen count per
 * denomination and sum, so a reachable sum reconstructs to the minimal-plate arrangement
 * that uses larger denominations first. Time and memory are O(denominations * maxSum).
 */
private class PlateSideSolver(
    private val weights: IntArray,
    private val available: IntArray,
    val maxSum: Int
) {
    private val choices: Array<ByteArray>
    private val minPlates: IntArray

    init {
        choices = Array(weights.size) { ByteArray(maxSum + 1) }
        var previous = IntArray(maxSum + 1) { UNREACHABLE }
        previous[0] = 0
        for (index in weights.indices) {
            val next = IntArray(maxSum + 1) { UNREACHABLE }
            relax(previous, next, choices[index], weights[index], available[index])
            previous = next
        }
        minPlates = previous
    }

    fun isReachable(sum: Int): Boolean = sum in 0..maxSum && minPlates[sum] < UNREACHABLE

    fun loadout(perSideTicks: Int, baseTicks: Int): PlateLoadout {
        var remaining = perSideTicks
        val used = ArrayList<PlateCount>(weights.size)
        for (index in weights.indices.reversed()) {
            val count = choices[index][remaining].toInt()
            if (count > 0) {
                used.add(PlateCount(weights[index], count))
                remaining -= count * weights[index]
            }
        }
        require(remaining == 0) {
            "Plate reconstruction left $remaining ticks unassigned for $perSideTicks"
        }
        return PlateLoadout(
            perSidePlates = used,
            perSideTicks = perSideTicks,
            baseTicks = baseTicks,
            totalTicks = baseTicks + 2 * perSideTicks
        )
    }

    /**
     * Sliding-window minimum over one denomination's residue classes: for `sum = residue + k *
     * weight` it picks the cheapest predecessor within `available` plates, breaking ties toward
     * the most plates of the current denomination.
     */
    private fun relax(
        previous: IntArray,
        next: IntArray,
        choice: ByteArray,
        weight: Int,
        count: Int
    ) {
        val capacity = count + 2
        val indices = IntArray(capacity)
        val keys = IntArray(capacity)
        val residues = if (weight > maxSum) maxSum + 1 else weight
        for (residue in 0 until residues) {
            var head = 0
            var size = 0
            var k = 0
            var sum = residue
            while (sum <= maxSum) {
                val best = previous[sum]
                val key = if (best >= UNREACHABLE) UNREACHABLE else best - k
                while (size > 0 && keys[(head + size - 1) % capacity] > key) size--
                val insert = (head + size) % capacity
                indices[insert] = k
                keys[insert] = key
                size++
                while (indices[head] < k - count) {
                    head = (head + 1) % capacity
                    size--
                }
                val total = keys[head] + k
                next[sum] = if (total >= UNREACHABLE) UNREACHABLE else total
                choice[sum] = (k - indices[head]).toByte()
                k++
                sum += weight
            }
        }
    }

    companion object {
        fun from(perSideDescending: List<PlateCount>, maxSum: Int): PlateSideSolver {
            val ascending = perSideDescending.asReversed()
            return PlateSideSolver(
                weights = IntArray(ascending.size) { ascending[it].weightTicks },
                available = IntArray(ascending.size) { ascending[it].perSideCount },
                maxSum = maxSum
            )
        }
    }
}
