package com.hydrafit.app.core.database

import com.hydrafit.app.core.domain.equipment.LoadingInventory
import com.hydrafit.app.core.domain.equipment.LoadingInventoryJson
import com.hydrafit.app.core.domain.schedule.WorkoutReminderDeliveryGate
import com.hydrafit.app.core.domain.storage.DataCategory
import com.hydrafit.app.core.domain.storage.DataCountKind
import com.hydrafit.app.core.domain.storage.DataManagementRepository
import com.hydrafit.app.core.domain.storage.ExerciseDataClearException
import com.hydrafit.app.core.domain.storage.ExerciseDataClearFailure

/**
 * Counts and removes the scoped data categories over SQLDelight. Counts read inside one transaction;
 * a removal runs inside [WorkoutReminderDeliveryGate.mutate] and one transaction, so the reminder
 * posting path never acts on a half-cleared schedule and a failure leaves the current data intact.
 * History and full resets include cluster segments and cardio activities, and profile resets delete
 * their station rows; custom equipment is catalog-like and survives a setup reset but not a full
 * reset. Planning removal includes routine templates, their workouts and entries alongside the
 * plan history and schedule. Setup and full resets restore the weight and distance units to their
 * defaults. The seeded catalog, credentials, model files and automatic snapshots are never touched.
 */
