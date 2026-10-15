package com.hydrafit.app.feature.progress

import com.hydrafit.app.core.domain.unit.WeightUnit
import com.hydrafit.app.core.userdata.measurements.BodyWeightMeasurement

/** A body-weight editor failure the dialog renders with localized guidance. */
enum class BodyWeightEditorError {
    INVALID_WEIGHT,
    INVALID_DATE,
    FUTURE_DATE,
    DATE_OCCUPIED,
    MISSING_MEASUREMENT
}

/**
 * The in-progress body-weight edit. The draft is ephemeral: it exists only while the editor is open
 * and never persists on its own, so Cancel discards it without writing.
 *
 * [initialWeightText] is the exact text the editor opened with. An untouched field compares equal to
 * it, which lets an unchanged edit reuse the stored kilograms rather than round-tripping them through
 * a rounded display value.
 */
data class BodyWeightEditorState(
    /** The day the edited entry currently occupies, or null for a new measurement. */
    val previousLocalEpochDay: Long?,
    val initialDateText: String,
    val initialWeightText: String,
    val dateText: String = initialDateText,
    val weightText: String = initialWeightText,
    val error: BodyWeightEditorError? = null
) {
    /** The date field as a local epoch day, or null when the text is not a real `YYYY-MM-DD` date. */
    val parsedLocalEpochDay: Long?
        get() = parseBodyWeightDate(dateText.trim())

    /** The weight field in display units, or null when the text is not a number. */
    val parsedDisplayWeight: Double?
        get() = weightText.trim().toDoubleOrNull()
}

/** A validated new measurement awaiting the explicit duplicate-day replacement confirmation. */
data class PendingBodyWeightAdd(val localEpochDay: Long, val weightKg: Double)

/**
 * Presentation state for the pushed Body weight destination.
 *
 * Every weight here is a display value derived from the canonical kilograms; nothing in this state is
 * written back except through the explicit add/update/delete/goal actions. [chartDayFrom] and
 * [chartDayTo] are the inclusive civil-day window the chart and list cover.
 */
data class BodyWeightUiState(
    val loading: Boolean = true,
    val loadFailed: Boolean = false,
    val todayLocalEpochDay: Long = 0L,
    val measurements: List<BodyWeightMeasurement> = emptyList(),
    val goalKg: Double? = null,
    val weightUnit: WeightUnit = WeightUnit.KG,
    val range: ProgressRange = ProgressRange.DAYS_28,
    val editor: BodyWeightEditorState? = null,
    val pendingDuplicate: PendingBodyWeightAdd? = null,
    val pendingDeleteDay: Long? = null,
    val goalInput: String = "",
    val goalError: Boolean = false,
    val submitting: Boolean = false
) {
    /** The inclusive last day of the chart window: today in the re-sampled local offset. */
    val chartDayTo: Long
        get() = todayLocalEpochDay

    /** The inclusive first day of the chart window, [ProgressRange.days] long ending on [chartDayTo]. */
    val chartDayFrom: Long
        get() = todayLocalEpochDay - (range.days - 1L)

    /** The measurements inside the selected window, oldest first; nothing is interpolated or invented. */
    val measurementsInRange: List<BodyWeightMeasurement>
        get() = measurements
            .filter { it.localEpochDay in chartDayFrom..chartDayTo }
            .sortedBy { it.localEpochDay }

    /** No measurement has ever been recorded, distinct from "none in this range". */
    val isEmpty: Boolean
        get() = !loading && !loadFailed && measurements.isEmpty()
}
