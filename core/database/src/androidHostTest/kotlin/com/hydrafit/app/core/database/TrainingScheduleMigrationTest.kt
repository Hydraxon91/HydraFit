package com.hydrafit.app.core.database

import app.cash.sqldelight.db.SqlDriver
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
        driver = HistoricalDatabaseFixtures.v26()

        HydraFitDatabase.Schema.migrate(driver, 26, 27)

        // Read the scoped old schema directly: the current generated queries expect the EX-02
        // columns added by 27.sqm (the next migration), which this 26→27 end version does not create.
        assertTrue(HistoricalDatabaseFixtures.count(driver, "trainingActivation") == 0L)
        assertTrue(HistoricalDatabaseFixtures.count(driver, "workoutOccurrence") == 0L)
        assertTrue(HistoricalDatabaseFixtures.count(driver, "workoutScheduleState") == 0L)

        exec(
            "INSERT INTO workoutSet(exerciseId, reps, weightKg, performedAt, isWarmup, " +
                "involvements) VALUES ('bench-press', 8, 80.0, 1, 0, NULL)"
        )
        val setId = HistoricalDatabaseFixtures.long(driver, "SELECT last_insert_rowid()")
        exec("UPDATE workoutSet SET occurrenceId = 4, occurrenceEntryId = 5 WHERE id = $setId")

        assertEquals(
            1L,
            HistoricalDatabaseFixtures.count(driver, "workoutSet", "occurrenceId = 4")
        )
        assertEquals(
            1L,
            HistoricalDatabaseFixtures.count(driver, "workoutSet", "occurrenceEntryId = 5")
        )
    }

    private fun exec(sql: String) {
        HistoricalDatabaseFixtures.exec(driver, sql)
    }
}
