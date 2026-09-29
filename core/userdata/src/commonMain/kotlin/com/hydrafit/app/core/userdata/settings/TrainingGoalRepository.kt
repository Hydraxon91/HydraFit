package com.hydrafit.app.core.userdata.settings

import com.hydrafit.app.core.domain.engine.TrainingGoal
import kotlinx.coroutines.flow.Flow

interface TrainingGoalRepository {
    suspend fun selectedGoal(): TrainingGoal

    fun goalFlow(): Flow<TrainingGoal>

    suspend fun setGoal(goal: TrainingGoal)
}
