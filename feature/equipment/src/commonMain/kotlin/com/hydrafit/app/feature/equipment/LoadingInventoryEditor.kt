package com.hydrafit.app.feature.equipment

import com.hydrafit.app.core.domain.equipment.LoadingBar
import com.hydrafit.app.core.domain.equipment.LoadingDumbbell
import com.hydrafit.app.core.domain.equipment.LoadingInventory
import com.hydrafit.app.core.domain.equipment.MAX_LOADING_TICKS
import com.hydrafit.app.core.domain.equipment.OwnedPlate
import com.hydrafit.app.core.domain.equipment.formatNativeTicks
import com.hydrafit.app.core.domain.equipment.parseNativeTicks

/** The inventory's own unit. Amounts stay in this unit; nothing here converts or reinterprets them. */
enum class LoadingUnitOption(val storageId: String) {
    KG("KG"),
    LB("LB");

    companion object {
        fun fromStorage(id: String): LoadingUnitOption =
            entries.firstOrNull { it.storageId == id } ?: KG
    }
}

/** A plate denomination being edited. Empty input is a blank row, not a zero plate. */
data class OwnedPlateDraft(val weightInput: String = "", val countInput: String = "")

/**
 * A bar being edited. [sourceId] keeps an existing bar's identity across a rename; new bars
 * derive one from the name at save time.
 */
data class LoadingBarDraft(
    val sourceId: String? = null,
    val name: String = "",
    val weightInput: String = "",
    /** Total collar weight, never a per-side value. */
    val collarInput: String = ""
)

/** Dumbbells use exactly one representation: a fixed value list, or an adjustable range. */
enum class DumbbellDraftMode { FIXED_VALUES, ADJUSTABLE_RANGE }

data class LoadingDumbbellDraft(
    val sourceId: String? = null,
    val name: String = "",
    val mode: DumbbellDraftMode = DumbbellDraftMode.FIXED_VALUES,
    /** Comma-separated per-dumbbell values; a pair is never implied. */
    val fixedInput: String = "",
    val minimumInput: String = "",
    val maximumInput: String = "",
    val incrementInput: String = ""
)

/** Everything that can refuse a draft at Save, mapped to a localized message by the UI. */
enum class LoadingEditorFailure {
    UNIT_REQUIRED,
    PLATE_VALUE,
    PLATE_COUNT,
    DUPLICATE_PLATE,
    PLATE_LIMIT,
    NAME_REQUIRED,
    NAME_DUPLICATE,
    AMOUNT_RANGE,
    INVENTORY_LIMIT,
    DUMBBELL_FIXED_VALUE,
    DUMBBELL_RANGE,
    DUMBBELL_INCREMENT,

    /** The active profile changed while saving: reopen the draft instead of retrying it. */
    STALE,
    SAVE_FAILED
}

sealed interface LoadingDraftResult {
    data class Valid(val inventory: LoadingInventory) : LoadingDraftResult
    data class Invalid(val failure: LoadingEditorFailure) : LoadingDraftResult
}

private const val MAX_DENOMINATIONS = 16
private const val MAX_PLATE_COUNT = 100
private const val MAX_NAME_LENGTH = 80

/**
 * An unsaved loading-inventory draft plus its visible saved list.
 *
 * Editing the draft never writes; only Save reaches the repository, and Cancel (or a dismissal)
 * discards it. Removing a row here removes it from the draft, not from the stored configuration.
 *
 * [token] identifies one open draft so a late save result cannot close or overwrite a newer draft.
 */
