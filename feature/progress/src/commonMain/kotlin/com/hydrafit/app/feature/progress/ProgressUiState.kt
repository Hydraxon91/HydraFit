package com.hydrafit.app.feature.progress

import com.hydrafit.app.core.domain.equipment.StationAttribution
import com.hydrafit.app.core.domain.unit.DistanceUnit
import com.hydrafit.app.core.domain.unit.WeightUnit
import com.hydrafit.app.core.domain.workout.ProgressDay
import com.hydrafit.app.core.domain.workout.ProgressRecord
import com.hydrafit.app.core.domain.workout.ProgressRecordKey
import com.hydrafit.app.core.domain.workout.StationFilter
import com.hydrafit.app.core.domain.workout.WorkoutHistorySet

/** The three explicit chart ranges. Only the chart window changes; records and history are all-time. */
enum class ProgressRange(val days: Int) {
    DAYS_28(28),
    DAYS_84(84),
    DAYS_365(365)
}

/** One selectable exercise, with the current catalog name used for search and display. */
data class ProgressExerciseOption(val id: String, val name: String)

/**
 * Presentation state for the standalone Exercise Progress destination.
 *
 * Charts are bounded to the selected range; records and the read-only history are all-time and are
 * labelled as such, so neither is ever presented as range-local. Nothing here is written back: the
 * ViewModel only reads.
 */
data class ProgressUiState(
    val utcOffsetMillis: Long,
    val weightUnit: WeightUnit = WeightUnit.KG,
    /** The display unit for recorded distance; storage stays whole metres. */
    val distanceUnit: DistanceUnit = DistanceUnit.METERS,
    val exerciseQuery: String = "",
    val exercises: List<ProgressExerciseOption> = emptyList(),
    val selectedExerciseId: String? = null,
    /** The current catalog name, or null when the selected id is no longer in the catalog. */
    val selectedExerciseName: String? = null,
    val range: ProgressRange = ProgressRange.DAYS_28,
    /** Distinct station labels observed in this exercise's history. */
    val stationOptions: List<StationAttribution> = emptyList(),
    val stationFilter: StationFilter = StationFilter.All,
    /** Inclusive civil-day window the charts cover; null before an exercise is selected. */
    val chartDayFrom: Long? = null,
    val chartDayTo: Long? = null,
    val days: List<ProgressDay> = emptyList(),
    val records: List<ProgressRecord> = emptyList(),
    val chartsLoading: Boolean = false,
    val chartsFailed: Boolean = false,
    val historySets: List<WorkoutHistorySet> = emptyList(),
    val historyLoading: Boolean = false,
    val historyFailed: Boolean = false,
    val historyHasMore: Boolean = false,
    val loadingMoreHistory: Boolean = false,
    val loadMoreHistoryFailed: Boolean = false,
    val selectedSetId: Long? = null,
    /**
     * The record whose supporting set is open. The same set id can hold several records, so the
     * placement needs both: the detail renders under the record this key names, and the id says
     * which holder within it is shown.
     */
    val selectedRecordKey: ProgressRecordKey? = null,
    val supportingSet: WorkoutHistorySet? = null,
    val supportingSetLoading: Boolean = false,
    val supportingSetFailed: Boolean = false,
    val supportingSetMissing: Boolean = false
) {
    /** True when the selected exercise id no longer resolves to a catalog name. */
    val selectedExerciseNameMissing: Boolean
        get() = selectedExerciseId != null && selectedExerciseName == null

    val matchingExercises: List<ProgressExerciseOption>
        get() {
            val query = exerciseQuery.trim()
            if (query.isEmpty()) return exercises
            return exercises.filter { it.name.contains(query, ignoreCase = true) }
        }

    val hasSelectedExercise: Boolean get() = selectedExerciseId != null

    /** No chartable working sets in the range, distinct from "history is also empty". */
    val chartsHaveNoEligibleDays: Boolean
        get() = hasSelectedExercise && !chartsLoading && !chartsFailed && days.isEmpty()

    val historyIsEmpty: Boolean
        get() = hasSelectedExercise && !historyLoading && !historyFailed && historySets.isEmpty()

    val hasStationOptions: Boolean
        get() = stationOptions.isNotEmpty()
}
