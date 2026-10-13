package com.hydrafit.app.feature.logger

import com.hydrafit.app.core.domain.unit.WeightUnit
import com.hydrafit.app.core.domain.workout.WarmupAction
import com.hydrafit.app.core.domain.workout.WarmupGuidance
import com.hydrafit.app.core.domain.workout.WarmupPreparationItem
import com.hydrafit.app.core.domain.workout.WarmupRampSet

/** Identity of one ramp row inside a workout's guidance. */
data class WarmupRampKey(val occurrenceEntryId: Long, val rampIndex: Int)

/** A preparation row as shown, after any transient edit; skipped rows are removed entirely. */
data class WarmupPreparationRow(
    val action: WarmupAction,
    val reps: Int?,
    val durationSeconds: Int?,
    /** True when the shown value came from a transient edit rather than the default. */
    val edited: Boolean
)

/**
 * A ramp row as shown; [percentOfWorkingWeight] is null once the row has been edited, and
 * [isEquipmentLimited] is true when the current equipment ceiling lowered the load below its
 * percentage, so the percentage is not shown as if it were exact. [ceilingKg] is the slot's current
 * equipment ceiling, so [exceedsCeiling] tells the UI to refuse the row.
 */
data class WarmupRampRow(
    val key: WarmupRampKey,
    val reps: Int,
    val weightKg: Double,
    val percentOfWorkingWeight: Int?,
    val isEquipmentLimited: Boolean = false,
    /** The slot's current equipment ceiling, or null when unbounded or unknown. */
    val ceilingKg: Double? = null
) {
    /**
     * True when the shown load is above the slot's current equipment ceiling. A default suggestion is
     * always clamped to the ceiling, so this only arises from an edit that a later equipment change
     * invalidated.
     */
    val exceedsCeiling: Boolean
        get() = ceilingKg?.let { weightKg > it } == true
}

data class WarmupRampPlanView(
    val occurrenceEntryId: Long,
    val exerciseName: String,
    val rows: List<WarmupRampRow>
)

/**
 * The warm-up guidance the UI renders: the derived defaults with transient edits applied and skipped
 * items removed. Pure, so the same rules are unit-tested without a Composable.
 * [skippedPreparationCount] counts skipped items that still exist, so the UI can offer to restore them
 * even when every preparation row is currently hidden.
 */
data class WarmupGuidanceView(
    val preparation: List<WarmupPreparationRow>,
    val ramps: List<WarmupRampPlanView>,
    val skippedPreparationCount: Int = 0
) {
    val isEmpty: Boolean
        get() = preparation.isEmpty() &&
            ramps.all { it.rows.isEmpty() } &&
            skippedPreparationCount == 0
}

/** Applies the transient edits and skips to [guidance]. Nothing here is persisted. */
fun buildWarmupGuidanceView(
    guidance: WarmupGuidance,
    preparationEdits: Map<WarmupAction, WarmupPreparationItem> = emptyMap(),
    skipped: Set<WarmupAction> = emptySet(),
    rampEdits: Map<WarmupRampKey, WarmupRampSet> = emptyMap()
): WarmupGuidanceView = WarmupGuidanceView(
    preparation = guidance.preparation
        .filterNot { it.action in skipped }
        .map { item ->
            val edited = preparationEdits[item.action]
            WarmupPreparationRow(
                action = item.action,
                reps = edited?.reps ?: item.reps,
                durationSeconds = edited?.durationSeconds ?: item.durationSeconds,
                edited = edited != null
            )
        },
    ramps = guidance.ramps.mapNotNull { plan ->
        val rows = plan.rampSets.mapIndexed { index, ramp ->
            val key = WarmupRampKey(plan.occurrenceEntryId, index)
            val edited = rampEdits[key]
            WarmupRampRow(
                key = key,
                reps = edited?.reps ?: ramp.reps,
                weightKg = edited?.weightKg ?: ramp.weightKg,
                // An edited load is no longer the default percentage of the working weight.
                percentOfWorkingWeight = if (edited != null) null else ramp.percentOfWorkingWeight,
                // A default the ceiling lowered also stops describing its percentage.
                isEquipmentLimited = edited == null && ramp.isEquipmentLimited,
                ceilingKg = plan.ceilingKg
            )
        }
        if (rows.isEmpty()) {
            null
        } else {
            WarmupRampPlanView(
                occurrenceEntryId = plan.occurrenceEntryId,
                exerciseName = plan.exerciseName,
                rows = rows
            )
        }
    },
    // Only items that still exist can be restored; a stale action is not counted.
    skippedPreparationCount = guidance.preparation.count { it.action in skipped }
)

/** The guidance value open in the transient editor. */
sealed interface WarmupEditTarget {
    data class Preparation(val action: WarmupAction) : WarmupEditTarget

    data class Ramp(val key: WarmupRampKey) : WarmupEditTarget
}

/**
 * A transient editor for one preparation or ramp value. Saving updates only the in-memory guidance
 * view; a ramp still has to be logged to become a recorded set. A timed preparation item is entered in
 * whole minutes and stored as seconds; a ramp load is entered in [weightUnit], frozen when the editor
 * opened so a mid-edit unit change cannot reinterpret the typed digits.
 */
data class WarmupGuidanceEditor(
    val target: WarmupEditTarget,
    val repsInput: String,
    val durationInput: String,
    val weightInput: String,
    /** True for a timed preparation item, where duration is edited instead of reps. */
    val timed: Boolean,
    /** True for a ramp, where load is editable. */
    val hasWeight: Boolean,
    /** The unit the load is entered and shown in, frozen when the editor opened. */
    val weightUnit: WeightUnit,
    /** The current equipment ceiling for a ramp, or null when unbounded/unknown. */
    val ceilingKg: Double? = null,
    /**
     * True after Reset restored the derived default into the fields, so Save applies the default
     * rather than the typed value. Any further input clears it.
     */
    val resetsToDefault: Boolean = false
) {
    /** The entered duration in whole minutes, or null when it is not a positive number. */
    private val durationMinutes: Int?
        get() = durationInput.toIntOrNull()?.takeIf { it > 0 }

    /** True when the entered load exceeds the equipment ceiling in the unit it was entered in. */
    val weightExceedsCeiling: Boolean
        get() {
            val ceiling = ceilingKg ?: return false
            if (!hasWeight) return false
            val entered = weightInput.toDoubleOrNull() ?: return false
            return weightUnit.displayToKilograms(entered) > ceiling
        }

    val canSave: Boolean
        get() {
            val repsOk = timed || (repsInput.toIntOrNull() ?: 0) > 0
            val durationOk = !timed ||
                durationMinutes?.let { it <= MAX_PREPARATION_MINUTES } == true
            val weightOk = !hasWeight ||
                weightInput.toDoubleOrNull()?.let { it.isFinite() && it >= 0.0 } == true
            return repsOk && durationOk && weightOk && !weightExceedsCeiling
        }

    companion object {
        /** A timed preparation item is entered in minutes; longer than an hour is not a warm-up. */
        const val MAX_PREPARATION_MINUTES: Int = 60
    }
}
