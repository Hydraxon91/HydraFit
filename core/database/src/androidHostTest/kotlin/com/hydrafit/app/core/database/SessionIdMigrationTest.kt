package com.hydrafit.app.core.database

import app.cash.sqldelight.db.QueryResult
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

        // Read the scoped old schema directly: the current generated workoutSet query expects the
        // later occurrence columns, which do not exist at v25.
        val row = driver.executeQuery(
            identifier = null,
            sql = "SELECT sessionId, reps, weightKg, involvements, weekNumber, rir FROM workoutSet",
            mapper = { cursor ->
                cursor.next()
                QueryResult.Value(
                    listOf<Any?>(
                        cursor.getString(0),
                        cursor.getLong(1),
                        cursor.getDouble(2),
                        cursor.getString(3),
                        cursor.getLong(4),
                        cursor.getLong(5)
                    )
                )
            },
            parameters = 0
        ).value
        assertNull(row[0])
        assertEquals(5L, row[1])
        assertEquals(50.0, row[2])
        assertEquals("CHEST:1.0", row[3])
        assertEquals(2L, row[4])
        assertEquals(3L, row[5])

        val sessions = driver.executeQuery(
            identifier = null,
            sql = "SELECT COUNT(*) FROM workoutSession",
            mapper = { cursor ->
                cursor.next()
                QueryResult.Value(cursor.getLong(0))
            },
            parameters = 0
        ).value
        assertTrue(sessions == 0L)
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
