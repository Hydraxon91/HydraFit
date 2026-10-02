package com.hydrafit.app.core.domain.workout

/** Closes an open session, stamping its end time. */
class EndWorkoutSessionUseCase(private val repository: WorkoutSessionRepository) {
    suspend operator fun invoke(sessionId: String, endedAtMillis: Long) =
        repository.end(sessionId, endedAtMillis)
}
