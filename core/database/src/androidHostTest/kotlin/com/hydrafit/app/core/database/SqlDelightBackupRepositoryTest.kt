package com.hydrafit.app.core.database

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.hydrafit.app.core.domain.backup.ApplyStagedBackupUseCase
import com.hydrafit.app.core.domain.backup.BACKUP_FORMAT_VERSION
import com.hydrafit.app.core.domain.backup.BackupBodyWeightRecord
import com.hydrafit.app.core.domain.backup.BackupException
import com.hydrafit.app.core.domain.backup.BackupExclusionRecord
import com.hydrafit.app.core.domain.backup.BackupExerciseNoteRecord
import com.hydrafit.app.core.domain.backup.BackupExerciseOverrideRecord
import com.hydrafit.app.core.domain.backup.BackupExerciseRecord
import com.hydrafit.app.core.domain.backup.BackupFailure
import com.hydrafit.app.core.domain.backup.BackupJson
import com.hydrafit.app.core.domain.backup.BackupMergePolicy
import com.hydrafit.app.core.domain.backup.BackupMergeSelection
import com.hydrafit.app.core.domain.backup.BackupMergedSet
import com.hydrafit.app.core.domain.backup.BackupPersonalRecordRecord
import com.hydrafit.app.core.domain.backup.BackupPreferenceRecord
import com.hydrafit.app.core.domain.backup.BackupSessionNoteRecord
import com.hydrafit.app.core.domain.backup.BackupValidator
import com.hydrafit.app.core.domain.backup.BackupWorkoutSessionRecord
import com.hydrafit.app.core.domain.backup.PreviewBackupUseCase
import com.hydrafit.app.core.domain.backup.RestoreBackupUseCase
import com.hydrafit.app.core.domain.backup.WARMUP_PREPARATION_OVERRIDE_FORMAT_VERSION
import com.hydrafit.app.core.domain.engine.SlotProgressionPolicy
import com.hydrafit.app.core.domain.engine.SlotProgressionPolicyJson
import com.hydrafit.app.core.domain.equipment.EquipmentTag
import com.hydrafit.app.core.domain.settings.WorkoutReminderPreferences
import com.hydrafit.app.core.domain.time.TimeProvider
import com.hydrafit.app.core.domain.workout.LoadKind
import com.hydrafit.app.core.domain.workout.WarmupAction
import com.hydrafit.app.core.domain.workout.WarmupPreparationItem
import com.hydrafit.app.core.domain.workout.WorkoutPrescriptionSnapshot
import com.hydrafit.app.core.domain.workout.WorkoutPrescriptionSnapshotJson
import com.hydrafit.app.core.domain.workout.unicodeCaseFold
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest

class SqlDelightBackupRepositoryTest {

    private lateinit var driver: JdbcSqliteDriver
    private lateinit var database: HydraFitDatabase
    private lateinit var repository: SqlDelightBackupRepository

    @BeforeTest
    fun setUp() {
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        HydraFitDatabase.Schema.create(driver)
        database = HydraFitDatabase(driver)
        SeedExerciseCatalog(database).seed()
        SeedEquipmentCatalog(database).seed()
        repository = SqlDelightBackupRepository(database, SqlDelightBackupCatalog())
    }

    @AfterTest
    fun tearDown() {
        driver.close()
    }

    @Test
    fun aSlotProgressionPolicySurvivesExportAndRestore() = runTest {
        val encoded = SlotProgressionPolicyJson.encode(
            SlotProgressionPolicy(minReps = 5, maxReps = 8, incrementKg = 2.5, equipmentId = "bar")
        )
        database.routineTemplateQueries.insertTemplate("Block", 1, 1, 1, null, null)
        val templateId = database.routineTemplateQueries.lastInsertedTemplateId().executeAsOne()
        database.routineTemplateQueries.insertWorkout(templateId, 0, "Day", "FULL_BODY")
        val workoutId = database.routineTemplateQueries.lastInsertedWorkoutId().executeAsOne()
        database.routineTemplateQueries.insertEntry(
            workoutId = workoutId,
            position = 0,
            exerciseId = "back-squat",
            sets = 3,
            reps = 5,
            durationSeconds = null,
            weightKg = 100.0,
            loadKind = "EXTERNAL",
            progressionPolicy = encoded
        )

        val exported = repository.export(appVersion = "dev", exportedAtMillis = 1L)
        repository.restore(exported)

        assertEquals(BACKUP_FORMAT_VERSION, exported.formatVersion)
        val restored = database.routineTemplateQueries.selectEntriesForWorkout(workoutId)
            .executeAsOne()
        assertEquals(encoded, restored.progressionPolicy)
    }

    @Test
    fun restoreClearsTargetExplanationsThePayloadDoesNotOwn() = runTest {
        // A restored plan keeps its stored id, so a leftover explanation row could attach to it.
        HistoricalDatabaseFixtures.exec(
            driver,
            "INSERT INTO planHistory(id, engineId, acceptedAt, weekNumber, cycleNumber) " +
                "VALUES (5, 'DETERMINISTIC', 1, 1, 1)"
        )
        HistoricalDatabaseFixtures.exec(
            driver,
            "INSERT INTO planTargetExplanationState VALUES " +
                "(5, 'DETERMINISTIC', 0, 0.7, 0.8, 1)"
        )
        HistoricalDatabaseFixtures.exec(
            driver,
            "INSERT INTO planTargetExplanation(planId, dayIndex, position, explanationVersion) " +
                "VALUES (5, 0, 0, 1)"
        )

        val exported = repository.export(appVersion = "dev", exportedAtMillis = 1L)
        repository.restore(
            exported.copy(
                planTargetExplanations = emptyList(),
                planTargetExplanationStates = emptyList()
            )
        )

        assertEquals(0L, HistoricalDatabaseFixtures.count(driver, "planTargetExplanation"))
        assertEquals(0L, HistoricalDatabaseFixtures.count(driver, "planTargetExplanationState"))
        // The plan itself is owned by the payload and is re-created from it.
        assertEquals(1L, HistoricalDatabaseFixtures.count(driver, "planHistory"))
    }

    @Test
    fun mergeKeysReadLiveIdentities() = runTest {
        seedRepresentativeData()
        database.bodyWeightQueries.insertMeasurement(7L, 80.0)
        database.personalRecordQueries.upsert("back-squat", 100.0, 5, 1L, "EXTERNAL")
        database.exercisePreferenceQueries.upsert("back-squat", "PREFER")
        database.exerciseExclusionQueries.upsert("back-squat", null)
        database.exerciseNoteQueries.upsert("back-squat", "n")

        val keys = repository.mergeKeys()

        assertEquals(setOf("s1"), keys.sessionIds)
        assertEquals(setOf(7L), keys.bodyWeightDays)
        assertEquals(setOf("back-squat"), keys.personalRecordExerciseIds)
        assertTrue(keys.customExerciseIds.contains("user-bicycle-crunch"))
        assertTrue(keys.customExerciseIds.contains("back-squat"))
        assertEquals(setOf("back-squat"), keys.exerciseOverrideIds)
        assertEquals(setOf("back-squat"), keys.exerciseNoteIds)
        assertEquals(setOf("back-squat"), keys.exercisePreferenceIds)
        assertEquals(setOf("back-squat"), keys.exerciseExclusionIds)
    }

    @Test
    fun mergeKeysOnAFreshInstallHoldOnlyTheSeededCatalog() = runTest {
        val keys = repository.mergeKeys()

        assertEquals(emptySet(), keys.sessionIds)
        assertEquals(emptySet(), keys.bodyWeightDays)
        assertEquals(emptySet(), keys.personalRecordExerciseIds)
        // Seeded catalog rows are local exercise ids too; no custom row exists yet.
        assertTrue(keys.customExerciseIds.contains("back-squat"))
        assertEquals(emptySet(), keys.exerciseOverrideIds)
        assertEquals(emptySet(), keys.exerciseNoteIds)
        assertEquals(emptySet(), keys.exercisePreferenceIds)
        assertEquals(emptySet(), keys.exerciseExclusionIds)
    }

