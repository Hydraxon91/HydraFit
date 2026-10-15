package com.hydrafit.app.feature.equipment

import com.hydrafit.app.core.domain.equipment.DumbbellLoadingResult
import com.hydrafit.app.core.domain.equipment.LoadingInventory
import com.hydrafit.app.core.domain.equipment.MAX_LOADING_TICKS
import com.hydrafit.app.core.domain.equipment.PlateLoadingResult
import com.hydrafit.app.core.domain.equipment.calculateDumbbellLoading
import com.hydrafit.app.core.domain.equipment.calculatePlateLoading

/** Which saved loading configuration a manual calculation targets. */
enum class LoadingCalculatorKind { PLATE, DUMBBELL }

/** One selectable saved bar or dumbbell. */
data class LoadingCalculatorOption(val id: String, val label: String)

/** Why a manual calculation could not produce a result; the UI renders a localized message. */
enum class LoadingCalculatorFailure {
    NO_EQUIPMENT,
    INVALID_TARGET,
    OUT_OF_RANGE,
    FAILED
}

enum class LoadingResultLabel { EXACT, LOWER, UPPER }

/** One denomination's count on a single side of the bar. */
data class PlateSideLine(val denomination: String, val perSideCount: Int)

/**
 * One reachable arrangement.
 *
 * [totalWeight] is the full native weight: bar plus collars once, plus both sides of plates. For a
 * dumbbell it is one dumbbell's weight, never a pair total.
 */
data class LoadingResultOption(
    val label: LoadingResultLabel,
    val totalWeight: String,
    val perSidePlates: List<PlateSideLine> = emptyList(),
    val perDumbbell: Boolean = false
)

/** A resolved manual calculation: either one exact arrangement or the adjacent reachable ones. */
data class LoadingCalculatorResultView(
    val targetWeight: String,
    val isExact: Boolean,
    val options: List<LoadingResultOption>,
    /** No reachable arrangement below/above the target exists in this profile's inventory. */
    val missingLower: Boolean,
    val missingUpper: Boolean
)

/** The manual calculator: a saved configuration, a native target, and the last resolved result. */
data class LoadingCalculatorState(
    val open: Boolean = false,
    val kind: LoadingCalculatorKind = LoadingCalculatorKind.PLATE,
    val selectedId: String? = null,
    val targetInput: String = "",
    val isRunning: Boolean = false,
    val failure: LoadingCalculatorFailure? = null,
    val result: LoadingCalculatorResultView? = null
) {
    val isOpen: Boolean
        get() = open
}

/** The saved bars or dumbbells this profile can calculate with. */
fun loadingCalculatorOptions(
    inventory: LoadingInventory,
    kind: LoadingCalculatorKind
): List<LoadingCalculatorOption> = when (kind) {
    LoadingCalculatorKind.PLATE ->
        inventory.bars.map { LoadingCalculatorOption(it.id, it.name) }

    LoadingCalculatorKind.DUMBBELL ->
        inventory.dumbbells.map { LoadingCalculatorOption(it.id, it.name) }
}

/**
 * Resolves a manual calculation against one saved configuration.
 *
 * The target is a native amount in the inventory's own unit and is never converted. Null means the
 * requested configuration is not in the inventory.
 */
internal fun runLoadingCalculation(
    inventory: LoadingInventory,
    kind: LoadingCalculatorKind,
    equipmentId: String,
    targetTicks: Int
): LoadingCalculatorResultView? = runCatching {
    when (kind) {
        LoadingCalculatorKind.PLATE -> plateLoadingResultView(
            calculatePlateLoading(inventory, equipmentId, targetTicks),
            LoadingUnitOption.fromStorage(inventory.unit)
        )

        LoadingCalculatorKind.DUMBBELL -> dumbbellLoadingResultView(
            calculateDumbbellLoading(inventory, equipmentId, targetTicks),
            LoadingUnitOption.fromStorage(inventory.unit)
        )
    }
}.getOrNull()

/** True when the entered target is a native amount the calculators accept. */
internal fun isCalculableTarget(targetTicks: Int): Boolean = targetTicks in 0..MAX_LOADING_TICKS

internal fun plateLoadingResultView(
    result: PlateLoadingResult,
    unit: LoadingUnitOption
): LoadingCalculatorResultView {
    val options = ArrayList<LoadingResultOption>(3)
    result.exact?.let { loadout ->
        options += LoadingResultOption(
            label = LoadingResultLabel.EXACT,
            totalWeight = unit.formatAmount(loadout.totalTicks),
            perSidePlates = loadout.perSidePlates.map {
                PlateSideLine(unit.formatAmount(it.weightTicks), it.perSideCount)
            }
        )
    }
    result.lower?.let { loadout ->
        options += LoadingResultOption(
            label = LoadingResultLabel.LOWER,
            totalWeight = unit.formatAmount(loadout.totalTicks),
            perSidePlates = loadout.perSidePlates.map {
                PlateSideLine(unit.formatAmount(it.weightTicks), it.perSideCount)
            }
        )
    }
    result.upper?.let { loadout ->
        options += LoadingResultOption(
            label = LoadingResultLabel.UPPER,
            totalWeight = unit.formatAmount(loadout.totalTicks),
            perSidePlates = loadout.perSidePlates.map {
                PlateSideLine(unit.formatAmount(it.weightTicks), it.perSideCount)
            }
        )
    }
    return LoadingCalculatorResultView(
        targetWeight = unit.formatAmount(result.targetTicks),
        isExact = result.isExact,
        options = options,
        missingLower = result.exact == null && result.lower == null,
        missingUpper = result.exact == null && result.upper == null
    )
}

internal fun dumbbellLoadingResultView(
    result: DumbbellLoadingResult,
    unit: LoadingUnitOption
): LoadingCalculatorResultView {
    val options = ArrayList<LoadingResultOption>(2)
    result.exact?.let {
        options += LoadingResultOption(
            label = LoadingResultLabel.EXACT,
            totalWeight = unit.formatAmount(it.perDumbbellTicks),
            perDumbbell = true
        )
    }
    result.lower?.let {
        options += LoadingResultOption(
            label = LoadingResultLabel.LOWER,
            totalWeight = unit.formatAmount(it.perDumbbellTicks),
            perDumbbell = true
        )
    }
    result.upper?.let {
        options += LoadingResultOption(
            label = LoadingResultLabel.UPPER,
            totalWeight = unit.formatAmount(it.perDumbbellTicks),
            perDumbbell = true
        )
    }
    return LoadingCalculatorResultView(
        targetWeight = unit.formatAmount(result.targetTicks),
        isExact = result.isExact,
        options = options,
        missingLower = result.exact == null && result.lower == null,
        missingUpper = result.exact == null && result.upper == null
    )
}
