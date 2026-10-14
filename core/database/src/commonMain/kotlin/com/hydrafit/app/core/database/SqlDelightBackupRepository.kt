package com.hydrafit.app.core.database

import com.hydrafit.app.core.domain.backup.BACKUP_FORMAT
import com.hydrafit.app.core.domain.backup.BACKUP_FORMAT_VERSION
import com.hydrafit.app.core.domain.backup.BackupActivationEntryRecord
import com.hydrafit.app.core.domain.backup.BackupActivationRecord
import com.hydrafit.app.core.domain.backup.BackupActivationWorkoutRecord
import com.hydrafit.app.core.domain.backup.BackupCatalog
import com.hydrafit.app.core.domain.backup.BackupCatalogManifest
import com.hydrafit.app.core.domain.backup.BackupEntryTargetExplanationRecord
import com.hydrafit.app.core.domain.backup.BackupEquipmentRecord
import com.hydrafit.app.core.domain.backup.BackupExclusionRecord
import com.hydrafit.app.core.domain.backup.BackupExerciseNoteRecord
import com.hydrafit.app.core.domain.backup.BackupExerciseOverrideRecord
import com.hydrafit.app.core.domain.backup.BackupExerciseRecord
import com.hydrafit.app.core.domain.backup.BackupFile
import com.hydrafit.app.core.domain.backup.BackupOccurrenceEntryRecord
import com.hydrafit.app.core.domain.backup.BackupOccurrenceRecord
import com.hydrafit.app.core.domain.backup.BackupPersonalRecordRecord
import com.hydrafit.app.core.domain.backup.BackupPlanDayRecord
import com.hydrafit.app.core.domain.backup.BackupPlanEntryRecord
import com.hydrafit.app.core.domain.backup.BackupPlanRecord
import com.hydrafit.app.core.domain.backup.BackupPlanTargetExplanationRecord
import com.hydrafit.app.core.domain.backup.BackupPlanTargetExplanationStateRecord
import com.hydrafit.app.core.domain.backup.BackupPreferenceRecord
import com.hydrafit.app.core.domain.backup.BackupRepository
import com.hydrafit.app.core.domain.backup.BackupRestPreferenceRecord
import com.hydrafit.app.core.domain.backup.BackupRoutineEntryRecord
import com.hydrafit.app.core.domain.backup.BackupRoutineRecord
import com.hydrafit.app.core.domain.backup.BackupRoutineWorkoutRecord
import com.hydrafit.app.core.domain.backup.BackupScheduleStateRecord
import com.hydrafit.app.core.domain.backup.BackupSeedProfile
import com.hydrafit.app.core.domain.backup.BackupSessionNoteRecord
import com.hydrafit.app.core.domain.backup.BackupSettingsRecord
import com.hydrafit.app.core.domain.backup.BackupSetupRecord
import com.hydrafit.app.core.domain.backup.BackupVolumeExplanationRecord
import com.hydrafit.app.core.domain.backup.BackupVolumeExplanationStateRecord
import com.hydrafit.app.core.domain.backup.BackupWorkoutSessionRecord
import com.hydrafit.app.core.domain.backup.BackupWorkoutSetRecord
import com.hydrafit.app.core.domain.settings.DEFAULT_REST_SECONDS
import com.hydrafit.app.core.domain.workout.unicodeCaseFold

/**
 * Reads the logical backup snapshot from SQLDelight. Every section is read inside one transaction so
 * concurrent writes cannot produce a torn export; the payload is returned as a value, and the caller
 * serializes and writes the file after the transaction has been released.
 */
