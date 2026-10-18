package com.hydrafit.app.core.database

import app.cash.sqldelight.db.SqlDriver
import com.hydrafit.app.core.domain.engine.SetTarget
import com.hydrafit.app.core.domain.engine.SlotSetTargets
import com.hydrafit.app.core.domain.engine.SlotSetTargetsJson
import com.hydrafit.app.core.domain.workout.LoadKind
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Migration 58 adds the nullable ordered-target column to the four prescription stores. The columns
 * are additive, so existing rows must survive with no ordered targets and no invented slot identity.
 */
class SlotSetTargetsMigrationTest {
    private lateinit var driver: SqlDriver

    @AfterTest
    fun tearDown() {
        if (::driver.isInitialized) driver.close()
    }

    @Test
    fun existingSlotsSurviveWithNoOrderedTargets() {
        driver = HistoricalDatabaseFixtures.v1()
        HydraFitDatabase.Schema.migrate(driver, 1, 58)
        HistoricalDatabaseFixtures.exec(
            driver,
            "INSERT INTO exercise(id,name,requiredEquipment,movementPattern,loadCapability) " +
                "VALUES ('targets-test','Targets test','','HORIZONTAL_PUSH','EXTERNAL')"
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
                "loadKind) VALUES (1,1,0,'targets-test',3,5,100.0,'EXTERNAL')"
        )

        HydraFitDatabase.Schema.migrate(driver, 58, HydraFitDatabase.Schema.version)

        val row = HydraFitDatabase(driver).routineTemplateQueries.selectEntriesForWorkout(1L)
            .executeAsOne()
        assertEquals(5L, row.reps)
        assertNull(row.setTargets)
    }

    @Test
    fun anOrderedPrescriptionRoundTripsThroughTheColumn() {
        driver = HistoricalDatabaseFixtures.empty()
        HydraFitDatabaseSchema.create(driver)
        val database = HydraFitDatabase(driver)
        val encoded = SlotSetTargetsJson.encode(
            SlotSetTargets.of(
                listOf(
                    SetTarget(reps = 10, loadKind = LoadKind.EXTERNAL, weightKg = 60.0),
                    SetTarget(
                        reps = 8,
                        loadKind = LoadKind.EXTERNAL,
                        weightKg = 70.0,
                        targetRir = 2
                    )
                )
            )
        )
        database.routineTemplateQueries.insertTemplate("Block", 1, 1, 1, null, null)
        val templateId = database.routineTemplateQueries.lastInsertedTemplateId().executeAsOne()
        database.routineTemplateQueries.insertWorkout(templateId, 0, "Day", "FULL_BODY")
        val workoutId = database.routineTemplateQueries.lastInsertedWorkoutId().executeAsOne()
        database.routineTemplateQueries.insertEntry(
            workoutId = workoutId,
            position = 0,
            exerciseId = "back-squat",
            sets = 2,
            reps = 10,
            durationSeconds = null,
            weightKg = 60.0,
            loadKind = "EXTERNAL",
            progressionPolicy = null,
            setTargets = encoded
        )

        val row = database.routineTemplateQueries.selectEntriesForWorkout(workoutId).executeAsOne()

        assertEquals(encoded, row.setTargets)
        val decoded = SlotSetTargetsJson.decode(requireNotNull(row.setTargets))
        assertEquals(listOf(10, 8), decoded.targets.map { it.reps })
        assertEquals(2, decoded.targets[1]?.targetRir)
    }
}
