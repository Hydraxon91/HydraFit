package com.hydrafit.app.core.database

import com.hydrafit.app.core.domain.engine.WorkoutPlanSources
import com.hydrafit.app.core.domain.engine.WorkoutPlanSourcesRepository
import com.hydrafit.app.core.domain.equipment.EquipmentTag
import com.hydrafit.app.core.domain.fatigue.LoggedSet
import com.hydrafit.app.core.domain.workout.WorkoutLogRepository
import com.hydrafit.app.core.domain.workout.WorkoutSet
import com.hydrafit.app.core.userdata.equipment.EquipmentRepository
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
    private val trainingGoalRepository: TrainingGoalRepository,
    private val equipmentRepository: EquipmentRepository
) : WorkoutPlanSourcesRepository {

    override fun observe(): Flow<WorkoutPlanSources> {
        val loggedSets = workoutLogRepository.loggedSetsFlow()
        val loggedWorkoutSets = workoutLogRepository.setsFlow()
        val sharing = enginePreferenceRepository.workoutDataSharingFlow()
        val logContext = combine(
            loggedSets,
            loggedWorkoutSets,
            sharing,
            equipmentRepository.observeAll()
        ) { sets, workoutSets, enabled, inventory ->
            LogContext(
                loggedSets = sets,
                loggedWorkoutSets = workoutSets,
                sharingEnabled = enabled,
                equipmentMaxWeights = inventory.mapNotNull { item ->
                    item.maxWeightKg?.let { item.id to it }
                }.toMap()
            )
        }
        return combine(
            equipmentSelectionRepository.selectedFlow(),
            enginePreferenceRepository.engineFlow(),
            enginePreferenceRepository.daysPerWeekFlow(),
            trainingGoalRepository.goalFlow(),
            logContext
        ) { equipment, engine, daysPerWeek, goal, context ->
            WorkoutPlanSources(
                availableEquipment = equipment,
                selectedEngine = engine,
                daysPerWeek = daysPerWeek,
                loggedSets = context.loggedSets,
                goal = goal,
                loggedWorkoutSets = context.loggedWorkoutSets,
                workoutDataSharingEnabled = context.sharingEnabled,
                equipmentMaxWeights = context.equipmentMaxWeights
            )
        }.distinctUntilChanged()
    }

    private data class LogContext(
        val loggedSets: List<LoggedSet>,
        val loggedWorkoutSets: List<WorkoutSet>,
        val sharingEnabled: Boolean,
        val equipmentMaxWeights: Map<EquipmentTag, Double>
    )
}
