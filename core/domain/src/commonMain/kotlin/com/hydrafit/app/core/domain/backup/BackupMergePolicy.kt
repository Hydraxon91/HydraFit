package com.hydrafit.app.core.domain.backup

/**
 * Selects the add-only records a merge inserts into the live install. The policy is deliberately
 * conservative: a session already present keeps its local sets and note whole, every other supported
 * collection is insert-if-absent by its stored identity, and nothing existing is modified or
 * deleted. Re-merging the same file therefore adds nothing.
 *
 * Full-graph id remapping (plans, routines, activations, occurrences, equipment profiles, singleton
 * settings) is out of v1 scope and disclosed through [BackupMergeSelection.excluded]. An in-progress
 * session (`endedAtMillis == null`, possible in a best-effort automatic snapshot) is skipped whole
 * and counted, so a merge never creates a phantom open session. The selection is a pure computation
 * over an already-validated [BackupFile]; the database layer applies it in one transaction.
 */
object BackupMergePolicy {

    fun select(file: BackupFile, local: BackupMergeKeys): BackupMergeSelection {
        val sessionsNotLocal = file.workoutSessions.filterNot { it.id in local.sessionIds }
        val newSessions = sessionsNotLocal.filter { it.endedAtMillis != null }
        val newSessionIds = newSessions.mapTo(mutableSetOf()) { it.id }

        val setsToInsert = file.workoutSets.mapNotNull { set ->
            val sessionId = set.sessionId ?: return@mapNotNull null
            if (sessionId !in newSessionIds) return@mapNotNull null
            BackupMergedSet(
                exerciseId = set.exerciseId,
                reps = set.reps,
                weightKg = set.weightKg,
                performedAtMillis = set.performedAtMillis,
                isWarmup = set.isWarmup,
                involvements = set.involvements,
                weekNumber = set.weekNumber,
                cycleNumber = set.cycleNumber,
                dayIndex = set.dayIndex,
                rir = set.rir,
                sessionId = sessionId,
                loadKind = set.loadKind,
                timingProvenance = set.timingProvenance,
                startedAtElapsedMillis = set.startedAtElapsedMillis,
                completedAtElapsedMillis = set.completedAtElapsedMillis,
                durationSeconds = set.durationSeconds,
                prescriptionSnapshot = set.prescriptionSnapshot,
                stationName = set.stationName,
                stationLoadBasis = set.stationLoadBasis,
                distanceMeters = set.distanceMeters
            )
        }

        val bodyWeight = file.bodyWeightMeasurements
            .filterNot { it.localEpochDay in local.bodyWeightDays }
        val personalRecords = file.personalRecords
            .filterNot { it.exerciseId in local.personalRecordExerciseIds }
        val preferences = file.preferences
            .filterNot { it.exerciseId in local.exercisePreferenceIds }
        val exclusions = file.exclusions
            .filterNot { it.exerciseId in local.exerciseExclusionIds }
        val exerciseNotes = file.exerciseNotes
            .filterNot { it.exerciseId in local.exerciseNoteIds }
        val overrides = file.exerciseOverrides
            .filterNot { it.exerciseId in local.exerciseOverrideIds }
        val customExercises = file.customExercises
            .filterNot { it.id in local.customExerciseIds }

        val keptLocalRows = (file.personalRecords.size - personalRecords.size) +
            (file.preferences.size - preferences.size) +
            (file.exclusions.size - exclusions.size) +
            (file.exerciseNotes.size - exerciseNotes.size) +
            (file.exerciseOverrides.size - overrides.size) +
            (file.customExercises.size - customExercises.size)

        val droppedOccurrenceLinks = file.workoutSets.count { set ->
            val sessionId = set.sessionId
            sessionId != null &&
                sessionId in newSessionIds &&
                (set.occurrenceId != null || set.occurrenceEntryId != null)
        }

        return BackupMergeSelection(
            sessionsToInsert = newSessions,
            setsToInsert = setsToInsert,
            sessionNotesToInsert = file.sessionNotes.filter { it.sessionId in newSessionIds },
            bodyWeightToInsert = bodyWeight,
            personalRecordsToInsert = personalRecords,
            preferencesToInsert = preferences,
            exclusionsToInsert = exclusions,
            exerciseNotesToInsert = exerciseNotes,
            overridesToInsert = overrides,
            customExercisesToInsert = customExercises,
            keptLocalSessions = file.workoutSessions.size - sessionsNotLocal.size,
            keptLocalRows = keptLocalRows,
            skippedOpenSessions = sessionsNotLocal.size - newSessions.size,
            skippedUnattachedSets = file.workoutSets.count { it.sessionId == null },
            setsWithDroppedOccurrenceLinks = droppedOccurrenceLinks,
            excluded = excludedCounts(file)
        )
    }

    private fun excludedCounts(file: BackupFile): List<BackupMergeExcludedCount> = listOf(
        BackupMergeExcludedCount(BackupMergeExcludedCategory.ACCEPTED_PLANS, file.plans.size),
        BackupMergeExcludedCount(BackupMergeExcludedCategory.ROUTINES, file.routines.size),
        BackupMergeExcludedCount(BackupMergeExcludedCategory.ACTIVATIONS, file.activations.size),
        BackupMergeExcludedCount(BackupMergeExcludedCategory.OCCURRENCES, file.occurrences.size),
        BackupMergeExcludedCount(
            BackupMergeExcludedCategory.EQUIPMENT_PROFILES,
            file.equipmentProfiles.size + file.equipmentStations.size
        ),
        BackupMergeExcludedCount(
            BackupMergeExcludedCategory.SETTINGS,
            (if (file.settings != null) 1 else 0) + (if (file.scheduleState != null) 1 else 0)
        ),
        BackupMergeExcludedCount(
            BackupMergeExcludedCategory.IMPORT_LEDGER,
            file.importProvenance.size + file.importBuckets.size
        )
    ).filter { it.count > 0 }
}
