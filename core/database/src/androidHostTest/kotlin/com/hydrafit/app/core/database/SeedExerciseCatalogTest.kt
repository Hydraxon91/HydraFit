package com.hydrafit.app.core.database

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest

class SeedExerciseCatalogTest {

    private lateinit var driver: SqlDriver
    private lateinit var database: HydraFitDatabase

    @BeforeTest
    fun setUp() {
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        HydraFitDatabase.Schema.create(driver)
        database = HydraFitDatabase(driver)
    }

    @AfterTest
    fun tearDown() {
        driver.close()
    }

    @Test
    fun seedsTheEntireDefaultCatalog() = runTest {
        SeedExerciseCatalog(database).seed()

        val seeded = SqlDelightExerciseCatalog(database).all()

        assertEquals(DefaultExercises.all.size, seeded.size)
        assertEquals(DefaultExercises.all.map { it.id }.toSet(), seeded.map { it.id }.toSet())
    }

    @Test
    fun seedingTwiceDoesNotDuplicateRows() {
        val seeder = SeedExerciseCatalog(database)

        seeder.seed()
        seeder.seed()

        assertEquals(
            DefaultExercises.all.size,
            database.exerciseQueries.selectAll().executeAsList().size
        )
    }

    @Test
    fun seededExercisePreservesMusclesAndEquipment() = runTest {
        SeedExerciseCatalog(database).seed()

        val squat = SqlDelightExerciseCatalog(database).all().first { it.id == "back-squat" }

        assertEquals(setOf("BARBELL"), squat.requiredEquipment.map { it.name }.toSet())
        assertEquals(setOf("QUADS", "GLUTES"), squat.primaryMuscles.map { it.name }.toSet())
    }
}
