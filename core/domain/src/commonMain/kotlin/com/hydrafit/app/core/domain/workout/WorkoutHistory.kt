package com.hydrafit.app.core.domain.workout

import com.hydrafit.app.core.domain.equipment.StationAttribution

/**
 * One identity in the workout history. History is keyed by explicit identity only: a session id, or
 * a single recorded set that has no session. Null identities are never grouped with one another and
 * are never inferred from dates or elapsed time.
 */
sealed interface WorkoutHistoryKey {
    /**
     * A recorded session. [sessionId] is the stored id even when the referenced session row is
     * missing, so the identity is preserved rather than invented.
     */
    data class Session(val sessionId: String) : WorkoutHistoryKey

    /** One recorded set with no recorded session identity. */
    data class UnassignedSet(val setId: Long) : WorkoutHistoryKey

    /** Ordering rank; at an identical timestamp a session precedes an unassigned set. */
    val kindRank: Long
        get() = when (this) {
            is Session -> WORKOUT_HISTORY_SESSION_KIND_RANK
            is UnassignedSet -> WORKOUT_HISTORY_UNASSIGNED_SET_KIND_RANK
        }

    /**
     * Deterministic, query-comparable form used only for ordering and cursor continuity.
     */
    val sortKey: String
        get() = when (this) {
            is Session -> sessionId
            is UnassignedSet -> {
                val digits = setId.toString()
                if (setId < 0L) {
                    "-${digits.drop(1).padStart(SET_ID_SORT_WIDTH - 1, '0')}"
                } else {
                    digits.padStart(SET_ID_SORT_WIDTH - 1, '0')
                }
            }
        }
}

/** Ordering rank for a session entry; see [WorkoutHistoryKey.kindRank]. */
const val WORKOUT_HISTORY_SESSION_KIND_RANK = 0L

/** Ordering rank for an unassigned-set entry; see [WorkoutHistoryKey.kindRank]. */
const val WORKOUT_HISTORY_UNASSIGNED_SET_KIND_RANK = 1L

/** Sort width must match the signed fixed-width format in `WorkoutHistory.sq`. */
private const val SET_ID_SORT_WIDTH = 20

/** Where a history entry's displayed exercise names come from. */
enum class WorkoutHistoryNameSource {
    /** The entry has no recorded sets, so it has no exercise names. */
    NONE,

    /**
     * Names are the current catalog names for the recorded exercise ids. They are current context,
     * not a historical snapshot: a rename changes what history search and display resolve.
     */
    CURRENT_CATALOG
}

/**
 * A history list entry. [note] uses the stored encoding: null means the note was never set, while an
 * empty string is an explicit clear. An unassigned set has no note.
 */
data class WorkoutHistoryEntry(
    val key: WorkoutHistoryKey,
    /** Ordering timestamp: a session's stored start, or a set's recorded performed time. */
    val timestampMillis: Long,
    /**
     * The stored local day a session started on. Missing-session entries derive a day from their
     * earliest set and the request's UTC offset.
     */
    val localEpochDay: Long?,
    /** True when a stored session id no longer resolves to a session row. */
    val sessionMissing: Boolean,
    val note: String?,
    val recordedSetCount: Int,
    val nameSource: WorkoutHistoryNameSource
)

/** One recorded set as shown in history, preserving the stored load, RIR and timing meaning. */
data class WorkoutHistorySet(
    val id: Long,
    val exerciseId: String,
    /** The current catalog name, or null when the exercise no longer exists. */
    val exerciseName: String?,
    /** Repetitions for counted work, or null for a timed set. */
    val reps: Int?,
    val weightKg: Double?,
    val loadKind: LoadKind,
    val isWarmup: Boolean,
    val rir: Int?,
    val performedAtMillis: Long,
    val timingProvenance: WorkoutTimingProvenance,
    /** Elapsed whole seconds for a timed set, or null for counted work. */
    val durationSeconds: Int? = null,
    /**
     * The workout occurrence this set was recorded in, or null when it was not part of one. Carried
     * so a correction reached from history keeps the same occurrence attribution as one reached from
     * the Logger, including cancelling that occurrence's rest prompt.
     */
    val occurrenceId: Long? = null,
    /** The frozen physical station attribution, or null for legacy and unattributed work. */
    val station: StationAttribution? = null,
    /** Whole metres for a distance set, or null for reps and timed work. */
    val distanceMeters: Int? = null
)

internal const val WORKOUT_HISTORY_DEFAULT_PAGE_SIZE = 30
internal const val WORKOUT_HISTORY_MAX_PAGE_SIZE = 100

/** Opaque position in a filter's ordering; only produced by [WorkoutHistoryPage.nextCursor]. */
data class WorkoutHistoryCursor(
    val key: WorkoutHistoryKey,
    val timestampMillis: Long,
    internal val search: String,
    internal val dayFrom: Long?,
    internal val dayTo: Long?,
    internal val utcOffsetMillis: Long
)

/**
 * A bounded, filtered request for history entries. Filter and paging inputs are validated here so an
 * invalid or mismatched page request fails loudly instead of returning a silently wrong page.
 */
