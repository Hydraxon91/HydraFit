package com.hydrafit.app.core.database

import app.cash.sqldelight.db.SqlDriver
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * C2G migration 28 -> 29. The scoped fixture starts from a minimal schema and migrates with an
 * explicit end version (29), so a later migration cannot leak into this test's assertions. The new
 * `exercisePreference` table is additive: it must not touch existing rows.
 */
class ExercisePreferenceMigrationTest {

    private lateinit var driver: SqlDriver

    @AfterTest
    fun tearDown() {
        if (::driver.isInitialized) driver.close()
    }

    @Test
    fun migratingFromV28AddsThePreferenceTableWithoutTouchingExistingRows() {
        driver = HistoricalDatabaseFixtures.empty()
        exec(
            "CREATE TABLE personalRecord (exerciseId TEXT NOT NULL PRIMARY KEY, " +
                "weightKg REAL NOT NULL, reps INTEGER NOT NULL, updatedAt INTEGER NOT NULL)"
        )
        exec(
            "INSERT INTO personalRecord(exerciseId, weightKg, reps, updatedAt) " +
                "VALUES ('back-squat', 100.0, 5, 1)"
        )

        HydraFitDatabase.Schema.migrate(driver, 28, 29)

        assertEquals(0L, count("exercisePreference"))
        // The pre-existing table and row are untouched.
        assertEquals(1L, count("personalRecord"))
        assertEquals(100.0, histDouble("SELECT weightKg FROM personalRecord"))

        // The new table accepts and returns an explicit preference row.
        exec(
            "INSERT INTO exercisePreference(exerciseId, preference) " +
                "VALUES ('back-squat', 'PREFER')"
        )
        assertEquals("PREFER", histText("SELECT preference FROM exercisePreference"))
    }

    @Test
    fun migratingALaterVersionLeavesNoPreferenceRowByDefault() {
        driver = HistoricalDatabaseFixtures.empty()
        HydraFitDatabase.Schema.migrate(driver, 28, 29)
        assertNull(histText("SELECT preference FROM exercisePreference"))
    }

    private fun exec(sql: String) = HistoricalDatabaseFixtures.exec(driver, sql)

    private fun count(table: String): Long = HistoricalDatabaseFixtures.count(driver, table)

    private fun histText(sql: String): String? = HistoricalDatabaseFixtures.text(driver, sql)

    private fun histDouble(sql: String): Double? = HistoricalDatabaseFixtures.double(driver, sql)
}
