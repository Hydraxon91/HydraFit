package com.hydrafit.app.feature.logger

import com.hydrafit.app.core.domain.unit.DistanceUnit
import com.hydrafit.app.core.domain.unit.WeightUnit
import com.hydrafit.app.core.domain.workout.WorkoutHistoryEntry
import com.hydrafit.app.core.domain.workout.WorkoutHistoryKey
import com.hydrafit.app.core.domain.workout.WorkoutHistorySet

/**
 * The filters the history list has actually been asked for, kept separate from the search text the
 * user is still typing so an uncommitted draft never narrows the results.
 */
data class WorkoutHistoryFilter(
    val search: String = "",
    /** Inclusive local-day lower bound, or null to leave it unbounded. */
    val dayFrom: Long? = null,
    /** Inclusive local-day upper bound, or null to leave it unbounded. */
    val dayTo: Long? = null
) {
    val hasFilters: Boolean get() = search.isNotBlank() || dayFrom != null || dayTo != null

    /**
     * An inverted range is reported to the user and never sent to the repository, which would
     * reject it.
     */
    val hasInvalidDayRange: Boolean
        get() {
            val from = dayFrom ?: return false
            val to = dayTo ?: return false
            return from > to
        }
}

/**
 * One exercise's current setup note, shown as context for recorded sets. The stored encoding is
 * preserved: null means no note was ever recorded, while an empty string is an explicit clear.
 */
data class HistoryExerciseNote(
    val exerciseId: String,
    val note: String?,
    /** True when the read failed; the note is unknown rather than absent. */
    val loadFailed: Boolean = false
)

/** The history browser's presentation state: the list, plus the one entry currently open. */
data class WorkoutHistoryUiState(
    val utcOffsetMillis: Long = 0L,
    val weightUnit: WeightUnit = WeightUnit.KG,
    val distanceUnit: DistanceUnit = DistanceUnit.METERS,
    // ── List ──────────────────────────────────────────────────────────────
    /** The search text being typed; only an explicit apply changes [filter]. */
    val searchDraft: String = "",
    val filter: WorkoutHistoryFilter = WorkoutHistoryFilter(),
    val entries: List<WorkoutHistoryEntry> = emptyList(),
    val loading: Boolean = true,
    val failed: Boolean = false,
    val hasMore: Boolean = false,
    val loadingMore: Boolean = false,
    val loadMoreFailed: Boolean = false,
    // ── Open entry ────────────────────────────────────────────────────────
    /** The explicit identity of the entry being shown, or null when no entry is open. */
    val selectedKey: WorkoutHistoryKey? = null,
    val detailEntry: WorkoutHistoryEntry? = null,
    val detailLoading: Boolean = false,
    val detailFailed: Boolean = false,
    val sets: List<WorkoutHistorySet> = emptyList(),
    val setsLoading: Boolean = false,
    val setsFailed: Boolean = false,
    val hasMoreSets: Boolean = false,
    val loadingMoreSets: Boolean = false,
    val loadMoreSetsFailed: Boolean = false,
    val exerciseNotes: Map<String, HistoryExerciseNote> = emptyMap()
) {
    /** Nothing recorded yet and no filter is narrowing the list. */
    val showEmptyHistory: Boolean
        get() = !loading && !failed && entries.isEmpty() && !filter.hasFilters

    /** Recorded history exists but none of it matches the applied filters. */
    val showNoMatches: Boolean
        get() = !loading && !failed && entries.isEmpty() && filter.hasFilters

    /** The entry's identity is gone or no longer resolves to a recorded entry. */
    val showDetailUnavailable: Boolean
        get() = selectedKey == null || (!detailLoading && !detailFailed && detailEntry == null)

    /** The entry still exists but holds no recorded sets, e.g. a noted session after a correction. */
    val showNoRecordedSets: Boolean
        get() = detailEntry != null && !setsLoading && !setsFailed && sets.isEmpty()
}

/**
 * The three stored note states. An explicit clear is a recorded value, so it must never be shown as
 * "never set".
 */
enum class HistoryNoteState { ABSENT, CLEARED, PRESENT }

internal fun noteStateOf(note: String?): HistoryNoteState = when {
    note == null -> HistoryNoteState.ABSENT
    note.isEmpty() -> HistoryNoteState.CLEARED
    else -> HistoryNoteState.PRESENT
}

/**
 * The correction target for one recorded history set. Identity, stored load meaning, warm-up flag and
 * occurrence attribution come from the stored row, so the existing Logger editor corrects exactly the
 * set the recent-sets list would correct, through the same use case.
 */
internal fun WorkoutHistorySet.toLoggedSetRow(key: WorkoutHistoryKey): LoggedSetRow = LoggedSetRow(
    id = id,
    performedAtMillis = performedAtMillis,
    exerciseId = exerciseId,
    exerciseName = exerciseName ?: exerciseId,
    reps = reps,
    durationSeconds = durationSeconds,
    distanceMeters = distanceMeters,
    weightKg = weightKg,
    rir = rir,
    isWarmup = isWarmup,
    loadKind = loadKind,
    occurrenceId = occurrenceId,
    sessionId = (key as? WorkoutHistoryKey.Session)?.sessionId
)
