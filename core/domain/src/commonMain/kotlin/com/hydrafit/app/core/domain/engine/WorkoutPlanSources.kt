package com.hydrafit.app.core.domain.engine

import com.hydrafit.app.core.domain.equipment.EquipmentTag
import com.hydrafit.app.core.domain.equipment.ExerciseExclusion
import com.hydrafit.app.core.domain.equipment.ExercisePreference
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
    val personalRecords: List<PersonalRecord> = emptyList(),
    /** Explicit user preference per exercise id; absence means [ExercisePreference.NEUTRAL]. */
    val exercisePreferences: Map<String, ExercisePreference> = emptyMap(),
    /** EX-01 exclusions with their expiry; the observer keeps only the currently active ones. */
    val exerciseExclusions: List<ExerciseExclusion> = emptyList()
)

interface WorkoutPlanSourcesRepository {
    fun observe(): Flow<WorkoutPlanSources>
}

data class WorkoutPlanInputs(val request: PlanRequest, val requestedEngine: PlannerEngineId)
