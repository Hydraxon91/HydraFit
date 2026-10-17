package com.hydrafit.app.core.domain.backup

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BackupMergePolicyTest {

    @Test
    fun addsNewSessionsWithTheirSetsAndNote() {
        val selection = BackupMergePolicy.select(
            file(
                sessions = listOf(session("s1")),
                sets = listOf(set(sessionId = "s1", id = 1L), set(sessionId = "s1", id = 2L)),
                sessionNotes = listOf(BackupSessionNoteRecord("s1", "note"))
            ),
            noLocalKeys()
        )

        assertEquals(listOf("s1"), selection.sessionsToInsert.map { it.id })
        assertEquals(2, selection.setsToInsert.size)
        assertEquals(listOf("s1", "s1"), selection.setsToInsert.map { it.sessionId })
        assertEquals(listOf("note"), selection.sessionNotesToInsert.map { it.note })
        assertEquals(0, selection.keptLocalSessions)
        assertEquals(0, selection.keptLocalRows)
        assertEquals(0, selection.skippedUnattachedSets)
        assertEquals(4, selection.insertCount)
        assertTrue(!selection.isEmpty)
    }

    @Test
    fun skipsSessionsAlreadyPresentWithTheirSetsAndNotes() {
        val selection = BackupMergePolicy.select(
            file(
                sessions = listOf(session("s1"), session("s2")),
                sets = listOf(set(sessionId = "s1", id = 1L), set(sessionId = "s2", id = 2L)),
                sessionNotes = listOf(BackupSessionNoteRecord("s1", "kept"))
            ),
            noLocalKeys().copy(sessionIds = setOf("s1"))
        )

        assertEquals(listOf("s2"), selection.sessionsToInsert.map { it.id })
        assertEquals(listOf("s2"), selection.setsToInsert.map { it.sessionId })
        assertEquals(0, selection.sessionNotesToInsert.size)
        assertEquals(1, selection.keptLocalSessions)
    }

    @Test
    fun countsUnattachedLegacySetsWithoutInserting() {
        val selection = BackupMergePolicy.select(
            file(
                sessions = listOf(session("s1")),
                sets = listOf(
                    set(sessionId = null, id = 1L),
                    set(sessionId = "s1", id = 2L)
                )
            ),
            noLocalKeys()
        )

        assertEquals(1, selection.skippedUnattachedSets)
        assertEquals(1, selection.setsToInsert.size)
        assertEquals("s1", selection.setsToInsert.single().sessionId)
    }

    @Test
    fun keepsLocalRowsForInsertIfAbsentCollections() {
        val selection = BackupMergePolicy.select(
            file(
                bodyWeight = listOf(
                    BackupBodyWeightRecord(5L, 80.0),
                    BackupBodyWeightRecord(6L, 81.0)
                ),
                personalRecords = listOf(pr("exA"), pr("exB")),
                preferences = listOf(BackupPreferenceRecord("exA", "PREFER")),
                exclusions = listOf(BackupExclusionRecord("exA", null)),
                exerciseNotes = listOf(BackupExerciseNoteRecord("exA", "n")),
                overrides = listOf(override("exA")),
                customExercises = listOf(customExercise("cx1"), customExercise("cx2"))
            ),
            noLocalKeys().copy(
                bodyWeightDays = setOf(5L),
                personalRecordExerciseIds = setOf("exA"),
                exercisePreferenceIds = setOf("exA"),
                exerciseExclusionIds = setOf("exA"),
                exerciseNoteIds = setOf("exA"),
                exerciseOverrideIds = setOf("exA"),
                customExerciseIds = setOf("cx1")
            )
        )

        assertEquals(listOf(6L), selection.bodyWeightToInsert.map { it.localEpochDay })
        assertEquals(listOf("exB"), selection.personalRecordsToInsert.map { it.exerciseId })
        assertEquals(0, selection.preferencesToInsert.size)
        assertEquals(0, selection.exclusionsToInsert.size)
        assertEquals(0, selection.exerciseNotesToInsert.size)
        assertEquals(0, selection.overridesToInsert.size)
        assertEquals(listOf("cx2"), selection.customExercisesToInsert.map { it.id })
        assertEquals(6, selection.keptLocalRows)
    }

    @Test
    fun remergeOfTheSameFileIsIdempotent() {
        val rich = file(
            sessions = listOf(session("s1")),
            sets = listOf(set(sessionId = "s1", id = 1L)),
            sessionNotes = listOf(BackupSessionNoteRecord("s1", "note")),
            bodyWeight = listOf(BackupBodyWeightRecord(5L, 80.0)),
            personalRecords = listOf(pr("exA")),
            preferences = listOf(BackupPreferenceRecord("exB", "NEUTRAL")),
            exclusions = listOf(BackupExclusionRecord("exC", null)),
            exerciseNotes = listOf(BackupExerciseNoteRecord("exD", "n")),
            overrides = listOf(override("exE")),
            customExercises = listOf(customExercise("cx1"))
        )

        val first = BackupMergePolicy.select(rich, noLocalKeys())
        assertTrue(first.insertCount > 0)

        val localAfterFirst = noLocalKeys().copy(
            sessionIds = first.sessionsToInsert.map { it.id }.toSet(),
            bodyWeightDays = first.bodyWeightToInsert.map { it.localEpochDay }.toSet(),
            personalRecordExerciseIds = first.personalRecordsToInsert.map { it.exerciseId }.toSet(),
            customExerciseIds = first.customExercisesToInsert.map { it.id }.toSet(),
            exerciseOverrideIds = first.overridesToInsert.map { it.exerciseId }.toSet(),
            exerciseNoteIds = first.exerciseNotesToInsert.map { it.exerciseId }.toSet(),
            exercisePreferenceIds = first.preferencesToInsert.map { it.exerciseId }.toSet(),
            exerciseExclusionIds = first.exclusionsToInsert.map { it.exerciseId }.toSet()
        )

        val second = BackupMergePolicy.select(rich, localAfterFirst)

        assertTrue(second.isEmpty)
        assertEquals(0, second.insertCount)
        assertEquals(1, second.keptLocalSessions)
        assertEquals(6, second.keptLocalRows)
        assertEquals(0, second.setsWithDroppedOccurrenceLinks)
    }

    @Test
    fun countsDroppedOccurrenceLinksOnlyForInsertedSets() {
        val selection = BackupMergePolicy.select(
            file(
                sessions = listOf(session("s1"), session("s2")),
                sets = listOf(
                    set(sessionId = "s1", id = 1L, occurrenceId = 9L),
                    set(sessionId = "s2", id = 2L, occurrenceEntryId = 9L)
                )
            ),
            noLocalKeys().copy(sessionIds = setOf("s1"))
        )

        assertEquals(listOf("s2"), selection.setsToInsert.map { it.sessionId })
        assertEquals(1, selection.setsWithDroppedOccurrenceLinks)
    }

    @Test
    fun reportsExcludedCollectionsWithPositiveCounts() {
        val selection = BackupMergePolicy.select(
            file(
                plans = listOf(BackupPlanRecord(1L, "DETERMINISTIC", 5L, 1, 1)),
                routines = listOf(BackupRoutineRecord(1L, "r", 1, 1L, 1L, null, null)),
                activations = listOf(activation()),
                occurrences = listOf(
                    BackupOccurrenceRecord(1L, 1L, 1L, 0, null, null, null, null, "QUEUED", 1)
                ),
                settings = BackupSettingsRecord("DETERMINISTIC", 3, "HYPERTROPHY", false, "KG"),
                scheduleState = BackupScheduleStateRecord(null, null, false),
                importProvenance = listOf(
                    BackupImportProvenanceRecord("FITNOTES", "k", "{}", "s9")
                )
            ),
            noLocalKeys()
        )

        val excluded = selection.excluded.associate { it.category to it.count }
        assertEquals(1, excluded[BackupMergeExcludedCategory.ACCEPTED_PLANS])
        assertEquals(1, excluded[BackupMergeExcludedCategory.ROUTINES])
        assertEquals(1, excluded[BackupMergeExcludedCategory.ACTIVATIONS])
        assertEquals(1, excluded[BackupMergeExcludedCategory.OCCURRENCES])
        assertEquals(2, excluded[BackupMergeExcludedCategory.SETTINGS])
        assertEquals(1, excluded[BackupMergeExcludedCategory.IMPORT_LEDGER])
        assertNull(excluded[BackupMergeExcludedCategory.EQUIPMENT_PROFILES])
    }
}

