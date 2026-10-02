package com.hydrafit.app.core.domain.workout

import com.hydrafit.app.core.domain.fatigue.LoggedSet
import kotlinx.coroutines.flow.Flow

interface WorkoutLogRepository {
    suspend fun add(set: WorkoutSet)

    /** Attaches a set to a session; used when auto-start or the legacy backfill assigns an id. */
    suspend fun assignSession(setId: Long, sessionId: String)

    suspend fun delete(id: Long)

    suspend fun all(): List<WorkoutSet>

    fun setsFlow(): Flow<List<WorkoutSet>>

    suspend fun loggedSets(): List<LoggedSet>

    fun loggedSetsFlow(): Flow<List<LoggedSet>>

    suspend fun clear()
}
