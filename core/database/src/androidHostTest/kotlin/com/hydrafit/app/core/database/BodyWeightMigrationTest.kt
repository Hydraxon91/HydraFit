package com.hydrafit.app.core.database

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Migration 48 -> 49 adds the body-weight measurement and goal tables. It is a schema addition
 * only: existing rows must survive and a fresh schema must create the same tables.
 */
class BodyWeightMigrationTest {
    private lateinit var driver: SqlDriver

    @AfterTest
    fun tearDown() {
        if (::driver.isInitialized) driver.close()
    }

    @Test
    fun migrationAddsBodyWeightTablesWithoutChangingExistingRows() {
        driver = HistoricalDatabaseFixtures.v1()
        HydraFitDatabase.Schema.migrate(driver, 1, 48)
        val database = HydraFitDatabase(driver)
        database.exerciseQueries.insert("squat", "Squat", "BARBELL", "SQUAT", "EXTERNAL")
        // Schema 53 adds `durationSeconds`, so the generated set queries can no longer run against
        // the 48 schema; write and read the stored row with raw SQL that matches it.
        HistoricalDatabaseFixtures.exec(
            driver,
            "INSERT INTO workoutSet(exerciseId, reps, weightKg, performedAt, isWarmup, loadKind) " +
                "VALUES ('squat', 10, 60.0, 100, 0, 'EXTERNAL')"
        )
        val before = storedSets()
        assertEquals(0L, tableCount())

        HydraFitDatabase.Schema.migrate(driver, 48, 49)

        assertEquals(2L, tableCount())
        assertEquals(before, storedSets())
        database.bodyWeightQueries.insertMeasurement(5L, 82.5)
        database.bodyWeightQueries.upsertGoal(78.0)
        val measurement = database.bodyWeightQueries.selectAllMeasurements().executeAsOne()
        assertEquals(82.5, measurement.weightKg)
        assertEquals(78.0, database.bodyWeightQueries.selectGoal().executeAsOne().weightKg)
    }

    @Test
    fun freshSchemaAlsoCreatesBodyWeightTables() {
        driver = HistoricalDatabaseFixtures.empty()
        HydraFitDatabase.Schema.create(driver)
        assertEquals(56L, HydraFitDatabase.Schema.version)
        assertEquals(2L, tableCount())
    }

    /** Every stored set column as text: a pre-53 database has no `durationSeconds` to select. */
    private fun storedSets(): List<List<String?>> = driver.executeQuery(
        identifier = null,
        sql = "SELECT id, exerciseId, reps, weightKg, performedAt, isWarmup, involvements, " +
            "weekNumber, cycleNumber, dayIndex, rir, sessionId, occurrenceId, occurrenceEntryId, " +
            "loadKind, timingProvenance, startedAtElapsedMillis, completedAtElapsedMillis " +
            "FROM workoutSet ORDER BY performedAt, id",
        mapper = { cursor ->
            val rows = mutableListOf<List<String?>>()
            while (cursor.next().value) rows += (0 until 18).map { cursor.getString(it) }
            QueryResult.Value(rows)
        },
        parameters = 0
    ).value

    private fun tableCount(): Long = HistoricalDatabaseFixtures.long(
        driver,
        "SELECT count(*) FROM sqlite_master WHERE type = 'table' " +
            "AND name IN ('bodyWeightMeasurement', 'bodyWeightGoal')"
    )
}
