package com.hydrafit.app.core.domain.engine

data class WeeklyPlan(val engine: PlannerEngineId, val days: List<WorkoutDay>)
