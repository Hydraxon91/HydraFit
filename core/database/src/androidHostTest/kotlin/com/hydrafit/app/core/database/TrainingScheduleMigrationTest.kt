package com.hydrafit.app.core.database

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
        val database = HydraFitDatabase(driver)

        assertTrue(
            database.trainingScheduleQueries.selectAllActivations().executeAsList().isEmpty()
        )
        assertTrue(
            database.trainingScheduleQueries.selectAllOccurrences().executeAsList().isEmpty()
        )
        assertEquals(
            null,
            database.trainingScheduleQueries.selectScheduleState().executeAsOneOrNull()
        )

        database.workoutLogQueries.insertSet(
            exerciseId = "bench-press",
            reps = 8,
            weightKg = 80.0,
            performedAt = 1L,
            isWarmup = 0L,
            involvements = null,
            weekNumber = null,
            cycleNumber = null,
            dayIndex = null,
            rir = null,
            sessionId = null
        )
        val setId = database.workoutLogQueries.lastInsertedSetId().executeAsOne()
        database.workoutLogQueries.assignOccurrence(
            occurrenceId = 4L,
            occurrenceEntryId = 5L,
            id = setId
        )

        assertEquals(1, database.workoutLogQueries.selectSetsForOccurrence(4L).executeAsList().size)
        assertEquals(
            1,
            database.workoutLogQueries.selectSetsForOccurrenceEntry(5L).executeAsList().size
        )
    }
}
