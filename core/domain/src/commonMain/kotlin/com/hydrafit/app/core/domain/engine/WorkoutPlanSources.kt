package com.hydrafit.app.core.domain.engine

import com.hydrafit.app.core.domain.equipment.EquipmentTag
import com.hydrafit.app.core.domain.fatigue.LoggedSet
import kotlinx.coroutines.flow.Flow

data class WorkoutPlanSources(
    val availableEquipment: Set<EquipmentTag>,
    val selectedEngine: PlannerEngineId,
    val daysPerWeek: Int,
    val loggedSets: List<LoggedSet>,
    val goal: TrainingGoal = TrainingGoal.BALANCED
)

interface WorkoutPlanSourcesRepository {
    fun observe(): Flow<WorkoutPlanSources>
}

data class WorkoutPlanInputs(val request: PlanRequest, val requestedEngine: PlannerEngineId)
