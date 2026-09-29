package com.hydrafit.app.core.database

import com.hydrafit.app.core.domain.engine.WorkoutPlanSources
import com.hydrafit.app.core.domain.engine.WorkoutPlanSourcesRepository
import com.hydrafit.app.core.domain.workout.WorkoutLogRepository
import com.hydrafit.app.core.userdata.equipment.EquipmentSelectionRepository
import com.hydrafit.app.core.userdata.settings.EnginePreferenceRepository
import com.hydrafit.app.core.userdata.settings.TrainingGoalRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged

class SqlDelightWorkoutPlanSourcesRepository(
    private val equipmentSelectionRepository: EquipmentSelectionRepository,
    private val enginePreferenceRepository: EnginePreferenceRepository,
    private val workoutLogRepository: WorkoutLogRepository,
    private val trainingGoalRepository: TrainingGoalRepository
) : WorkoutPlanSourcesRepository {

    override fun observe(): Flow<WorkoutPlanSources> {
        val loggedSets = workoutLogRepository.loggedSetsFlow()
        val loggedWorkoutSets = workoutLogRepository.setsFlow()
        val sharing = enginePreferenceRepository.workoutDataSharingFlow()
        return combine(
            equipmentSelectionRepository.selectedFlow(),
            enginePreferenceRepository.engineFlow(),
            enginePreferenceRepository.daysPerWeekFlow(),
            trainingGoalRepository.goalFlow(),
            combine(loggedSets, loggedWorkoutSets, sharing) { sets, workoutSets, enabled ->
                Triple(sets, workoutSets, enabled)
            }
        ) { equipment, engine, daysPerWeek, goal, logged ->
            WorkoutPlanSources(
                availableEquipment = equipment,
                selectedEngine = engine,
                daysPerWeek = daysPerWeek,
                loggedSets = logged.first,
                goal = goal,
                loggedWorkoutSets = logged.second,
                workoutDataSharingEnabled = logged.third
            )
        }.distinctUntilChanged()
    }
}