    @Test
    fun mergeAppliesAdditiveSelection() = runTest {
        seedRepresentativeData()
        val setsBefore = HistoricalDatabaseFixtures.count(driver, "workoutSet")
        val exercisesBefore = HistoricalDatabaseFixtures.count(driver, "exercise")

        repository.merge(mergedSelection())

        assertEquals(setsBefore + 1, HistoricalDatabaseFixtures.count(driver, "workoutSet"))
        assertEquals(exercisesBefore + 1, HistoricalDatabaseFixtures.count(driver, "exercise"))
        assertEquals(
            "note",
            database.sessionNoteQueries.selectBySessionId("merged-s").executeAsOne().note
        )
        assertEquals(
            82.5,
            database.bodyWeightQueries.selectMeasurement(7L).executeAsOne().weightKg
        )
        assertEquals(
            8L,
            database.personalRecordQueries.selectById("user-merged").executeAsOne().reps
        )
        assertEquals(
            "PREFER",
            database.exercisePreferenceQueries.selectById("user-merged").executeAsOne()
        )
        // Existing rows stay untouched: the seeded session, its override and the set survive.
        assertNotNull(database.workoutSessionQueries.selectSessionById("s1").executeAsOneOrNull())
        assertEquals(
            "Back Squat (edited)",
            database.exerciseOverrideQueries.selectById("back-squat").executeAsOne().name
        )
        // The merge keys reflect the inserted rows, so re-merging the same selection adds nothing.
        assertTrue(repository.mergeKeys().sessionIds.contains("merged-s"))
        assertTrue(repository.mergeKeys().customExerciseIds.contains("user-merged"))
        // A merged set keeps its recorded target verbatim even though the merge drops scheduling links.
        assertEquals(
            MERGED_TARGET,
            database.workoutLogQueries.selectAllSets().executeAsList()
                .single { it.sessionId == "merged-s" }
                .prescriptionSnapshot
        )
    }

    @Test
    fun mergeRollsBackEntirelyOnConflict() = runTest {
        seedRepresentativeData()
        val setsBefore = HistoricalDatabaseFixtures.count(driver, "workoutSet")
        val exercisesBefore = HistoricalDatabaseFixtures.count(driver, "exercise")
        val conflicting = mergedSelection().copy(
            sessionsToInsert = listOf(BackupWorkoutSessionRecord("s1", 1L, 2L, 1L, null))
        )

        assertFailsWith<Exception> { repository.merge(conflicting) }

        assertEquals(setsBefore, HistoricalDatabaseFixtures.count(driver, "workoutSet"))
        assertEquals(exercisesBefore, HistoricalDatabaseFixtures.count(driver, "exercise"))
        assertEquals(0L, HistoricalDatabaseFixtures.count(driver, "sessionNote"))
        assertEquals(0L, HistoricalDatabaseFixtures.count(driver, "bodyWeightMeasurement"))
        assertNull(database.personalRecordQueries.selectById("user-merged").executeAsOneOrNull())
    }

    @Test
    fun mergeKeepsTheLocalRowWhenAFileCustomCollidesWithASeededId() = runTest {
        seedRepresentativeData()
        val exercisesBefore = HistoricalDatabaseFixtures.count(driver, "exercise")
        val file = repository.export(appVersion = "t", exportedAtMillis = 1L).copy(
            customExercises = listOf(
                BackupExerciseRecord(
                    id = "back-squat",
                    name = "Colliding custom",
                    requiredEquipment = "BARBELL",
                    movementPattern = "SQUAT",
                    isUnilateral = false,
                    loadCapability = "EXTERNAL",
                    involvements = null
                )
            )
        )

        val selection = BackupMergePolicy.select(file, repository.mergeKeys())
        repository.merge(selection)

        assertEquals(0, selection.customExercisesToInsert.size)
        assertEquals(exercisesBefore, HistoricalDatabaseFixtures.count(driver, "exercise"))
        assertEquals(0L, database.exerciseQueries.selectById("back-squat").executeAsOne().isCustom)
    }

    @Test
    fun exportsEveryIncludedSection() = runTest {
        seedRepresentativeData()
        val exerciseNotes = SqlDelightExerciseNoteRepository(database)
        exerciseNotes.save("back-squat", "Set up tall")
        exerciseNotes.clear("barbell-bench-press")
        val sessionNotes = SqlDelightSessionNoteRepository(database)
        sessionNotes.save("s1", "Session setup")
        SqlDelightGuidedWorkoutPreferenceRepository(database).setGuidedWorkoutEnabled(true)
        val restPreferences = SqlDelightRestPreferenceRepository(database)
        restPreferences.setGlobalDefaultSeconds(180L)
        restPreferences.setExerciseOverrideSeconds("back-squat", 240L)
        SqlDelightTrainingGuidancePreferenceRepository(database).apply {
            setRestCountdownEnabled(true)
            setRestCompletionSignalEnabled(true)
        }
        SqlDelightWorkoutReminderPreferenceRepository(database).apply {
            setEnabled(true)
            setReminderMinuteOfDay(390)
            setQuietHoursEnabled(true)
            setQuietHours(1320, 360)
        }

        val file = repository.export(appVersion = "0.5.0-dev", exportedAtMillis = 99L)

        assertEquals("0.5.0-dev", file.appVersion)
        assertEquals(99L, file.exportedAtMillis)
        assertTrue(file.catalog.seedExerciseIds.contains("back-squat"))
        assertTrue(file.catalog.seedProfiles.any { it.id == "back-squat" })
        assertEquals(listOf("user-bicycle-crunch"), file.customExercises.map { it.id })
        assertEquals("Back Squat (edited)", file.exerciseOverrides.single().name)
        assertTrue(file.equipment.any { it.id == "CUSTOM-1" })
        assertEquals(listOf("BARBELL"), file.selectedEquipment)
        assertEquals(1, file.workoutSets.size)
        assertEquals("EXTERNAL", file.workoutSets.single().loadKind)
        assertEquals("LIVE", file.workoutSets.single().timingProvenance)
        assertEquals(100L, file.workoutSets.single().startedAtElapsedMillis)
        assertEquals(150L, file.workoutSets.single().completedAtElapsedMillis)
        assertEquals("s1", file.workoutSets.single().sessionId)
        assertEquals(listOf("s1"), file.workoutSessions.map { it.id })
        assertEquals(
            listOf("s1" to "Session setup"),
            file.sessionNotes.map { it.sessionId to it.note }
        )
        assertEquals(1, file.plans.size)
        assertEquals(1, file.planDays.size)
        assertEquals(1, file.planEntries.size)
        assertEquals(1, file.volumeExplanations.size)
        assertEquals(1, file.volumeExplanationStates.size)
        assertEquals(1, file.planTargetExplanations.size)
        assertEquals(1, file.planTargetExplanationStates.size)
        assertEquals(
            setOf("ACTIVATION", "OCCURRENCE"),
            file.entryTargetExplanations.map { it.entryKind }.toSet()
        )
        assertEquals("APPLIED", file.planTargetExplanations.single().progressionOutcome)
        assertEquals(0.7, file.planTargetExplanationStates.single().deloadVolumeScale)
        assertEquals(1, file.routines.size)
        assertEquals(1, file.routineWorkouts.size)
        assertEquals(1, file.routineEntries.size)
        assertEquals(1, file.activations.size)
        assertEquals(1, file.activationWorkouts.size)
        assertEquals(1, file.activationEntries.size)
        assertEquals(1, file.occurrences.size)
        assertEquals(1, file.occurrenceEntries.size)
        assertNotNull(file.scheduleState)
        assertEquals(120.0, file.personalRecords.single().weightKg)
        assertEquals("PREFER", file.preferences.single().preference)
        assertEquals(1, file.exclusions.size)
        assertEquals(3, file.settings?.daysPerWeek)
        assertEquals("LB", file.settings?.weightUnit)
        assertEquals(true, file.settings?.guidedWorkoutEnabled)
        assertEquals(
            listOf(
                null to 180L,
                "back-squat" to 240L
            ),
            file.restPreferences.map { it.exerciseId to it.durationSeconds }
        )
        assertEquals(
            listOf("back-squat" to "Set up tall", "barbell-bench-press" to ""),
            file.exerciseNotes.map { it.exerciseId to it.note }
        )
        assertEquals(true, file.trainingGuidance.restCountdownEnabled)
        assertEquals(true, file.trainingGuidance.restCompletionSignalEnabled)
        assertEquals(true, file.workoutReminders?.remindersEnabled)
        assertEquals(390, file.workoutReminders?.reminderMinuteOfDay)
        assertEquals(true, file.workoutReminders?.quietHoursEnabled)
        assertEquals(1320, file.workoutReminders?.quietStartMinuteOfDay)
        assertEquals(360, file.workoutReminders?.quietEndMinuteOfDay)
    }

