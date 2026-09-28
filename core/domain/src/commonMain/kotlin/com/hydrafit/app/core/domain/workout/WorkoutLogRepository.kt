package com.hydrafit.app.core.domain.workout

import com.hydrafit.app.core.domain.fatigue.LoggedSet

interface WorkoutLogRepository {
    suspend fun add(set: WorkoutSet)

    suspend fun all(): List<WorkoutSet>

    suspend fun loggedSets(): List<LoggedSet>

    suspend fun clear()
}
