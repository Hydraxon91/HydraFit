package com.hydrafit.app.core.domain.workout

import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/** Opens a new session anchored at [startedAtMillis] and returns the persisted row. */
@OptIn(ExperimentalUuidApi::class)
class StartWorkoutSessionUseCase(private val repository: WorkoutSessionRepository) {
    suspend operator fun invoke(startedAtMillis: Long, localEpochDay: Long): WorkoutSession {
        val session = WorkoutSession(
            id = Uuid.random().toString(),
            startedAtMillis = startedAtMillis,
            localEpochDay = localEpochDay
        )
        repository.create(session)
        return session
    }
}