    @Test
    fun reminderPreferencesRoundTripAndALegacyPayloadClearsThem() = runTest {
        seedRepresentativeData()
        val reminders = SqlDelightWorkoutReminderPreferenceRepository(database)
        val guidance = SqlDelightTrainingGuidancePreferenceRepository(database)
        reminders.setEnabled(true)
        reminders.setReminderMinuteOfDay(390)
        reminders.setQuietHoursEnabled(true)
        reminders.setQuietHours(1320, 360)
        guidance.setRestCountdownEnabled(true)
        guidance.setRestCompletionSignalEnabled(true)

        val exported = repository.export(appVersion = "t", exportedAtMillis = 1L)

        // A payload from before format 12 carries no reminder section, so restore resets them.
        repository.restore(
            exported.copy(
                formatVersion = 11,
                equipmentProfiles = emptyList(),
                activeEquipmentProfileId = null,
                workoutReminders = null,
                trainingGuidance = exported.trainingGuidance.copy(
                    restCountdownEnabled = false,
                    restCompletionSignalEnabled = false
                )
            )
        )

        assertEquals(WorkoutReminderPreferences(), reminders.get())
        assertEquals(false, guidance.get().restCountdownEnabled)
        assertEquals(false, guidance.get().restCompletionSignalEnabled)

        repository.restore(exported)

        assertEquals(390, reminders.get().reminderMinuteOfDay)
        assertEquals(true, reminders.get().remindersEnabled)
        assertEquals(true, reminders.get().quietHoursEnabled)
        assertEquals(1320, reminders.get().quietStartMinuteOfDay)
        assertEquals(true, guidance.get().restCountdownEnabled)
        assertEquals(true, guidance.get().restCompletionSignalEnabled)
    }

    @Test
    fun restoreDropsNotesThePayloadDoesNotOwnAndRecreatesItsOwn() = runTest {
        seedRepresentativeData()
        val exerciseNotes = SqlDelightExerciseNoteRepository(database)
        exerciseNotes.save("back-squat", "Set up tall")
        exerciseNotes.clear("barbell-bench-press")
        val file = repository.export(appVersion = "t", exportedAtMillis = 1L)
        // A note absent from the restored payload must not survive the whole-replacement restore.
        database.exerciseNoteQueries.upsert("barbell-bench-press", "stale")

        repository.restore(file.copy(exerciseNotes = emptyList()))

        assertNull(database.exerciseNoteQueries.selectById("back-squat").executeAsOneOrNull())
        assertNull(
            database.exerciseNoteQueries.selectById("barbell-bench-press").executeAsOneOrNull()
        )

        repository.restore(file)

        assertEquals(
            "Set up tall",
            database.exerciseNoteQueries.selectById("back-squat").executeAsOne().note
        )
        assertEquals("", exerciseNotes.note("barbell-bench-press"))
    }

    @Test
    fun sessionNotesRoundTripExplicitClearAndWholeReplacementCleanup() = runTest {
        seedRepresentativeData()
        val notes = SqlDelightSessionNoteRepository(database)
        notes.save("s1", "Session context")
        val exported = repository.export("test", 1L)
        val emptyPayload = exported.copy(sessionNotes = emptyList())

        repository.restore(emptyPayload)
        assertNull(notes.note("s1"))
        repository.restore(exported)
        assertEquals("Session context", notes.note("s1"))
        // The restore path rebuilds the folded search projection, not just the note text.
        assertEquals(
            unicodeCaseFold("Session context"),
            database.sessionNoteQueries.selectAll().executeAsList()
                .single { it.sessionId == "s1" }.searchNote
        )

        notes.clear("s1")
        val explicitlyCleared = repository.export("test", 2L)
        val decoded = BackupJson.decode(BackupJson.encode(explicitlyCleared))
        repository.restore(decoded)
        assertEquals("", notes.note("s1"))
    }

    @Test
    fun warmupPreparationOverridesRoundTripAndWholeReplacementDropsThem() = runTest {
        val overrides = SqlDelightWarmupPreparationOverrideRepository(database)
        overrides.set(WarmupPreparationItem(WarmupAction.LOWER_BODY_PRACTICE, reps = 15))
        overrides.set(
            WarmupPreparationItem(WarmupAction.EASY_GENERAL_MOVEMENT, durationSeconds = 300)
        )

        val exported = repository.export("test", 1L)
        assertEquals(BACKUP_FORMAT_VERSION, exported.formatVersion)
        assertEquals(
            setOf(WarmupAction.LOWER_BODY_PRACTICE.name, WarmupAction.EASY_GENERAL_MOVEMENT.name),
            exported.warmupPreparationOverrides.map { it.action }.toSet()
        )

        // A payload that carries no overrides whole-replaces the store, and re-restoring the exported
        // file brings the persisted measure back.
        repository.restore(exported.copy(warmupPreparationOverrides = emptyList()))
        assertEquals(emptyMap(), overrides.observeAll().first())

        repository.restore(exported)
        assertEquals(
            WarmupPreparationItem(WarmupAction.LOWER_BODY_PRACTICE, reps = 15),
            overrides.observeAll().first()[WarmupAction.LOWER_BODY_PRACTICE]
        )

        // A file from before the section restores with no overrides rather than the current ones.
        repository.restore(
            exported.copy(
                formatVersion = WARMUP_PREPARATION_OVERRIDE_FORMAT_VERSION - 1,
                warmupPreparationOverrides = emptyList()
            )
        )
        assertEquals(emptyMap(), overrides.observeAll().first())
    }

    @Test
    fun failedSessionNoteReplacementRollsBackSessionsAndSets() = runTest {
        seedRepresentativeData()
        val notes = SqlDelightSessionNoteRepository(database)
        notes.save("s1", "Keep this note")
        val before = repository.export("test", 1L)
        driver.execute(
            null,
            "CREATE TRIGGER fail_restored_session_note BEFORE INSERT ON sessionNote " +
                "BEGIN SELECT RAISE(ABORT, 'test failure'); END",
            0
        )

        assertFailsWith<Exception> { repository.restore(before) }

        assertEquals("Keep this note", notes.note("s1"))
        assertEquals(
            before.workoutSessions.map { it.id },
            database.workoutSessionQueries.selectAllSessions().executeAsList().map { it.id }
        )
        assertEquals(
            before.workoutSets.map { it.id },
            database.workoutLogQueries.selectAllSets().executeAsList().map { it.id }
        )
    }

    @Test
    fun exportRoundTripsThroughJsonUnchanged() = runTest {
        database.exerciseQueries.insertCustom(
            id = "user-my-thing",
            name = "My Thing",
            requiredEquipment = "BARBELL",
            movementPattern = "HINGE",
            isUnilateral = 1,
            loadCapability = "EXTERNAL",
            involvements = "LOWER_BACK:1.0"
        )
        val file = repository.export(appVersion = "test", exportedAtMillis = 1L)

        val decoded = BackupJson.decode(BackupJson.encode(file))

        assertEquals(file, decoded)
    }

