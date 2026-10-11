package com.hydrafit.app.core.database

import app.cash.sqldelight.db.SqlDriver
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Migration 38 -> 39 creates the recorded plan-target explanation tables. Existing databases have no
 * recorded reasons, so the tables start empty and are read as "not recorded" rather than as an empty
 * explanation.
 */
class TargetExplanationMigrationTest {

    private lateinit var driver: SqlDriver

    @AfterTest
    fun tearDown() {
        if (::driver.isInitialized) driver.close()
    }

    @Test
    fun migratingFromV38CreatesEmptyTargetExplanationTables() {
        driver = HistoricalDatabaseFixtures.empty()

        HydraFitDatabase.Schema.migrate(driver, 38, 39)

        assertEquals(0L, HistoricalDatabaseFixtures.count(driver, "planTargetExplanation"))
        assertEquals(0L, HistoricalDatabaseFixtures.count(driver, "planTargetExplanationState"))

        HistoricalDatabaseFixtures.exec(
            driver,
            "INSERT INTO planTargetExplanationState VALUES (7, 'DETERMINISTIC', 0, 0.7, 0.8, 1)"
        )
        HistoricalDatabaseFixtures.exec(
            driver,
            "INSERT INTO planTargetExplanation(planId, dayIndex, position, explanationVersion) " +
                "VALUES (7, 0, 1, 1)"
        )

        assertEquals(1L, HistoricalDatabaseFixtures.count(driver, "planTargetExplanation"))
        assertEquals(1L, HistoricalDatabaseFixtures.count(driver, "planTargetExplanationState"))
    }
}
