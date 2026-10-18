package com.hydrafit.app.core.database

import app.cash.sqldelight.db.AfterVersion
import app.cash.sqldelight.db.SqlDriver
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Migration 54 -> 55 makes an accepted-plan slot measurable by elapsed whole seconds:
 * `planHistoryEntry` accepts NULL reps with a stored duration. SQLite cannot drop NOT NULL in place,
 * so the table is rebuilt: every stored row must keep its id, every snapshot column and its day link.
 * A pre-existing (counted) row keeps its reps verbatim and gains a NULL duration; a new timed slot
 * stores its seconds with NULL reps. The table has no index to recreate, but its AUTOINCREMENT
 * high-water mark must survive the rebuild (repaired by [HydraFitDatabaseSchema]).
 */
class PlanEntryDurationMigrationTest {

    private lateinit var driver: SqlDriver

    @AfterTest
    fun tearDown() {
        if (::driver.isInitialized) driver.close()
    }

    @Test
    fun migrationRebuildKeepsEveryStoredColumnAndLink() {
        driver = HistoricalDatabaseFixtures.v1()
        // 54 is the pre-timed-plan-entry schema: reps is NOT NULL and no duration column exists.
        HydraFitDatabaseSchema.migrate(driver, 1, 54)
        insertPlanAndDay()
        insertCountedEntry(id = 71, position = 2, reps = 6)

        HydraFitDatabaseSchema.migrate(driver, 54, HydraFitDatabaseSchema.version)

        val database = HydraFitDatabase(driver)
        val entry = database.planHistoryQueries.selectEntriesForDay(7L).executeAsOne()
        assertEquals(71L, entry.id)
        assertEquals(7L, entry.dayId)
        assertEquals(2L, entry.position)
        assertEquals("back-squat", entry.exerciseId)
        assertEquals(5L, entry.sets)
        assertEquals(6L, entry.reps)
        assertNull(entry.durationSeconds)
        assertEquals("Back Squat", entry.exerciseName)
        assertEquals("SQUAT", entry.movementPattern)
        assertEquals(100.0, entry.suggestedWeightKg)
        assertEquals("EXTERNAL", entry.loadCapability)
        assertEquals("EXTERNAL", entry.loadKind)

        // The rebuilt table accepts a timed slot: NULL reps with a positive stored duration.
        database.planHistoryQueries.insertEntry(
            dayId = 7L,
            position = 3,
            exerciseId = "plank",
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
        val timed = database.planHistoryQueries.selectEntriesForDay(7L)
            .executeAsList().first { it.durationSeconds != null }
        assertNull(timed.reps)
        assertEquals(45L, timed.durationSeconds)
    }

    @Test
    fun rebuildPreservesTheDeletedIdHighWaterMark() {
        driver = HistoricalDatabaseFixtures.v1()
        HydraFitDatabaseSchema.migrate(driver, 1, 54)
        insertPlanAndDay()
        insertCountedEntry(id = 1000, position = 0, reps = 5)
        HistoricalDatabaseFixtures.exec(driver, "DELETE FROM planHistoryEntry WHERE id = 1000")
        assertEquals(1000L, sequenceHighWater())

        HydraFitDatabaseSchema.migrate(driver, 54, HydraFitDatabaseSchema.version)

        // Without the wrapper the rebuilt table falls back to its largest copied id (none here) and
        // would hand out id 1 again; the stored mark is restored instead.
        assertEquals(1000L, sequenceHighWater())
        val database = HydraFitDatabase(driver)
        database.planHistoryQueries.insertEntry(
            dayId = 7L,
            position = 1,
            exerciseId = "plank",
            sets = 3,
            reps = null,
            durationSeconds = 30,
            exerciseName = "Plank",
            movementPattern = "CORE",
            suggestedWeightKg = null,
            loadCapability = "BODYWEIGHT_ONLY",
            loadKind = "BODYWEIGHT",
            progressionPolicy = null
        )
        assertEquals(1001L, database.planHistoryQueries.lastInsertedPlanId().executeAsOne())
    }

    @Test
    fun multiHopUpgradePreservesDeletedIdsWithSurvivingRows() {
        driver = HistoricalDatabaseFixtures.v1()
        HydraFitDatabaseSchema.migrate(driver, 1, 53)
        insertPlanAndDay()
        insertCountedEntry(id = 41, position = 0, reps = 6)
        insertCountedEntry(id = 1000, position = 1, reps = 5)
        HistoricalDatabaseFixtures.exec(driver, "DELETE FROM planHistoryEntry WHERE id = 1000")
        assertEquals(1000L, sequenceHighWater())

        var markSeenByCallback: Long? = null
        HydraFitDatabaseSchema.migrate(
            driver,
            53,
            HydraFitDatabaseSchema.version,
            AfterVersion(54) { markSeenByCallback = sequenceHighWater() }
        )

        assertEquals(1000L, markSeenByCallback)
        assertEquals(1000L, sequenceHighWater())
        val database = HydraFitDatabase(driver)
        assertEquals(41L, database.planHistoryQueries.selectEntriesForDay(7L).executeAsOne().id)
        database.planHistoryQueries.insertEntry(
            dayId = 7L,
            position = 2,
            exerciseId = "plank",
            sets = 3,
            reps = null,
            durationSeconds = 30,
            exerciseName = "Plank",
            movementPattern = "CORE",
            suggestedWeightKg = null,
            loadCapability = "BODYWEIGHT_ONLY",
            loadKind = "BODYWEIGHT",
            progressionPolicy = null
        )
        assertEquals(1001L, database.planHistoryQueries.lastInsertedPlanId().executeAsOne())
    }

    @Test
    fun freshSchemaStoresTimedAndCountedSlots() {
        driver = HistoricalDatabaseFixtures.empty()
        HydraFitDatabaseSchema.create(driver)
        val database = HydraFitDatabase(driver)
        database.planHistoryQueries.insertPlan("DETERMINISTIC", 42, 1, 1)
        val planId = database.planHistoryQueries.lastInsertedPlanId().executeAsOne()
        database.planHistoryQueries.insertDay(planId, 0, "PUSH")
        val dayId = database.planHistoryQueries.lastInsertedPlanId().executeAsOne()

        database.planHistoryQueries.insertEntry(
            dayId = dayId,
            position = 0,
            exerciseId = "plank",
            sets = 3,
            reps = null,
            durationSeconds = 60,
            exerciseName = "Plank",
            movementPattern = "CORE",
            suggestedWeightKg = null,
            loadCapability = "BODYWEIGHT_ONLY",
            loadKind = "BODYWEIGHT",
            progressionPolicy = null
        )
        database.planHistoryQueries.insertEntry(
            dayId = dayId,
            position = 1,
            exerciseId = "back-squat",
            sets = 3,
            reps = 5,
            durationSeconds = null,
            exerciseName = "Back Squat",
            movementPattern = "SQUAT",
            suggestedWeightKg = null,
            loadCapability = "EXTERNAL",
            loadKind = "EXTERNAL",
            progressionPolicy = null
        )

        val entries = database.planHistoryQueries.selectEntriesForDay(dayId).executeAsList()
        val timed = entries.first { it.durationSeconds != null }
        assertNull(timed.reps)
        assertEquals(60L, timed.durationSeconds)
        val counted = entries.first { it.durationSeconds == null }
        assertEquals(5L, counted.reps)
    }

    private fun insertPlanAndDay() {
        HistoricalDatabaseFixtures.exec(
            driver,
            "INSERT INTO planHistory(id, engineId, acceptedAt, weekNumber, cycleNumber) " +
                "VALUES (1, 'DETERMINISTIC', 42, 1, 1)"
        )
        HistoricalDatabaseFixtures.exec(
            driver,
            "INSERT INTO planHistoryDay(id, planId, dayIndex, focus) VALUES (7, 1, 0, 'PUSH')"
        )
    }

    /** The pre-55 schema stores reps as NOT NULL, so only those columns are needed here. */
    private fun insertCountedEntry(id: Long, position: Int, reps: Int) {
        HistoricalDatabaseFixtures.exec(
            driver,
            "INSERT INTO planHistoryEntry(id, dayId, position, exerciseId, sets, reps, " +
                "exerciseName, movementPattern, suggestedWeightKg, loadCapability, loadKind) " +
                "VALUES ($id, 7, $position, 'back-squat', 5, $reps, 'Back Squat', " +
                "'SQUAT', 100.0, 'EXTERNAL', 'EXTERNAL')"
        )
    }

    private fun sequenceHighWater() = HistoricalDatabaseFixtures.long(
        driver,
        "SELECT COALESCE((SELECT seq FROM sqlite_sequence WHERE name = 'planHistoryEntry'), 0)"
    )
}
