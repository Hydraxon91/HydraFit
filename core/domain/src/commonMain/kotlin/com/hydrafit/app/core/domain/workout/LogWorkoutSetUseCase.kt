package com.hydrafit.app.core.domain.workout

class LogWorkoutSetUseCase(private val repository: WorkoutLogRepository) {
    suspend operator fun invoke(set: WorkoutSet) = repository.add(set)
}
