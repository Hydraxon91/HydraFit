package com.hydrafit.app.core.database

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.hydrafit.app.core.domain.backup.BackupJson
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
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
        database.workoutLogQueries.insertSet(
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
            loadKind = "EXTERNAL"
        )
        database.workoutSessionQueries.insertSession("s1", 10, 20, 1)
        database.personalRecordQueries.upsert("back-squat", 120.0, 3, 5, "EXTERNAL")
        database.exercisePreferenceQueries.upsert("back-squat", "PREFER")
        database.exerciseExclusionQueries.upsert("back-squat", null)
        database.plannerEngineQueries.insertIgnoreRow("DETERMINISTIC")
        database.plannerEngineQueries.updateDaysPerWeek(3)
        database.plannerEngineQueries.updateWeightUnit("LB")

        val file = repository.export(appVersion = "0.5.0-dev", exportedAtMillis = 99L)

        assertEquals("0.5.0-dev", file.appVersion)
        assertEquals(99L, file.exportedAtMillis)
        assertTrue(file.catalog.seedExerciseIds.contains("back-squat"))
        assertEquals(listOf("user-bicycle-crunch"), file.customExercises.map { it.id })
        assertEquals("Back Squat (edited)", file.exerciseOverrides.single().name)
        assertEquals(listOf("BARBELL"), file.selectedEquipment)
        assertEquals(1, file.workoutSets.size)
        assertEquals("EXTERNAL", file.workoutSets.single().loadKind)
        assertEquals("s1", file.workoutSets.single().sessionId)
        assertEquals(listOf("s1"), file.workoutSessions.map { it.id })
        assertEquals(120.0, file.personalRecords.single().weightKg)
        assertEquals("PREFER", file.preferences.single().preference)
        assertEquals(1, file.exclusions.size)
        assertEquals(3, file.settings?.daysPerWeek)
        assertEquals("LB", file.settings?.weightUnit)
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
        database.workoutLogQueries.insertSet(
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
            loadKind = "EXTERNAL"
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
        database.personalRecordQueries.upsert("back-squat", 120.0, 3, 5, "EXTERNAL")
        database.exercisePreferenceQueries.upsert("back-squat", "PREFER")
        database.exerciseExclusionQueries.upsert("back-squat", null)
        database.plannerEngineQueries.insertIgnoreRow("DETERMINISTIC")
        database.plannerEngineQueries.updateDaysPerWeek(3)
        database.plannerEngineQueries.updateWeightUnit("LB")
    }
}
