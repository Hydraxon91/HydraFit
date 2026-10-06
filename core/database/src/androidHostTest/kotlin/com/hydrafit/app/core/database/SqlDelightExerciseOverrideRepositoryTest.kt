package com.hydrafit.app.core.database

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.hydrafit.app.core.domain.equipment.EquipmentTag
import com.hydrafit.app.core.domain.equipment.MovementPattern
import com.hydrafit.app.core.domain.fatigue.MuscleGroup
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest

class SqlDelightExerciseOverrideRepositoryTest {

    private lateinit var driver: SqlDriver
    private lateinit var database: HydraFitDatabase
    private lateinit var repository: SqlDelightExerciseOverrideRepository
    private lateinit var catalog: SqlDelightExerciseCatalog

    @BeforeTest
    fun setUp() {
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        HydraFitDatabase.Schema.create(driver)
        database = HydraFitDatabase(driver)
        SeedExerciseCatalog(database).seed()
        repository = SqlDelightExerciseOverrideRepository(database)
        catalog = SqlDelightExerciseCatalog(database)
    }

    @AfterTest
    fun tearDown() {
        driver.close()
    }

    @Test
    fun overlaysNamePatternEquipmentAndMusclesAndResetRestoresAll() = runTest {
        repository.update(
            exerciseId = "back-squat",
            name = "Low-Bar Back Squat",
            requiredEquipment = setOf(EquipmentTag.BARBELL, EquipmentTag.BENCH),
            movementPattern = MovementPattern.HINGE,
            involvements = mapOf(MuscleGroup.CHEST_UPPER to 1.0, MuscleGroup.TRICEPS to 0.5)
        )

        val edited = catalog.exercise("back-squat")
        assertEquals("Low-Bar Back Squat", edited.name)
        assertEquals(setOf(EquipmentTag.BARBELL, EquipmentTag.BENCH), edited.requiredEquipment)
        assertEquals(setOf(MuscleGroup.CHEST_UPPER), edited.primaryMuscles)
        assertEquals(setOf(MuscleGroup.TRICEPS), edited.secondaryMuscles)
        assertEquals(MovementPattern.HINGE, edited.movementPattern)

        repository.reset("back-squat")

        val reset = catalog.exercise("back-squat")
        assertEquals("Back Squat", reset.name)
        assertEquals(setOf(EquipmentTag.BARBELL), reset.requiredEquipment)
        assertEquals(setOf(MuscleGroup.QUADS, MuscleGroup.GLUTES), reset.primaryMuscles)
        assertEquals(MovementPattern.SQUAT, reset.movementPattern)
    }

    @Test
    fun aNameOnlyOverrideKeepsTheSeededEquipmentAndMuscles() = runTest {
        repository.update(
            exerciseId = "back-squat",
            name = "Renamed Only",
            requiredEquipment = setOf(EquipmentTag.BARBELL),
            movementPattern = MovementPattern.SQUAT,
            involvements = mapOf(
                MuscleGroup.QUADS to 1.0,
                MuscleGroup.GLUTES to 1.0,
                MuscleGroup.HAMSTRINGS to 0.5,
                MuscleGroup.ABS to 0.5
            )
        )

        val edited = catalog.exercise("back-squat")

        assertEquals("Renamed Only", edited.name)
        assertEquals(setOf(EquipmentTag.BARBELL), edited.requiredEquipment)
    }

    private suspend fun SqlDelightExerciseCatalog.exercise(id: String) = all().first { it.id == id }
}
