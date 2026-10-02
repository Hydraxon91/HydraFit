package com.hydrafit.app.core.domain.workout

import kotlinx.coroutines.flow.Flow

/** Streams the currently open session (or null) so the logger can show active-session state. */
class ObserveOpenWorkoutSessionUseCase(private val repository: WorkoutSessionRepository) {
    operator fun invoke(): Flow<WorkoutSession?> = repository.openFlow()

    /** One-shot read of the open session, for resolution before a set is written. */
    suspend fun current(): WorkoutSession? = repository.open()
}
