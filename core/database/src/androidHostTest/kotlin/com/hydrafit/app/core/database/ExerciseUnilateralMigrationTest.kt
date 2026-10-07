package com.hydrafit.app.core.database

import app.cash.sqldelight.db.SqlDriver
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ExerciseUnilateralMigrationTest {

    private lateinit var driver: SqlDriver

    @AfterTest
    fun tearDown() {
        if (::driver.isInitialized) driver.close()
    }

    @Test
    fun migratingFromV16DefaultsUnilateralToOff() {
        driver = HistoricalDatabaseFixtures.v16()
        HistoricalDatabaseFixtures.exec(
            driver,
            "INSERT INTO exercise(id, name, requiredEquipment, primaryMuscles, " +
                "secondaryMuscles, movementPattern) " +
                "VALUES ('x', 'X', '', 'CHEST', '', 'HORIZONTAL_PUSH')"
        )
        HistoricalDatabaseFixtures.exec(
            driver,
            "INSERT INTO exerciseOverride(exerciseId, name) VALUES ('x', 'Y')"
        )

        HydraFitDatabase.Schema.migrate(driver, 16, HydraFitDatabase.Schema.version)

        val database = HydraFitDatabase(driver)
        assertEquals(0L, database.exerciseQueries.selectById("x").executeAsOne().isUnilateral)
        assertNull(database.exerciseOverrideQueries.selectById("x").executeAsOne().isUnilateral)
    }
}
