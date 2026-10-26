package com.hydrafit.app.core.database

import app.cash.sqldelight.db.SqlDriver
import com.hydrafit.app.core.domain.engine.SlotProgressionPolicy
import com.hydrafit.app.core.domain.engine.SlotProgressionPolicyJson
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Migration 57 adds the nullable slot progression policy to the four prescription stores. The columns
 * are additive, so existing rows must survive with no policy and no invented rule.
 */
class SlotProgressionPolicyMigrationTest {
    private lateinit var driver: SqlDriver

    @AfterTest
    fun tearDown() {
        if (::driver.isInitialized) driver.close()
    }

    @Test
    fun existingSlotsSurviveWithNoPolicyConfigured() {
        driver = HistoricalDatabaseFixtures.v1()
        HydraFitDatabase.Schema.migrate(driver, 1, 57)
        HistoricalDatabaseFixtures.exec(
            driver,
            "INSERT INTO exercise(id,name,requiredEquipment,movementPattern,loadCapability) " +
                "VALUES ('policy-test','Policy test','','HORIZONTAL_PUSH','EXTERNAL')"
        )
        HistoricalDatabaseFixtures.exec(
            driver,
            "INSERT INTO routineTemplate(id,name,revision,createdAtMillis,updatedAtMillis) " +
                "VALUES (1,'Block',1,1,1)"
        )
        HistoricalDatabaseFixtures.exec(
            driver,
            "INSERT INTO routineWorkout(id,templateId,position,name) VALUES (1,1,0,'Day')"
        )
        HistoricalDatabaseFixtures.exec(
            driver,
            "INSERT INTO routineEntry(id,workoutId,position,exerciseId,sets,reps,weightKg," +
                "loadKind) VALUES (1,1,0,'policy-test',3,5,100.0,'EXTERNAL')"
        )

        HydraFitDatabase.Schema.migrate(driver, 57, HydraFitDatabase.Schema.version)

        val row = HydraFitDatabase(driver).routineTemplateQueries.selectEntriesForWorkout(1L)
            .executeAsOne()
        assertEquals(5L, row.reps)
        assertEquals(100.0, row.weightKg)
        assertNull(row.progressionPolicy)
    }

    @Test
    fun aStoredPolicyRoundTripsThroughTheColumn() {
        driver = HistoricalDatabaseFixtures.empty()
        HydraFitDatabaseSchema.create(driver)
        val database = HydraFitDatabase(driver)
        val encoded = SlotProgressionPolicyJson.encode(
            SlotProgressionPolicy(minReps = 5, maxReps = 8, incrementKg = 2.5)
        )
        database.routineTemplateQueries.insertTemplate("Block", 1, 1, 1, null, null)
        val templateId = database.routineTemplateQueries.lastInsertedTemplateId().executeAsOne()
        database.routineTemplateQueries.insertWorkout(templateId, 0, "Day", "FULL_BODY")
        val workoutId = database.routineTemplateQueries.lastInsertedWorkoutId().executeAsOne()
        database.routineTemplateQueries.insertEntry(
            workoutId = workoutId,
            position = 0,
            exerciseId = "back-squat",
            sets = 3,
            reps = 5,
            durationSeconds = null,
            weightKg = 100.0,
            loadKind = "EXTERNAL",
            progressionPolicy = encoded,
            setTargets = null,
            distanceMeters = null,
            forceNewtons = null
        )

        val row = database.routineTemplateQueries.selectEntriesForWorkout(workoutId)
            .executeAsOne()

        assertEquals(encoded, row.progressionPolicy)
        assertEquals(5, SlotProgressionPolicyJson.decode(row.progressionPolicy!!).minReps)
    }
}
