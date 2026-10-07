package com.hydrafit.app.core.database

import app.cash.sqldelight.db.SqlDriver
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertNull

class InvolvementsMigrationTest {

    private lateinit var driver: SqlDriver

    @AfterTest
    fun tearDown() {
        if (::driver.isInitialized) driver.close()
    }

    @Test
    fun migratingFromV18DefaultsInvolvementsToNull() {
        driver = HistoricalDatabaseFixtures.v18()
        exec(
            "INSERT INTO exercise(id, name, requiredEquipment, primaryMuscles, secondaryMuscles, " +
                "movementPattern, isCustom, isUnilateral) " +
                "VALUES ('x', 'X', '', 'CHEST', '', 'SQUAT', 0, 0)"
        )
        exec("INSERT INTO exerciseOverride(exerciseId, name) VALUES ('x', 'Y')")
        exec(
            "INSERT INTO workoutSet(exerciseId, reps, weightKg, performedAt, isWarmup, " +
                "primaryMuscles, secondaryMuscles) VALUES ('x', 5, 50.0, 1, 0, 'CHEST', '')"
        )

        // Scoped to 18→19: this test asserts the columns default to null, before the v20 conversion.
        HydraFitDatabase.Schema.migrate(driver, 18, 19)

        // Read the scoped old schema directly: the current generated queries expect columns added
        // later (workoutSet.rir, exercise.loadCapability), which do not exist at v19.
        assertNull(HistoricalDatabaseFixtures.text(driver, "SELECT involvements FROM exercise"))
        assertNull(
            HistoricalDatabaseFixtures.text(
                driver,
                "SELECT involvements FROM exerciseOverride WHERE exerciseId = 'x'"
            )
        )
        assertNull(HistoricalDatabaseFixtures.text(driver, "SELECT involvements FROM workoutSet"))
    }

    private fun exec(sql: String) {
        HistoricalDatabaseFixtures.exec(driver, sql)
    }
}
