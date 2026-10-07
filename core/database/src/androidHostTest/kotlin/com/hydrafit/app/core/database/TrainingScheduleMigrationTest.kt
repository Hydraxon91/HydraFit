package com.hydrafit.app.core.database

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TrainingScheduleMigrationTest {

    private lateinit var driver: SqlDriver

    @AfterTest
    fun tearDown() {
        if (::driver.isInitialized) driver.close()
    }

    @Test
    fun migratingFromV26CreatesScheduleTablesAndAddsLoggingLinks() {
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        // Only the two tables 26.sqm alters need to pre-exist for this scoped fixture.
        driver.execute(
            identifier = null,
            sql = "CREATE TABLE workoutSet (" +
                "id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, exerciseId TEXT NOT NULL, " +
                "reps INTEGER NOT NULL, weightKg REAL, performedAt INTEGER NOT NULL, " +
                "isWarmup INTEGER NOT NULL DEFAULT 0, involvements TEXT, weekNumber INTEGER, " +
                "cycleNumber INTEGER, dayIndex INTEGER, rir INTEGER, sessionId TEXT)",
            parameters = 0
        )
        driver.execute(
            identifier = null,
            sql = "CREATE TABLE workoutSession (" +
                "id TEXT NOT NULL PRIMARY KEY, startedAtMillis INTEGER NOT NULL, " +
                "endedAtMillis INTEGER, localEpochDay INTEGER NOT NULL)",
            parameters = 0
        )

        HydraFitDatabase.Schema.migrate(driver, 26, 27)

        // Read the scoped old schema directly: the current generated queries expect the EX-02
        // columns added by 27.sqm (the next migration), which this 26→27 end version does not create.
        assertTrue(countOf("trainingActivation") == 0L)
        assertTrue(countOf("workoutOccurrence") == 0L)
        assertTrue(countOf("workoutScheduleState") == 0L)

        exec(
            "INSERT INTO workoutSet(exerciseId, reps, weightKg, performedAt, isWarmup, " +
                "involvements) VALUES ('bench-press', 8, 80.0, 1, 0, NULL)"
        )
        val setId = longOf("SELECT last_insert_rowid()")
        exec("UPDATE workoutSet SET occurrenceId = 4, occurrenceEntryId = 5 WHERE id = $setId")

        assertEquals(1L, countOf("workoutSet", "occurrenceId = 4"))
        assertEquals(1L, countOf("workoutSet", "occurrenceEntryId = 5"))
    }

    private fun exec(sql: String) {
        driver.execute(identifier = null, sql = sql, parameters = 0)
    }

    private fun countOf(table: String, where: String? = null): Long = longOf(
        "SELECT COUNT(*) FROM $table" + (where?.let { " WHERE $it" } ?: "")
    )

    private fun longOf(sql: String): Long = driver.executeQuery(
        identifier = null,
        sql = sql,
        mapper = { cursor ->
            cursor.next()
            QueryResult.Value(cursor.getLong(0) ?: 0L)
        },
        parameters = 0
    ).value
}
