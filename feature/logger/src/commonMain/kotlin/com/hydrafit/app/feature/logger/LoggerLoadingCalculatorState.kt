package com.hydrafit.app.feature.logger

import com.hydrafit.app.core.domain.equipment.DumbbellLoadingResult
import com.hydrafit.app.core.domain.equipment.LOADING_SCALE
import com.hydrafit.app.core.domain.equipment.LoadingInventory
import com.hydrafit.app.core.domain.equipment.MAX_LOADING_TICKS
import com.hydrafit.app.core.domain.equipment.PlateLoadingResult
import com.hydrafit.app.core.domain.equipment.calculateDumbbellLoading
import com.hydrafit.app.core.domain.equipment.calculatePlateLoading
import com.hydrafit.app.core.domain.equipment.formatNativeTicks
import com.hydrafit.app.core.domain.equipment.parseNativeTicks
import com.hydrafit.app.core.domain.unit.WeightUnit
import com.hydrafit.app.core.domain.unit.formatWeight
import com.hydrafit.app.core.domain.workout.LoadKind
import kotlin.math.abs
import kotlin.math.roundToInt

/*
 * The Logger's read-only loading helper.
 *
 * It resolves one native target against the active equipment profile's saved inventory. It writes
 * nothing: the profile, the Logger's draft/session state and the prescription are inputs only, and a
 * result is presentation. Prescribed targets stay canonical kilograms; only the helper's own target
 * is expressed in the inventory's native unit. A conversion that cannot be represented exactly at
 * 0.001 native units is disclosed as approximate instead of being presented as the prescription.
 */

/** Which saved loading configuration the helper resolves. */
enum class LoggerLoadingCalculatorKind { PLATE, DUMBBELL }

/** Why the helper could not resolve a result; the dialog renders a localized message. */
enum class LoggerLoadingCalculatorFailure { NO_EQUIPMENT, INVALID_TARGET, OUT_OF_RANGE, FAILED }

/** Where a helper target came from, so the dialog can label it honestly. */
enum class LoggerLoadingTargetSource { MANUAL, DRAFT, GUIDED }

/** One selectable saved bar or dumbbell, keyed by its own stored identity. */
data class LoggerLoadingCalculatorOption(val id: String, val label: String)

/** What a prescribed target converts to when that conversion is not exact at 0.001 native units. */
data class LoggerLoadingApproximation(
    val nativeAmount: String,
    val unitLabel: String,
    val prescribedKg: String
)

/** A target the helper resolves, captured from one source when the helper opened. */
data class LoggerLoadingCalculatorTarget(
    val source: LoggerLoadingTargetSource,
    val sourceId: String,
    val sourceName: String,
    val loadKind: LoadKind?,
    val prescribedKg: Double,
    val targetTicks: Int,
    /** Non-null when the prescribed kilograms are not exactly representable at 0.001 native units. */
    val approximation: LoggerLoadingApproximation? = null
)

/** What asked for the helper, so a late result cannot be applied to a different target. */
data class LoggerLoadingCalculatorRequest(
    val source: LoggerLoadingTargetSource,
    val sourceId: String,
    val sourceName: String,
    val loadKind: LoadKind?,
    val weightKg: Double?,
    val occurrenceId: Long?
)

/**
 * The Logger context an open helper belongs to. When any of it moves, the helper closes rather than
 * reinterpreting its captured target or configuration against the new context.
 */
data class LoggerLoadingSourceContext(
    val selectedExerciseId: String?,
    val sessionId: String?,
    val occurrenceId: Long?,
    val availableSourceIds: Set<String>
)

/** How one reachable arrangement relates to the target. */
enum class LoggerLoadingResultLabel { EXACT, LOWER, UPPER }

/** One denomination's count on a single side of the bar. */
data class LoggerLoadingPlateLine(val denomination: String, val perSideCount: Int)

/**
 * One reachable arrangement.
 *
 * [totalWeight] is the full native weight: the bar plus its collars once and both sides of plates.
 * A dumbbell option is one dumbbell's weight, never a pair total.
 */
data class LoggerLoadingResultOption(
    val label: LoggerLoadingResultLabel,
    val totalWeight: String,
    val plateLines: List<LoggerLoadingPlateLine> = emptyList(),
    val perDumbbell: Boolean = false
)

/** A resolved target: one exact arrangement, or the adjacent reachable ones. */
data class LoggerLoadingCalculatorResultView(
    val targetWeight: String,
    val isExact: Boolean,
    val options: List<LoggerLoadingResultOption>,
    /** No reachable arrangement below/above the target exists in this inventory. */
    val missingLower: Boolean,
    val missingUpper: Boolean
)