    @Test
    fun restoreReplacesIncludedDataAndPreservesStoredIds() = runTest {
        seedRepresentativeData()
        val restPreferences = SqlDelightRestPreferenceRepository(database)
        restPreferences.setGlobalDefaultSeconds(180L)
        restPreferences.setExerciseOverrideSeconds("back-squat", 240L)
        val guidance = SqlDelightTrainingGuidancePreferenceRepository(database)
        guidance.setRestAlertsEnabled(true)
        guidance.setShortRestCoachingEnabled(true)
        val file = repository.export(appVersion = "t", exportedAtMillis = 1L)

        // Remove some data and add a value the restore must replace.
        database.workoutLogQueries.deleteAllSets()
        database.exerciseQueries.deleteAllCustom()
        database.exercisePreferenceQueries.upsert("back-squat", "PREFER_LESS")
        restPreferences.setGlobalDefaultSeconds(300L)
        guidance.setRestAlertsEnabled(false)
        guidance.setShortRestCoachingEnabled(false)
        restPreferences.setExerciseOverrideSeconds("bench-press", 90L)
        val countdownRepository = SqlDelightRestCountdownRepository(database)
        countdownRepository.save(
            com.hydrafit.app.core.domain.workout.PersistedRestCountdown(
                deadlineElapsedMillis = 500_000L,
                durationMillis = 120_000L,
                bootIdentity = "boot-test",
                sessionId = "s1",
                occurrenceId = 1L,
                exerciseId = "back-squat"
            )
        )

        repository.restore(file)

        assertEquals(file, repository.export(appVersion = "t", exportedAtMillis = 1L))
        assertNull(countdownRepository.load())
    }

    @Test
    fun recordedTargetsSurviveReplaceRestore() = runTest {
        seedRepresentativeData()
        database.workoutSessionQueries.insertSession("target-s", 30, 40, 2)
        database.workoutLogQueries.insertSetWithTimingProvenance(
            exerciseId = "back-squat",
            reps = 5,
            weightKg = 100.0,
            performedAt = 35,
            isWarmup = 0,
            involvements = "QUADS:1.0",
            weekNumber = null,
            cycleNumber = null,
            dayIndex = null,
            rir = null,
            sessionId = "target-s",
            loadKind = "EXTERNAL",
            timingProvenance = "LIVE",
            startedAtElapsedMillis = null,
            completedAtElapsedMillis = null,
            durationSeconds = null,
            prescriptionSnapshot = MERGED_TARGET
        )
        val file = repository.export(appVersion = "t", exportedAtMillis = 1L)
        BackupValidator(SqlDelightBackupCatalog()).validate(file)
        database.workoutLogQueries.deleteAllSets()

        repository.restore(file)

        val restored = database.workoutLogQueries.selectAllSets().executeAsList()
            .single { it.sessionId == "target-s" }
        assertEquals(MERGED_TARGET, restored.prescriptionSnapshot)
        assertEquals(
            MERGED_TARGET,
            repository.export("t", 1L).workoutSets.single { it.sessionId == "target-s" }
                .prescriptionSnapshot
        )
    }

    @Test
    fun repeatedRestoreIsStable() = runTest {
        seedRepresentativeData()
        val file = repository.export(appVersion = "t", exportedAtMillis = 1L)

        repository.restore(file)
        repository.restore(file)

        assertEquals(file, repository.export(appVersion = "t", exportedAtMillis = 1L))
    }

    @Test
    fun roundTripsATimedPlanEntryMeasure() = runTest {
        seedRepresentativeData()
        val dayId = database.planHistoryQueries.selectAllDays().executeAsOne().id
        database.planHistoryQueries.insertEntry(
            dayId = dayId,
            position = 1,
            exerciseId = "back-squat",
            sets = 3,
            reps = null,
            durationSeconds = 45,
            exerciseName = "Plank",
            movementPattern = "CORE",
            suggestedWeightKg = null,
            loadCapability = "BODYWEIGHT_ONLY",
            loadKind = "BODYWEIGHT",
            progressionPolicy = null
        )

        val file = repository.export(appVersion = "t", exportedAtMillis = 1L)

        val timed = file.planEntries.first { it.durationSeconds != null }
        assertNull(timed.reps)
        assertEquals(45, timed.durationSeconds)
        assertEquals(BACKUP_FORMAT_VERSION, file.formatVersion)

        repository.restore(file)

        // A restore rebuilds the same timed slot: NULL reps with its stored seconds.
        assertEquals(file, repository.export(appVersion = "t", exportedAtMillis = 1L))
    }

    @Test
    fun exactPre081CatalogProfilesRestoreWithoutChangingOwnedData() = runTest {
        seedRepresentativeData()
        val before = repository.export(appVersion = "t", exportedAtMillis = 1L)
        val previous = mapOf(
            "upright-barbell-row" to
                "BARBELL|HORIZONTAL_PULL|false|EXTERNAL|FRONT_DELTS:0.7,SIDE_DELTS:0.7,UPPER_BACK:0.5",
            "cable-deadlifts" to
                "CABLE_MACHINE|HINGE|false|EXTERNAL|" +
                "FOREARMS:0.3,GLUTES:0.5,HAMSTRINGS:0.5,LOWER_BACK:0.3,QUADS:1.0"
        )
        val historical = before.copy(
            catalog = before.catalog.copy(
                seedProfiles = before.catalog.seedProfiles.map { profile ->
                    profile.copy(profile = previous[profile.id] ?: profile.profile)
                }
            )
        )
        val text = BackupJson.encode(historical)
        val preview = PreviewBackupUseCase(BackupValidator(SqlDelightBackupCatalog()))
        val staging = SqlDelightBackupStagingRepository(database)
        RestoreBackupUseCase(preview, staging)(text, "t", 1L)
        ApplyStagedBackupUseCase(preview, repository, staging, TimeProvider { 2L })()
        SeedExerciseCatalog(database).seed()

        assertEquals(before, repository.export(appVersion = "t", exportedAtMillis = 1L))
        assertEquals(historical, BackupJson.decode(text))
        assertNull(staging.applyError())
        val changed = historical.copy(
            catalog = historical.catalog.copy(
                seedProfiles = historical.catalog.seedProfiles.map { profile ->
                    if (profile.id == "cable-deadlifts") {
                        profile.copy(profile = profile.profile + " ")
                    } else {
                        profile
                    }
                }
            )
        )
        assertFailsWith<com.hydrafit.app.core.domain.backup.BackupException> {
            RestoreBackupUseCase(preview, staging)(BackupJson.encode(changed), "t", 3L)
        }
        assertNull(staging.staged())
        assertEquals(before, repository.export(appVersion = "t", exportedAtMillis = 1L))
    }

    @Test
    fun exportStageApplyRoundTripsEverySection() = runTest {
        seedRepresentativeData()
        val catalog = SqlDelightBackupCatalog()
        val preview = PreviewBackupUseCase(BackupValidator(catalog))
        val staging = SqlDelightBackupStagingRepository(database)
        val backupRepository = SqlDelightBackupRepository(database, catalog)
        val stageRestore = RestoreBackupUseCase(preview, staging)
        val applyStaged =
            ApplyStagedBackupUseCase(preview, backupRepository, staging, TimeProvider { 0L })

        val exported = backupRepository.export(appVersion = "t", exportedAtMillis = 1L)
        stageRestore(BackupJson.encode(exported), "t", 1L)
        applyStaged()

        // Validation accepted every section, and the staged apply restored the identical snapshot.
        assertEquals(exported, backupRepository.export(appVersion = "t", exportedAtMillis = 1L))
    }

    @Test
    fun restoreMaterializesDefaultSettingsWhenThePayloadHasNone() = runTest {
        seedRepresentativeData()
        val file = repository.export(appVersion = "t", exportedAtMillis = 1L).copy(settings = null)
        database.plannerEngineQueries.updateDaysPerWeek(2)
        database.plannerEngineQueries.updateWeightUnit("LB")

        repository.restore(file)

        assertEquals("DETERMINISTIC", database.plannerEngineQueries.selectEngine().executeAsOne())
        assertEquals(4L, database.plannerEngineQueries.selectDaysPerWeek().executeAsOne())
        assertEquals("BALANCED", database.plannerEngineQueries.selectTrainingGoal().executeAsOne())
        assertEquals("KG", database.plannerEngineQueries.selectWeightUnit().executeAsOne())
        assertEquals(
            0L,
            database.plannerEngineQueries.selectGuidedWorkoutEnabled().executeAsOne()
        )
    }

