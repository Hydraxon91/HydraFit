package com.hydrafit.app.core.database

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
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
        driver = v23Database()
        driver.execute(
            identifier = null,
            sql = "INSERT INTO workoutSet(exerciseId, reps, weightKg, performedAt, isWarmup, " +
                "involvements, weekNumber, cycleNumber, dayIndex) " +
                "VALUES ('x', 5, 50.0, 1, 0, 'CHEST:1.0', 2, 1, 0)",
            parameters = 0
        )

        // Scoped to 23→24: this test asserts only the rir column added here. Reading the scoped
        // schema directly avoids the later columns the current generated query expects.
        HydraFitDatabase.Schema.migrate(driver, 23, 24)

        val row = driver.executeQuery(
            identifier = null,
            sql = "SELECT rir, reps, weightKg, involvements, weekNumber FROM workoutSet",
            mapper = { cursor ->
                cursor.next()
                QueryResult.Value(
                    listOf(
                        cursor.getString(0),
                        cursor.getLong(1),
                        cursor.getDouble(2),
                        cursor.getString(3),
                        cursor.getLong(4)
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
    }

    /** The v23 shape: workoutSet has week/cycle/day but no rir column yet. */
    private fun v23Database(): SqlDriver {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        driver.execute(
            identifier = null,
            sql = "CREATE TABLE workoutSet (id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, " +
                "exerciseId TEXT NOT NULL, reps INTEGER NOT NULL, weightKg REAL, " +
                "performedAt INTEGER NOT NULL, isWarmup INTEGER NOT NULL DEFAULT 0, " +
                "involvements TEXT, weekNumber INTEGER, cycleNumber INTEGER, dayIndex INTEGER)",
            parameters = 0
        )
        return driver
    }
}
