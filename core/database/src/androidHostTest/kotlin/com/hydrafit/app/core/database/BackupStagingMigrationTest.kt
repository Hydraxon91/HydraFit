package com.hydrafit.app.core.database

import app.cash.sqldelight.db.SqlDriver
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * OF-01 staged-apply migration 32 -> 33. Additive: a v32 database gains the staging and apply-error
 * tables with no data change, so existing rows are untouched.
 */
class BackupStagingMigrationTest {

    private lateinit var driver: SqlDriver

    @AfterTest
    fun tearDown() {
        if (::driver.isInitialized) driver.close()
    }

    @Test
    fun migratingFromV32AddsTheStagingTables() {
        driver = HistoricalDatabaseFixtures.empty()
        HistoricalDatabaseFixtures.exec(
            driver,
            "CREATE TABLE workoutSet (id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, " +
                "exerciseId TEXT NOT NULL, reps INTEGER NOT NULL)"
        )
        HistoricalDatabaseFixtures.exec(
            driver,
            "INSERT INTO workoutSet(exerciseId, reps) VALUES ('back-squat', 5)"
        )

        HydraFitDatabase.Schema.migrate(driver, 32, 33)

        assertEquals(0L, HistoricalDatabaseFixtures.count(driver, "stagedBackup"))
        assertEquals(0L, HistoricalDatabaseFixtures.count(driver, "lastBackupApplyError"))
        // Pre-existing rows survive the migration untouched.
        assertEquals(1L, HistoricalDatabaseFixtures.count(driver, "workoutSet"))
    }
}