/** The helper's single observed profile projection plus the open draft and its last result. */
data class LoggerLoadingCalculatorState(
    val open: Boolean = false,
    val profileName: String = "",
    val inventoryUnit: WeightUnit = WeightUnit.KG,
    val kind: LoggerLoadingCalculatorKind = LoggerLoadingCalculatorKind.PLATE,
    val options: List<LoggerLoadingCalculatorOption> = emptyList(),
    val selectedId: String? = null,
    val targetInput: String = "",
    /** The prescribed target the helper opened from, or null for a blank manual entry. */
    val seed: LoggerLoadingCalculatorTarget? = null,
    val isRunning: Boolean = false,
    val failure: LoggerLoadingCalculatorFailure? = null,
    val result: LoggerLoadingCalculatorResultView? = null
) {
    /** False when this profile has no saved bar/dumbbell to resolve against. */
    val hasSavedEquipment: Boolean get() = options.isNotEmpty()

    /** A saved configuration must be chosen explicitly, and no calculation may already be running. */
    val canRun: Boolean get() = selectedId != null && !isRunning
}

/** A parsed manual target, or the typed failure the dialog renders instead of a raw message. */
sealed interface LoggerLoadingTargetParse {
    data class Valid(val ticks: Int) : LoggerLoadingTargetParse
    data class Invalid(val failure: LoggerLoadingCalculatorFailure) : LoggerLoadingTargetParse
}

/**
 * Parses one manual native amount.
 *
 * A blank, malformed, over-precise or non-finite entry is refused, and an amount outside
 * `0..MAX_LOADING_TICKS` is reported as out of range; neither is clamped or truncated.
 */
fun parseLoggerLoadingTarget(text: String): LoggerLoadingTargetParse {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) {
        return LoggerLoadingTargetParse.Invalid(LoggerLoadingCalculatorFailure.INVALID_TARGET)
    }
    val ticks = runCatching { parseNativeTicks(trimmed) }.getOrNull()
        ?: return LoggerLoadingTargetParse.Invalid(LoggerLoadingCalculatorFailure.INVALID_TARGET)
    if (ticks !in 0..MAX_LOADING_TICKS) {
        return LoggerLoadingTargetParse.Invalid(LoggerLoadingCalculatorFailure.OUT_OF_RANGE)
    }
    return LoggerLoadingTargetParse.Valid(ticks)
}

/**
 * True when a recorded load can seed a contextual calculation: a numeric external or added amount.
 *
 * A bodyweight-only or unrecorded amount, and legacy load whose meaning was never recorded, are
 * never passed in as an assumed total.
 */
fun loggerLoadingTargetEligible(loadKind: LoadKind, weightKg: Double?): Boolean =
    weightKg != null &&
        weightKg.isFinite() &&
        weightKg >= 0.0 &&
        (loadKind == LoadKind.EXTERNAL || loadKind == LoadKind.ADDED)

/** Sub-micro-tick tolerance for a conversion that is exact at the 0.001 native-unit scale. */
private const val EXACT_NATIVE_TOLERANCE = 1e-6

/**
 * Converts a prescribed kilogram target into the inventory's native unit.
 *
 * The conversion uses the inventory's own unit, never the display preference, and rounds the
 * thousandth tick count with ordinary rounding after rejecting a non-finite or out-of-range value.
 * A conversion that is not exactly representable at 0.001 keeps its rounded native amount and carries
 * an explicit approximation; the prescribed kilograms are never rewritten or reinterpreted.
 */
fun loggerLoadingTargetFor(
    request: LoggerLoadingCalculatorRequest,
    unit: WeightUnit
): LoggerLoadingCalculatorTarget? {
    val kilograms = request.weightKg ?: return null
    if (!kilograms.isFinite() || kilograms < 0.0) return null
    val converted = unit.kilogramsToDisplay(kilograms) * LOADING_SCALE
    if (!converted.isFinite()) return null
    val ticks = converted.roundToInt()
    if (ticks !in 0..MAX_LOADING_TICKS) return null
    val approximation = if (abs(converted - ticks) < EXACT_NATIVE_TOLERANCE) {
        null
    } else {
        LoggerLoadingApproximation(
            nativeAmount = formatNativeTicks(ticks),
            unitLabel = unit.label,
            prescribedKg = formatWeight(kilograms)
        )
    }
    return LoggerLoadingCalculatorTarget(
        source = request.source,
        sourceId = request.sourceId,
        sourceName = request.sourceName,
        loadKind = request.loadKind,
        prescribedKg = kilograms,
        targetTicks = ticks,
        approximation = approximation
    )
}

/**
 * The inventory's own unit. `KG`/`LB` is the validated storage contract, with `KG` its default.
 */
fun loggerLoadingInventoryUnit(storageId: String): WeightUnit = when (storageId) {
    "LB" -> WeightUnit.LB
    else -> WeightUnit.KG
}