    @Test
    fun restoreOfVersionOneSettingsKeepsGuidedWorkoutsDisabled() = runTest {
        seedRepresentativeData()
        val exported = repository.export(appVersion = "t", exportedAtMillis = 1L)
        val file = exported.copy(
            formatVersion = 1,
            equipmentProfiles = emptyList(),
            activeEquipmentProfileId = null,
            settings = exported.settings?.copy(guidedWorkoutEnabled = false)
        )
        database.plannerEngineQueries.updateGuidedWorkoutEnabled(1L)

        repository.restore(file)

        assertEquals(
            0L,
            database.plannerEngineQueries.selectGuidedWorkoutEnabled().executeAsOne()
        )
    }

    @Test
    fun restoreKeepsABuiltInEquipmentLimitThePayloadOmits() = runTest {
        seedRepresentativeData()
        val file = repository.export(appVersion = "t", exportedAtMillis = 1L)
            .copy(
                formatVersion = 13,
                equipment = emptyList(),
                equipmentProfiles = emptyList(),
                activeEquipmentProfileId = null
            )
        database.equipmentQueries.updateMaxWeight(999.0, "BARBELL")

        repository.restore(file)

        assertEquals(
            999.0,
            database.equipmentQueries.selectAll().executeAsList()
                .first { it.id == "BARBELL" }.maxWeightKg
        )
    }

    @Test
    fun restoreDropsUserEquipmentThePayloadOmits() = runTest {
        seedRepresentativeData()
        val file = repository.export(appVersion = "t", exportedAtMillis = 1L)
        database.equipmentQueries.insert("CUSTOM-2", "Extra", 0)

        repository.restore(file)

        val restored = database.equipmentQueries.selectAll().executeAsList()
        assertNull(restored.firstOrNull { it.id == "CUSTOM-2" })
    }

    @Test
    fun restoreRollsBackWhenAWriteFails() = runTest {
        seedRepresentativeData()
        val before = repository.export(appVersion = "t", exportedAtMillis = 1L)
        driver.execute(
            identifier = null,
            sql = "CREATE TRIGGER fail_set_insert BEFORE INSERT ON workoutSet " +
                "BEGIN SELECT RAISE(ABORT, 'boom'); END",
            parameters = 0
        )

        assertFailsWith<Exception> { repository.restore(before) }

        assertEquals(before, repository.export(appVersion = "t", exportedAtMillis = 1L))
    }

    @Test
    fun invalidStagedExerciseNoteDoesNotWriteAnything() = runTest {
        seedRepresentativeData()
        val before = repository.export(appVersion = "t", exportedAtMillis = 1L)
        val catalog = SqlDelightBackupCatalog()
        val staging = SqlDelightBackupStagingRepository(database)
        val preview = PreviewBackupUseCase(BackupValidator(catalog))
        val applyStaged =
            ApplyStagedBackupUseCase(preview, repository, staging, TimeProvider { 2L })
        val invalid = before.copy(
            exerciseNotes = listOf(
                com.hydrafit.app.core.domain.backup.BackupExerciseNoteRecord(
                    "back-squat",
                    "x".repeat(com.hydrafit.app.core.domain.workout.NOTE_MAX_LENGTH + 1)
                )
            )
        )
        staging.stage(BackupJson.encode(invalid), "t", 1L)

        applyStaged()

        assertEquals(before, repository.export(appVersion = "t", exportedAtMillis = 1L))
        assertNull(staging.staged())
        assertEquals(
            com.hydrafit.app.core.domain.backup.BackupFailure.INVALID_VALUE,
            staging.applyError()?.failure
        )
    }

    @Test
    fun invalidStagedSessionNoteDoesNotWriteAnything() = runTest {
        seedRepresentativeData()
        val before = repository.export(appVersion = "t", exportedAtMillis = 1L)
        val catalog = SqlDelightBackupCatalog()
        val staging = SqlDelightBackupStagingRepository(database)
        val preview = PreviewBackupUseCase(BackupValidator(catalog))
        val applyStaged =
            ApplyStagedBackupUseCase(preview, repository, staging, TimeProvider { 2L })
        val invalid = before.copy(
            sessionNotes = listOf(
                com.hydrafit.app.core.domain.backup.BackupSessionNoteRecord(
                    "s1",
                    "x".repeat(com.hydrafit.app.core.domain.workout.NOTE_MAX_LENGTH + 1)
                )
            )
        )
        staging.stage(BackupJson.encode(invalid), "t", 1L)

        applyStaged()

        assertEquals(before, repository.export(appVersion = "t", exportedAtMillis = 1L))
        assertNull(staging.staged())
        assertEquals(
            com.hydrafit.app.core.domain.backup.BackupFailure.INVALID_VALUE,
            staging.applyError()?.failure
        )
    }

    @Test
    fun restoredCustomSurvivesStartupSeedingAndDedupe() = runTest {
        seedRepresentativeData()
        val file = repository.export(appVersion = "t", exportedAtMillis = 1L)
        database.exerciseQueries.deleteAllCustom()

        repository.restore(file)
        SeedExerciseCatalog(database).seed()
        CustomExerciseDedupe(database).run()

        assertEquals(file, repository.export(appVersion = "t", exportedAtMillis = 1L))
        assertTrue(
            database.exerciseQueries.selectById("user-bicycle-crunch").executeAsOneOrNull() != null
        )
        // The restore path rebuilds the custom exercise's folded name projection.
        assertEquals(
            unicodeCaseFold("Bicycle Crunch"),
            database.exerciseQueries.selectById("user-bicycle-crunch").executeAsOne().searchName
        )
    }

    @Test
    fun startupRepairPreservesAllUserReferencesAndOnlyMovesEquipmentIdentities() = runTest {
        seedEquipmentCollisionData()
        val before = repository.export("t", 1L)

        SeedEquipmentCatalog(database).seed()

        val after = repository.export("t", 1L)
        val custom = after.equipment.single { it.id == "EZ_BAR_3" }
        assertEquals(false, custom.isBuiltIn)
        assertEquals(35.0, custom.maxWeightKg)
        assertEquals("EZ bar", custom.name)
        assertEquals(true, after.equipment.single { it.id == "EZ_BAR" }.isBuiltIn)
        assertEquals(null, after.equipment.single { it.id == "EZ_BAR" }.maxWeightKg)
        assertTrue("EZ_BAR_3" in after.selectedEquipment)
        assertTrue("EZ_BAR" !in after.selectedEquipment)
        assertEquals("EZ_BAR_3,BARBELL", after.customExercises.single().requiredEquipment)
        assertEquals("EZ_BAR_3", after.exerciseOverrides.single().requiredEquipment)
        assertEquals("EZ_BAR_3", after.activationEntries.single().requiredEquipment)
        assertEquals("EZ_BAR_3", after.occurrenceEntries.single().requiredEquipment)
        val selected = after.selectedEquipment.map { EquipmentTag(it) }.toSet()
        val effectiveCatalog = SqlDelightExerciseCatalog(database).all()
        val effectiveSquat = effectiveCatalog.single { it.id == "back-squat" }
        val effectiveCustom = effectiveCatalog.single { it.id == "user-bicycle-crunch" }
        assertTrue(effectiveSquat.isAvailableWith(selected))
        assertTrue(effectiveCustom.isAvailableWith(selected))
        assertEquals(
            setOf(EquipmentTag("EZ_BAR_3")),
            effectiveSquat.requiredEquipment
        )
        assertEquals(before.workoutSets, after.workoutSets)
        assertEquals(before.workoutSessions, after.workoutSessions)
        assertEquals(before.plans, after.plans)
        assertEquals(before.routines, after.routines)
        assertEquals(before.entryTargetExplanations, after.entryTargetExplanations)
        assertEquals(
            "BARBELL",
            database.exerciseQueries.selectById("overhead-press").executeAsOne().requiredEquipment
        )
        BackupValidator(SqlDelightBackupCatalog()).validate(after)
        SeedEquipmentCatalog(database).seed()
        assertEquals(after, repository.export("t", 1L))
    }

