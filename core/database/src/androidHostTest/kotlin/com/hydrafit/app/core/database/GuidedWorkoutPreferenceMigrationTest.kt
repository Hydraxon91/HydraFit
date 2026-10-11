package com.hydrafit.app.core.database

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlin.test.Test
import kotlin.test.assertEquals

class GuidedWorkoutPreferenceMigrationTest {

    @Test
    fun migrationAddsThePreferenceDisabledByDefaultAndPreservesExistingSettings() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        try {
            driver.execute(
                identifier = null,
                sql = "CREATE TABLE plannerEngine (id INTEGER NOT NULL PRIMARY KEY, " +
                    "engineId TEXT NOT NULL, daysPerWeek INTEGER NOT NULL DEFAULT 4, " +
                    "trainingGoal TEXT NOT NULL DEFAULT 'BALANCED', " +
                    "shareWorkoutData INTEGER NOT NULL DEFAULT 0, " +
                    "weightUnit TEXT NOT NULL DEFAULT 'KG')",
                parameters = 0
            )
            driver.execute(
                identifier = null,
                sql = "INSERT INTO plannerEngine(id, engineId, weightUnit) " +
                    "VALUES (0, 'DETERMINISTIC', 'LB')",
                parameters = 0
            )

            HydraFitDatabase.Schema.migrate(driver, 33, 34)

            val database = HydraFitDatabase(driver)
            assertEquals("LB", database.plannerEngineQueries.selectWeightUnit().executeAsOne())
            assertEquals(
                0L,
                database.plannerEngineQueries.selectGuidedWorkoutEnabled().executeAsOne()
            )
        } finally {
            driver.close()
        }
    }
}
