package com.hydrafit.app.core.database

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.hydrafit.app.core.domain.equipment.EquipmentTag
import com.hydrafit.app.core.domain.fatigue.MuscleGroup
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CustomExerciseDedupeTest {

    private lateinit var driver: JdbcSqliteDriver
    private lateinit var database: HydraFitDatabase

    @BeforeTest
    fun setUp() {
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        HydraFitDatabase.Schema.create(driver)
        database = HydraFitDatabase(driver)
        SeedExerciseCatalog(database).seed()
        SeedEquipmentCatalog(database).seed()
    }

    @AfterTest
    fun tearDown() {
        driver.close()
    }

    @Test
    fun mergesACustomExerciseIntoTheSeededOneAndMovesItsHistory() {
        insertLegacyCustom()
        database.workoutLogQueries.insertSet(
            exerciseId = "user-trap-bar-deadlift",
            reps = 5,
            weightKg = 100.0,
            performedAt = 1,
            isWarmup = 0,
            involvements = "BACK:1.0,GLUTES:1.0",
            weekNumber = null,
            cycleNumber = null,
            dayIndex = null,
            rir = null,
            sessionId = null
        )
        database.personalRecordQueries.upsert(
            exerciseId = "user-trap-bar-deadlift",
            weightKg = 140.0,
            reps = 3,
            updatedAt = 1
        )

        CustomExerciseDedupe(database).run()

        assertNull(
            database.exerciseQueries.selectById("user-trap-bar-deadlift").executeAsOneOrNull()
        )
        assertTrue(
            database.exerciseQueries.selectById("trap-bar-deadlift").executeAsOneOrNull() != null
        )
        assertEquals(
            "trap-bar-deadlift",
            database.workoutLogQueries.selectAllSets().executeAsOne().exerciseId
        )
        assertEquals(
            "trap-bar-deadlift",
            database.personalRecordQueries.selectAll().executeAsOne().exerciseId
        )
    }

    @Test
    fun leavesCustomExercisesWithNoSeededMatchAlone() {
        database.exerciseQueries.insertCustom(
            id = "user-my-thing",
            name = "My Thing",
            requiredEquipment = "BARBELL",
            movementPattern = "HINGE",
            isUnilateral = 0,
            involvements = encodeInvolvements(mapOf(MuscleGroup.LATS to 1.0))
        )

        CustomExerciseDedupe(database).run()

        assertTrue(
            database.exerciseQueries.selectById("user-my-thing").executeAsOneOrNull() != null
        )
    }

    private fun insertLegacyCustom() {
        database.exerciseQueries.insertCustom(
            id = "user-trap-bar-deadlift",
            name = "Trap Bar Deadlift",
            requiredEquipment = EquipmentTag.TRAP_BAR.id,
            movementPattern = "HINGE",
            isUnilateral = 0,
            involvements = encodeInvolvements(
                mapOf(MuscleGroup.LOWER_BACK to 1.0, MuscleGroup.GLUTES to 1.0)
            )
        )
    }
}