class SqlDelightDataManagementRepository(
    private val database: HydraFitDatabase,
    private val gate: WorkoutReminderDeliveryGate
) : DataManagementRepository {

    override suspend fun counts(): Map<DataCountKind, Int> = database.transactionWithResult {
        val queries = database.storageCountsQueries
        mapOf(
            DataCountKind.SESSIONS to queries.countSessions().executeAsOne().toInt(),
            DataCountKind.SETS to queries.countSets().executeAsOne().toInt(),
            DataCountKind.SESSION_NOTES to queries.countSessionNotes().executeAsOne().toInt(),
            DataCountKind.ACCEPTED_PLANS to queries.countPlans().executeAsOne().toInt(),
            DataCountKind.ROUTINES to queries.countRoutines().executeAsOne().toInt(),
            DataCountKind.ACTIVATIONS to queries.countActivations().executeAsOne().toInt(),
            DataCountKind.SCHEDULED_WORKOUTS to queries.countOccurrences().executeAsOne().toInt(),
            DataCountKind.BODY_WEIGHT_MEASUREMENTS to
                queries.countBodyWeightMeasurements().executeAsOne().toInt(),
            DataCountKind.PERSONAL_RECORDS to queries.countPersonalRecords().executeAsOne().toInt(),
            DataCountKind.EQUIPMENT_PROFILES to
                queries.countEquipmentProfiles().executeAsOne().toInt(),
            DataCountKind.CUSTOM_EXERCISES to queries.countCustomExercises().executeAsOne().toInt(),
            DataCountKind.CARDIO_ACTIVITIES to
                queries.countCardioActivities().executeAsOne().toInt()
        )
    }

    override suspend fun clear(category: DataCategory) = gate.mutate {
        database.transaction { clearLocked(category) }
    }

    private fun clearLocked(category: DataCategory) {
        when (category) {
            DataCategory.WORKOUT_HISTORY -> clearWorkoutHistory()
            DataCategory.PLANNING -> clearPlanning()
            DataCategory.BODY_AND_RECORDS -> clearBodyAndRecords()
            DataCategory.TRAINING_SETUP -> clearTrainingSetup()
            DataCategory.EXERCISE_DATA -> clearExerciseData()
            DataCategory.ALL_DATA -> clearAllData()
            DataCategory.BACKUP_FILES ->
                throw IllegalArgumentException("$category is removed outside the database")
        }
    }

    private fun clearWorkoutHistory() {
        database.sessionNoteQueries.deleteAll()
        database.workoutClusterQueries.deleteAllSegments()
        database.workoutLogQueries.deleteAllSets()
        database.workoutSessionQueries.deleteAllSessions()
        database.cardioActivityQueries.deleteAllActivities()
    }

    private fun clearPlanning() {
        database.planVolumeExplanationStateQueries.deleteAll()
        database.planVolumeExplanationQueries.deleteAll()
        database.planTargetExplanationQueries.deleteAll()
        database.planTargetExplanationStateQueries.deleteAll()
        database.entryTargetExplanationQueries.deleteAll()
        database.planHistoryQueries.deleteAllEntries()
        database.planHistoryQueries.deleteAllDays()
        database.planHistoryQueries.deleteAllPlans()
        database.trainingScheduleQueries.deleteAllOccurrenceEntries()
        database.trainingScheduleQueries.deleteAllOccurrences()
        database.trainingScheduleQueries.deleteAllActivationEntries()
        database.trainingScheduleQueries.deleteAllActivationWorkouts()
        database.trainingScheduleQueries.deleteAllActivations()
        database.trainingScheduleQueries.deleteScheduleState()
        database.reminderDeliveryQueries.deleteAll()
        // Routine children go before their parents; nothing outside these tables references a
        // routine id once the activations above are gone.
        database.routineTemplateQueries.deleteAllEntries()
        database.routineTemplateQueries.deleteAllWorkouts()
        database.routineTemplateQueries.deleteAllTemplates()
    }

    private fun clearBodyAndRecords() {
        database.bodyWeightQueries.deleteAllMeasurements()
        database.bodyWeightQueries.clearGoal()
        database.personalRecordQueries.deleteAll()
    }

    private fun clearTrainingSetup() {
        resetPlannerPreferences(keepEngine = true)
        // Station rows reference their profile, so they go first. Custom equipment is catalog-like,
        // like a custom exercise, and survives this reset: frozen prescriptions and custom exercises
        // may still name a custom tag, so removing it here would make an exported backup fail
        // validation. Only a full reset removes it, after history and planning are gone.
        database.equipmentStationQueries.deleteAllStations()
        database.equipmentProfileQueries.deleteState()
        database.equipmentProfileQueries.deleteAllEntries()
        database.equipmentProfileQueries.deleteAllProfiles()
        database.userEquipmentQueries.deleteAllSelected()
        database.trainingGuidancePreferenceQueries.deleteAll()
        database.restPreferenceQueries.deleteAll()
        database.restCountdownQueries.deletePersisted()
        database.workoutReminderPreferenceQueries.deleteAll()
        SeedEquipmentCatalog(database).seed()
        seedDefaultEquipmentProfile()
    }

    private fun clearExerciseData() {
        val referenced = database.exerciseQueries.countReferencedCustomExercises().executeAsOne()
        if (referenced > 0) {
            throw ExerciseDataClearException(
                ExerciseDataClearFailure.CUSTOM_EXERCISES_REFERENCED
            )
        }
        database.exerciseOverrideQueries.deleteAll()
        database.exerciseNoteQueries.deleteAll()
        database.exercisePreferenceQueries.deleteAll()
        database.exerciseExclusionQueries.deleteAll()
        // Overrides are keyed by exercise id, so they must go before their custom definitions;
        // the global rest default has no exercise key and is left in place.
        database.restPreferenceQueries.deleteAllCustomExerciseOverrides()
        database.exerciseQueries.deleteAllCustom()
    }

    /**
     * Every user-owned row, mirroring a replace-restore's delete phase, then a fresh setup state.
     * Cluster segments, station rows and cardio activities are included, and custom equipment is
     * removed after the history/planning rows that may still reference it.
     */
    private fun clearAllData() {
        database.exerciseOverrideQueries.deleteAll()
        database.exercisePreferenceQueries.deleteAll()
        database.restPreferenceQueries.deleteAll()
        database.trainingGuidancePreferenceQueries.deleteAll()
        database.workoutReminderPreferenceQueries.deleteAll()
        database.reminderDeliveryQueries.deleteAll()
        database.restCountdownQueries.deletePersisted()
        database.bodyWeightQueries.deleteAllMeasurements()
        database.bodyWeightQueries.clearGoal()
        database.exerciseExclusionQueries.deleteAll()
        database.exerciseNoteQueries.deleteAll()
        database.sessionNoteQueries.deleteAll()
        database.warmupPreparationOverrideQueries.deleteAll()
        database.personalRecordQueries.deleteAll()
        database.workoutClusterQueries.deleteAllSegments()
        database.workoutLogQueries.deleteAllSets()
        database.workoutSessionQueries.deleteAllSessions()
        database.cardioActivityQueries.deleteAllActivities()
        database.workoutImportQueries.deleteAllProvenance()
        database.workoutImportBucketQueries.deleteBucketLinks()
        database.workoutImportBucketQueries.deleteBuckets()
        database.planVolumeExplanationStateQueries.deleteAll()
        database.planVolumeExplanationQueries.deleteAll()
        database.planTargetExplanationQueries.deleteAll()
        database.planTargetExplanationStateQueries.deleteAll()
        database.entryTargetExplanationQueries.deleteAll()
        database.planHistoryQueries.deleteAllEntries()
        database.planHistoryQueries.deleteAllDays()
        database.planHistoryQueries.deleteAllPlans()
        database.trainingScheduleQueries.deleteAllOccurrenceEntries()
        database.trainingScheduleQueries.deleteAllOccurrences()
        database.trainingScheduleQueries.deleteAllActivationEntries()
        database.trainingScheduleQueries.deleteAllActivationWorkouts()
        database.trainingScheduleQueries.deleteAllActivations()
        database.trainingScheduleQueries.deleteScheduleState()
        database.routineTemplateQueries.deleteAllEntries()
        database.routineTemplateQueries.deleteAllWorkouts()
        database.routineTemplateQueries.deleteAllTemplates()
        database.exerciseQueries.deleteAllCustom()
        database.equipmentStationQueries.deleteAllStations()
        database.equipmentProfileQueries.deleteState()
        database.equipmentProfileQueries.deleteAllEntries()
        database.equipmentProfileQueries.deleteAllProfiles()
        database.equipmentQueries.deleteCustomEquipment()
        database.userEquipmentQueries.deleteAllSelected()
        resetPlannerPreferences(keepEngine = false)
        database.setupQueries.updateState(1L, "PENDING", "WELCOME", 0L)
        database.setupQueries.updateWeekdays(null)
        // A reset never leaves a staged restore or its failure behind: applying it on the next start
        // would resurrect data the user just removed.
        database.backupStagingQueries.clearStagedBackup()
        database.backupStagingQueries.clearApplyError()
        SeedEquipmentCatalog(database).seed()
        seedDefaultEquipmentProfile()
    }

    /**
     * Re-creates the schema's seeded equipment profile and state row after a reset deleted them.
     * Every equipment/profile reader resolves the singleton row, so leaving the tables empty would
     * break the equipment surface, the logger calculator and the onboarding equipment step.
     */
    private fun seedDefaultEquipmentProfile() {
        database.equipmentProfileQueries.restoreProfile(
            id = 1L,
            name = "My equipment",
            loadingInventory = LoadingInventoryJson.encode(LoadingInventory())
        )
        database.equipmentProfileQueries.restoreState(1L)
    }

    /** Restores the planner and unit defaults; the engine choice is kept unless a full reset asks. */
    private fun resetPlannerPreferences(keepEngine: Boolean) {
        database.plannerEngineQueries.insertIgnoreRow("DETERMINISTIC")
        if (!keepEngine) {
            database.plannerEngineQueries.updateEngine("DETERMINISTIC")
        }
        database.plannerEngineQueries.updateDaysPerWeek(4L)
        database.plannerEngineQueries.updateTrainingGoal("BALANCED")
        database.plannerEngineQueries.updateShareWorkoutData(0L)
        database.plannerEngineQueries.updateWeightUnit("KG")
        database.plannerEngineQueries.updateDistanceUnit(
            SqlDelightDistanceUnitRepository.DEFAULT_UNIT.name
        )
        database.plannerEngineQueries.updateGuidedWorkoutEnabled(0L)
    }
}
