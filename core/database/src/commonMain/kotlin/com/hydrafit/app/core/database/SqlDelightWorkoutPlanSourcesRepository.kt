package com.hydrafit.app.core.database

import app.cash.sqldelight.coroutines.asFlow
import com.hydrafit.app.core.domain.engine.PeriodizationConfig
import com.hydrafit.app.core.domain.engine.PersonalRecord
import com.hydrafit.app.core.domain.engine.ProgressionHistory
import com.hydrafit.app.core.domain.engine.WorkoutPlanSources
import com.hydrafit.app.core.domain.engine.WorkoutPlanSourcesRepository
import com.hydrafit.app.core.domain.equipment.EquipmentTag
import com.hydrafit.app.core.domain.equipment.LoadingInventory
import com.hydrafit.app.core.domain.fatigue.LoggedSet
import com.hydrafit.app.core.domain.workout.WorkoutLogRepository
import com.hydrafit.app.core.domain.workout.WorkoutPrescriptionSnapshot
import com.hydrafit.app.core.domain.workout.WorkoutSet
import com.hydrafit.app.core.userdata.equipment.EquipmentProfileRepository
import com.hydrafit.app.core.userdata.equipment.ExerciseExclusionRepository
import com.hydrafit.app.core.userdata.equipment.ExercisePreferenceRepository
import com.hydrafit.app.core.userdata.equipment.PersonalRecordRepository
import com.hydrafit.app.core.userdata.settings.EnginePreferenceRepository
import com.hydrafit.app.core.userdata.settings.TrainingGoalRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class SqlDelightWorkoutPlanSourcesRepository(
    private val equipmentProfileRepository: EquipmentProfileRepository,
    private val enginePreferenceRepository: EnginePreferenceRepository,
    private val workoutLogRepository: WorkoutLogRepository,
    private val trainingGoalRepository: TrainingGoalRepository,
    private val personalRecordRepository: PersonalRecordRepository,
    private val exercisePreferenceRepository: ExercisePreferenceRepository,
    private val exerciseExclusionRepository: ExerciseExclusionRepository,
    private val database: HydraFitDatabase
) : WorkoutPlanSourcesRepository {

    override fun observe(): Flow<WorkoutPlanSources> {
        val loggedSets = workoutLogRepository.loggedSetsFlow()
        val history = database.progressionHistoryQueries.selectProgressionInvalidation().asFlow()
            .map { withContext(Dispatchers.Default) { readProgressionHistory() } }
        val sharing = enginePreferenceRepository.workoutDataSharingFlow()
        val logContext = combine(
            loggedSets,
            history,
            sharing,
            equipmentProfileRepository.observe(),
            personalRecordRepository.observe()
        ) { sets, progression, enabled, inventory, records ->
            LogContext(
                loggedSets = sets,
                loggedWorkoutSets = progression.sets,
                progressionHistory = progression,
                sharingEnabled = enabled,
                equipmentMaxWeights = inventory.active.maxWeightsKg,
                loadingInventory = inventory.active.loadingInventory,
                selectedEquipment = inventory.active.selected,
                equipmentContext = "${inventory.context.profileId}:${inventory.context.revision}",
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
            enginePreferenceRepository.engineFlow(),
            enginePreferenceRepository.daysPerWeekFlow(),
            trainingGoalRepository.goalFlow(),
            contextWithSettings
        ) { engine, daysPerWeek, goal, (context, preferences, exclusions) ->
            WorkoutPlanSources(
                availableEquipment = context.selectedEquipment,
                selectedEngine = engine,
                daysPerWeek = daysPerWeek,
                loggedSets = context.loggedSets,
                goal = goal,
                loggedWorkoutSets = context.loggedWorkoutSets,
                workoutDataSharingEnabled = context.sharingEnabled,
                equipmentMaxWeights = context.equipmentMaxWeights,
                loadingInventory = context.loadingInventory,
                personalRecords = context.personalRecords,
                exercisePreferences = preferences,
                exerciseExclusions = exclusions,
                equipmentContext = context.equipmentContext,
                progressionHistory = context.progressionHistory
            )
        }.distinctUntilChanged()
    }

    private fun readProgressionHistory(): ProgressionHistory = database.transactionWithResult {
        // Station-attributed work is excluded from the generic progression replay. Its own station
        // history is a separate comparison population and must not seed this exercise-level rule.
        val sets = database.workoutLogQueries.selectAllSets().executeAsList()
            .map { it.toDomain() }
            .filter { it.station == null }
        val sessions = database.workoutSessionQueries.selectAllSessions().executeAsList()
            .map { it.toDomain() }
        val entries = database.trainingScheduleQueries.selectAllOccurrenceEntries().executeAsList()
            .associateBy { it.id }
        val occurrences = database.trainingScheduleQueries.selectAllOccurrences().executeAsList()
            .associateBy { it.id }
        val activations = database.trainingScheduleQueries.selectAllActivations().executeAsList()
            .associateBy { it.id }
        val sessionsById = sessions.associateBy { it.id }
        val targets = sets.mapNotNull { set ->
            if (set.prescriptionSnapshot != null) return@mapNotNull null
            val entry = set.occurrenceEntryId?.let(entries::get) ?: return@mapNotNull null
            if (set.occurrenceId != entry.occurrenceId || set.exerciseId != entry.exerciseId) {
                return@mapNotNull null
            }
            val session = set.sessionId?.let(sessionsById::get) ?: return@mapNotNull null
            if (session.occurrenceId != null && session.occurrenceId != entry.occurrenceId) {
                return@mapNotNull null
            }
            val occurrence = occurrences[entry.occurrenceId] ?: return@mapNotNull null
            val activation = activations[occurrence.activationId] ?: return@mapNotNull null
            val week = activation.weekNumber?.toInt() ?: return@mapNotNull null
            val target = WorkoutPrescriptionSnapshot(
                exerciseId = entry.exerciseId,
                sets = entry.sets.toInt(),
                reps = entry.reps?.toInt(),
                durationSeconds = entry.durationSeconds?.toInt(),
                distanceMeters = entry.distanceMeters?.toInt(),
                loadKind = decodeLoadKind(entry.loadKind),
                weightKg = entry.weightKg,
                isDeload = PeriodizationConfig().isDeload(week)
            )
            target.requireValid()
            set.id to target
        }.toMap()
        ProgressionHistory(sets, sessions, targets)
    }

    private data class LogContext(
        val loggedSets: List<LoggedSet>,
        val loggedWorkoutSets: List<WorkoutSet>,
        val progressionHistory: ProgressionHistory,
        val sharingEnabled: Boolean,
        val equipmentMaxWeights: Map<EquipmentTag, Double>,
        val loadingInventory: LoadingInventory,
        val selectedEquipment: Set<EquipmentTag>,
        val equipmentContext: String,
        val personalRecords: List<PersonalRecord>
    )
}
