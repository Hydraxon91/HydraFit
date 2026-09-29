package com.hydrafit.app.core.database

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.hydrafit.app.core.domain.equipment.EquipmentTag
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest

class SqlDelightExerciseEquipmentRepositoryTest {

    private lateinit var driver: SqlDriver
    private lateinit var database: HydraFitDatabase
    private lateinit var repository: SqlDelightExerciseEquipmentRepository
    private lateinit var catalog: SqlDelightExerciseCatalog

    @BeforeTest
    fun setUp() {
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        HydraFitDatabase.Schema.create(driver)
        database = HydraFitDatabase(driver)
        SeedExerciseCatalog(database).seed()
        repository = SqlDelightExerciseEquipmentRepository(database)
        catalog = SqlDelightExerciseCatalog(database)
    }

    @AfterTest
    fun tearDown() {
        driver.close()
    }

    @Test
    fun overrideReplacesTheSeededEquipmentAndResetRestoresIt() = runTest {
        assertEquals(
            setOf(EquipmentTag.BARBELL),
            catalog.exercise("back-squat").requiredEquipment
        )

        repository.update("back-squat", setOf(EquipmentTag.DUMBBELL, EquipmentTag.BENCH))

        assertEquals(
            setOf(EquipmentTag.DUMBBELL, EquipmentTag.BENCH),
            catalog.exercise("back-squat").requiredEquipment
        )

        repository.reset("back-squat")

        assertEquals(
            setOf(EquipmentTag.BARBELL),
            catalog.exercise("back-squat").requiredEquipment
        )
    }

    @Test
    fun overrideCanClearEquipmentToBodyweightOnly() = runTest {
        repository.update("back-squat", emptySet())

        assertEquals(emptySet(), catalog.exercise("back-squat").requiredEquipment)
    }

    private suspend fun SqlDelightExerciseCatalog.exercise(id: String) = all().first { it.id == id }
}
