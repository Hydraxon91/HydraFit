package com.hydrafit.app.core.database

import app.cash.sqldelight.db.SqlDriver
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Migration 43 -> 44 adds the `(sessionId, performedAt)` index the history reads rely on. It is a
 * schema addition only: it must exist after the chain and must not change stored sets.
 */
class WorkoutSetSessionIndexMigrationTest {

    private lateinit var driver: SqlDriver

    @AfterTest
    fun tearDown() {
        if (::driver.isInitialized) driver.close()
    }

    @Test
    fun theWholeChainCreatesTheSessionIndex() {
        driver = HistoricalDatabaseFixtures.v1()

        HydraFitDatabase.Schema.migrate(driver, 1, HydraFitDatabase.Schema.version)

        assertEquals(
            "index",
            HistoricalDatabaseFixtures.text(
                driver,
                "SELECT type FROM sqlite_master WHERE name = 'workoutSet_sessionId_performedAt'"
            )
        )
    }

    @Test
    fun migratingFromThePreviousVersionAddsTheIndexAndKeepsStoredSets() {
        driver = HistoricalDatabaseFixtures.v1()
        HydraFitDatabase.Schema.migrate(driver, 1, 43)
        HistoricalDatabaseFixtures.exec(
            driver,
            "INSERT INTO workoutSession(id, startedAtMillis, localEpochDay) " +
                "VALUES ('s1', 100, 0)"
        )
        HistoricalDatabaseFixtures.exec(
            driver,
            "INSERT INTO workoutSet(exerciseId, reps, weightKg, performedAt, isWarmup, loadKind, " +
                "sessionId) VALUES ('back-squat', 5, 100.0, 10, 0, 'EXTERNAL', 's1')"
        )

        HydraFitDatabase.Schema.migrate(driver, 43, HydraFitDatabase.Schema.version)

        assertEquals(
            "index",
            HistoricalDatabaseFixtures.text(
                driver,
                "SELECT type FROM sqlite_master WHERE name = 'workoutSet_sessionId_performedAt'"
            )
        )
        assertEquals(1L, HistoricalDatabaseFixtures.count(driver, "workoutSet"))
        assertEquals(
            "s1",
            HistoricalDatabaseFixtures.text(
                driver,
                "SELECT sessionId FROM workoutSet"
            )
        )
    }
}
