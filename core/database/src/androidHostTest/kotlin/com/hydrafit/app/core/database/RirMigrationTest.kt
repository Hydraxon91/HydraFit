package com.hydrafit.app.core.database

import app.cash.sqldelight.db.SqlDriver
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RirMigrationTest {

    private lateinit var driver: SqlDriver

    @AfterTest
    fun tearDown() {
        if (::driver.isInitialized) driver.close()
    }

    @Test
    fun migratingFromV23DefaultsRirToNullAndRetainsValues() {
        driver = HistoricalDatabaseFixtures.v23()
        HistoricalDatabaseFixtures.exec(
            driver,
            "INSERT INTO workoutSet(exerciseId, reps, weightKg, performedAt, isWarmup, " +
                "involvements, weekNumber, cycleNumber, dayIndex) " +
                "VALUES ('x', 5, 50.0, 1, 0, 'CHEST:1.0', 2, 1, 0)"
        )

        // Scoped to 23→24: this test asserts only the rir column added here. Reading the scoped
        // schema directly avoids the later columns the current generated query expects.
        HydraFitDatabase.Schema.migrate(driver, 23, 24)

        assertNull(HistoricalDatabaseFixtures.text(driver, "SELECT rir FROM workoutSet"))
        assertEquals(5L, HistoricalDatabaseFixtures.long(driver, "SELECT reps FROM workoutSet"))
        assertEquals(
            50.0,
            HistoricalDatabaseFixtures.double(driver, "SELECT weightKg FROM workoutSet")
        )
        assertEquals(
            "CHEST:1.0",
            HistoricalDatabaseFixtures.text(driver, "SELECT involvements FROM workoutSet")
        )
        assertEquals(
            2L,
            HistoricalDatabaseFixtures.long(driver, "SELECT weekNumber FROM workoutSet")
        )
    }
}