data class LoadingInventoryEditorState(
    val open: Boolean = false,
    val token: Long = 0L,
    val unit: LoadingUnitOption = LoadingUnitOption.KG,
    /** A populated inventory shows its stored unit read-only; an empty one lets the user choose. */
    val unitLocked: Boolean = false,
    val plates: List<OwnedPlateDraft> = emptyList(),
    val bars: List<LoadingBarDraft> = emptyList(),
    val dumbbells: List<LoadingDumbbellDraft> = emptyList(),
    val isSaving: Boolean = false,
    /** The active profile changed mid-save: reopen the editor instead of retrying into a new profile. */
    val staleContext: Boolean = false,
    val failure: LoadingEditorFailure? = null
) {
    val isOpen: Boolean
        get() = open

    /** A stale draft must be reopened, so it cannot write into whichever profile is now active. */
    val canSave: Boolean
        get() = open && !isSaving && !staleContext

    val isEmpty: Boolean
        get() = plates.isEmpty() && bars.isEmpty() && dumbbells.isEmpty()

    fun withPlateChanged(index: Int, draft: OwnedPlateDraft): LoadingInventoryEditorState =
        copy(plates = plates.replaceAt(index, draft), failure = null)

    fun withPlateRemoved(index: Int): LoadingInventoryEditorState =
        copy(plates = plates.withoutAt(index), failure = null)

    fun withPlateAdded(): LoadingInventoryEditorState =
        copy(plates = plates + OwnedPlateDraft(), failure = null)

    fun withBarChanged(index: Int, draft: LoadingBarDraft): LoadingInventoryEditorState =
        copy(bars = bars.replaceAt(index, draft), failure = null)

    fun withBarRemoved(index: Int): LoadingInventoryEditorState =
        copy(bars = bars.withoutAt(index), failure = null)

    /** A new bar starts with an explicit collar weight of 0, so most bars need only a name/weight. */
    fun withBarAdded(): LoadingInventoryEditorState =
        copy(bars = bars + LoadingBarDraft(collarInput = "0"), failure = null)

    fun withDumbbellChanged(index: Int, draft: LoadingDumbbellDraft): LoadingInventoryEditorState =
        copy(dumbbells = dumbbells.replaceAt(index, draft), failure = null)

    fun withDumbbellRemoved(index: Int): LoadingInventoryEditorState =
        copy(dumbbells = dumbbells.withoutAt(index), failure = null)

    fun withDumbbellAdded(): LoadingInventoryEditorState =
        copy(dumbbells = dumbbells + LoadingDumbbellDraft(), failure = null)

    /** The unit is selectable only while nothing is configured; a populated inventory keeps its own. */
    fun withUnit(option: LoadingUnitOption): LoadingInventoryEditorState =
        if (unitLocked) copy() else copy(unit = option, failure = null)

    /**
     * Validates the whole draft and builds the inventory to store.
     *
     * Overprecision, out-of-range amounts, duplicate denominations, an over-limit inventory and a
     * half-specified dumbbell all produce a typed failure instead of a truncated or clamped value.
     */
    fun buildInventory(): LoadingDraftResult {
        if (plates.size > MAX_DENOMINATIONS) {
            return LoadingDraftResult.Invalid(LoadingEditorFailure.PLATE_LIMIT)
        }
        val ownedPlates = ArrayList<OwnedPlate>(plates.size)
        plates.forEach { draft ->
            val weight = draft.weightInput.nativeTicksOrNull()
                ?: return LoadingDraftResult.Invalid(LoadingEditorFailure.PLATE_VALUE)
            if (weight !in 1..MAX_LOADING_TICKS) {
                return LoadingDraftResult.Invalid(LoadingEditorFailure.PLATE_VALUE)
            }
            val count = draft.countInput.trim().toIntOrNull()
                ?: return LoadingDraftResult.Invalid(LoadingEditorFailure.PLATE_COUNT)
            if (count !in 0..MAX_PLATE_COUNT) {
                return LoadingDraftResult.Invalid(LoadingEditorFailure.PLATE_COUNT)
            }
            if (ownedPlates.any { it.weightTicks == weight }) {
                return LoadingDraftResult.Invalid(LoadingEditorFailure.DUPLICATE_PLATE)
            }
            ownedPlates += OwnedPlate(weight, count)
        }
        val plateTotal = ownedPlates.sumOf { it.weightTicks.toLong() * it.count }
        if (plateTotal > MAX_LOADING_TICKS) {
            return LoadingDraftResult.Invalid(LoadingEditorFailure.INVENTORY_LIMIT)
        }
        val bars = ArrayList<LoadingBar>(this.bars.size)
        this.bars.forEach { draft ->
            val name = draft.name.trim()
            if (!name.isValidLoadingName()) {
                return LoadingDraftResult.Invalid(LoadingEditorFailure.NAME_REQUIRED)
            }
            val id = draft.sourceId?.takeIf { it.isNotBlank() } ?: name.toLoadingId()
            if (bars.any { it.id == id }) {
                return LoadingDraftResult.Invalid(LoadingEditorFailure.NAME_DUPLICATE)
            }
            val weight = draft.weightInput.nativeTicksOrNull()
                ?: return LoadingDraftResult.Invalid(LoadingEditorFailure.AMOUNT_RANGE)
            val collar = draft.collarInput.nativeTicksOrNull()
                ?: return LoadingDraftResult.Invalid(LoadingEditorFailure.AMOUNT_RANGE)
            if (weight !in 0..MAX_LOADING_TICKS || collar !in 0..MAX_LOADING_TICKS) {
                return LoadingDraftResult.Invalid(LoadingEditorFailure.AMOUNT_RANGE)
            }
            if (plateTotal + weight + collar > MAX_LOADING_TICKS) {
                return LoadingDraftResult.Invalid(LoadingEditorFailure.INVENTORY_LIMIT)
            }
            bars += LoadingBar(id, name, weight, collar)
        }
        val dumbbells = ArrayList<LoadingDumbbell>(this.dumbbells.size)
        this.dumbbells.forEach { draft ->
            val name = draft.name.trim()
            if (!name.isValidLoadingName()) {
                return LoadingDraftResult.Invalid(LoadingEditorFailure.NAME_REQUIRED)
            }
            val id = draft.sourceId?.takeIf { it.isNotBlank() } ?: name.toLoadingId()
            if (dumbbells.any { it.id == id }) {
                return LoadingDraftResult.Invalid(LoadingEditorFailure.NAME_DUPLICATE)
            }
            val resolved = when (draft.mode) {
                DumbbellDraftMode.FIXED_VALUES ->
                    draft.fixedInput.toFixedValueList()
                        ?: return LoadingDraftResult.Invalid(
                            LoadingEditorFailure.DUMBBELL_FIXED_VALUE
                        )

                DumbbellDraftMode.ADJUSTABLE_RANGE ->
                    draft.toAdjustableRange()
                        ?: return LoadingDraftResult.Invalid(LoadingEditorFailure.DUMBBELL_RANGE)
            }
            dumbbells += resolved.copy(id = id, name = name)
        }
        return LoadingDraftResult.Valid(
            LoadingInventory(
                unit = unit.storageId,
                plates = ownedPlates,
                bars = bars,
                dumbbells = dumbbells
            )
        )
    }

    companion object {
        /**
         * Opens a draft over a stored inventory. Existing weights are copied verbatim in their own
         * unit, and a populated inventory locks the unit rather than reinterpreting anything.
         */
        fun forInventory(inventory: LoadingInventory, token: Long): LoadingInventoryEditorState =
            LoadingInventoryEditorState(
                open = true,
                token = token,
                unit = LoadingUnitOption.fromStorage(inventory.unit),
                unitLocked = inventory.plates.isNotEmpty() ||
                    inventory.bars.isNotEmpty() ||
                    inventory.dumbbells.isNotEmpty(),
                plates = inventory.plates.map {
                    OwnedPlateDraft(formatNativeTicks(it.weightTicks), it.count.toString())
                },
                bars = inventory.bars.map {
                    LoadingBarDraft(
                        sourceId = it.id,
                        name = it.name,
                        weightInput = formatNativeTicks(it.weightTicks),
                        collarInput = formatNativeTicks(it.collarTicks)
                    )
                },
                dumbbells = inventory.dumbbells.map { it.toDraft() }
            )
    }
}