/** The saved bars or dumbbells this profile can run against; the choice is always explicit. */
fun loggerLoadingCalculatorOptions(
    inventory: LoadingInventory,
    kind: LoggerLoadingCalculatorKind
): List<LoggerLoadingCalculatorOption> = when (kind) {
    LoggerLoadingCalculatorKind.PLATE ->
        inventory.bars.map { LoggerLoadingCalculatorOption(it.id, it.name) }

    LoggerLoadingCalculatorKind.DUMBBELL ->
        inventory.dumbbells.map { LoggerLoadingCalculatorOption(it.id, it.name) }
}

/** Formats a native amount in the inventory's unit, never a converted display value. */
fun formatLoggerLoadingAmount(ticks: Int, unit: WeightUnit): String =
    "${formatNativeTicks(ticks)} ${unit.label}"

/** The largest amount a manual target accepts, in whole native units. */
fun loggerLoadingMaxWholeAmount(): Int = MAX_LOADING_TICKS / LOADING_SCALE

/** The full plate arrangement, including the bar and its collars once. */
fun plateLoadingResultView(
    result: PlateLoadingResult,
    unit: WeightUnit
): LoggerLoadingCalculatorResultView {
    val options = ArrayList<LoggerLoadingResultOption>(3)
    result.exact?.let { loadout ->
        options += LoggerLoadingResultOption(
            label = LoggerLoadingResultLabel.EXACT,
            totalWeight = formatLoggerLoadingAmount(loadout.totalTicks, unit),
            plateLines = loadout.perSidePlates.map {
                LoggerLoadingPlateLine(formatNativeTicks(it.weightTicks), it.perSideCount)
            }
        )
    }
    result.lower?.let { loadout ->
        options += LoggerLoadingResultOption(
            label = LoggerLoadingResultLabel.LOWER,
            totalWeight = formatLoggerLoadingAmount(loadout.totalTicks, unit),
            plateLines = loadout.perSidePlates.map {
                LoggerLoadingPlateLine(formatNativeTicks(it.weightTicks), it.perSideCount)
            }
        )
    }
    result.upper?.let { loadout ->
        options += LoggerLoadingResultOption(
            label = LoggerLoadingResultLabel.UPPER,
            totalWeight = formatLoggerLoadingAmount(loadout.totalTicks, unit),
            plateLines = loadout.perSidePlates.map {
                LoggerLoadingPlateLine(formatNativeTicks(it.weightTicks), it.perSideCount)
            }
        )
    }
    return LoggerLoadingCalculatorResultView(
        targetWeight = formatLoggerLoadingAmount(result.targetTicks, unit),
        isExact = result.isExact,
        options = options,
        missingLower = result.exact == null && result.lower == null,
        missingUpper = result.exact == null && result.upper == null
    )
}

/** One dumbbell's value, never doubled into a pair total. */
fun dumbbellLoadingResultView(
    result: DumbbellLoadingResult,
    unit: WeightUnit
): LoggerLoadingCalculatorResultView {
    val options = ArrayList<LoggerLoadingResultOption>(2)
    result.exact?.let {
        options += LoggerLoadingResultOption(
            label = LoggerLoadingResultLabel.EXACT,
            totalWeight = formatLoggerLoadingAmount(it.perDumbbellTicks, unit),
            perDumbbell = true
        )
    }
    result.lower?.let {
        options += LoggerLoadingResultOption(
            label = LoggerLoadingResultLabel.LOWER,
            totalWeight = formatLoggerLoadingAmount(it.perDumbbellTicks, unit),
            perDumbbell = true
        )
    }
    result.upper?.let {
        options += LoggerLoadingResultOption(
            label = LoggerLoadingResultLabel.UPPER,
            totalWeight = formatLoggerLoadingAmount(it.perDumbbellTicks, unit),
            perDumbbell = true
        )
    }
    return LoggerLoadingCalculatorResultView(
        targetWeight = formatLoggerLoadingAmount(result.targetTicks, unit),
        isExact = result.isExact,
        options = options,
        missingLower = result.exact == null && result.lower == null,
        missingUpper = result.exact == null && result.upper == null
    )
}

/**
 * Resolves one saved configuration against [targetTicks].
 *
 * Null means this inventory no longer resolves the request (an unknown configuration or an invalid
 * inventory), which the dialog reports as a plain failure instead of a raw exception.
 */
fun runLoggerLoadingCalculation(
    inventory: LoadingInventory,
    kind: LoggerLoadingCalculatorKind,
    equipmentId: String,
    targetTicks: Int
): LoggerLoadingCalculatorResultView? = runCatching {
    val unit = loggerLoadingInventoryUnit(inventory.unit)
    when (kind) {
        LoggerLoadingCalculatorKind.PLATE ->
            plateLoadingResultView(calculatePlateLoading(inventory, equipmentId, targetTicks), unit)

        LoggerLoadingCalculatorKind.DUMBBELL ->
            dumbbellLoadingResultView(
                calculateDumbbellLoading(inventory, equipmentId, targetTicks),
                unit
            )
    }
}.getOrNull()
