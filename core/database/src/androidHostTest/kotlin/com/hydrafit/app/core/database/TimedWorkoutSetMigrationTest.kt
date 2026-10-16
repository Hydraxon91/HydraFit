package com.hydrafit.app.core.database

import app.cash.sqldelight.db.SqlDriver
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Migration 52 -> 53 makes `workoutSet.reps` nullable and adds `durationSeconds`, so a set is
 * measured by repetitions or by elapsed whole seconds. SQLite cannot drop NOT NULL in place, so the
 * migration rebuilds the table: every stored row must keep its id, rep count, involvement/load/
 * timing snapshots and its session and occurrence links, the three indexes must be recreated, and
 * the AUTOINCREMENT high-water mark must be preserved so an id that was allocated and later deleted
 * is never handed out a second time.
 * A pre-existing (counted) row keeps its reps verbatim and gains a NULL duration; a new timed row
 * stores its seconds with NULL reps.
 */
class TimedWorkoutSetMigrationTest {

    private lateinit var driver: SqlDriver

    @AfterTest
    fun tearDown() {
        if (::driver.isInitialized) driver.close()
    }

    @Test
    fun migrationRebuildKeepsEveryStoredColumnAndLink() {
        driver = HistoricalDatabaseFixtures.v1()
        // 52 is the pre-timed schema: reps is NOT NULL and there is no durationSeconds column yet.
        HydraFitDatabaseSchema.migrate(driver, 1, 52)
        HistoricalDatabaseFixtures.exec(
            driver,
            "INSERT INTO workoutSet(id, exerciseId, reps, weightKg, performedAt, isWarmup, " +
                "involvements, weekNumber, cycleNumber, dayIndex, rir, sessionId, occurrenceId, " +
                "occurrenceEntryId, loadKind, timingProvenance, startedAtElapsedMillis, " +
                "completedAtElapsedMillis) VALUES " +
                "(41, 'back-squat', 5, 100.0, 10, 0, 'QUADS:1.0', 1, 2, 3, 2, 's1', 7, 9, " +
                "'EXTERNAL', 'LIVE', 100, 150)"
        )
        assertEquals(3L, indexCount())

        HydraFitDatabaseSchema.migrate(driver, 52, HydraFitDatabaseSchema.version)

        assertEquals(3L, indexCount())
        val database = HydraFitDatabase(driver)
        val preserved = database.workoutLogQueries.selectSetById(41L).executeAsOne()
        assertEquals(41L, preserved.id)
        assertEquals("back-squat", preserved.exerciseId)
        assertEquals(5L, preserved.reps)
        assertNull(preserved.durationSeconds)
        assertEquals(100.0, preserved.weightKg)
        assertEquals(10L, preserved.performedAt)
        assertEquals(0L, preserved.isWarmup)
        assertEquals("QUADS:1.0", preserved.involvements)
        assertEquals(1L, preserved.weekNumber)
        assertEquals(2L, preserved.cycleNumber)
        assertEquals(3L, preserved.dayIndex)
        assertEquals(2L, preserved.rir)
        assertEquals("s1", preserved.sessionId)
        assertEquals(7L, preserved.occurrenceId)
        assertEquals(9L, preserved.occurrenceEntryId)
        assertEquals("EXTERNAL", preserved.loadKind)
        assertEquals("LIVE", preserved.timingProvenance)
        assertEquals(100L, preserved.startedAtElapsedMillis)
        assertEquals(150L, preserved.completedAtElapsedMillis)

        // The rebuilt schema accepts a timed row: NULL reps with a positive stored duration.
        database.workoutLogQueries.insertSet(
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
            durationSeconds = 90
        )
        val timed = database.workoutLogQueries.selectAllSets().executeAsList()
            .first { it.durationSeconds != null }
        assertNull(timed.reps)
        assertEquals(90L, timed.durationSeconds)
    }

    @Test
    fun migrationKeepsTheAutoincrementHighWaterMarkOfADeletedSet() {
        driver = HistoricalDatabaseFixtures.v1()
        HydraFitDatabaseSchema.migrate(driver, 1, 52)
        insertCountedSetWithId(id = 1L, performedAt = 10)
        insertCountedSetWithId(id = 1000L, performedAt = 20)
        HistoricalDatabaseFixtures.exec(driver, "DELETE FROM workoutSet WHERE id = 1000")
        assertEquals(1000L, sequenceHighWater())

        HydraFitDatabaseSchema.migrate(driver, 52, HydraFitDatabaseSchema.version)

        // The rebuild copies only the surviving row (id 1), which must not reset the mark to 1.
        assertEquals(1000L, sequenceHighWater())
        val database = HydraFitDatabase(driver)
        database.workoutLogQueries.insertSet(
            exerciseId = "back-squat",
            reps = 5,
            weightKg = null,
            performedAt = 30,
            isWarmup = 0,
            involvements = null,
            weekNumber = null,
            cycleNumber = null,
            dayIndex = null,
            rir = null,
            sessionId = null,
            loadKind = "BODYWEIGHT",
            durationSeconds = null
        )
        assertEquals(1001L, database.workoutLogQueries.lastInsertedSetId().executeAsOne())
    }

