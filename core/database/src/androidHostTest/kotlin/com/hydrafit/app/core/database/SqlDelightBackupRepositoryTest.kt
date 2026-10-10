package com.hydrafit.app.core.database

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.hydrafit.app.core.domain.backup.ApplyStagedBackupUseCase
import com.hydrafit.app.core.domain.backup.BackupJson
import com.hydrafit.app.core.domain.backup.BackupValidator
import com.hydrafit.app.core.domain.backup.PreviewBackupUseCase
import com.hydrafit.app.core.domain.backup.RestoreBackupUseCase
import com.hydrafit.app.core.domain.time.TimeProvider
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
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
    fun exportsEveryIncludedSection() = runTest {
        seedRepresentativeData()
        SqlDelightGuidedWorkoutPreferenceRepository(database).setGuidedWorkoutEnabled(true)

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
        assertEquals("s1", file.workoutSets.single().sessionId)
        assertEquals(listOf("s1"), file.workoutSessions.map { it.id })
        assertEquals(1, file.plans.size)
        assertEquals(1, file.planDays.size)
        assertEquals(1, file.planEntries.size)
        assertEquals(1, file.volumeExplanations.size)
        assertEquals(1, file.volumeExplanationStates.size)
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
        val file = repository.export(appVersion = "t", exportedAtMillis = 1L)

        // Remove some data and add a value the restore must replace.
        database.workoutLogQueries.deleteAllSets()
        database.exerciseQueries.deleteAllCustom()
        database.exercisePreferenceQueries.upsert("back-squat", "PREFER_LESS")

        repository.restore(file)

        assertEquals(file, repository.export(appVersion = "t", exportedAtMillis = 1L))
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
            .copy(equipment = emptyList())
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
    }

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
            timingProvenance = "LIVE"
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
            exerciseName = "Back Squat",
            movementPattern = "SQUAT",
            suggestedWeightKg = 100.0,
            loadCapability = "EXTERNAL",
            loadKind = "EXTERNAL"
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
            weightKg = 100.0,
            loadKind = "EXTERNAL"
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
            weightKg = 100.0,
            loadCapability = "EXTERNAL",
            loadKind = "EXTERNAL"
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
            weightKg = 100.0,
            loadCapability = "EXTERNAL",
            loadKind = "EXTERNAL",
            remainingDisposition = null,
            terminalRemainingSets = null
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
}
