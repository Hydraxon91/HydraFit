package com.hydrafit.app.core.database

import app.cash.sqldelight.db.SqlDriver
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Migration 40 -> 41 creates the persistent exercise setup-note table. Existing databases have no
 * notes, so the table starts empty and reads as "no note".
 */
class ExerciseNoteMigrationTest {

    private lateinit var driver: SqlDriver

    @AfterTest
    fun tearDown() {
        if (::driver.isInitialized) driver.close()
    }

    @Test
    fun migratingFromV40CreatesAnEmptyExerciseNoteTable() {
        driver = HistoricalDatabaseFixtures.empty()

        HydraFitDatabase.Schema.migrate(driver, 40, 41)

        assertEquals(0L, HistoricalDatabaseFixtures.count(driver, "exerciseNote"))

        HistoricalDatabaseFixtures.exec(
            driver,
            "INSERT INTO exerciseNote(exerciseId, note) VALUES ('back-squat', 'Set up tall')"
        )

        assertEquals(1L, HistoricalDatabaseFixtures.count(driver, "exerciseNote"))
    }
}
