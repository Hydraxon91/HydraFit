package com.hydrafit.app.core.domain.workout

/** Removes a single logged set, so a mistyped entry can be corrected after the fact. */
class DeleteWorkoutSetUseCase(private val repository: WorkoutLogRepository) {
    suspend operator fun invoke(id: Long) = repository.delete(id)
}
