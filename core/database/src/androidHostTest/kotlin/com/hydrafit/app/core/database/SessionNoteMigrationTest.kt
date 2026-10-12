package com.hydrafit.app.core.database

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlin.test.Test
import kotlin.test.assertEquals

class SessionNoteMigrationTest {

    @Test
    fun migratingFromV41CreatesAnEmptySessionNoteTableWithoutChangingSessionsOrSets() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        try {
            driver.execute(
                null,
                "CREATE TABLE workoutSession (id TEXT NOT NULL PRIMARY KEY, " +
                    "startedAtMillis INTEGER NOT NULL, endedAtMillis INTEGER, " +
                    "localEpochDay INTEGER NOT NULL, occurrenceId INTEGER)",
                0
            )
            driver.execute(
                null,
                "INSERT INTO workoutSession VALUES ('existing', 10, 20, 1, NULL)",
                0
            )
            driver.execute(
                null,
                "CREATE TABLE workoutSet (id INTEGER NOT NULL PRIMARY KEY)",
                0
            )
            driver.execute(null, "INSERT INTO workoutSet VALUES (7)", 0)

            HydraFitDatabase.Schema.migrate(driver, 41, 42)

            assertEquals(
                "existing",
                HistoricalDatabaseFixtures.text(driver, "SELECT id FROM workoutSession")
            )
            assertEquals(
                10L,
                HistoricalDatabaseFixtures.long(
                    driver,
                    "SELECT startedAtMillis FROM workoutSession"
                )
            )
            assertEquals(
                20L,
                HistoricalDatabaseFixtures.long(driver, "SELECT endedAtMillis FROM workoutSession")
            )
            assertEquals(
                1L,
                HistoricalDatabaseFixtures.long(driver, "SELECT localEpochDay FROM workoutSession")
            )
            assertEquals(1L, HistoricalDatabaseFixtures.count(driver, "workoutSet"))
            assertEquals(0L, HistoricalDatabaseFixtures.count(driver, "sessionNote"))
        } finally {
            driver.close()
        }
    }
}
