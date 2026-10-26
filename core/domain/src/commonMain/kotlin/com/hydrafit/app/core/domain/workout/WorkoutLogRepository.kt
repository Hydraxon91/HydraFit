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

    fun setsFlow(): Flow<List<WorkoutSet>>

    suspend fun loggedSets(): List<LoggedSet>

    fun loggedSetsFlow(): Flow<List<LoggedSet>>

    suspend fun clear()
}
