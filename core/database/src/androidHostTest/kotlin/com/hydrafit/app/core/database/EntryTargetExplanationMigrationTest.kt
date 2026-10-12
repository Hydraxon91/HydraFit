package com.hydrafit.app.core.database

import app.cash.sqldelight.db.SqlDriver
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Migration 39 -> 40 creates the recorded reasoning table for frozen activation/occurrence entries.
 * Existing databases have no recorded reasons, so the table starts empty and reads as "not recorded".
 */
class EntryTargetExplanationMigrationTest {

    private lateinit var driver: SqlDriver

    @AfterTest
    fun tearDown() {
        if (::driver.isInitialized) driver.close()
    }

    @Test
    fun migratingFromV39CreatesAnEmptyEntryTargetExplanationTable() {
        driver = HistoricalDatabaseFixtures.empty()

        HydraFitDatabase.Schema.migrate(driver, 39, 40)

        assertEquals(0L, HistoricalDatabaseFixtures.count(driver, "entryTargetExplanation"))

        HistoricalDatabaseFixtures.exec(
            driver,
            "INSERT INTO entryTargetExplanation(entryKind, entryId, explanationVersion) " +
                "VALUES ('ACTIVATION', 1, 1)"
        )

        assertEquals(1L, HistoricalDatabaseFixtures.count(driver, "entryTargetExplanation"))
    }
}