    @Test
    fun legacyCollisionExportCanPreviewStageApplyAndReExportWithoutLosingData() = runTest {
        seedEquipmentCollisionData()
        val original = repository.export("t", 1L)
        val text = BackupJson.encode(original)
        val preview = PreviewBackupUseCase(BackupValidator(SqlDelightBackupCatalog()))
        val repaired = preview(text)
        val staging = SqlDelightBackupStagingRepository(database)
        RestoreBackupUseCase(preview, staging)(text, "t", 1L)
        ApplyStagedBackupUseCase(preview, repository, staging, TimeProvider { 2L })()

        assertNull(staging.applyError())
        assertNull(staging.staged())
        val restored = repository.export("t", 1L)
        assertEquals(repaired.copy(equipment = emptyList()), restored.copy(equipment = emptyList()))
        assertEquals(
            repaired.equipment.sortedBy { it.id },
            restored.equipment.filter { it.id != "EZ_BAR" }.sortedBy { it.id }
        )
        assertEquals(true, restored.equipment.single { it.id == "EZ_BAR" }.isBuiltIn)
        assertEquals(restored, preview(BackupJson.encode(restored)))
        assertEquals("EZ_BAR", original.equipment.single { it.id == "EZ_BAR" }.id)
    }

    @Test
    fun seedCollisionRepairRollsBackAllReferencesWhenTheTransactionFails() {
        seedEquipmentCollisionData()
        val originalEquipment = database.equipmentQueries.selectAll().executeAsList()
        val originalSelected = database.userEquipmentQueries.selectAllSelected().executeAsList()
        val originalCustom = database.exerciseQueries.selectAll().executeAsList()
        val originalOverrides = database.exerciseOverrideQueries.selectAll().executeAsList()
        HistoricalDatabaseFixtures.exec(
            driver,
            "CREATE TRIGGER reject_equipment_repair " +
                "BEFORE UPDATE OF requiredEquipment ON occurrenceEntry " +
                "BEGIN SELECT RAISE(ABORT, 'injected repair failure'); END"
        )

        assertFailsWith<Exception> { SeedEquipmentCatalog(database).seed() }

        assertEquals(originalEquipment, database.equipmentQueries.selectAll().executeAsList())
        assertEquals(
            originalSelected,
            database.userEquipmentQueries.selectAllSelected().executeAsList()
        )
        assertEquals(originalCustom, database.exerciseQueries.selectAll().executeAsList())
        assertEquals(
            originalOverrides,
            database.exerciseOverrideQueries.selectAll().executeAsList()
        )
        assertEquals(
            "EZ_BAR",
            database.trainingScheduleQueries.selectAllActivationEntries()
                .executeAsOne().requiredEquipment
        )
        assertEquals(
            "EZ_BAR",
            database.trainingScheduleQueries.selectAllOccurrenceEntries()
                .executeAsOne().requiredEquipment
        )
    }

    @Test
    fun customCreationCannotClaimAnUnseededBuiltInId() = runTest {
        database.equipmentQueries.deleteById("TRAP_BAR")
        val custom = SqlDelightEquipmentRepository(database).add("Trap bar")
        assertEquals("TRAP_BAR_2", custom.id.id)
        assertEquals(false, custom.isBuiltIn)
        SeedEquipmentCatalog(database).seed()
        assertEquals(1L, database.equipmentQueries.selectById("TRAP_BAR").executeAsOne().isBuiltIn)
        assertEquals(
            0L,
            database.equipmentQueries.selectById("TRAP_BAR_2").executeAsOne().isBuiltIn
        )
    }

    @Test
    fun restoreCreatesMissingCanonicalRowsBeforeApplyingTheirCeilings() = runTest {
        val file = repository.export("t", 1L).let { exported ->
            exported.copy(
                equipment = exported.equipment.map {
                    if (it.id == "EZ_BAR") it.copy(maxWeightKg = 42.0) else it
                }
            )
        }
        database.equipmentQueries.deleteById("EZ_BAR")
        repository.restore(file)
        assertEquals(
            42.0,
            database.equipmentQueries.selectById("EZ_BAR").executeAsOne().maxWeightKg
        )
    }

    private fun seedEquipmentCollisionData() {
        seedRepresentativeData()
        HistoricalDatabaseFixtures.exec(
            driver,
            "UPDATE equipment SET isBuiltIn = 0, maxWeightKg = 35 WHERE id = 'EZ_BAR'"
        )
        database.equipmentQueries.insert("EZ_BAR_2", "Other curved bar", 0L)
        database.userEquipmentQueries.insertSelected("EZ_BAR")
        database.equipmentProfileQueries.upsertEntry(1L, "EZ_BAR", 1L, 35.0)
        database.exerciseQueries.remapCustomEquipment("EZ_BAR,BARBELL", "user-bicycle-crunch")
        database.exerciseOverrideQueries.remapEquipment("EZ_BAR", "back-squat")
        database.trainingScheduleQueries.selectAllActivationEntries().executeAsList().forEach {
            database.trainingScheduleQueries.remapActivationEquipment("EZ_BAR", it.id)
        }
        database.trainingScheduleQueries.selectAllOccurrenceEntries().executeAsList().forEach {
            database.trainingScheduleQueries.remapOccurrenceEquipment("EZ_BAR", it.id)
        }
    }

    @Test
    fun bodyWeightRoundTripsAndLegacyFilesReplaceItWithNothing() = runTest {
        seedRepresentativeData()
        database.bodyWeightQueries.insertMeasurement(19_998L, 82.5)
        database.bodyWeightQueries.insertMeasurement(19_999L, 82.25)
        database.bodyWeightQueries.upsertGoal(78.0)
        val catalog = SqlDelightBackupCatalog()
        val preview = PreviewBackupUseCase(BackupValidator(catalog))
        val staging = SqlDelightBackupStagingRepository(database)
        val exported = repository.export("t", 1L)
        BackupValidator(catalog).validate(exported)
        assertEquals(2, exported.bodyWeightMeasurements.size)
        assertEquals(78.0, exported.bodyWeightGoalKg)

        // A legacy (format 12) file carries no body-weight section, and whole replacement clears it.
        val legacy = exported.copy(
            formatVersion = 12,
            equipmentProfiles = emptyList(),
            activeEquipmentProfileId = null,
            bodyWeightMeasurements = emptyList(),
            bodyWeightGoalKg = null
        )
        RestoreBackupUseCase(preview, staging)(BackupJson.encode(legacy), "t", 1L)
        ApplyStagedBackupUseCase(preview, repository, staging, TimeProvider { 0L })()
        assertTrue(database.bodyWeightQueries.selectAllMeasurements().executeAsList().isEmpty())
        assertNull(database.bodyWeightQueries.selectGoal().executeAsOneOrNull())

        // Re-adding and round-tripping through stage/apply preserves the exact rows and goal.
        database.bodyWeightQueries.insertMeasurement(19_998L, 82.5)
        database.bodyWeightQueries.upsertGoal(78.0)
        val second = repository.export("t", 1L)
        RestoreBackupUseCase(preview, staging)(BackupJson.encode(second), "t", 1L)
        ApplyStagedBackupUseCase(preview, repository, staging, TimeProvider { 0L })()
        assertEquals(1, second.bodyWeightMeasurements.size)
        assertEquals(second, repository.export("t", 1L))
    }

