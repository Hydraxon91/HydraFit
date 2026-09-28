package com.hydrafit.app.core.domain.engine

class GenerateWeeklySplitUseCase(
    private val engine: WorkoutPlannerEngine
) {
    suspend operator fun invoke(request: PlanRequest): WeeklyPlan = engine.generatePlan(request)
}