class SqlDelightBackupRepository(
    private val database: HydraFitDatabase,
    private val catalog: BackupCatalog
) : BackupRepository {

    override suspend fun export(appVersion: String, exportedAtMillis: Long): BackupFile =
        database.transactionWithResult {
            BackupFile(
                format = BACKUP_FORMAT,
                formatVersion = BACKUP_FORMAT_VERSION,
                appVersion = appVersion,
                exportedAtMillis = exportedAtMillis,
                catalog = BackupCatalogManifest(
                    seedExerciseIds = catalog.seedExerciseIds().sorted(),
                    seedProfiles = catalog.seedProfiles()
                        .map { BackupSeedProfile(it.key, it.value) }
                        .sortedBy { it.id }
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
                        loadKind = it.loadKind,
                        timingProvenance = it.timingProvenance,
                        startedAtElapsedMillis = it.startedAtElapsedMillis,
                        completedAtElapsedMillis = it.completedAtElapsedMillis
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
                planTargetExplanations = database.planTargetExplanationQueries.selectAll()
                    .executeAsList()
                    .mapNotNull { row ->
                        // A row with no recorded version is unreadable; the read path treats it as
                        // "no explanation", so export omits it rather than failing the whole backup.
                        val version = row.explanationVersion ?: return@mapNotNull null
                        BackupPlanTargetExplanationRecord(
                            planId = row.planId,
                            dayIndex = row.dayIndex.toInt(),
                            position = row.position.toInt(),
                            explanationVersion = version.toInt(),
                            baselineSource = row.baselineSource,
                            baselineRecentEstimateKg = row.baselineRecentEstimateKg,
                            baselineQualifyingSetCount = row.baselineQualifyingSetCount?.toInt(),
                            baselineManualFloorKg = row.baselineManualFloorKg,
                            baselineKg = row.baselineKg,
                            progressionOutcome = row.progressionOutcome,
                            progressionIncrements = row.progressionIncrements?.toInt(),
                            progressionDeltaKg = row.progressionDeltaKg,
                            progressionRoundToKg = row.progressionRoundToKg,
                            progressionSuccessStreakSize =
                            row.progressionSuccessStreakSize?.toInt(),
                            progressionFailureStreakSize =
                            row.progressionFailureStreakSize?.toInt(),
                            conversionReps = row.conversionReps?.toInt(),
                            conversionIntensityFraction = row.conversionIntensityFraction,
                            conversionDeloadIntensityScale = row.conversionDeloadIntensityScale,
                            conversionRoundToKg = row.conversionRoundToKg,
                            conversionUncappedWorkingKg = row.conversionUncappedWorkingKg,
                            conversionEquipmentCeilingKg = row.conversionEquipmentCeilingKg,
                            conversionEquipmentCeilingBinds =
                            row.conversionEquipmentCeilingBinds?.let { flag -> flag == 1L },
                            aiProposedKg = row.aiProposedKg,
                            aiProposedAccepted = row.aiProposedAccepted?.let { flag -> flag == 1L },
                            aiDeloadScaled = row.aiDeloadScaled?.let { flag -> flag == 1L },
                            aiDeloadIntensityScale = row.aiDeloadIntensityScale,
                            aiProgressionBoundKg = row.aiProgressionBoundKg,
                            aiBoundedByProgression =
                            row.aiBoundedByProgression?.let { flag -> flag == 1L },
                            aiEquipmentCeilingKg = row.aiEquipmentCeilingKg,
                            aiCappedByEquipment = row.aiCappedByEquipment?.let { flag ->
                                flag == 1L
                            },
                            setBaseSets = row.setBaseSets?.toInt(),
                            setDeloadApplied = row.setDeloadApplied?.let { flag -> flag == 1L },
                            setDeloadVolumeScale = row.setDeloadVolumeScale,
                            setFatigueEffect = row.setFatigueEffect,
                            setFinalSets = row.setFinalSets?.toInt(),
                            unavailableReason = row.unavailableReason
                        )
                    },
                planTargetExplanationStates =
                database.planTargetExplanationStateQueries.selectAll()
                    .executeAsList().map {
                        BackupPlanTargetExplanationStateRecord(
                            planId = it.planId,
                            attribution = it.attribution,
                            isDeload = it.isDeload != 0L,
                            deloadVolumeScale = it.deloadVolumeScale,
                            deloadIntensityScale = it.deloadIntensityScale,
                            explanationVersion = it.explanationVersion.toInt()
                        )
                    },
                entryTargetExplanations = database.entryTargetExplanationQueries.selectAll()
                    .executeAsList()
                    .mapNotNull { row ->
                        val version = row.explanationVersion ?: return@mapNotNull null
                        BackupEntryTargetExplanationRecord(
                            entryKind = row.entryKind,
                            entryId = row.entryId,
                            explanationVersion = version.toInt(),
                            baselineSource = row.baselineSource,
                            baselineRecentEstimateKg = row.baselineRecentEstimateKg,
                            baselineQualifyingSetCount = row.baselineQualifyingSetCount?.toInt(),
                            baselineManualFloorKg = row.baselineManualFloorKg,
                            baselineKg = row.baselineKg,
                            progressionOutcome = row.progressionOutcome,
                            progressionIncrements = row.progressionIncrements?.toInt(),
                            progressionDeltaKg = row.progressionDeltaKg,
                            progressionRoundToKg = row.progressionRoundToKg,
                            progressionSuccessStreakSize =
                            row.progressionSuccessStreakSize?.toInt(),
                            progressionFailureStreakSize =
                            row.progressionFailureStreakSize?.toInt(),
                            conversionReps = row.conversionReps?.toInt(),
                            conversionIntensityFraction = row.conversionIntensityFraction,
                            conversionDeloadIntensityScale = row.conversionDeloadIntensityScale,
                            conversionRoundToKg = row.conversionRoundToKg,
                            conversionUncappedWorkingKg = row.conversionUncappedWorkingKg,
                            conversionEquipmentCeilingKg = row.conversionEquipmentCeilingKg,
                            conversionEquipmentCeilingBinds =
                            row.conversionEquipmentCeilingBinds?.let { flag -> flag == 1L },
                            aiProposedKg = row.aiProposedKg,
                            aiProposedAccepted = row.aiProposedAccepted?.let { flag -> flag == 1L },
                            aiDeloadScaled = row.aiDeloadScaled?.let { flag -> flag == 1L },
                            aiDeloadIntensityScale = row.aiDeloadIntensityScale,
                            aiProgressionBoundKg = row.aiProgressionBoundKg,
                            aiBoundedByProgression =
                            row.aiBoundedByProgression?.let { flag -> flag == 1L },
                            aiEquipmentCeilingKg = row.aiEquipmentCeilingKg,
                            aiCappedByEquipment = row.aiCappedByEquipment?.let { flag ->
                                flag == 1L
                            },
                            setBaseSets = row.setBaseSets?.toInt(),
                            setDeloadApplied = row.setDeloadApplied?.let { flag -> flag == 1L },
                            setDeloadVolumeScale = row.setDeloadVolumeScale,
                            setFatigueEffect = row.setFatigueEffect,
                            setFinalSets = row.setFinalSets?.toInt(),
                            unavailableReason = row.unavailableReason
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
                    BackupPreferenceRecord(
                        exerciseId = it.exerciseId,
                        preference = it.preference
                    )
                },
                exclusions = database.exerciseExclusionQueries.selectAll().executeAsList().map {
                    BackupExclusionRecord(exerciseId = it.exerciseId, expiresAt = it.expiresAt)
                },
                restPreferences = listOf(
                    BackupRestPreferenceRecord(
                        exerciseId = null,
                        durationSeconds = database.restPreferenceQueries.selectGlobalDefault()
                            .executeAsOneOrNull() ?: DEFAULT_REST_SECONDS
                    )
                ) + database.restPreferenceQueries.selectAllExerciseOverrides()
                    .executeAsList()
                    .map {
                        BackupRestPreferenceRecord(
                            exerciseId = it.exerciseId,
                            durationSeconds = it.durationSeconds
                        )
                    },
                trainingGuidance = database.trainingGuidancePreferenceQueries.selectPreferences()
                    .executeAsOneOrNull().let {
                        com.hydrafit.app.core.domain.backup.BackupTrainingGuidanceRecord(
                            restAlertsEnabled = it?.restAlertsEnabled == 1L,
                            shortRestCoachingEnabled = it?.shortRestCoachingEnabled == 1L
                        )
                    },
                exerciseNotes = database.exerciseNoteQueries.selectAll().executeAsList().map {
                    BackupExerciseNoteRecord(exerciseId = it.exerciseId, note = it.note)
                },
                sessionNotes = database.sessionNoteQueries.selectAll().executeAsList().map {
                    BackupSessionNoteRecord(sessionId = it.sessionId, note = it.note)
                },
                setup = database.setupQueries.selectState().executeAsOne().let {
                    BackupSetupRecord(it.version.toInt(), it.disposition, it.step)
                },
                preferredWeekdays = decodePreferredWeekdays(
                    database.setupQueries.selectWeekdays().executeAsOne().weekdays
                )?.sortedBy { it.ordinal }?.map { it.name }
            )
        }

    override suspend fun restore(file: BackupFile) {
        database.transaction {
            database.setupQueries.updateState(
                file.setup.version.toLong(),
                file.setup.disposition,
                file.setup.step,
                0L
            )
            database.setupQueries.updateWeekdays(file.preferredWeekdays?.joinToString(","))
            // Delete included user-owned data, children before parents. Seeded catalog rows,
            // built-in equipment and credentials/model files are left untouched.
            database.exerciseOverrideQueries.deleteAll()
            database.exercisePreferenceQueries.deleteAll()
            database.restPreferenceQueries.deleteAll()
            database.trainingGuidancePreferenceQueries.deleteAll()
            database.restCountdownQueries.deletePersisted()
            database.exerciseExclusionQueries.deleteAll()
            database.exerciseNoteQueries.deleteAll()
            database.sessionNoteQueries.deleteAll()
            database.personalRecordQueries.deleteAll()
            database.workoutLogQueries.deleteAllSets()
            database.workoutSessionQueries.deleteAllSessions()
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
            database.routineTemplateQueries.deleteAllEntries()
            database.routineTemplateQueries.deleteAllWorkouts()
            database.routineTemplateQueries.deleteAllTemplates()
            database.exerciseQueries.deleteAllCustom()
            database.equipmentQueries.deleteCustomEquipment()
            database.userEquipmentQueries.deleteAllSelected()

            // Restore may run before startup seeding on an upgraded install. Recreate missing
            // canonical rows before applying their exported ceilings, not after those writes.
            SeedEquipmentCatalog(database).seed()

            // Equipment: built-in rows stay; custom rows are re-created, then every max weight applied.
            file.equipment.forEach { equipment ->
                if (!equipment.isBuiltIn) {
                    database.equipmentQueries.insert(equipment.id, equipment.name, 0)
                }
                database.equipmentQueries.updateMaxWeight(equipment.maxWeightKg, equipment.id)
            }
            file.selectedEquipment.forEach { database.userEquipmentQueries.insertSelected(it) }
            database.trainingGuidancePreferenceQueries.restorePreferences(
                if (file.trainingGuidance.restAlertsEnabled) 1L else 0L,
                if (file.trainingGuidance.shortRestCoachingEnabled) 1L else 0L
            )
            file.restPreferences.forEach { preference ->
                val exerciseId = preference.exerciseId
                if (exerciseId == null) {
                    database.restPreferenceQueries.upsertGlobalDefault(preference.durationSeconds)
                } else {
                    database.restPreferenceQueries.upsertExerciseOverride(
                        exerciseId,
                        exerciseId,
                        preference.durationSeconds
                    )
                }
            }

            file.workoutSessions.forEach { session ->
                database.workoutSessionQueries.insertSessionWithOccurrence(
                    id = session.id,
                    startedAtMillis = session.startedAtMillis,
                    endedAtMillis = session.endedAtMillis,
                    localEpochDay = session.localEpochDay,
                    occurrenceId = session.occurrenceId
                )
            }
            file.sessionNotes.forEach {
                database.sessionNoteQueries.upsert(it.sessionId, it.note)
                database.sessionNoteQueries.updateSearchNote(
                    searchNote = unicodeCaseFold(it.note),
                    sessionId = it.sessionId
                )
            }
            file.customExercises.forEach { exercise ->
                database.exerciseQueries.insertCustom(
                    id = exercise.id,
                    name = exercise.name,
                    requiredEquipment = exercise.requiredEquipment,
                    movementPattern = exercise.movementPattern,
                    isUnilateral = if (exercise.isUnilateral) 1L else 0L,
                    loadCapability = exercise.loadCapability,
                    involvements = exercise.involvements
                )
                // The stored projection is the effective name, so a rename of this custom follows.
                database.refreshExerciseSearchName(exercise.id)
            }
            file.exerciseOverrides.forEach { override ->
                database.exerciseOverrideQueries.upsert(
                    exerciseId = override.exerciseId,
                    name = override.name,
                    requiredEquipment = override.requiredEquipment,
                    movementPattern = override.movementPattern,
                    isUnilateral = override.isUnilateral?.let { if (it) 1L else 0L },
                    loadCapability = override.loadCapability,
                    involvements = override.involvements
                )
                // A restored rename decides the effective display name, so its projection follows.
                database.refreshExerciseSearchName(override.exerciseId)
            }
            file.personalRecords.forEach { record ->
                database.personalRecordQueries.upsert(
                    exerciseId = record.exerciseId,
                    weightKg = record.weightKg,
                    reps = record.reps.toLong(),
                    updatedAt = record.updatedAt,
                    loadKind = record.loadKind
                )
            }
            file.preferences.forEach {
                database.exercisePreferenceQueries.upsert(it.exerciseId, it.preference)
            }
            file.exclusions.forEach {
                database.exerciseExclusionQueries.upsert(it.exerciseId, it.expiresAt)
            }
            file.exerciseNotes.forEach {
                database.exerciseNoteQueries.upsert(it.exerciseId, it.note)
            }

            file.plans.forEach { plan ->
                database.planHistoryQueries.insertPlanWithId(
                    id = plan.id,
                    engineId = plan.engineId,
                    acceptedAt = plan.acceptedAt,
                    weekNumber = plan.weekNumber.toLong(),
                    cycleNumber = plan.cycleNumber.toLong()
                )
            }
            file.planDays.forEach { day ->
                database.planHistoryQueries.insertDayWithId(
                    id = day.id,
                    planId = day.planId,
                    dayIndex = day.dayIndex.toLong(),
                    focus = day.focus
                )
            }
            file.planEntries.forEach { entry ->
                database.planHistoryQueries.insertEntryWithId(
                    id = entry.id,
                    dayId = entry.dayId,
                    position = entry.position.toLong(),
                    exerciseId = entry.exerciseId,
                    sets = entry.sets.toLong(),
                    reps = entry.reps.toLong(),
                    exerciseName = entry.exerciseName,
                    movementPattern = entry.movementPattern,
                    suggestedWeightKg = entry.suggestedWeightKg,
                    loadCapability = entry.loadCapability,
                    loadKind = entry.loadKind
                )
            }
            file.volumeExplanations.forEach { explanation ->
                database.planVolumeExplanationQueries.insert(
                    planId = explanation.planId,
                    muscle = explanation.muscle,
                    targetSets = explanation.targetSets.toLong(),
                    isTargetEnforced = if (explanation.isTargetEnforced) 1L else 0L,
                    directIsolationSets = explanation.directIsolationSets.toLong(),
                    estimatedOtherInvolvementCredits = explanation.estimatedOtherInvolvementCredits,
                    unmetReason = explanation.unmetReason,
                    attribution = explanation.attribution
                )
            }
            file.volumeExplanationStates.forEach { state ->
                database.planVolumeExplanationStateQueries.upsert(
                    planId = state.planId,
                    status = state.status,
                    assessmentVersion = state.assessmentVersion.toLong()
                )
            }
            file.planTargetExplanationStates.forEach { state ->
                database.planTargetExplanationStateQueries.upsert(
                    planId = state.planId,
                    attribution = state.attribution,
                    isDeload = if (state.isDeload) 1L else 0L,
                    deloadVolumeScale = state.deloadVolumeScale,
                    deloadIntensityScale = state.deloadIntensityScale,
                    explanationVersion = state.explanationVersion.toLong()
                )
            }
            file.planTargetExplanations.forEach { explanation ->
                database.planTargetExplanationQueries.insertExplanation(
                    planId = explanation.planId,
                    dayIndex = explanation.dayIndex.toLong(),
                    position = explanation.position.toLong(),
                    explanationVersion = explanation.explanationVersion.toLong(),
                    baselineSource = explanation.baselineSource,
                    baselineRecentEstimateKg = explanation.baselineRecentEstimateKg,
                    baselineQualifyingSetCount =
                    explanation.baselineQualifyingSetCount?.toLong(),
                    baselineManualFloorKg = explanation.baselineManualFloorKg,
                    baselineKg = explanation.baselineKg,
                    progressionOutcome = explanation.progressionOutcome,
                    progressionIncrements = explanation.progressionIncrements?.toLong(),
                    progressionDeltaKg = explanation.progressionDeltaKg,
                    progressionRoundToKg = explanation.progressionRoundToKg,
                    progressionSuccessStreakSize =
                    explanation.progressionSuccessStreakSize?.toLong(),
                    progressionFailureStreakSize =
                    explanation.progressionFailureStreakSize?.toLong(),
                    conversionReps = explanation.conversionReps?.toLong(),
                    conversionIntensityFraction = explanation.conversionIntensityFraction,
                    conversionDeloadIntensityScale = explanation.conversionDeloadIntensityScale,
                    conversionRoundToKg = explanation.conversionRoundToKg,
                    conversionUncappedWorkingKg = explanation.conversionUncappedWorkingKg,
                    conversionEquipmentCeilingKg = explanation.conversionEquipmentCeilingKg,
                    conversionEquipmentCeilingBinds =
                    explanation.conversionEquipmentCeilingBinds?.let { if (it) 1L else 0L },
                    aiProposedKg = explanation.aiProposedKg,
                    aiProposedAccepted =
                    explanation.aiProposedAccepted?.let { if (it) 1L else 0L },
                    aiDeloadScaled = explanation.aiDeloadScaled?.let { if (it) 1L else 0L },
                    aiDeloadIntensityScale = explanation.aiDeloadIntensityScale,
                    aiProgressionBoundKg = explanation.aiProgressionBoundKg,
                    aiBoundedByProgression =
                    explanation.aiBoundedByProgression?.let { if (it) 1L else 0L },
                    aiEquipmentCeilingKg = explanation.aiEquipmentCeilingKg,
                    aiCappedByEquipment = explanation.aiCappedByEquipment?.let {
                        if (it) 1L else 0L
                    },
                    setBaseSets = explanation.setBaseSets?.toLong(),
                    setDeloadApplied = explanation.setDeloadApplied?.let { if (it) 1L else 0L },
                    setDeloadVolumeScale = explanation.setDeloadVolumeScale,
                    setFatigueEffect = explanation.setFatigueEffect,
                    setFinalSets = explanation.setFinalSets?.toLong(),
                    unavailableReason = explanation.unavailableReason
                )
            }
            file.entryTargetExplanations.forEach { explanation ->
                database.entryTargetExplanationQueries.insertExplanation(
                    entryKind = explanation.entryKind,
                    entryId = explanation.entryId,
                    explanationVersion = explanation.explanationVersion.toLong(),
                    baselineSource = explanation.baselineSource,
                    baselineRecentEstimateKg = explanation.baselineRecentEstimateKg,
                    baselineQualifyingSetCount =
                    explanation.baselineQualifyingSetCount?.toLong(),
                    baselineManualFloorKg = explanation.baselineManualFloorKg,
                    baselineKg = explanation.baselineKg,
                    progressionOutcome = explanation.progressionOutcome,
                    progressionIncrements = explanation.progressionIncrements?.toLong(),
                    progressionDeltaKg = explanation.progressionDeltaKg,
                    progressionRoundToKg = explanation.progressionRoundToKg,
                    progressionSuccessStreakSize =
                    explanation.progressionSuccessStreakSize?.toLong(),
                    progressionFailureStreakSize =
                    explanation.progressionFailureStreakSize?.toLong(),
                    conversionReps = explanation.conversionReps?.toLong(),
                    conversionIntensityFraction = explanation.conversionIntensityFraction,
                    conversionDeloadIntensityScale = explanation.conversionDeloadIntensityScale,
                    conversionRoundToKg = explanation.conversionRoundToKg,
                    conversionUncappedWorkingKg = explanation.conversionUncappedWorkingKg,
                    conversionEquipmentCeilingKg = explanation.conversionEquipmentCeilingKg,
                    conversionEquipmentCeilingBinds =
                    explanation.conversionEquipmentCeilingBinds?.let { if (it) 1L else 0L },
                    aiProposedKg = explanation.aiProposedKg,
                    aiProposedAccepted =
                    explanation.aiProposedAccepted?.let { if (it) 1L else 0L },
                    aiDeloadScaled = explanation.aiDeloadScaled?.let { if (it) 1L else 0L },
                    aiDeloadIntensityScale = explanation.aiDeloadIntensityScale,
                    aiProgressionBoundKg = explanation.aiProgressionBoundKg,
                    aiBoundedByProgression =
                    explanation.aiBoundedByProgression?.let { if (it) 1L else 0L },
                    aiEquipmentCeilingKg = explanation.aiEquipmentCeilingKg,
                    aiCappedByEquipment = explanation.aiCappedByEquipment?.let {
                        if (it) 1L else 0L
                    },
                    setBaseSets = explanation.setBaseSets?.toLong(),
                    setDeloadApplied = explanation.setDeloadApplied?.let { if (it) 1L else 0L },
                    setDeloadVolumeScale = explanation.setDeloadVolumeScale,
                    setFatigueEffect = explanation.setFatigueEffect,
                    setFinalSets = explanation.setFinalSets?.toLong(),
                    unavailableReason = explanation.unavailableReason
                )
            }

            file.routines.forEach { routine ->
                database.routineTemplateQueries.insertTemplateWithId(
                    id = routine.id,
                    name = routine.name,
                    revision = routine.revision.toLong(),
                    createdAtMillis = routine.createdAtMillis,
                    updatedAtMillis = routine.updatedAtMillis,
                    archivedAtMillis = routine.archivedAtMillis,
                    sourcePlanId = routine.sourcePlanId
                )
            }
            file.routineWorkouts.forEach { workout ->
                database.routineTemplateQueries.insertWorkoutWithId(
                    id = workout.id,
                    templateId = workout.templateId,
                    position = workout.position.toLong(),
                    name = workout.name,
                    focus = workout.focus
                )
            }
            file.routineEntries.forEach { entry ->
                database.routineTemplateQueries.insertEntryWithId(
                    id = entry.id,
                    workoutId = entry.workoutId,
                    position = entry.position.toLong(),
                    exerciseId = entry.exerciseId,
                    sets = entry.sets.toLong(),
                    reps = entry.reps.toLong(),
                    weightKg = entry.weightKg,
                    loadKind = entry.loadKind
                )
            }

            file.activations.forEach { activation ->
                database.trainingScheduleQueries.insertActivationWithId(
                    id = activation.id,
                    templateId = activation.templateId,
                    templateRevision = activation.templateRevision?.toLong(),
                    sourcePlanId = activation.sourcePlanId,
                    name = activation.name,
                    createdAtMillis = activation.createdAtMillis,
                    startEpochDay = activation.startEpochDay,
                    mode = activation.mode,
                    weekdayMask = activation.weekdayMask,
                    status = activation.status,
                    weekNumber = activation.weekNumber?.toLong(),
                    cycleNumber = activation.cycleNumber?.toLong(),
                    endedAtMillis = activation.endedAtMillis,
                    revision = activation.revision.toLong()
                )
            }
            file.activationWorkouts.forEach { workout ->
                database.trainingScheduleQueries.insertActivationWorkoutWithId(
                    id = workout.id,
                    activationId = workout.activationId,
                    sourceWorkoutId = workout.sourceWorkoutId,
                    position = workout.position.toLong(),
                    name = workout.name,
                    focus = workout.focus
                )
            }
            file.activationEntries.forEach { entry ->
                database.trainingScheduleQueries.insertActivationEntryWithId(
                    id = entry.id,
                    workoutId = entry.workoutId,
                    position = entry.position.toLong(),
                    exerciseId = entry.exerciseId,
                    exerciseName = entry.exerciseName,
                    movementPattern = entry.movementPattern,
                    requiredEquipment = entry.requiredEquipment,
                    involvements = entry.involvements,
                    isUnilateral = if (entry.isUnilateral) 1L else 0L,
                    sets = entry.sets.toLong(),
                    reps = entry.reps.toLong(),
                    weightKg = entry.weightKg,
                    loadCapability = entry.loadCapability,
                    loadKind = entry.loadKind
                )
            }
            file.occurrences.forEach { occurrence ->
                database.trainingScheduleQueries.insertOccurrenceWithId(
                    id = occurrence.id,
                    activationId = occurrence.activationId,
                    activationWorkoutId = occurrence.activationWorkoutId,
                    queuePosition = occurrence.queuePosition.toLong(),
                    scheduledEpochDay = occurrence.scheduledEpochDay,
                    notBeforeEpochDay = occurrence.notBeforeEpochDay,
                    startedAtMillis = occurrence.startedAtMillis,
                    resolvedAtMillis = occurrence.resolvedAtMillis,
                    status = occurrence.status,
                    revision = occurrence.revision.toLong()
                )
            }
            file.occurrenceEntries.forEach { entry ->
                database.trainingScheduleQueries.insertOccurrenceEntryWithId(
                    id = entry.id,
                    occurrenceId = entry.occurrenceId,
                    sourceActivationEntryId = entry.sourceActivationEntryId,
                    position = entry.position.toLong(),
                    exerciseId = entry.exerciseId,
                    exerciseName = entry.exerciseName,
                    movementPattern = entry.movementPattern,
                    requiredEquipment = entry.requiredEquipment,
                    involvements = entry.involvements,
                    isUnilateral = if (entry.isUnilateral) 1L else 0L,
                    sets = entry.sets.toLong(),
                    reps = entry.reps.toLong(),
                    weightKg = entry.weightKg,
                    loadCapability = entry.loadCapability,
                    loadKind = entry.loadKind,
                    remainingDisposition = entry.remainingDisposition,
                    terminalRemainingSets = entry.terminalRemainingSets?.toLong()
                )
            }
            val state = file.scheduleState
            if (state == null) {
                database.trainingScheduleQueries.deleteScheduleState()
            } else {
                database.trainingScheduleQueries.upsertScheduleState(
                    activeActivationId = state.activeActivationId,
                    selectedOccurrenceId = state.selectedOccurrenceId,
                    legacyFallbackEnabled = if (state.legacyFallbackEnabled) 1L else 0L
                )
            }

            file.workoutSets.forEach { set ->
                database.workoutLogQueries.insertSetWithIdAndTimingProvenance(
                    id = set.id,
                    exerciseId = set.exerciseId,
                    reps = set.reps.toLong(),
                    weightKg = set.weightKg,
                    performedAt = set.performedAtMillis,
                    isWarmup = if (set.isWarmup) 1L else 0L,
                    involvements = set.involvements,
                    weekNumber = set.weekNumber?.toLong(),
                    cycleNumber = set.cycleNumber?.toLong(),
                    dayIndex = set.dayIndex?.toLong(),
                    rir = set.rir?.toLong(),
                    sessionId = set.sessionId,
                    occurrenceId = set.occurrenceId,
                    occurrenceEntryId = set.occurrenceEntryId,
                    loadKind = set.loadKind,
                    timingProvenance = set.timingProvenance,
                    startedAtElapsedMillis = set.startedAtElapsedMillis,
                    completedAtElapsedMillis = set.completedAtElapsedMillis
                )
            }

            val settings = file.settings
            database.plannerEngineQueries.insertIgnoreRow(
                settings?.engineId ?: "DETERMINISTIC"
            )
            database.plannerEngineQueries.updateEngine(settings?.engineId ?: "DETERMINISTIC")
            database.plannerEngineQueries.updateDaysPerWeek(
                (settings?.daysPerWeek ?: 4).toLong()
            )
            database.plannerEngineQueries.updateTrainingGoal(
                settings?.trainingGoal ?: "BALANCED"
            )
            database.plannerEngineQueries.updateShareWorkoutData(
                if (settings?.shareWorkoutData == true) 1L else 0L
            )
            database.plannerEngineQueries.updateWeightUnit(settings?.weightUnit ?: "KG")
            database.plannerEngineQueries.updateGuidedWorkoutEnabled(
                if (settings?.guidedWorkoutEnabled == true) 1L else 0L
            )
        }
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
            weightUnit = queries.selectWeightUnit().executeAsOneOrNull() ?: "KG",
            guidedWorkoutEnabled =
            queries.selectGuidedWorkoutEnabled().executeAsOneOrNull()?.let { it != 0L } ?: false
        )
    }
}
