package com.hydrafit.app.core.domain.workout

class GetWorkoutLogUseCase(private val repository: WorkoutLogRepository) {
    suspend operator fun invoke(): List<WorkoutSet> = repository.all()
}