data class WorkoutHistoryRequest(
    /** Free-text search; surrounding whitespace is ignored and blank means no search filter. */
    val search: String = "",
    /** Inclusive local-day lower bound, or null to leave it unbounded. */
    val dayFrom: Long? = null,
    /** Inclusive local-day upper bound, or null to leave it unbounded. */
    val dayTo: Long? = null,
    /** Caller-supplied UTC offset used to derive a local day for sets without a stored day. */
    val utcOffsetMillis: Long = 0L,
    val pageSize: Int = WORKOUT_HISTORY_DEFAULT_PAGE_SIZE,
    val cursor: WorkoutHistoryCursor? = null
) {
    internal val normalizedSearch: String = search.trim()

    init {
        require(pageSize in 1..WORKOUT_HISTORY_MAX_PAGE_SIZE) {
            "History page size must be between 1 and $WORKOUT_HISTORY_MAX_PAGE_SIZE"
        }
        require(dayFrom == null || dayTo == null || dayFrom <= dayTo) {
            "History day range must not be inverted"
        }
        if (cursor != null) {
            // A cursor is only meaningful against the exact filter that produced it; a mismatch
            // would page over a different result set and silently skip or repeat entries.
            require(
                cursor.search == normalizedSearch &&
                    cursor.dayFrom == dayFrom &&
                    cursor.dayTo == dayTo &&
                    cursor.utcOffsetMillis == utcOffsetMillis
            ) {
                "History cursor does not belong to this filter"
            }
        }
    }

    // The properties below are the persistence-binding projection of this request. They are part of
    // the repository contract rather than UI state, so callers build a request and never set them.

    val hasSearch: Boolean get() = normalizedSearch.isNotEmpty()

    val hasDay: Boolean get() = dayFrom != null || dayTo != null

    val dayFromBound: Long get() = dayFrom ?: Long.MIN_VALUE

    val dayToBound: Long get() = dayTo ?: Long.MAX_VALUE

    /**
     * Literal substring pattern over the case-folded search projections; wildcard characters in user
     * input are escaped. See [unicodeCaseFold] for why input is folded before escaping.
     */
    val searchPattern: String
        get() = if (hasSearch) {
            "%${escapeLikePattern(unicodeCaseFold(normalizedSearch))}%"
        } else {
            "%"
        }

    val hasCursor: Boolean get() = cursor != null

    val cursorTimestamp: Long get() = cursor?.timestampMillis ?: 0L

    val cursorKindRank: Long get() = cursor?.key?.kindRank ?: 0L

    val cursorSortKey: String get() = cursor?.key?.sortKey ?: ""

    /** One extra row is read so the repository can report whether another page exists. */
    val fetchLimit: Int get() = pageSize + 1
}

/** One bounded page of history entries plus whether more follow on the same filter. */
data class WorkoutHistoryPage(val entries: List<WorkoutHistoryEntry>, val hasMore: Boolean) {
    /** The cursor for the next page, or null when this page is the last one. */
    fun nextCursor(request: WorkoutHistoryRequest): WorkoutHistoryCursor? {
        if (!hasMore) return null
        val last = entries.lastOrNull() ?: return null
        return WorkoutHistoryCursor(
            key = last.key,
            timestampMillis = last.timestampMillis,
            search = request.normalizedSearch,
            dayFrom = request.dayFrom,
            dayTo = request.dayTo,
            utcOffsetMillis = request.utcOffsetMillis
        )
    }
}

/** Position within one entry's recorded sets, ordered newest first. */
data class WorkoutHistorySetCursor(val performedAtMillis: Long, val setId: Long)

/** A bounded request for one history entry's recorded sets. */
data class WorkoutHistorySetRequest(
    val pageSize: Int = WORKOUT_HISTORY_DEFAULT_PAGE_SIZE,
    val cursor: WorkoutHistorySetCursor? = null
) {
    init {
        require(pageSize in 1..WORKOUT_HISTORY_MAX_PAGE_SIZE) {
            "History page size must be between 1 and $WORKOUT_HISTORY_MAX_PAGE_SIZE"
        }
    }

    // Persistence-binding projection; see [WorkoutHistoryRequest].
    val hasCursor: Boolean get() = cursor != null

    val cursorPerformedAt: Long get() = cursor?.performedAtMillis ?: 0L

    val cursorSetId: Long get() = cursor?.setId ?: 0L

    val fetchLimit: Int get() = pageSize + 1
}

/** One bounded page of an entry's recorded sets plus whether more follow. */
data class WorkoutHistorySetPage(val sets: List<WorkoutHistorySet>, val hasMore: Boolean) {
    fun nextCursor(request: WorkoutHistorySetRequest): WorkoutHistorySetCursor? {
        if (!hasMore) return null
        val last = sets.lastOrNull() ?: return null
        return WorkoutHistorySetCursor(last.performedAtMillis, last.id)
    }
}

/**
 * Escapes SQL `LIKE` wildcards so user input matches literally. The repository binds the result with
 * `ESCAPE '\'`.
 */
internal fun escapeLikePattern(value: String): String {
    val builder = StringBuilder(value.length)
    for (character in value) {
        when (character) {
            LIKE_ESCAPE, '%', '_' -> builder.append(LIKE_ESCAPE).append(character)
            else -> builder.append(character)
        }
    }
    return builder.toString()
}

private const val LIKE_ESCAPE = '\\'
