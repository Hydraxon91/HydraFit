package com.hydrafit.app.core.database

import com.hydrafit.app.core.domain.engine.PersonalRecord
import com.hydrafit.app.core.domain.engine.WorkoutPlanSources
import com.hydrafit.app.core.domain.engine.WorkoutPlanSourcesRepository
import com.hydrafit.app.core.domain.equipment.EquipmentTag
import com.hydrafit.app.core.domain.fatigue.LoggedSet
import com.hydrafit.app.core.domain.workout.WorkoutLogRepository
import com.hydrafit.app.core.domain.workout.WorkoutSet
import com.hydrafit.app.core.userdata.equipment.EquipmentRepository
import com.hydrafit.app.core.userdata.equipment.EquipmentSelectionRepository
import com.hydrafit.app.core.userdata.equipment.ExerciseExclusionRepository
import com.hydrafit.app.core.userdata.equipment.ExercisePreferenceRepository
import com.hydrafit.app.core.userdata.equipment.PersonalRecordRepository
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
    private val equipmentRepository: EquipmentRepository,
    private val personalRecordRepository: PersonalRecordRepository,
    private val exercisePreferenceRepository: ExercisePreferenceRepository,
    private val exerciseExclusionRepository: ExerciseExclusionRepository
) : WorkoutPlanSourcesRepository {

    override fun observe(): Flow<WorkoutPlanSources> {
        val loggedSets = workoutLogRepository.loggedSetsFlow()
        val loggedWorkoutSets = workoutLogRepository.setsFlow()
        val sharing = enginePreferenceRepository.workoutDataSharingFlow()
        val logContext = combine(
            loggedSets,
            loggedWorkoutSets,
            sharing,
            equipmentRepository.observeAll(),
            personalRecordRepository.observe()
        ) { sets, workoutSets, enabled, inventory, records ->
            LogContext(
                loggedSets = sets,
                loggedWorkoutSets = workoutSets,
                sharingEnabled = enabled,
                equipmentMaxWeights = inventory.mapNotNull { item ->
                    item.maxWeightKg?.let { item.id to it }
                }.toMap(),
                personalRecords = records
            )
        }
        // Fold the preference and exclusion flows into the log context so the outer combine keeps its
        // five-flow shape rather than dropping to an untyped vararg combine.
        val contextWithSettings = combine(
            logContext,
            exercisePreferenceRepository.observe(),
            exerciseExclusionRepository.observe()
        ) { context, preferences, exclusions -> Triple(context, preferences, exclusions) }
        return combine(
            equipmentSelectionRepository.selectedFlow(),
            enginePreferenceRepository.engineFlow(),
            enginePreferenceRepository.daysPerWeekFlow(),
            trainingGoalRepository.goalFlow(),
            contextWithSettings
        ) { equipment, engine, daysPerWeek, goal, (context, preferences, exclusions) ->
            WorkoutPlanSources(
                availableEquipment = equipment,
                selectedEngine = engine,
                daysPerWeek = daysPerWeek,
                loggedSets = context.loggedSets,
                goal = goal,
                loggedWorkoutSets = context.loggedWorkoutSets,
                workoutDataSharingEnabled = context.sharingEnabled,
                equipmentMaxWeights = context.equipmentMaxWeights,
                personalRecords = context.personalRecords,
                exercisePreferences = preferences,
                exerciseExclusions = exclusions
            )
        }.distinctUntilChanged()
    }

    private data class LogContext(
        val loggedSets: List<LoggedSet>,
        val loggedWorkoutSets: List<WorkoutSet>,
        val sharingEnabled: Boolean,
        val equipmentMaxWeights: Map<EquipmentTag, Double>,
        val personalRecords: List<PersonalRecord>
    )
}
