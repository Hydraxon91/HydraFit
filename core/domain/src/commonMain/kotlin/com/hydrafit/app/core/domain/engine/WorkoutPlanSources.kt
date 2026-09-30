package com.hydrafit.app.core.domain.engine

import com.hydrafit.app.core.domain.equipment.EquipmentTag
import com.hydrafit.app.core.domain.fatigue.LoggedSet
import com.hydrafit.app.core.domain.workout.WorkoutSet
import kotlinx.coroutines.flow.Flow

data class WorkoutPlanSources(
    val availableEquipment: Set<EquipmentTag>,
    val selectedEngine: PlannerEngineId,
    val daysPerWeek: Int,
    val loggedSets: List<LoggedSet>,
    val goal: TrainingGoal = TrainingGoal.BALANCED,
    val loggedWorkoutSets: List<WorkoutSet> = emptyList(),
    val workoutDataSharingEnabled: Boolean = false,
    val equipmentMaxWeights: Map<EquipmentTag, Double> = emptyMap(),
    val personalRecords: List<PersonalRecord> = emptyList()
)

interface WorkoutPlanSourcesRepository {
    fun observe(): Flow<WorkoutPlanSources>
}

data class WorkoutPlanInputs(val request: PlanRequest, val requestedEngine: PlannerEngineId)
