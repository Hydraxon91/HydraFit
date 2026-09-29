package com.hydrafit.app.core.database

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.hydrafit.app.core.domain.equipment.EquipmentTag
import com.hydrafit.app.core.domain.fatigue.MuscleGroup
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest

class SqlDelightExerciseMuscleRepositoryTest {

    private lateinit var driver: SqlDriver
    private lateinit var database: HydraFitDatabase
    private lateinit var repository: SqlDelightExerciseMuscleRepository
    private lateinit var catalog: SqlDelightExerciseCatalog

    @BeforeTest
    fun setUp() {
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        HydraFitDatabase.Schema.create(driver)
        database = HydraFitDatabase(driver)
        SeedExerciseCatalog(database).seed()
        repository = SqlDelightExerciseMuscleRepository(database)
        catalog = SqlDelightExerciseCatalog(database)
    }

    @AfterTest
    fun tearDown() {
        driver.close()
    }

    @Test
    fun overrideReplacesTheSeededMusclesAndResetRestoresThem() = runTest {
        val seeded = catalog.exercise("back-squat")
        assertEquals(setOf(MuscleGroup.QUADS, MuscleGroup.GLUTES), seeded.primaryMuscles)

        repository.update(
            exerciseId = "back-squat",
            primaryMuscles = setOf(MuscleGroup.CHEST),
            secondaryMuscles = setOf(MuscleGroup.TRICEPS)
        )

        val edited = catalog.exercise("back-squat")
        assertEquals(setOf(MuscleGroup.CHEST), edited.primaryMuscles)
        assertEquals(setOf(MuscleGroup.TRICEPS), edited.secondaryMuscles)

        repository.reset("back-squat")

        val reset = catalog.exercise("back-squat")
        assertEquals(setOf(MuscleGroup.QUADS, MuscleGroup.GLUTES), reset.primaryMuscles)
        assertEquals(setOf(MuscleGroup.HAMSTRINGS, MuscleGroup.CORE), reset.secondaryMuscles)
    }

    @Test
    fun muscleAndEquipmentOverridesCoexist() = runTest {
        SqlDelightExerciseEquipmentRepository(database)
            .update("back-squat", setOf(EquipmentTag.DUMBBELL))
        repository.update(
            exerciseId = "back-squat",
            primaryMuscles = setOf(MuscleGroup.CHEST),
            secondaryMuscles = emptySet()
        )

        val exercise = catalog.exercise("back-squat")

        assertEquals(setOf(EquipmentTag.DUMBBELL), exercise.requiredEquipment)
        assertEquals(setOf(MuscleGroup.CHEST), exercise.primaryMuscles)
        assertEquals(emptySet(), exercise.secondaryMuscles)
    }

    private suspend fun SqlDelightExerciseCatalog.exercise(id: String) = all().first { it.id == id }
}