private fun noLocalKeys() = BackupMergeKeys(
    sessionIds = emptySet(),
    bodyWeightDays = emptySet(),
    personalRecordExerciseIds = emptySet(),
    customExerciseIds = emptySet(),
    exerciseOverrideIds = emptySet(),
    exerciseNoteIds = emptySet(),
    exercisePreferenceIds = emptySet(),
    exerciseExclusionIds = emptySet()
)

private fun file(
    sessions: List<BackupWorkoutSessionRecord> = emptyList(),
    sets: List<BackupWorkoutSetRecord> = emptyList(),
    sessionNotes: List<BackupSessionNoteRecord> = emptyList(),
    bodyWeight: List<BackupBodyWeightRecord> = emptyList(),
    personalRecords: List<BackupPersonalRecordRecord> = emptyList(),
    preferences: List<BackupPreferenceRecord> = emptyList(),
    exclusions: List<BackupExclusionRecord> = emptyList(),
    exerciseNotes: List<BackupExerciseNoteRecord> = emptyList(),
    overrides: List<BackupExerciseOverrideRecord> = emptyList(),
    customExercises: List<BackupExerciseRecord> = emptyList(),
    plans: List<BackupPlanRecord> = emptyList(),
    routines: List<BackupRoutineRecord> = emptyList(),
    activations: List<BackupActivationRecord> = emptyList(),
    occurrences: List<BackupOccurrenceRecord> = emptyList(),
    settings: BackupSettingsRecord? = null,
    scheduleState: BackupScheduleStateRecord? = null,
    importProvenance: List<BackupImportProvenanceRecord> = emptyList()
): BackupFile = emptyBackupFile().copy(
    equipmentProfiles = emptyList(),
    activeEquipmentProfileId = null,
    workoutSessions = sessions,
    workoutSets = sets,
    sessionNotes = sessionNotes,
    bodyWeightMeasurements = bodyWeight,
    personalRecords = personalRecords,
    preferences = preferences,
    exclusions = exclusions,
    exerciseNotes = exerciseNotes,
    exerciseOverrides = overrides,
    customExercises = customExercises,
    plans = plans,
    routines = routines,
    activations = activations,
    occurrences = occurrences,
    settings = settings,
    scheduleState = scheduleState,
    importProvenance = importProvenance
)

