package com.hydrafit.app.core.database

import app.cash.sqldelight.db.SqlDriver
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * EX-01 migration 29 -> 30. The scoped fixture migrates with an explicit end version (30) so a later
 * migration cannot leak in. The new `exerciseExclusion` table is additive.
 */
class ExerciseExclusionMigrationTest {

    private lateinit var driver: SqlDriver

    @AfterTest
    fun tearDown() {
        if (::driver.isInitialized) driver.close()
    }

    @Test
    fun migratingFromV29AddsTheExclusionTableWithoutTouchingExistingRows() {
        driver = HistoricalDatabaseFixtures.empty()
        exec(
            "CREATE TABLE personalRecord (exerciseId TEXT NOT NULL PRIMARY KEY, " +
                "weightKg REAL NOT NULL, reps INTEGER NOT NULL, updatedAt INTEGER NOT NULL)"
        )
        exec(
            "INSERT INTO personalRecord(exerciseId, weightKg, reps, updatedAt) " +
                "VALUES ('back-squat', 100.0, 5, 1)"
        )

        HydraFitDatabase.Schema.migrate(driver, 29, 30)

        assertEquals(0L, HistoricalDatabaseFixtures.count(driver, "exerciseExclusion"))
        assertEquals(1L, HistoricalDatabaseFixtures.count(driver, "personalRecord"))
        assertEquals(
            100.0,
            HistoricalDatabaseFixtures.double(driver, "SELECT weightKg FROM personalRecord")
        )

        // Both an indefinite (NULL) and a dated exclusion round-trip through the new table.
        exec("INSERT INTO exerciseExclusion(exerciseId, expiresAt) VALUES ('back-squat', NULL)")
        exec("INSERT INTO exerciseExclusion(exerciseId, expiresAt) VALUES ('bench-press', 1000)")
        assertNull(
            HistoricalDatabaseFixtures.text(
                driver,
                "SELECT expiresAt FROM exerciseExclusion WHERE exerciseId = 'back-squat'"
            )
        )
        assertEquals(
            1000L,
            HistoricalDatabaseFixtures.long(
                driver,
                "SELECT expiresAt FROM exerciseExclusion WHERE exerciseId = 'bench-press'"
            )
        )
    }

    private fun exec(sql: String) = HistoricalDatabaseFixtures.exec(driver, sql)
}
