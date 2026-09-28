package com.hydrafit.app.core.domain.engine

interface WorkoutPlannerEngine {
    val id: PlannerEngineId

    suspend fun generatePlan(request: PlanRequest): WeeklyPlan
}
