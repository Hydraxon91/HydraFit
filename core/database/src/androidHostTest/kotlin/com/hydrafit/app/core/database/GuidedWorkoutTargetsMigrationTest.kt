package com.hydrafit.app.core.database

import app.cash.sqldelight.db.SqlDriver
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GuidedWorkoutTargetsMigrationTest {
    private lateinit var driver: SqlDriver

    @AfterTest
    fun tearDown() {
        if (::driver.isInitialized) driver.close()
    }

    @Test
    fun migrationKeepsLegacyOccurrenceAndAddsNullableRoundTrippablePayload() {
        driver = HistoricalDatabaseFixtures.v1()
        HydraFitDatabase.Schema.migrate(driver, 1, 65)
        driver.execute(
            null,
            "INSERT INTO exercise(id, name, requiredEquipment, movementPattern, loadCapability) " +
                "VALUES ('back-squat', 'Back Squat', 'BARBELL', 'SQUAT', 'EXTERNAL')",
            0
        )
        HistoricalDatabaseFixtures.exec(
            driver,
            "INSERT INTO trainingActivation(id, name, createdAtMillis, startEpochDay, mode, " +
                "weekdayMask, status, revision) " +
                "VALUES (1, 'Block', 1, 1, 'SEQUENCE', 0, 'ACTIVE', 1)"
        )
        HistoricalDatabaseFixtures.exec(
            driver,
            "INSERT INTO activationWorkout(id, activationId, position, name) VALUES (1, 1, 0, 'Day')"
        )
        HistoricalDatabaseFixtures.exec(
            driver,
            "INSERT INTO workoutOccurrence(id, activationId, activationWorkoutId, queuePosition, " +
                "status, revision) " +
                "VALUES (1, 1, 1, 0, 'PENDING', 1)"
        )
        HistoricalDatabaseFixtures.exec(
            driver,
            "INSERT INTO occurrenceEntry(id, occurrenceId, position, exerciseId, exerciseName, " +
                "movementPattern, " +
                "requiredEquipment, isUnilateral, sets, reps, weightKg, " +
                "loadCapability, loadKind) " +
                "VALUES (1, 1, 0, 'back-squat', 'Squat', 'SQUAT', 'BARBELL', 0, 3, 5, " +
                "80.0, 'EXTERNAL', 'EXTERNAL')"
        )
        HydraFitDatabase.Schema.migrate(driver, 65, 66)
        val database = HydraFitDatabase(driver)
        assertNull(
            database.trainingScheduleQueries.selectOccurrenceEntryById(1L)
                .executeAsOne().guidedTargets
        )
        val payload = "{\"version\":1,\"overrides\":[]}"
        database.trainingScheduleQueries.applyGuidedTargets(payload, 1L)
        assertEquals(
            payload,
            database.trainingScheduleQueries.selectOccurrenceEntryById(1L)
                .executeAsOne().guidedTargets
        )
    }
}
