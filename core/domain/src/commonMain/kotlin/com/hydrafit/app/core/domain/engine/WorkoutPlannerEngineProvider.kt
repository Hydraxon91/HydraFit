package com.hydrafit.app.core.domain.engine

fun interface WorkoutPlannerEngineProvider {
    suspend fun get(): WorkoutPlannerEngine
}
