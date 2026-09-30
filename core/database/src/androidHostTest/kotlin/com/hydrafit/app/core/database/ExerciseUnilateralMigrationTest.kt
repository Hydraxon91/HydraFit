package com.hydrafit.app.core.database

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
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
        driver = v16Database()
        driver.execute(
            identifier = null,
            sql = "INSERT INTO exercise(id, name, requiredEquipment, primaryMuscles, " +
                "secondaryMuscles, movementPattern) VALUES ('x', 'X', '', 'CHEST', '', 'HORIZONTAL_PUSH')",
            parameters = 0
        )
        driver.execute(
            identifier = null,
            sql = "INSERT INTO exerciseOverride(exerciseId, name) VALUES ('x', 'Y')",
            parameters = 0
        )

        HydraFitDatabase.Schema.migrate(driver, 16, 17)

        val database = HydraFitDatabase(driver)
        assertEquals(0L, database.exerciseQueries.selectById("x").executeAsOne().isUnilateral)
        assertNull(database.exerciseOverrideQueries.selectById("x").executeAsOne().isUnilateral)
    }

    /** The v16 shape: neither the exercise nor the override table has the unilateral column. */
    private fun v16Database(): SqlDriver {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        val statements = listOf(
            "CREATE TABLE exercise (id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, " +
                "requiredEquipment TEXT NOT NULL, primaryMuscles TEXT NOT NULL, " +
                "secondaryMuscles TEXT NOT NULL, movementPattern TEXT NOT NULL, " +
                "isCustom INTEGER NOT NULL DEFAULT 0)",
            "CREATE TABLE exerciseOverride (exerciseId TEXT NOT NULL PRIMARY KEY, name TEXT, " +
                "requiredEquipment TEXT, primaryMuscles TEXT, secondaryMuscles TEXT, " +
                "movementPattern TEXT)"
        )
        statements.forEach { driver.execute(identifier = null, sql = it, parameters = 0) }
        return driver
    }
}
