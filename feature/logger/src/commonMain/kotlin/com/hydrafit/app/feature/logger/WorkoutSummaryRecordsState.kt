package com.hydrafit.app.feature.logger

import com.hydrafit.app.core.domain.schedule.OccurrenceStatus
import com.hydrafit.app.core.domain.unit.WeightUnit
import com.hydrafit.app.core.domain.workout.LoadKind
import com.hydrafit.app.core.domain.workout.ProgressRecordType

/**
 * What the summary's records section shows. It is separate from the summary read, so a records
 * failure is isolated and never blocks Done or the recorded work itself.
 */
sealed interface WorkoutSummaryRecordsScreen {
    /** No occurrence is open, so the section renders nothing. */
    data object Hidden : WorkoutSummaryRecordsScreen

    /** The occurrence's record improvements are being read. */
    data object Loading : WorkoutSummaryRecordsScreen

    /** The occurrence has no chronological improvements to show. */
    data object Empty : WorkoutSummaryRecordsScreen

    /** Reading the improvements failed; the section offers a retry. */
    data object Failed : WorkoutSummaryRecordsScreen

    /** Chronological improvements, in the occurrence's recorded order. */
    data class Ready(val records: List<SummaryRecordRow>) : WorkoutSummaryRecordsScreen
}

/**
 * One chronological improvement: a set that beat this exercise's earlier history's strict best for
 * its metric. [value] is the achievement (kilograms for a load or estimate, reps for a rep record);
 * [reps]/[weightKg] are the qualifying inputs, from the record key or the contributing set when the
 * key omits them. [exerciseName] is the current effective catalog name, or null when it no longer
 * resolves. This is descriptive only and never a prescription-success claim.
 */
data class SummaryRecordRow(
    val exerciseId: String,
    val exerciseName: String?,
    val type: ProgressRecordType,
    val loadKind: LoadKind,
    val reps: Int?,
    val weightKg: Double?,
    val value: Double,
    val setId: Long
)

/**
 * The records section's state. The unit is observed independently so a displayed record tracks a
 * unit change without re-reading the record.
 */
data class WorkoutSummaryRecordsUiState(
    val unit: WeightUnit = WeightUnit.KG,
    val screen: WorkoutSummaryRecordsScreen = WorkoutSummaryRecordsScreen.Hidden
)

/**
 * True when a summary's workout was not finished, so the records section carries the partial-work
 * disclaimer: an eligible improvement in partial work never claims the prescription was completed.
 */
internal fun recordsDisclaimerApplies(status: OccurrenceStatus): Boolean =
    status != OccurrenceStatus.FINISHED