    @Test
    fun migrationKeepsTheAutoincrementHighWaterMarkWhenEverySetWasDeleted() {
        driver = HistoricalDatabaseFixtures.v1()
        HydraFitDatabaseSchema.migrate(driver, 1, 52)
        insertCountedSetWithId(id = 500L, performedAt = 10)
        HistoricalDatabaseFixtures.exec(driver, "DELETE FROM workoutSet")
        assertEquals(500L, sequenceHighWater())

        HydraFitDatabaseSchema.migrate(driver, 52, HydraFitDatabaseSchema.version)

        // No row survives the rebuild, so the mark has to come from the captured old value alone.
        assertEquals(0L, HistoricalDatabaseFixtures.count(driver, "workoutSet"))
        assertEquals(500L, sequenceHighWater())
        val database = HydraFitDatabase(driver)
        database.workoutLogQueries.insertSet(
            exerciseId = "back-squat",
            reps = 5,
            weightKg = null,
            performedAt = 20,
            isWarmup = 0,
            involvements = null,
            weekNumber = null,
            cycleNumber = null,
            dayIndex = null,
            rir = null,
            sessionId = null,
            loadKind = "BODYWEIGHT",
            durationSeconds = null
        )
        assertEquals(501L, database.workoutLogQueries.lastInsertedSetId().executeAsOne())
    }

    @Test
    fun freshSchemaAlsoStoresTimedAndCountedSets() {
        driver = HistoricalDatabaseFixtures.empty()
        HydraFitDatabaseSchema.create(driver)
        val database = HydraFitDatabase(driver)

        database.workoutLogQueries.insertSet(
            exerciseId = "back-squat",
            reps = null,
            weightKg = null,
            performedAt = 1,
            isWarmup = 0,
            involvements = null,
            weekNumber = null,
            cycleNumber = null,
            dayIndex = null,
            rir = null,
            sessionId = null,
            loadKind = "BODYWEIGHT",
            durationSeconds = 60
        )
        database.workoutLogQueries.insertSet(
            exerciseId = "back-squat",
            reps = 8,
            weightKg = null,
            performedAt = 2,
            isWarmup = 0,
            involvements = null,
            weekNumber = null,
            cycleNumber = null,
            dayIndex = null,
            rir = null,
            sessionId = null,
            loadKind = "BODYWEIGHT",
            durationSeconds = null
        )

        val sets = database.workoutLogQueries.selectAllSets().executeAsList()
        // A database created at the current schema still numbers its first sets from 1.
        assertEquals(listOf(1L, 2L), sets.map { it.id })
        val timed = sets.first { it.durationSeconds != null }
        assertNull(timed.reps)
        assertEquals(60L, timed.durationSeconds)
        val counted = sets.first { it.durationSeconds == null }
        assertEquals(8L, counted.reps)
        assertEquals(3L, indexCount())
    }

    private fun indexCount() = HistoricalDatabaseFixtures.long(
        driver,
        "SELECT count(*) FROM sqlite_master WHERE type = 'index' AND name IN " +
            "('workoutSet_sessionId_performedAt', 'workoutSet_exerciseId_performedAt_id', " +
            "'workoutSet_occurrenceId_exerciseId')"
    )

    /** The pre-53 schema stores a set's rep count as NOT NULL, so only those columns are needed. */
    private fun insertCountedSetWithId(id: Long, performedAt: Long) {
        HistoricalDatabaseFixtures.exec(
            driver,
            "INSERT INTO workoutSet(id, exerciseId, reps, performedAt) " +
                "VALUES ($id, 'back-squat', 5, $performedAt)"
        )
    }

    /** The stored AUTOINCREMENT high-water mark, or 0 when `workoutSet` never held a row. */
    private fun sequenceHighWater() = HistoricalDatabaseFixtures.long(
        driver,
        "SELECT COALESCE((SELECT seq FROM sqlite_sequence WHERE name = 'workoutSet'), 0)"
    )
}
