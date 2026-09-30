package com.hydrafit.app.core.database

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertNull

class WorkoutSetWeekDayMigrationTest {

    private lateinit var driver: SqlDriver

    @AfterTest
    fun tearDown() {
        if (::driver.isInitialized) driver.close()
    }

    @Test
    fun migratingFromV22DefaultsTheWeekDayContextToNull() {
        driver = v22Database()
        driver.execute(
            identifier = null,
            sql = "INSERT INTO workoutSet(exerciseId, reps, weightKg, performedAt, isWarmup) " +
                "VALUES ('x', 5, 50.0, 1, 0)",
            parameters = 0
        )

        HydraFitDatabase.Schema.migrate(driver, 22, 23)

        val row = HydraFitDatabase(driver).workoutLogQueries.selectAllSets().executeAsOne()
        assertNull(row.weekNumber)
        assertNull(row.cycleNumber)
        assertNull(row.dayIndex)
    }

    /** The v22 shape: workoutSet has no week/cycle/day columns yet. */
    private fun v22Database(): SqlDriver {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        driver.execute(
            identifier = null,
            sql = "CREATE TABLE workoutSet (id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, " +
                "exerciseId TEXT NOT NULL, reps INTEGER NOT NULL, weightKg REAL, " +
                "performedAt INTEGER NOT NULL, isWarmup INTEGER NOT NULL DEFAULT 0, " +
                "involvements TEXT)",
            parameters = 0
        )
        return driver
    }
}
