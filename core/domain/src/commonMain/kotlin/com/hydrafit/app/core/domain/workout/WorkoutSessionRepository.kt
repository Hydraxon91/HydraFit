package com.hydrafit.app.core.domain.workout

import kotlinx.coroutines.flow.Flow

interface WorkoutSessionRepository {
    suspend fun create(session: WorkoutSession)

    suspend fun end(id: String, endedAtMillis: Long)

    /** The most recently started session that has not been closed, or null when none is open. */
    suspend fun open(): WorkoutSession?

    fun openFlow(): Flow<WorkoutSession?>

    suspend fun all(): List<WorkoutSession>
}
