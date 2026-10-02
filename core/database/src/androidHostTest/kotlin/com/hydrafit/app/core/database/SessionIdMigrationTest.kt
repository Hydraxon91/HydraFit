package com.hydrafit.app.core.database

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SessionIdMigrationTest {

    private lateinit var driver: SqlDriver

    @AfterTest
    fun tearDown() {
        if (::driver.isInitialized) driver.close()
    }

    @Test
    fun migratingFromV24AddsSessionColumnAsNullAndPreservesValues() {
        driver = v24Database()
        driver.execute(
            identifier = null,
            sql = "INSERT INTO workoutSet(exerciseId, reps, weightKg, performedAt, isWarmup, " +
                "involvements, weekNumber, cycleNumber, dayIndex, rir) " +
                "VALUES ('x', 5, 50.0, 1, 0, 'CHEST:1.0', 2, 1, 0, 3)",
            parameters = 0
        )

        HydraFitDatabase.Schema.migrate(driver, 24, 25)

        val row = HydraFitDatabase(driver).workoutLogQueries.selectAllSets().executeAsOne()
        assertNull(row.sessionId)
        assertEquals(5L, row.reps)
        assertEquals(50.0, row.weightKg)
        assertEquals("CHEST:1.0", row.involvements)
        assertEquals(2L, row.weekNumber)
        assertEquals(3L, row.rir)

        val sessions = HydraFitDatabase(driver).workoutSessionQueries.selectAllSessions()
            .executeAsList()
        assertTrue(sessions.isEmpty())
    }

    /** The v24 shape: workoutSet has rir but no sessionId, and workoutSession does not exist. */
    private fun v24Database(): SqlDriver {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        driver.execute(
            identifier = null,
            sql = "CREATE TABLE workoutSet (id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, " +
                "exerciseId TEXT NOT NULL, reps INTEGER NOT NULL, weightKg REAL, " +
                "performedAt INTEGER NOT NULL, isWarmup INTEGER NOT NULL DEFAULT 0, " +
                "involvements TEXT, weekNumber INTEGER, cycleNumber INTEGER, dayIndex INTEGER, " +
                "rir INTEGER)",
            parameters = 0
        )
        return driver
    }
}
