package com.hydrafit.app.feature.logger

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

/** A ramp row as shown; [percentOfWorkingWeight] is null once the row has been edited. */
data class WarmupRampRow(
    val key: WarmupRampKey,
    val reps: Int,
    val weightKg: Double,
    val percentOfWorkingWeight: Int?
)

data class WarmupRampPlanView(
    val occurrenceEntryId: Long,
    val exerciseName: String,
    val rows: List<WarmupRampRow>
)

/**
 * The warm-up guidance the UI renders: the derived defaults with transient edits applied and skipped
 * items removed. Pure, so the same rules are unit-tested without a Composable.
 */
data class WarmupGuidanceView(
    val preparation: List<WarmupPreparationRow>,
    val ramps: List<WarmupRampPlanView>
) {
    val isEmpty: Boolean get() = preparation.isEmpty() && ramps.all { it.rows.isEmpty() }
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
                percentOfWorkingWeight = if (edited != null) null else ramp.percentOfWorkingWeight
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
    }
)

/** The guidance value open in the transient editor. */
sealed interface WarmupEditTarget {
    data class Preparation(val action: WarmupAction) : WarmupEditTarget

    data class Ramp(val key: WarmupRampKey) : WarmupEditTarget
}

/**
 * A transient editor for one preparation or ramp value. Saving updates only the in-memory guidance
 * view; a ramp still has to be logged to become a recorded set.
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
    /** The current equipment ceiling for a ramp, or null when unbounded/unknown. */
    val ceilingKg: Double? = null
) {
    /** True when the edited value exceeds the equipment ceiling, so it cannot be saved as typed. */
    val weightExceedsCeiling: Boolean
        get() {
            val ceiling = ceilingKg ?: return false
            if (!hasWeight) return false
            return weightInput.toDoubleOrNull()?.let { it > ceiling } == true
        }

    val canSave: Boolean
        get() {
            val repsOk = timed || (repsInput.toIntOrNull() ?: 0) > 0
            val durationOk = !timed || (durationInput.toIntOrNull() ?: 0) > 0
            val weightOk = !hasWeight ||
                weightInput.toDoubleOrNull()?.let { it.isFinite() && it >= 0.0 } == true
            return repsOk && durationOk && weightOk && !weightExceedsCeiling
        }
}
