package com.hydrafit.app.core.database

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.hydrafit.app.core.domain.equipment.EquipmentTag
import com.hydrafit.app.core.domain.fatigue.MuscleGroup
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class SqlDelightExerciseCatalogTest {

    private lateinit var driver: SqlDriver
    private lateinit var database: HydraFitDatabase
    private lateinit var catalog: SqlDelightExerciseCatalog

    @BeforeTest
    fun setUp() {
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        HydraFitDatabase.Schema.create(driver)
        database = HydraFitDatabase(driver)
        catalog = SqlDelightExerciseCatalog(database)
    }

    @AfterTest
    fun tearDown() {
        driver.close()
    }

    @Test
    fun returnsEmptyListWhenTableIsEmpty() = runTest {
        assertTrue(catalog.all().isEmpty())
    }

    @Test
    fun readsRowsIntoTheDomainModel() = runTest {
        insert(
            id = "bench-press",
            name = "Bench Press",
            equipment = setOf(EquipmentTag.BARBELL, EquipmentTag.BENCH),
            primary = setOf(MuscleGroup.CHEST),
            secondary = setOf(MuscleGroup.TRICEPS, MuscleGroup.SHOULDERS)
        )

        val exercise = catalog.all().single()

        assertEquals("bench-press", exercise.id)
        assertEquals("Bench Press", exercise.name)
        assertEquals(setOf(EquipmentTag.BARBELL, EquipmentTag.BENCH), exercise.requiredEquipment)
        assertEquals(setOf(MuscleGroup.CHEST), exercise.primaryMuscles)
        assertEquals(setOf(MuscleGroup.TRICEPS, MuscleGroup.SHOULDERS), exercise.secondaryMuscles)
    }

    @Test
    fun treatsBlankSetsAsEmpty() = runTest {
        insert(
            id = "push-up",
            name = "Push Up",
            equipment = emptySet(),
            primary = setOf(MuscleGroup.CHEST),
            secondary = emptySet()
        )

        val exercise = catalog.all().single()

        assertTrue(exercise.requiredEquipment.isEmpty())
        assertTrue(exercise.secondaryMuscles.isEmpty())
    }

    @Test
    fun returnsExercisesOrderedById() = runTest {
        insert("b-exercise", "B", emptySet(), setOf(MuscleGroup.BACK), emptySet())
        insert("a-exercise", "A", emptySet(), setOf(MuscleGroup.CHEST), emptySet())

        val ids = catalog.all().map { it.id }

        assertEquals(listOf("a-exercise", "b-exercise"), ids)
    }

    private fun insert(
        id: String,
        name: String,
        equipment: Set<EquipmentTag>,
        primary: Set<MuscleGroup>,
        secondary: Set<MuscleGroup>
    ) {
        database.exerciseQueries.insert(
            id = id,
            name = name,
            requiredEquipment = encodeEquipment(equipment),
            primaryMuscles = encodeMuscles(primary),
            secondaryMuscles = encodeMuscles(secondary)
        )
    }
}
