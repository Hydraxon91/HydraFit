package com.hydrafit.app.core.domain.workout

import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Creates a session anchored at [startedAtMillis] and returns the persisted row.
 *
 * [endedAtMillis] is normally null (an open session); the backdated path passes the chosen instant
 * so a historical session is inserted already closed, without a transient open emission.
 */
@OptIn(ExperimentalUuidApi::class)
class StartWorkoutSessionUseCase(private val repository: WorkoutSessionRepository) {
    suspend operator fun invoke(
        startedAtMillis: Long,
        localEpochDay: Long,
        endedAtMillis: Long? = null
    ): WorkoutSession {
        val session = WorkoutSession(
            id = Uuid.random().toString(),
            startedAtMillis = startedAtMillis,
            endedAtMillis = endedAtMillis,
            localEpochDay = localEpochDay
        )
        repository.create(session)
        return session
    }
}
