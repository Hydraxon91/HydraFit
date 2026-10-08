package com.hydrafit.app.core.database

import app.cash.sqldelight.db.SqlDriver
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Full additive chain from the v0.3.1 schema (28) to the current schema. Every 0.4.0 migration is
 * additive, so a v0.3.1 database must upgrade in one pass, create all new tables, and keep existing
 * rows. Scoped per-migration tests live in the `*MigrationTest` classes; this proves the chain.
 */
class FullMigrationChainTest {

    private lateinit var driver: SqlDriver

    @AfterTest
    fun tearDown() {
        if (::driver.isInitialized) driver.close()
    }

    @Test
    fun migratingFromTheV0_3_1SchemaAddsAllNewTablesAndRetainsData() {
        driver = HistoricalDatabaseFixtures.empty()
        exec(
            "CREATE TABLE personalRecord (exerciseId TEXT NOT NULL PRIMARY KEY, " +
                "weightKg REAL NOT NULL, reps INTEGER NOT NULL, updatedAt INTEGER NOT NULL)"
        )
        exec(
            "INSERT INTO personalRecord(exerciseId, weightKg, reps, updatedAt) " +
                "VALUES ('back-squat', 100.0, 5, 1)"
        )

        HydraFitDatabase.Schema.migrate(driver, 28, 32)

        assertEquals(0L, HistoricalDatabaseFixtures.count(driver, "exercisePreference"))
        assertEquals(0L, HistoricalDatabaseFixtures.count(driver, "exerciseExclusion"))
        assertEquals(0L, HistoricalDatabaseFixtures.count(driver, "planVolumeExplanation"))
        assertEquals(0L, HistoricalDatabaseFixtures.count(driver, "planVolumeExplanationState"))
        // Pre-existing rows survive the whole chain untouched.
        assertEquals(1L, HistoricalDatabaseFixtures.count(driver, "personalRecord"))
        assertEquals(
            100.0,
            HistoricalDatabaseFixtures.double(driver, "SELECT weightKg FROM personalRecord")
        )
    }

    private fun exec(sql: String) = HistoricalDatabaseFixtures.exec(driver, sql)
}