private fun session(id: String) = BackupWorkoutSessionRecord(
    id = id,
    startedAtMillis = 1L,
    endedAtMillis = 2L,
    localEpochDay = 1L,
    occurrenceId = null
)

private fun set(
    sessionId: String?,
    id: Long,
    occurrenceId: Long? = null,
    occurrenceEntryId: Long? = null
) = BackupWorkoutSetRecord(
    id = id,
    exerciseId = "ex1",
    reps = 8,
    weightKg = 50.0,
    performedAtMillis = 10L + id,
    isWarmup = false,
    involvements = null,
    weekNumber = null,
    cycleNumber = null,
    dayIndex = null,
    rir = null,
    sessionId = sessionId,
    occurrenceId = occurrenceId,
    occurrenceEntryId = occurrenceEntryId,
    loadKind = "EXTERNAL"
)

private fun pr(exerciseId: String) = BackupPersonalRecordRecord(
    exerciseId = exerciseId,
    weightKg = 100.0,
    reps = 5,
    updatedAt = 1L,
    loadKind = "EXTERNAL"
)

private fun override(exerciseId: String) = BackupExerciseOverrideRecord(
    exerciseId = exerciseId,
    name = null,
    requiredEquipment = null,
    movementPattern = null,
    isUnilateral = null,
    loadCapability = null,
    involvements = null
)

private fun customExercise(id: String) = BackupExerciseRecord(
    id = id,
    name = "Custom",
    requiredEquipment = "BARBELL",
    movementPattern = "SQUAT",
    isUnilateral = false,
    loadCapability = "EXTERNAL",
    involvements = null
)

private fun activation() = BackupActivationRecord(
    id = 1L,
    templateId = null,
    templateRevision = null,
    sourcePlanId = null,
    name = "a",
    createdAtMillis = 1L,
    startEpochDay = 1L,
    mode = "WEEKLY",
    weekdayMask = 0L,
    status = "ACTIVE",
    weekNumber = null,
    cycleNumber = null,
    endedAtMillis = null,
    revision = 1
)
