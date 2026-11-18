package com.hydrafit.app.core.domain.workout

import com.hydrafit.app.core.domain.fatigue.LoggedSet
import kotlinx.coroutines.flow.Flow

interface WorkoutLogRepository {
    suspend fun add(set: WorkoutSet)

    /**
     * Persists one performed cluster: the parent set plus its additional segments, in one transaction.
     * The parent keeps the opening segment's own measurement and gains the cluster identity, so a
     * partial failure never leaves a half-written cluster behind.
     */
    suspend fun addCluster(set: WorkoutSet, cluster: WorkoutCluster)

    /**
     * Appends the segments performed after an interrupted cluster, keeping the parent's identity and
     * opening segment. A completed cluster cannot be extended beyond its prescription.
     */
    suspend fun appendClusterSegments(setId: Long, segments: List<PerformedClusterSegment>)

    /**
     * Every recorded segment of a performed cluster, including the parent's own opening segment.
     * Returns an empty list for an ordinary set, so a caller distinguishes a cluster by the set's
     * own summary rather than by this read.
     */
    suspend fun clusterSegments(setId: Long): List<PerformedClusterSegment> = emptyList()

    /** Attaches a set to a session; used when auto-start or the legacy backfill assigns an id. */
    suspend fun assignSession(setId: Long, sessionId: String)

    suspend fun delete(id: Long)

    /** Corrects only a set's performed-at time; every other column is left untouched. */
    suspend fun updateSetPerformedAt(setId: Long, performedAtMillis: Long)

    suspend fun all(): List<WorkoutSet>

    /**
     * The latest set in [sessionId] by `performedAt` (ties broken by most recently inserted), or
     * null when the session has no sets yet. Bounded single-row read for session-boundary checks.
     */
    suspend fun lastSetBySession(sessionId: String): WorkoutSet?

    /** Every set logged against [occurrenceId], oldest first; used to compute workout progress. */
    suspend fun setsForOccurrence(occurrenceId: Long): List<WorkoutSet>

    // The bounded reads below let a screen avoid loading the whole log. Each default filters `all()`
    // with the exact rule the database overrides implement, so a repository without an index
    // behaves identically and only the cost differs.

    /** Every set of [sessionId], newest first (performed time, then most recently inserted). */
    suspend fun setsBySessionNewestFirst(sessionId: String): List<WorkoutSet> = all()
        .filter { it.sessionId == sessionId }
        .sortedWith(
            compareByDescending<WorkoutSet> {
                it.performedAtMillis
            }.thenByDescending { it.id }
        )

    /**
     * The session that most recently recorded a set, ignoring [excludedSessionId] and sets with no
     * recorded session; null when there is none.
     */
    suspend fun latestSessionIdExcluding(excludedSessionId: String?): String? = all()
        .filter { it.sessionId != null && it.sessionId != excludedSessionId }
        .maxWithOrNull(compareBy<WorkoutSet> { it.performedAtMillis }.thenBy { it.id })
        ?.sessionId

    /**
     * The newest working (non-warm-up) set of [exerciseId] at [stationId], where a null station
     * matches only sets logged without a station. Equal times resolve to the earliest inserted set.
     */
    suspend fun lastWorkingSetForExercise(exerciseId: String, stationId: Long?): WorkoutSet? = all()
        .filter {
            it.exerciseId == exerciseId &&
                !it.isWarmup &&
                if (stationId == null) it.station == null else it.station?.id == stationId
        }
        .maxByOrNull { it.performedAtMillis }

    /**
     * The newest working set of [exerciseId] that recorded an external weight; equal times resolve
     * to the most recently inserted set.
     */
    suspend fun lastExternalLoadSet(exerciseId: String): WorkoutSet? = all()
        .filter {
            it.exerciseId == exerciseId &&
                !it.isWarmup &&
                it.loadKind == LoadKind.EXTERNAL &&
                it.weightKg != null
        }
        .maxWithOrNull(compareBy<WorkoutSet> { it.performedAtMillis }.thenBy { it.id })

    /**
     * Up to [limit] of the newest working (non-warm-up) sets of [exerciseId] across every session and
     * station, newest first; equal times resolve to the most recently inserted set.
     */
    suspend fun recentWorkingSetsForExercise(exerciseId: String, limit: Int): List<WorkoutSet> =
        all()
            .filter { it.exerciseId == exerciseId && !it.isWarmup }
            .sortedWith(
                compareByDescending<WorkoutSet> { it.performedAtMillis }.thenByDescending { it.id }
            )
            .take(limit)

    /** Exercises with a working set performed in `[fromMillis, toMillisExclusive)`. */
    suspend fun exerciseIdsLoggedBetween(fromMillis: Long, toMillisExclusive: Long): Set<String> =
        all()
            .filter {
                !it.isWarmup &&
                    it.performedAtMillis >= fromMillis &&
                    it.performedAtMillis < toMillisExclusive
            }
            .mapTo(mutableSetOf()) { it.exerciseId }

    /**
     * Sets attributed to [occurrenceId] or naming one of [entryIds], oldest first. An entry-linked set
     * belongs to its slot even when its derived occurrence id is missing or disagrees.
     */
    suspend fun setsForOccurrenceOrEntries(
        occurrenceId: Long,
        entryIds: Collection<Long>
    ): List<WorkoutSet> = all()
        .filter { it.occurrenceId == occurrenceId || it.occurrenceEntryId in entryIds }
        .sortedWith(compareBy<WorkoutSet> { it.performedAtMillis }.thenBy { it.id })

    fun setsFlow(): Flow<List<WorkoutSet>>

    suspend fun loggedSets(): List<LoggedSet>

    fun loggedSetsFlow(): Flow<List<LoggedSet>>

    suspend fun clear()
}
