package com.hydrafit.app.core.domain.workout

import kotlinx.coroutines.flow.Flow

interface WorkoutSessionRepository {
    suspend fun create(session: WorkoutSession)

    suspend fun end(id: String, endedAtMillis: Long)

    /** The most recently started session that has not been closed, or null when none is open. */
    suspend fun open(): WorkoutSession?

    fun openFlow(): Flow<WorkoutSession?>

    suspend fun all(): List<WorkoutSession>

    /**
     * The session with [id], or null when no such session exists. A bounded single-row read for
     * consumers resolving one recorded session identity; the database-backed implementation reads
     * by primary key. The default filters [all] so simple and test implementations stay small.
     */
    suspend fun byId(id: String): WorkoutSession? = all().firstOrNull { it.id == id }
}
