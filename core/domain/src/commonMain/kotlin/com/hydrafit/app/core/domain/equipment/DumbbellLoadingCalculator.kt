package com.hydrafit.app.core.domain.equipment

/**
 * One dumbbell's resolved weight.
 *
 * [perDumbbellTicks] is the weight of a single dumbbell in native inventory ticks and is
 * never doubled: a caller that needs a pair total adds both dumbbells itself.
 */
data class DumbbellSelection(val dumbbellId: String, val perDumbbellTicks: Int)

/**
 * Resolved per-dumbbell loading for [targetTicks].
 *
 * When the target is exactly reachable [exact] is set and [lower]/[upper] are null.
 * Otherwise [exact] is null and the nearest reachable values strictly below and/or above
 * the target are reported when they exist. All values are per dumbbell.
 */
data class DumbbellLoadingResult(
    val targetTicks: Int,
    val exact: DumbbellSelection?,
    val lower: DumbbellSelection?,
    val upper: DumbbellSelection?
) {
    val isExact: Boolean get() = exact != null
}

/**
 * Resolves the per-dumbbell loading for [targetTicks] on [dumbbellId].
 *
 * Rejects an unknown dumbbell, an invalid inventory or a target outside
 * `0..MAX_LOADING_TICKS` rather than clamping.
 */
fun calculateDumbbellLoading(
    inventory: LoadingInventory,
    dumbbellId: String,
    targetTicks: Int
): DumbbellLoadingResult {
    requireLoadingInventory(inventory)
    require(targetTicks in 0..MAX_LOADING_TICKS) {
        "Dumbbell loading target $targetTicks is outside 0..$MAX_LOADING_TICKS"
    }
    val dumbbell = inventory.dumbbells.firstOrNull { it.id == dumbbellId }
        ?: throw IllegalArgumentException("No dumbbell '$dumbbellId' in the inventory")
    return calculateDumbbellLoading(dumbbell, targetTicks)
}

/**
 * Resolves the per-dumbbell loading for [targetTicks] on [dumbbell].
 *
 * A fixed dumbbell resolves to one of its fixed values; an adjustable dumbbell resolves to a
 * point on its validated `minimum..maximum` increment grid, whose endpoints are reachable.
 */
fun calculateDumbbellLoading(dumbbell: LoadingDumbbell, targetTicks: Int): DumbbellLoadingResult {
    requireLoadingInventory(LoadingInventory(dumbbells = listOf(dumbbell)))
    require(targetTicks in 0..MAX_LOADING_TICKS) {
        "Dumbbell loading target $targetTicks is outside 0..$MAX_LOADING_TICKS"
    }
    return if (dumbbell.fixedTicks.isNotEmpty()) {
        resolveValues(dumbbell.id, dumbbell.fixedTicks.sorted(), targetTicks)
    } else {
        resolveAdjustable(dumbbell, targetTicks)
    }
}

private fun resolveValues(
    dumbbellId: String,
    values: List<Int>,
    targetTicks: Int
): DumbbellLoadingResult {
    if (targetTicks in values) {
        return DumbbellLoadingResult(
            targetTicks = targetTicks,
            exact = DumbbellSelection(dumbbellId, targetTicks),
            lower = null,
            upper = null
        )
    }
    val lower = values.lastOrNull { it < targetTicks }
    val upper = values.firstOrNull { it > targetTicks }
    return DumbbellLoadingResult(
        targetTicks = targetTicks,
        exact = null,
        lower = lower?.let { DumbbellSelection(dumbbellId, it) },
        upper = upper?.let { DumbbellSelection(dumbbellId, it) }
    )
}

private fun resolveAdjustable(dumbbell: LoadingDumbbell, targetTicks: Int): DumbbellLoadingResult {
    val minimum = requireNotNull(dumbbell.minimumTicks)
    val maximum = requireNotNull(dumbbell.maximumTicks)
    val increment = requireNotNull(dumbbell.incrementTicks)
    if (targetTicks < minimum) {
        return DumbbellLoadingResult(
            targetTicks,
            exact = null,
            lower = null,
            upper = DumbbellSelection(dumbbell.id, minimum)
        )
    }
    if (targetTicks > maximum) {
        return DumbbellLoadingResult(
            targetTicks,
            exact = null,
            lower = DumbbellSelection(dumbbell.id, maximum),
            upper = null
        )
    }
    val offset = targetTicks - minimum
    val steps = offset / increment
    if (offset % increment == 0) {
        return DumbbellLoadingResult(
            targetTicks,
            exact = DumbbellSelection(dumbbell.id, minimum + steps * increment),
            lower = null,
            upper = null
        )
    }
    return DumbbellLoadingResult(
        targetTicks,
        exact = null,
        lower = DumbbellSelection(dumbbell.id, minimum + steps * increment),
        upper = DumbbellSelection(dumbbell.id, minimum + (steps + 1) * increment)
    )
}
