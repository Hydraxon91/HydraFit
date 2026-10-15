package com.hydrafit.app.core.database

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
        database.workoutLogQueries.insertSet(
            "squat", 10, 60.0, 100, 0, null, null, null, null, null, null, "EXTERNAL"
        )
        val before = database.workoutLogQueries.selectAllSets().executeAsList()
        assertEquals(0L, tableCount())

        HydraFitDatabase.Schema.migrate(driver, 48, 49)

        assertEquals(2L, tableCount())
        assertEquals(before, database.workoutLogQueries.selectAllSets().executeAsList())
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
        assertEquals(51L, HydraFitDatabase.Schema.version)
        assertEquals(2L, tableCount())
    }

    private fun tableCount(): Long = HistoricalDatabaseFixtures.long(
        driver,
        "SELECT count(*) FROM sqlite_master WHERE type = 'table' " +
            "AND name IN ('bodyWeightMeasurement', 'bodyWeightGoal')"
    )
}
