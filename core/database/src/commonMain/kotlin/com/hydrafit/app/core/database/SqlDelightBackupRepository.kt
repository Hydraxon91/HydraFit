package com.hydrafit.app.core.database

import com.hydrafit.app.core.domain.backup.BackupActivationEntryRecord
import com.hydrafit.app.core.domain.backup.BackupActivationRecord
import com.hydrafit.app.core.domain.backup.BackupActivationWorkoutRecord
import com.hydrafit.app.core.domain.backup.BackupCatalogManifest
import com.hydrafit.app.core.domain.backup.BackupEquipmentRecord
import com.hydrafit.app.core.domain.backup.BackupExclusionRecord
import com.hydrafit.app.core.domain.backup.BackupExerciseOverrideRecord
import com.hydrafit.app.core.domain.backup.BackupExerciseRecord
import com.hydrafit.app.core.domain.backup.BackupFile
import com.hydrafit.app.core.domain.backup.BackupOccurrenceEntryRecord
import com.hydrafit.app.core.domain.backup.BackupOccurrenceRecord
import com.hydrafit.app.core.domain.backup.BackupPersonalRecordRecord
import com.hydrafit.app.core.domain.backup.BackupPlanDayRecord
import com.hydrafit.app.core.domain.backup.BackupPlanEntryRecord
import com.hydrafit.app.core.domain.backup.BackupPlanRecord
import com.hydrafit.app.core.domain.backup.BackupPreferenceRecord
import com.hydrafit.app.core.domain.backup.BackupRepository
import com.hydrafit.app.core.domain.backup.BackupRoutineEntryRecord
import com.hydrafit.app.core.domain.backup.BackupRoutineRecord
import com.hydrafit.app.core.domain.backup.BackupRoutineWorkoutRecord
import com.hydrafit.app.core.domain.backup.BackupScheduleStateRecord
import com.hydrafit.app.core.domain.backup.BackupSettingsRecord
import com.hydrafit.app.core.domain.backup.BackupVolumeExplanationRecord
import com.hydrafit.app.core.domain.backup.BackupVolumeExplanationStateRecord
import com.hydrafit.app.core.domain.backup.BackupWorkoutSessionRecord
import com.hydrafit.app.core.domain.backup.BackupWorkoutSetRecord

/**
 * Reads the logical backup snapshot from SQLDelight. Every section is read inside one transaction so
 * concurrent writes cannot produce a torn export; the payload is returned as a value, and the caller
 * serializes and writes the file after the transaction has been released.
 */
class SqlDelightBackupRepository(private val database: HydraFitDatabase) : BackupRepository {

