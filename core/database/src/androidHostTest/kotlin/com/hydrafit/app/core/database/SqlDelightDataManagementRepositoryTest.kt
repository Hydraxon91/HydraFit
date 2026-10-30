package com.hydrafit.app.core.database

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.hydrafit.app.core.domain.schedule.WorkoutReminderDeliveryGate
import com.hydrafit.app.core.domain.storage.DataCategory
import com.hydrafit.app.core.domain.storage.DataCountKind
import com.hydrafit.app.core.domain.storage.ExerciseDataClearException
import com.hydrafit.app.core.domain.storage.ExerciseDataClearFailure
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class SqlDelightDataManagementRepositoryTest {

    private lateinit var driver: JdbcSqliteDriver
    private lateinit var database: HydraFitDatabase
    private lateinit var repository: SqlDelightDataManagementRepository

    @BeforeTest
    fun setUp() {
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        HydraFitDatabase.Schema.create(driver)
        database = HydraFitDatabase(driver)
        SeedExerciseCatalog(database).seed()
        SeedEquipmentCatalog(database).seed()
        database.plannerEngineQueries.insertIgnoreRow("DETERMINISTIC")
        repository = SqlDelightDataManagementRepository(database, WorkoutReminderDeliveryGate())
    }

    @AfterTest
    fun tearDown() {
        driver.close()
    }

    @Test
    fun countsReflectStoredRows() = runTest {
        val profilesBefore = repository.counts()[DataCountKind.EQUIPMENT_PROFILES] ?: 0
        seedAllCategories()

        val counts = repository.counts()

        assertEquals(1, counts[DataCountKind.SESSIONS])
        assertEquals(1, counts[DataCountKind.SETS])
        assertEquals(1, counts[DataCountKind.SESSION_NOTES])
        assertEquals(1, counts[DataCountKind.ACCEPTED_PLANS])
        assertEquals(1, counts[DataCountKind.ROUTINES])
        assertEquals(1, counts[DataCountKind.ACTIVATIONS])
        assertEquals(1, counts[DataCountKind.SCHEDULED_WORKOUTS])
        assertEquals(1, counts[DataCountKind.BODY_WEIGHT_MEASUREMENTS])
        assertEquals(1, counts[DataCountKind.PERSONAL_RECORDS])
        assertEquals(profilesBefore + 1, counts[DataCountKind.EQUIPMENT_PROFILES])
        assertEquals(1, counts[DataCountKind.CUSTOM_EXERCISES])
    }

    @Test
    fun clearingWorkoutHistoryRemovesOnlyHistory() = runTest {
        seedAllCategories()

        repository.clear(DataCategory.WORKOUT_HISTORY)

        val counts = repository.counts()
        assertEquals(0, counts[DataCountKind.SESSIONS])
        assertEquals(0, counts[DataCountKind.SETS])
        assertEquals(0, counts[DataCountKind.SESSION_NOTES])
        assertEquals(1, counts[DataCountKind.CUSTOM_EXERCISES])
        assertEquals(1, counts[DataCountKind.ACCEPTED_PLANS])
        assertEquals(1, counts[DataCountKind.BODY_WEIGHT_MEASUREMENTS])
    }

    @Test
    fun clearingExerciseDataKeepsTheSeededCatalog() = runTest {
        seedAllCategories()
        HistoricalDatabaseFixtures.exec(
            driver,
            "INSERT INTO restPreference(preferenceKey, exerciseId, durationSeconds) " +
                "VALUES ('GLOBAL', NULL, 90)"
        )
        HistoricalDatabaseFixtures.exec(
            driver,
            "INSERT INTO restPreference(preferenceKey, exerciseId, durationSeconds) " +
                "VALUES ('EXERCISE:hf-storage-custom', 'hf-storage-custom', 60)"
        )
        val exercisesBefore = HistoricalDatabaseFixtures.count(driver, "exercise")

        repository.clear(DataCategory.EXERCISE_DATA)

        assertEquals(exercisesBefore - 1, HistoricalDatabaseFixtures.count(driver, "exercise"))
        assertEquals(0, repository.counts()[DataCountKind.CUSTOM_EXERCISES])
        assertEquals(0L, HistoricalDatabaseFixtures.count(driver, "exerciseOverride"))
        assertEquals(0L, HistoricalDatabaseFixtures.count(driver, "exerciseNote"))
        // The removed custom's per-exercise override goes with it; the global default stays.
        assertEquals(
            0L,
            HistoricalDatabaseFixtures.count(driver, "restPreference WHERE exerciseId IS NOT NULL")
        )
        assertEquals(
            1L,
            HistoricalDatabaseFixtures.long(
                driver,
                "SELECT COUNT(*) FROM restPreference WHERE preferenceKey = 'GLOBAL'"
            )
        )
        assertEquals(1, repository.counts()[DataCountKind.SESSIONS])
    }

    @Test
    fun clearingTrainingSetupResetsPreferencesAndKeepsTheEngine() = runTest {
        seedAllCategories()
        database.plannerEngineQueries.updateEngine("GEMINI_API")
        database.plannerEngineQueries.updateWeightUnit("LB")
        database.plannerEngineQueries.updateDaysPerWeek(6L)
        database.plannerEngineQueries.updateGuidedWorkoutEnabled(1L)

        repository.clear(DataCategory.TRAINING_SETUP)

        assertEquals("GEMINI_API", database.plannerEngineQueries.selectEngine().executeAsOne())
        assertEquals("KG", database.plannerEngineQueries.selectWeightUnit().executeAsOne())
        assertEquals(4L, database.plannerEngineQueries.selectDaysPerWeek().executeAsOne())
        assertEquals(0L, database.plannerEngineQueries.selectGuidedWorkoutEnabled().executeAsOne())
        // The reset re-creates the schema's seeded profile singleton instead of leaving it empty.
        assertEquals(1, repository.counts()[DataCountKind.EQUIPMENT_PROFILES])
        assertEquals(
            1L,
            database.equipmentProfileQueries.selectState().executeAsOne().activeProfileId
        )
        assertEquals(
            "My equipment",
            database.equipmentProfileQueries.selectProfile(1L).executeAsOne().name
        )
    }

    @Test
    fun clearingAllDataResetsTheInstallAndKeepsTheCatalog() = runTest {
        seedAllCategories()
        database.setupQueries.updateState(1L, "COMPLETED", "FIRST_WORKOUT", 3L)
        database.setupQueries.updateWeekdays("MONDAY")
        HistoricalDatabaseFixtures.exec(
            driver,
            "INSERT OR REPLACE INTO stagedBackup(id, payload, appVersion, stagedAtMillis) " +
                "VALUES (0, 'pending', 't', 1)"
        )
        val exercisesBefore = HistoricalDatabaseFixtures.count(driver, "exercise")

        repository.clear(DataCategory.ALL_DATA)

        val counts = repository.counts()
        assertEquals(0, counts[DataCountKind.SESSIONS])
        assertEquals(0, counts[DataCountKind.CUSTOM_EXERCISES])
        assertEquals(0, counts[DataCountKind.ACCEPTED_PLANS])
        assertEquals(0, counts[DataCountKind.SCHEDULED_WORKOUTS])
        assertEquals(0, counts[DataCountKind.PERSONAL_RECORDS])
        val setup = database.setupQueries.selectState().executeAsOne()
        assertEquals("PENDING", setup.disposition)
        assertEquals("WELCOME", setup.step)
        assertEquals(0L, setup.revision)
        assertNull(database.setupQueries.selectWeekdays().executeAsOne().weekdays)
        assertEquals("DETERMINISTIC", database.plannerEngineQueries.selectEngine().executeAsOne())
        assertEquals("KG", database.plannerEngineQueries.selectWeightUnit().executeAsOne())
        assertEquals(0L, HistoricalDatabaseFixtures.count(driver, "workoutImportProvenance"))
        // The seeded catalog and the built-in equipment rows survive; only the custom row is gone.
        assertTrue(HistoricalDatabaseFixtures.count(driver, "exercise") >= exercisesBefore - 1)
        assertTrue(HistoricalDatabaseFixtures.count(driver, "equipment") > 0)
        // The seeded profile singleton is re-created and no selection is left behind.
        assertEquals(1, counts[DataCountKind.EQUIPMENT_PROFILES])
        assertEquals(
            1L,
            database.equipmentProfileQueries.selectState().executeAsOne().activeProfileId
        )
        assertEquals(0L, HistoricalDatabaseFixtures.count(driver, "equipmentProfileEntry"))
        // A staged restore never survives a full reset and cannot resurrect data on the next start.
        assertEquals(0L, HistoricalDatabaseFixtures.count(driver, "stagedBackup"))
    }

    @Test
    fun clearRejectsCategoriesHandledOutsideTheDatabase() = runTest {
        seedAllCategories()

        assertFailsWith<IllegalArgumentException> {
            repository.clear(DataCategory.BACKUP_FILES)
        }
        assertEquals(1, repository.counts()[DataCountKind.SESSIONS])
    }

    @Test
    fun clearingWorkoutHistoryRemovesClusterSegmentsAndCardio() = runTest {
        enableForeignKeys()
        seedRecordedClusterAndCardio()
        assertEquals(2L, HistoricalDatabaseFixtures.count(driver, "workoutClusterSegment"))
        assertEquals(1, repository.counts()[DataCountKind.CARDIO_ACTIVITIES])

        repository.clear(DataCategory.WORKOUT_HISTORY)

        val counts = repository.counts()
        assertEquals(0, counts[DataCountKind.SESSIONS])
        assertEquals(0, counts[DataCountKind.SETS])
        assertEquals(0, counts[DataCountKind.SESSION_NOTES])
        assertEquals(0, counts[DataCountKind.CARDIO_ACTIVITIES])
        assertEquals(0L, HistoricalDatabaseFixtures.count(driver, "workoutClusterSegment"))
        assertEquals(0L, foreignKeyViolations())
    }

    @Test
    fun clearingTrainingSetupRemovesStationsAndKeepsCustomEquipment() = runTest {
        enableForeignKeys()
        database.plannerEngineQueries.updateEngine("GEMINI_API")
        database.plannerEngineQueries.updateWeightUnit("LB")
        HistoricalDatabaseFixtures.exec(
            driver,
            "INSERT INTO equipment(id, name, isBuiltIn) VALUES ('CUSTOM_RACK', 'Rack', 0)"
        )
        HistoricalDatabaseFixtures.exec(
            driver,
            "INSERT INTO equipmentStation(profileId, equipmentId, name, loadBasis) " +
                "VALUES (1, 'CUSTOM_RACK', 'Half rack', 'TOTAL')"
        )

        repository.clear(DataCategory.TRAINING_SETUP)

        // The reseeded profile 1 cannot inherit a station from the deleted profile rows.
        assertEquals(0L, HistoricalDatabaseFixtures.count(driver, "equipmentStation"))
        assertEquals(
            1L,
            HistoricalDatabaseFixtures.long(
                driver,
                "SELECT COUNT(*) FROM equipment WHERE id = 'CUSTOM_RACK' AND isBuiltIn = 0"
            )
        )
        assertEquals("GEMINI_API", database.plannerEngineQueries.selectEngine().executeAsOne())
        assertEquals("KG", database.plannerEngineQueries.selectWeightUnit().executeAsOne())
        assertEquals(
            1L,
            database.equipmentProfileQueries.selectState().executeAsOne().activeProfileId
        )
        assertEquals(0L, foreignKeyViolations())
    }

    @Test
    fun clearingAllDataRemovesClustersStationsAndCardioWithoutForeignKeyViolations() = runTest {
        enableForeignKeys()
        seedRecordedClusterAndCardio()
        HistoricalDatabaseFixtures.exec(
            driver,
            "INSERT INTO equipment(id, name, isBuiltIn) VALUES ('CUSTOM_RACK', 'Rack', 0)"
        )
        HistoricalDatabaseFixtures.exec(
            driver,
            "INSERT INTO equipmentStation(profileId, equipmentId, name, loadBasis) " +
                "VALUES (1, 'CUSTOM_RACK', 'Half rack', 'TOTAL')"
        )

        repository.clear(DataCategory.ALL_DATA)

        assertEquals(0L, HistoricalDatabaseFixtures.count(driver, "workoutClusterSegment"))
        assertEquals(0L, HistoricalDatabaseFixtures.count(driver, "equipmentStation"))
        assertEquals(0L, HistoricalDatabaseFixtures.count(driver, "cardioActivity"))
        assertEquals(0L, HistoricalDatabaseFixtures.count(driver, "equipment WHERE isBuiltIn = 0"))
        assertEquals(0L, foreignKeyViolations())
    }

    @Test
    fun clearingExerciseDataRefusesWhileACustomExerciseIsReferenced() = runTest {
        enableForeignKeys()
        HistoricalDatabaseFixtures.exec(
            driver,
            "INSERT INTO exercise(" +
                "id, name, requiredEquipment, movementPattern, isCustom, isUnilateral, " +
                "loadCapability, searchName" +
                ") VALUES ('custom-ref', 'Custom', 'BARBELL', 'SQUAT', 1, 0, 'EXTERNAL', '')"
        )
        HistoricalDatabaseFixtures.exec(
            driver,
            "INSERT INTO workoutSet(exerciseId, reps, weightKg, performedAt, isWarmup, loadKind) " +
                "VALUES ('custom-ref', 5, 50.0, 1, 0, 'EXTERNAL')"
        )
        HistoricalDatabaseFixtures.exec(
            driver,
            "INSERT INTO restPreference(preferenceKey, exerciseId, durationSeconds) VALUES " +
                "('GLOBAL', NULL, 90), ('EXERCISE:custom-ref', 'custom-ref', 60)"
        )
        HistoricalDatabaseFixtures.exec(
            driver,
            "INSERT INTO exerciseOverride(exerciseId, name) VALUES ('custom-ref', 'Edited')"
        )
        HistoricalDatabaseFixtures.exec(
            driver,
            "INSERT INTO exerciseNote(exerciseId, note) VALUES ('custom-ref', 'n')"
        )

        val refusal = assertFailsWith<ExerciseDataClearException> {
            repository.clear(DataCategory.EXERCISE_DATA)
        }

        assertEquals(ExerciseDataClearFailure.CUSTOM_EXERCISES_REFERENCED, refusal.failure)
        assertEquals(
            1L,
            HistoricalDatabaseFixtures.count(driver, "exercise WHERE id = 'custom-ref'")
        )
        assertEquals(1L, HistoricalDatabaseFixtures.count(driver, "workoutSet"))
        assertEquals(2L, HistoricalDatabaseFixtures.count(driver, "restPreference"))
        assertEquals(1L, HistoricalDatabaseFixtures.count(driver, "exerciseOverride"))
        assertEquals(1L, HistoricalDatabaseFixtures.count(driver, "exerciseNote"))
        assertEquals(0L, foreignKeyViolations())
    }

    private fun enableForeignKeys() {
        HistoricalDatabaseFixtures.exec(driver, "PRAGMA foreign_keys = ON")
    }

    private fun foreignKeyViolations(): Long =
        HistoricalDatabaseFixtures.long(driver, "SELECT COUNT(*) FROM pragma_foreign_key_check")

    /** A coherent history fixture: one session, one clustered set and one cardio record. */
    private fun seedRecordedClusterAndCardio() {
        HistoricalDatabaseFixtures.exec(
            driver,
            "INSERT INTO workoutSession(id, startedAtMillis, endedAtMillis, localEpochDay) " +
                "VALUES ('s1', 1, 2, 1)"
        )
        HistoricalDatabaseFixtures.exec(
            driver,
            "INSERT INTO workoutSet(" +
                "exerciseId, reps, weightKg, performedAt, isWarmup, sessionId, loadKind" +
                ") VALUES ('back-squat', 5, 100.0, 1, 0, 's1', 'EXTERNAL')"
        )
        val setId = HistoricalDatabaseFixtures.long(driver, "SELECT id FROM workoutSet LIMIT 1")
        HistoricalDatabaseFixtures.exec(
            driver,
            "INSERT INTO workoutClusterSegment(setId, position, reps, weightKg, rir) " +
                "VALUES ($setId, 1, 5, 90.0, NULL), ($setId, 2, 5, 80.0, NULL)"
        )
        HistoricalDatabaseFixtures.exec(
            driver,
            "INSERT INTO sessionNote(sessionId, note) VALUES ('s1', 'note')"
        )
        HistoricalDatabaseFixtures.exec(
            driver,
            "INSERT INTO cardioActivity(identity, type, startedAt, durationSeconds) " +
                "VALUES ('c1', 'RUN', 1, 60)"
        )
    }

    private fun seedAllCategories() {
        HistoricalDatabaseFixtures.exec(
            driver,
            "INSERT INTO workoutSession(id, startedAtMillis, endedAtMillis, localEpochDay) " +
                "VALUES ('s1', 1, 2, 1)"
        )
        HistoricalDatabaseFixtures.exec(
            driver,
            "INSERT INTO workoutSet(" +
                "exerciseId, reps, weightKg, performedAt, isWarmup, sessionId, loadKind" +
                ") VALUES ('back-squat', 5, 100.0, 1, 0, 's1', 'EXTERNAL')"
        )
        HistoricalDatabaseFixtures.exec(
            driver,
            "INSERT INTO sessionNote(sessionId, note) VALUES ('s1', 'note')"
        )
        HistoricalDatabaseFixtures.exec(
            driver,
            "INSERT INTO planHistory(engineId, acceptedAt, weekNumber, cycleNumber) " +
                "VALUES ('DETERMINISTIC', 1, 1, 1)"
        )
        HistoricalDatabaseFixtures.exec(
            driver,
            "INSERT INTO routineTemplate(" +
                "name, revision, createdAtMillis, updatedAtMillis, archivedAtMillis" +
                ") VALUES ('Block', 1, 1, 1, NULL)"
        )
        HistoricalDatabaseFixtures.exec(
            driver,
            "INSERT INTO trainingActivation(" +
                "name, createdAtMillis, startEpochDay, mode, weekdayMask, status, revision" +
                ") VALUES ('a', 1, 1, 'WEEKLY', 0, 'ACTIVE', 1)"
        )
        HistoricalDatabaseFixtures.exec(
            driver,
            "INSERT INTO workoutOccurrence(" +
                "activationId, activationWorkoutId, queuePosition, status, revision" +
                ") VALUES (1, 1, 0, 'QUEUED', 1)"
        )
        HistoricalDatabaseFixtures.exec(
            driver,
            "INSERT INTO bodyWeightMeasurement(localEpochDay, weightKg) VALUES (1, 80.0)"
        )
        HistoricalDatabaseFixtures.exec(
            driver,
            "INSERT INTO personalRecord(exerciseId, weightKg, reps, updatedAt) " +
                "VALUES ('back-squat', 100.0, 5, 1)"
        )
        HistoricalDatabaseFixtures.exec(
            driver,
            "INSERT INTO equipmentProfile(name, loadingInventory) VALUES ('Home', '{}')"
        )
        HistoricalDatabaseFixtures.exec(
            driver,
            "INSERT INTO exerciseOverride(exerciseId, name) VALUES ('back-squat', 'Edited')"
        )
        HistoricalDatabaseFixtures.exec(
            driver,
            "INSERT INTO exerciseNote(exerciseId, note) VALUES ('back-squat', 'n')"
        )
        HistoricalDatabaseFixtures.exec(
            driver,
            "INSERT INTO exercise(" +
                "id, name, requiredEquipment, movementPattern, isCustom, isUnilateral, " +
                "loadCapability, searchName" +
                ") VALUES ('hf-storage-custom', 'Custom', 'BARBELL', 'SQUAT', 1, 0, " +
                "'EXTERNAL', '')"
        )
    }
}