    @Test
    fun timedSetsRoundTripThroughBackupAndInvalidMeasurementShapesAreRejected() = runTest {
        seedRepresentativeData()
        database.workoutLogQueries.insertSetWithTimingProvenance(
            exerciseId = "back-squat",
            reps = null,
            weightKg = null,
            performedAt = 20,
            isWarmup = 0,
            involvements = "QUADS:1.0",
            weekNumber = null,
            cycleNumber = null,
            dayIndex = null,
            rir = null,
            sessionId = "s1",
            loadKind = "BODYWEIGHT",
            timingProvenance = "LIVE",
            startedAtElapsedMillis = null,
            completedAtElapsedMillis = null,
            durationSeconds = 90,
            prescriptionSnapshot = null
        )
        val timedSetId = database.workoutLogQueries.selectAllSets().executeAsList()
            .first { it.durationSeconds != null }
            .id
        val catalog = SqlDelightBackupCatalog()
        val exported = repository.export("t", 1L)
        BackupValidator(catalog).validate(exported)
        val timedRecord = exported.workoutSets.first { it.durationSeconds != null }
        assertNull(timedRecord.reps)
        assertEquals(90, timedRecord.durationSeconds)

        // The real restore path writes the stored shape back: NULL reps with the recorded seconds.
        repository.restore(exported)
        val restored = database.workoutLogQueries.selectAllSets().executeAsList()
            .first { it.durationSeconds != null }
        assertEquals(timedSetId, restored.id)
        assertNull(restored.reps)
        assertEquals(90L, restored.durationSeconds)

        // A legacy (format 16) payload is reps-only and must not gain a duration.
        val legacy = exported.copy(
            formatVersion = 16,
            workoutSets = exported.workoutSets.map { set ->
                if (set.durationSeconds != null) {
                    set.copy(reps = 30, durationSeconds = null)
                } else {
                    set
                }
            }
        )
        BackupValidator(catalog).validate(legacy)
        repository.restore(legacy)
        assertTrue(
            database.workoutLogQueries.selectAllSets().executeAsList()
                .none { it.durationSeconds != null }
        )

        // A set measured by both reps and seconds, or by neither, is rejected.
        val both = exported.copy(
            workoutSets = exported.workoutSets.map { it.copy(reps = 5, durationSeconds = 90) }
        )
        assertEquals(
            BackupFailure.INVALID_VALUE,
            assertFailsWith<BackupException> { BackupValidator(catalog).validate(both) }.failure
        )
        val neither = exported.copy(
            workoutSets = exported.workoutSets.map { it.copy(reps = null, durationSeconds = null) }
        )
        assertEquals(
            BackupFailure.INVALID_VALUE,
            assertFailsWith<BackupException> { BackupValidator(catalog).validate(neither) }.failure
        )
    }

    @Test
    fun timedPrescriptionsRoundTripThroughBackupWithAnActiveOccurrence() = runTest {
        seedRepresentativeData()
        listOf("routineEntry", "activationEntry", "occurrenceEntry").forEach { table ->
            HistoricalDatabaseFixtures.exec(
                driver,
                "UPDATE $table SET reps = NULL, durationSeconds = 90 WHERE position = 0"
            )
        }

        val catalog = SqlDelightBackupCatalog()
        val exported = repository.export("t", 1L)
        assertEquals(BACKUP_FORMAT_VERSION, exported.formatVersion)
        BackupValidator(catalog).validate(exported)

        val routineEntry = exported.routineEntries.single()
        assertNull(routineEntry.reps)
        assertEquals(90, routineEntry.durationSeconds)
        val activationEntry = exported.activationEntries.single()
        assertNull(activationEntry.reps)
        assertEquals(90, activationEntry.durationSeconds)
        val occurrenceEntry = exported.occurrenceEntries.single()
        assertNull(occurrenceEntry.reps)
        assertEquals(90, occurrenceEntry.durationSeconds)

        // The real restore path writes the stored shape back: NULL reps with the recorded seconds.
        repository.restore(exported)
        val storedRoutine = database.routineTemplateQueries.selectAllEntries().executeAsList()
            .single()
        assertNull(storedRoutine.reps)
        assertEquals(90L, storedRoutine.durationSeconds)
        val storedActivation = database.trainingScheduleQueries.selectAllActivationEntries()
            .executeAsList().single()
        assertNull(storedActivation.reps)
        assertEquals(90L, storedActivation.durationSeconds)
        val storedOccurrence = database.trainingScheduleQueries.selectAllOccurrenceEntries()
            .executeAsList().single()
        assertNull(storedOccurrence.reps)
        assertEquals(90L, storedOccurrence.durationSeconds)
    }

    private fun mergedSelection(): BackupMergeSelection = BackupMergeSelection(
        customExercisesToInsert = listOf(
            BackupExerciseRecord(
                id = "user-merged",
                name = "Merged Exercise",
                requiredEquipment = "BARBELL",
                movementPattern = "SQUAT",
                isUnilateral = false,
                loadCapability = "EXTERNAL",
                involvements = null
            )
        ),
        sessionsToInsert = listOf(
            BackupWorkoutSessionRecord(
                id = "merged-s",
                startedAtMillis = 30L,
                endedAtMillis = 40L,
                localEpochDay = 2L,
                occurrenceId = null
            )
        ),
        sessionNotesToInsert = listOf(BackupSessionNoteRecord("merged-s", "note")),
        setsToInsert = listOf(
            BackupMergedSet(
                exerciseId = "back-squat",
                reps = 5,
                weightKg = 90.0,
                performedAtMillis = 35L,
                isWarmup = false,
                involvements = null,
                weekNumber = null,
                cycleNumber = null,
                dayIndex = null,
                rir = null,
                sessionId = "merged-s",
                loadKind = "EXTERNAL",
                timingProvenance = "UNKNOWN",
                startedAtElapsedMillis = null,
                completedAtElapsedMillis = null,
                durationSeconds = null,
                prescriptionSnapshot = MERGED_TARGET
            )
        ),
        bodyWeightToInsert = listOf(BackupBodyWeightRecord(7L, 82.5)),
        personalRecordsToInsert = listOf(
            BackupPersonalRecordRecord("user-merged", 50.0, 8, 60L, "EXTERNAL")
        ),
        preferencesToInsert = listOf(BackupPreferenceRecord("user-merged", "PREFER")),
        exclusionsToInsert = listOf(BackupExclusionRecord("user-merged", null)),
        exerciseNotesToInsert = listOf(BackupExerciseNoteRecord("user-merged", "n")),
        overridesToInsert = listOf(
            BackupExerciseOverrideRecord("user-merged", "Merged", null, null, null, null, null)
        ),
        keptLocalSessions = 0,
        keptLocalRows = 0,
        skippedOpenSessions = 0,
        skippedUnattachedSets = 0,
        setsWithDroppedOccurrenceLinks = 0,
        excluded = emptyList()
    )

