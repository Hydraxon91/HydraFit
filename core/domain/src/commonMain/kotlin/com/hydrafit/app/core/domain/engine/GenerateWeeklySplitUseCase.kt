package com.hydrafit.app.core.domain.engine

class GenerateWeeklySplitUseCase(private val engines: WorkoutPlannerEngineProvider) {
    suspend operator fun invoke(request: PlanRequest): WeeklyPlan =
        engines.get().generatePlan(request)
}