    override suspend fun export(appVersion: String, exportedAtMillis: Long): BackupFile =
        database.transactionWithResult {
            BackupFile(
                appVersion = appVersion,
                exportedAtMillis = exportedAtMillis,
                catalog = BackupCatalogManifest(
                    seedExerciseIds = DefaultExercises.all.map { it.id }.sorted()
                ),
                settings = readSettings(),
                customExercises = database.exerciseQueries.selectAll().executeAsList()
                    .filter { it.isCustom == 1L }
                    .map {
                        BackupExerciseRecord(
                            id = it.id,
                            name = it.name,
                            requiredEquipment = it.requiredEquipment,
                            movementPattern = it.movementPattern,
                            isUnilateral = it.isUnilateral != 0L,
                            loadCapability = it.loadCapability,
                            involvements = it.involvements
                        )
                    },
                exerciseOverrides = database.exerciseOverrideQueries.selectAll().executeAsList()
                    .map {
                        BackupExerciseOverrideRecord(
                            exerciseId = it.exerciseId,
                            name = it.name,
                            requiredEquipment = it.requiredEquipment,
                            movementPattern = it.movementPattern,
                            isUnilateral = it.isUnilateral?.let { v -> v != 0L },
                            loadCapability = it.loadCapability,
                            involvements = it.involvements
                        )
                    },
                equipment = database.equipmentQueries.selectAll().executeAsList().map {
                    BackupEquipmentRecord(
                        id = it.id,
                        name = it.name,
                        isBuiltIn = it.isBuiltIn != 0L,
                        maxWeightKg = it.maxWeightKg
                    )
                },
                selectedEquipment = database.userEquipmentQueries.selectAllSelected()
                    .executeAsList(),
                workoutSets = database.workoutLogQueries.selectAllSets().executeAsList().map {
                    BackupWorkoutSetRecord(
                        id = it.id,
                        exerciseId = it.exerciseId,
                        reps = it.reps.toInt(),
                        weightKg = it.weightKg,
                        performedAtMillis = it.performedAt,
                        isWarmup = it.isWarmup != 0L,
                        involvements = it.involvements,
                        weekNumber = it.weekNumber?.toInt(),
                        cycleNumber = it.cycleNumber?.toInt(),
                        dayIndex = it.dayIndex?.toInt(),
                        rir = it.rir?.toInt(),
                        sessionId = it.sessionId,
                        occurrenceId = it.occurrenceId,
                        occurrenceEntryId = it.occurrenceEntryId,
                        loadKind = it.loadKind
                    )
                },
                workoutSessions = database.workoutSessionQueries.selectAllSessions()
                    .executeAsList().map {
                        BackupWorkoutSessionRecord(
                            id = it.id,
                            startedAtMillis = it.startedAtMillis,
                            endedAtMillis = it.endedAtMillis,
                            localEpochDay = it.localEpochDay,
                            occurrenceId = it.occurrenceId
                        )
                    },
                plans = database.planHistoryQueries.selectAllPlans().executeAsList().map {
                    BackupPlanRecord(
                        id = it.id,
                        engineId = it.engineId,
                        acceptedAt = it.acceptedAt,
                        weekNumber = it.weekNumber.toInt(),
                        cycleNumber = it.cycleNumber.toInt()
                    )
                },
                planDays = database.planHistoryQueries.selectAllDays().executeAsList().map {
                    BackupPlanDayRecord(
                        id = it.id,
                        planId = it.planId,
                        dayIndex = it.dayIndex.toInt(),
                        focus = it.focus
                    )
                },
                planEntries = database.planHistoryQueries.selectAllEntries().executeAsList().map {
                    BackupPlanEntryRecord(
                        id = it.id,
                        dayId = it.dayId,
                        position = it.position.toInt(),
                        exerciseId = it.exerciseId,
                        sets = it.sets.toInt(),
                        reps = it.reps.toInt(),
                        exerciseName = it.exerciseName,
                        movementPattern = it.movementPattern,
                        suggestedWeightKg = it.suggestedWeightKg,
                        loadCapability = it.loadCapability,
                        loadKind = it.loadKind
                    )
                },
                volumeExplanations = database.planVolumeExplanationQueries.selectAll()
                    .executeAsList().map {
                        BackupVolumeExplanationRecord(
                            planId = it.planId,
                            muscle = it.muscle,
                            targetSets = it.targetSets.toInt(),
                            isTargetEnforced = it.isTargetEnforced != 0L,
                            directIsolationSets = it.directIsolationSets.toInt(),
                            estimatedOtherInvolvementCredits = it.estimatedOtherInvolvementCredits,
                            unmetReason = it.unmetReason,
                            attribution = it.attribution
                        )
                    },
                volumeExplanationStates = database.planVolumeExplanationStateQueries.selectAll()
                    .executeAsList().map {
                        BackupVolumeExplanationStateRecord(
                            planId = it.planId,
                            status = it.status,
                            assessmentVersion = it.assessmentVersion.toInt()
                        )
                    },
                routines = database.routineTemplateQueries.selectAllTemplates()
                    .executeAsList().map {
                        BackupRoutineRecord(
                            id = it.id,
                            name = it.name,
                            revision = it.revision.toInt(),
                            createdAtMillis = it.createdAtMillis,
                            updatedAtMillis = it.updatedAtMillis,
                            archivedAtMillis = it.archivedAtMillis,
                            sourcePlanId = it.sourcePlanId
                        )
                    },
                routineWorkouts = database.routineTemplateQueries.selectAllWorkouts()
                    .executeAsList().map {
                        BackupRoutineWorkoutRecord(
                            id = it.id,
                            templateId = it.templateId,
                            position = it.position.toInt(),
                            name = it.name,
                            focus = it.focus
                        )
                    },
                routineEntries = database.routineTemplateQueries.selectAllEntries()
                    .executeAsList().map {
                        BackupRoutineEntryRecord(
                            id = it.id,
                            workoutId = it.workoutId,
                            position = it.position.toInt(),
                            exerciseId = it.exerciseId,
                            sets = it.sets.toInt(),
                            reps = it.reps.toInt(),
                            weightKg = it.weightKg,
                            loadKind = it.loadKind
                        )
                    },
                activations = database.trainingScheduleQueries.selectAllActivations()
                    .executeAsList().map {
                        BackupActivationRecord(
                            id = it.id,
                            templateId = it.templateId,
                            templateRevision = it.templateRevision?.toInt(),
                            sourcePlanId = it.sourcePlanId,
                            name = it.name,
                            createdAtMillis = it.createdAtMillis,
                            startEpochDay = it.startEpochDay,
                            mode = it.mode,
                            weekdayMask = it.weekdayMask,
                            status = it.status,
                            weekNumber = it.weekNumber?.toInt(),
                            cycleNumber = it.cycleNumber?.toInt(),
                            endedAtMillis = it.endedAtMillis,
                            revision = it.revision.toInt()
                        )
                    },
                activationWorkouts = database.trainingScheduleQueries
                    .selectAllActivationWorkouts().executeAsList().map {
                        BackupActivationWorkoutRecord(
                            id = it.id,
                            activationId = it.activationId,
                            sourceWorkoutId = it.sourceWorkoutId,
                            position = it.position.toInt(),
                            name = it.name,
                            focus = it.focus
                        )
                    },
                activationEntries = database.trainingScheduleQueries
                    .selectAllActivationEntries().executeAsList().map {
                        BackupActivationEntryRecord(
                            id = it.id,
                            workoutId = it.workoutId,
                            position = it.position.toInt(),
                            exerciseId = it.exerciseId,
                            exerciseName = it.exerciseName,
                            movementPattern = it.movementPattern,
                            requiredEquipment = it.requiredEquipment,
                            involvements = it.involvements,
                            isUnilateral = it.isUnilateral != 0L,
                            sets = it.sets.toInt(),
                            reps = it.reps.toInt(),
                            weightKg = it.weightKg,
                            loadCapability = it.loadCapability,
                            loadKind = it.loadKind
                        )
                    },
                occurrences = database.trainingScheduleQueries.selectAllOccurrences()
                    .executeAsList().map {
                        BackupOccurrenceRecord(
                            id = it.id,
                            activationId = it.activationId,
                            activationWorkoutId = it.activationWorkoutId,
                            queuePosition = it.queuePosition.toInt(),
                            scheduledEpochDay = it.scheduledEpochDay,
                            notBeforeEpochDay = it.notBeforeEpochDay,
                            startedAtMillis = it.startedAtMillis,
                            resolvedAtMillis = it.resolvedAtMillis,
                            status = it.status,
                            revision = it.revision.toInt()
                        )
                    },
                occurrenceEntries = database.trainingScheduleQueries.selectAllOccurrenceEntries()
                    .executeAsList().map {
                        BackupOccurrenceEntryRecord(
                            id = it.id,
                            occurrenceId = it.occurrenceId,
                            sourceActivationEntryId = it.sourceActivationEntryId,
                            position = it.position.toInt(),
                            exerciseId = it.exerciseId,
                            exerciseName = it.exerciseName,
                            movementPattern = it.movementPattern,
                            requiredEquipment = it.requiredEquipment,
                            involvements = it.involvements,
                            isUnilateral = it.isUnilateral != 0L,
                            sets = it.sets.toInt(),
                            reps = it.reps.toInt(),
                            weightKg = it.weightKg,
                            loadCapability = it.loadCapability,
                            loadKind = it.loadKind,
                            remainingDisposition = it.remainingDisposition,
                            terminalRemainingSets = it.terminalRemainingSets?.toInt()
                        )
                    },
                scheduleState = database.trainingScheduleQueries.selectScheduleState()
                    .executeAsOneOrNull()?.let {
                        BackupScheduleStateRecord(
                            activeActivationId = it.activeActivationId,
                            selectedOccurrenceId = it.selectedOccurrenceId,
                            legacyFallbackEnabled = it.legacyFallbackEnabled != 0L
                        )
                    },
                personalRecords = database.personalRecordQueries.selectAll().executeAsList().map {
                    BackupPersonalRecordRecord(
                        exerciseId = it.exerciseId,
                        weightKg = it.weightKg,
                        reps = it.reps.toInt(),
                        updatedAt = it.updatedAt,
                        loadKind = it.loadKind
                    )
                },
                preferences = database.exercisePreferenceQueries.selectAll().executeAsList().map {
                    BackupPreferenceRecord(exerciseId = it.exerciseId, preference = it.preference)
                },
                exclusions = database.exerciseExclusionQueries.selectAll().executeAsList().map {
                    BackupExclusionRecord(exerciseId = it.exerciseId, expiresAt = it.expiresAt)
                }
            )
        }

    /** Reads the single planner-settings row; null when the user has never changed any setting. */
    private fun readSettings(): BackupSettingsRecord? {
        val queries = database.plannerEngineQueries
        val engineId = queries.selectEngine().executeAsOneOrNull() ?: return null
        return BackupSettingsRecord(
            engineId = engineId,
            daysPerWeek = (queries.selectDaysPerWeek().executeAsOneOrNull() ?: 4L).toInt(),
            trainingGoal = queries.selectTrainingGoal().executeAsOneOrNull() ?: "BALANCED",
            shareWorkoutData = (queries.selectShareWorkoutData().executeAsOneOrNull() ?: 0L) != 0L,
            weightUnit = queries.selectWeightUnit().executeAsOneOrNull() ?: "KG"
        )
    }
}