    private fun seedRepresentativeData() {
        database.exerciseQueries.insertCustom(
            id = "user-bicycle-crunch",
            name = "Bicycle Crunch",
            requiredEquipment = "BODYWEIGHT",
            movementPattern = "CORE",
            isUnilateral = 0,
            loadCapability = "BODYWEIGHT_ONLY",
            involvements = "ABS:0.9,OBLIQUES:0.4"
        )
        database.exerciseOverrideQueries.upsert(
            exerciseId = "back-squat",
            name = "Back Squat (edited)",
            requiredEquipment = null,
            movementPattern = null,
            isUnilateral = null,
            loadCapability = null,
            involvements = null
        )
        database.userEquipmentQueries.insertSelected("BARBELL")
        database.equipmentProfileQueries.upsertEntry(1L, "BARBELL", 1L, null)
        database.workoutSessionQueries.insertSession("s1", 10, 20, 1)
        database.workoutLogQueries.insertSetWithTimingProvenance(
            exerciseId = "back-squat",
            reps = 5,
            weightKg = 100.0,
            performedAt = 10,
            isWarmup = 0,
            involvements = "QUADS:1.0",
            weekNumber = 1,
            cycleNumber = 1,
            dayIndex = 0,
            rir = 2,
            sessionId = "s1",
            loadKind = "EXTERNAL",
            timingProvenance = "LIVE",
            startedAtElapsedMillis = 100,
            completedAtElapsedMillis = 150,
            durationSeconds = null,
            prescriptionSnapshot = null
        )
        database.planHistoryQueries.insertPlan("deterministic", 1, 1, 1)
        val planId = database.planHistoryQueries.lastInsertedPlanId().executeAsOne()
        database.planHistoryQueries.insertDay(planId = planId, dayIndex = 0, focus = "FULL_BODY")
        val dayId = database.planHistoryQueries.selectAllDays().executeAsOne().id
        database.planHistoryQueries.insertEntry(
            dayId = dayId,
            position = 0,
            exerciseId = "back-squat",
            sets = 3,
            reps = 5,
            durationSeconds = null,
            exerciseName = "Back Squat",
            movementPattern = "SQUAT",
            suggestedWeightKg = 100.0,
            loadCapability = "EXTERNAL",
            loadKind = "EXTERNAL",
            progressionPolicy = null
        )
        database.routineTemplateQueries.insertTemplate("Block", 1, 1, 1, null, null)
        val templateId = database.routineTemplateQueries.lastInsertedTemplateId().executeAsOne()
        database.routineTemplateQueries.insertWorkout(templateId, 0, "Day", "FULL_BODY")
        val workoutId = database.routineTemplateQueries.lastInsertedWorkoutId().executeAsOne()
        database.routineTemplateQueries.insertEntry(
            workoutId = workoutId,
            position = 0,
            exerciseId = "back-squat",
            sets = 3,
            reps = 5,
            durationSeconds = null,
            weightKg = 100.0,
            loadKind = "EXTERNAL",
            progressionPolicy = null
        )
        database.equipmentQueries.insert("CUSTOM-1", "Custom Rack", 0)
        database.planVolumeExplanationQueries.insert(
            planId = planId,
            muscle = "BICEPS",
            targetSets = 4,
            isTargetEnforced = 1,
            directIsolationSets = 4,
            estimatedOtherInvolvementCredits = 1.0,
            unmetReason = null,
            attribution = "DETERMINISTIC"
        )
        database.planVolumeExplanationStateQueries.upsert(planId, "AVAILABLE", 1)
        database.planTargetExplanationStateQueries.upsert(
            planId = planId,
            attribution = "DETERMINISTIC",
            isDeload = 0L,
            deloadVolumeScale = 0.7,
            deloadIntensityScale = 0.8,
            explanationVersion = 1L
        )
        database.planTargetExplanationQueries.insertExplanation(
            planId = planId,
            dayIndex = 0L,
            position = 0L,
            explanationVersion = 1L,
            baselineSource = "RECENT_PERFORMANCE",
            baselineRecentEstimateKg = 101.0,
            baselineQualifyingSetCount = 3L,
            baselineManualFloorKg = null,
            baselineKg = 101.0,
            progressionOutcome = "APPLIED",
            progressionIncrements = 1L,
            progressionDeltaKg = 2.5,
            progressionRoundToKg = 2.5,
            progressionSuccessStreakSize = 3L,
            progressionFailureStreakSize = 3L,
            conversionReps = 5L,
            conversionIntensityFraction = 0.783,
            conversionDeloadIntensityScale = 1.0,
            conversionRoundToKg = 2.5,
            conversionUncappedWorkingKg = 80.0,
            conversionEquipmentCeilingKg = null,
            conversionEquipmentCeilingBinds = 0L,
            aiProposedKg = null,
            aiProposedAccepted = null,
            aiDeloadScaled = null,
            aiDeloadIntensityScale = null,
            aiProgressionBoundKg = null,
            aiBoundedByProgression = null,
            aiEquipmentCeilingKg = null,
            aiCappedByEquipment = null,
            setBaseSets = 3L,
            setDeloadApplied = 0L,
            setDeloadVolumeScale = 0.7,
            setFatigueEffect = "NONE",
            setFinalSets = 3L,
            unavailableReason = null
        )
        database.trainingScheduleQueries.insertActivation(
            templateId = null,
            templateRevision = null,
            sourcePlanId = planId,
            name = "Block",
            createdAtMillis = 1,
            startEpochDay = 1,
            mode = "SEQUENCE",
            weekdayMask = 0,
            status = "ACTIVE",
            weekNumber = 1,
            cycleNumber = 1,
            endedAtMillis = null,
            revision = 1
        )
        val activationId =
            database.trainingScheduleQueries.lastInsertedActivationId().executeAsOne()
        database.trainingScheduleQueries.insertActivationWorkout(
            activationId = activationId,
            sourceWorkoutId = null,
            position = 0,
            name = "Day",
            focus = "FULL_BODY"
        )
        val activationWorkoutId =
            database.trainingScheduleQueries.lastInsertedActivationWorkoutId().executeAsOne()
        database.trainingScheduleQueries.insertActivationEntry(
            workoutId = activationWorkoutId,
            position = 0,
            exerciseId = "back-squat",
            exerciseName = "Back Squat",
            movementPattern = "SQUAT",
            requiredEquipment = "BARBELL",
            involvements = "QUADS:1.0",
            isUnilateral = 0,
            sets = 3,
            reps = 5,
            durationSeconds = null,
            weightKg = 100.0,
            loadCapability = "EXTERNAL",
            loadKind = "EXTERNAL",
            progressionPolicy = null
        )
        val activationEntryId =
            database.trainingScheduleQueries.lastInsertedActivationEntryId().executeAsOne()
        HistoricalDatabaseFixtures.exec(
            driver,
            "INSERT INTO entryTargetExplanation(entryKind, entryId, explanationVersion, " +
                "setBaseSets, setDeloadApplied, setDeloadVolumeScale, setFatigueEffect, " +
                "setFinalSets) " +
                "VALUES ('ACTIVATION', $activationEntryId, 1, 3, 0, 0.7, 'NONE', 3)"
        )
        database.trainingScheduleQueries.insertOccurrence(
            activationId = activationId,
            activationWorkoutId = activationWorkoutId,
            queuePosition = 0,
            scheduledEpochDay = null,
            notBeforeEpochDay = null,
            startedAtMillis = null,
            resolvedAtMillis = null,
            status = "PENDING",
            revision = 1
        )
        val occurrenceId =
            database.trainingScheduleQueries.lastInsertedOccurrenceId().executeAsOne()
        database.trainingScheduleQueries.insertOccurrenceEntry(
            occurrenceId = occurrenceId,
            sourceActivationEntryId = null,
            position = 0,
            exerciseId = "back-squat",
            exerciseName = "Back Squat",
            movementPattern = "SQUAT",
            requiredEquipment = "BARBELL",
            involvements = "QUADS:1.0",
            isUnilateral = 0,
            sets = 3,
            reps = 5,
            durationSeconds = null,
            weightKg = 100.0,
            loadCapability = "EXTERNAL",
            loadKind = "EXTERNAL",
            remainingDisposition = null,
            terminalRemainingSets = null,
            progressionPolicy = null
        )
        val occurrenceEntryId =
            database.trainingScheduleQueries.lastInsertedOccurrenceEntryId().executeAsOne()
        HistoricalDatabaseFixtures.exec(
            driver,
            "INSERT INTO entryTargetExplanation(entryKind, entryId, explanationVersion, " +
                "setBaseSets, setDeloadApplied, setDeloadVolumeScale, setFatigueEffect, " +
                "setFinalSets) " +
                "VALUES ('OCCURRENCE', $occurrenceEntryId, 1, 3, 0, 0.7, 'NONE', 3)"
        )
        database.trainingScheduleQueries.upsertScheduleState(
            activeActivationId = activationId,
            selectedOccurrenceId = null,
            legacyFallbackEnabled = 1
        )
        database.personalRecordQueries.upsert("back-squat", 120.0, 3, 5, "EXTERNAL")
        database.exercisePreferenceQueries.upsert("back-squat", "PREFER")
        database.exerciseExclusionQueries.upsert("back-squat", null)
        database.plannerEngineQueries.insertIgnoreRow("DETERMINISTIC")
        database.plannerEngineQueries.updateDaysPerWeek(3)
        database.plannerEngineQueries.updateWeightUnit("LB")
    }

    private companion object {
        /** A merged set keeps its target even though the merge drops its scheduling links. */
        val MERGED_TARGET = WorkoutPrescriptionSnapshotJson.encode(
            WorkoutPrescriptionSnapshot(
                exerciseId = "back-squat",
                sets = 5,
                reps = 5,
                loadKind = LoadKind.EXTERNAL,
                weightKg = 90.0,
                isDeload = false
            )
        )
    }
}
