package com.hydrafit.app.core.database

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class PlanHistoryMigrationTest {

    private lateinit var driver: SqlDriver

    @AfterTest
    fun tearDown() {
        if (::driver.isInitialized) driver.close()
    }

    @Test
    fun migratingFromV15DefaultsWeekAndCycleToOne() {
        driver = v15Database()
        driver.execute(
            identifier = null,
            sql = "INSERT INTO planHistory(engineId, acceptedAt) VALUES ('DETERMINISTIC', 42)",
            parameters = 0
        )

        HydraFitDatabase.Schema.migrate(driver, 15, HydraFitDatabase.Schema.version)
        val row = HydraFitDatabase(driver).planHistoryQueries.selectLatestPlan().executeAsOne()

        assertEquals(1L, row.weekNumber)
        assertEquals(1L, row.cycleNumber)
    }

    /** The v15 schema shape: planHistory has no week/cycle columns yet. */
    private fun v15Database(): SqlDriver {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        val statements = listOf(
            "CREATE TABLE planHistory (id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, " +
                "engineId TEXT NOT NULL, acceptedAt INTEGER NOT NULL)",
            "CREATE TABLE planHistoryDay (id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, " +
                "planId INTEGER NOT NULL, dayIndex INTEGER NOT NULL, focus TEXT NOT NULL)",
            "CREATE TABLE planHistoryEntry (id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, " +
                "dayId INTEGER NOT NULL, position INTEGER NOT NULL, exerciseId TEXT NOT NULL, " +
                "sets INTEGER NOT NULL, reps INTEGER NOT NULL, exerciseName TEXT NOT NULL, " +
                "movementPattern TEXT NOT NULL, suggestedWeightKg REAL)"
        )
        statements.forEach { driver.execute(identifier = null, sql = it, parameters = 0) }
        return driver
    }
}