/** The stored unit amount as a raw decimal, or null when the text is not an exact native amount. */
private fun String.nativeTicksOrNull(): Int? {
    val text = trim()
    if (text.isEmpty()) return null
    return runCatching { parseNativeTicks(text) }.getOrNull()
}

private fun String.isValidLoadingName(): Boolean =
    isNotEmpty() && length <= MAX_NAME_LENGTH && this == trim()

/** Stable, name-derived identity for a new bar or dumbbell, mirroring custom-equipment ids. */
private fun String.toLoadingId(): String =
    uppercase().map { if (it.isLetterOrDigit()) it else '_' }.joinToString("")

private fun LoadingDumbbell.toDraft(): LoadingDumbbellDraft = if (fixedTicks.isNotEmpty()) {
    LoadingDumbbellDraft(
        sourceId = id,
        name = name,
        mode = DumbbellDraftMode.FIXED_VALUES,
        fixedInput = fixedTicks.joinToString(", ") { formatNativeTicks(it) }
    )
} else {
    LoadingDumbbellDraft(
        sourceId = id,
        name = name,
        mode = DumbbellDraftMode.ADJUSTABLE_RANGE,
        minimumInput = minimumTicks?.let { formatNativeTicks(it) }.orEmpty(),
        maximumInput = maximumTicks?.let { formatNativeTicks(it) }.orEmpty(),
        incrementInput = incrementTicks?.let { formatNativeTicks(it) }.orEmpty()
    )
}

private fun LoadingDumbbellDraft.toAdjustableRange(): LoadingDumbbell? {
    val minimum = minimumInput.nativeTicksOrNull() ?: return null
    val maximum = maximumInput.nativeTicksOrNull() ?: return null
    val increment = incrementInput.nativeTicksOrNull() ?: return null
    if (minimum !in 1..MAX_LOADING_TICKS) return null
    if (maximum !in minimum..MAX_LOADING_TICKS) return null
    if (increment !in 1..MAX_LOADING_TICKS) return null
    if ((maximum - minimum) % increment != 0) return null
    return LoadingDumbbell(
        id = "",
        name = "",
        minimumTicks = minimum,
        maximumTicks = maximum,
        incrementTicks = increment
    )
}

private fun String.toFixedValueList(): LoadingDumbbell? {
    val parts = split(',').map { it.trim() }
    if (parts.isEmpty() || parts.any { it.isEmpty() }) return null
    val values = parts.map { it.nativeTicksOrNull() ?: return null }
    if (values.any { it !in 1..MAX_LOADING_TICKS }) return null
    if (values.distinct().size != values.size) return null
    return LoadingDumbbell(id = "", name = "", fixedTicks = values)
}

/** Formats a native amount for display without implying a unit conversion. */
internal fun LoadingUnitOption.formatAmount(ticks: Int): String =
    "${formatNativeTicks(ticks)} $storageId"

private fun <T> List<T>.replaceAt(index: Int, value: T): List<T> =
    if (index !in indices) this else mapIndexed { i, item -> if (i == index) value else item }

private fun <T> List<T>.withoutAt(index: Int): List<T> =
    if (index !in indices) this else filterIndexed { i, _ -> i != index }
