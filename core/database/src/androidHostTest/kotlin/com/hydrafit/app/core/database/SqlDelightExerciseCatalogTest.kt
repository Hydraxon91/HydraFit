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
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
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
            secondary = setOf(MuscleGroup.TRICEPS, MuscleGroup.SHOULDERS),
            pattern = MovementPattern.HORIZONTAL_PUSH
        )

        val exercise = catalog.all().single()

        assertEquals("bench-press", exercise.id)
        assertEquals("Bench Press", exercise.name)
        assertEquals(setOf(EquipmentTag.BARBELL, EquipmentTag.BENCH), exercise.requiredEquipment)
        assertEquals(setOf(MuscleGroup.CHEST), exercise.primaryMuscles)
        assertEquals(setOf(MuscleGroup.TRICEPS, MuscleGroup.SHOULDERS), exercise.secondaryMuscles)
        assertEquals(MovementPattern.HORIZONTAL_PUSH, exercise.movementPattern)
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

    @Test
    fun observeAllAppliesExerciseOverrides() = runTest {
        insert(
            id = "bench-press",
            name = "Bench Press",
            equipment = setOf(EquipmentTag.BARBELL),
            primary = setOf(MuscleGroup.CHEST),
            secondary = emptySet()
        )
        SqlDelightExerciseOverrideRepository(database).update(
            exerciseId = "bench-press",
            name = "Flat Bench",
            requiredEquipment = setOf(EquipmentTag.BARBELL),
            movementPattern = MovementPattern.HORIZONTAL_PUSH,
            involvements = mapOf(MuscleGroup.CHEST to 1.0, MuscleGroup.SHOULDERS to 0.7)
        )

        val exercise = catalog.observeAll().first().single()

        assertEquals("Flat Bench", exercise.name)
        assertEquals(setOf(MuscleGroup.CHEST, MuscleGroup.SHOULDERS), exercise.primaryMuscles)
    }

    @Test
    fun readsInvolvementWeightsFromAnOverride() = runTest {
        insert(
            id = "bench",
            name = "Bench",
            equipment = setOf(EquipmentTag.BARBELL),
            primary = setOf(MuscleGroup.CHEST),
            secondary = emptySet()
        )
        SqlDelightExerciseOverrideRepository(database).update(
            exerciseId = "bench",
            name = null,
            requiredEquipment = setOf(EquipmentTag.BARBELL),
            movementPattern = null,
            involvements = mapOf(MuscleGroup.CHEST to 1.0, MuscleGroup.SHOULDERS to 0.4)
        )

        val exercise = catalog.all().single { it.id == "bench" }

        assertEquals(
            mapOf(MuscleGroup.CHEST to 1.0, MuscleGroup.SHOULDERS to 0.4),
            exercise.involvements
        )
        assertEquals(0.4, exercise.effectiveInvolvements.getValue(MuscleGroup.SHOULDERS))
    }

    private fun insert(
        id: String,
        name: String,
        equipment: Set<EquipmentTag>,
        primary: Set<MuscleGroup>,
        secondary: Set<MuscleGroup>,
        pattern: MovementPattern = MovementPattern.CORE
    ) {
        database.exerciseQueries.insert(
            id = id,
            name = name,
            requiredEquipment = encodeEquipment(equipment),
            movementPattern = pattern.name
        )
        database.exerciseQueries.updateInvolvements(
            involvements = encodeInvolvements(
                primary.associateWith { 1.0 } + secondary.associateWith { 0.5 }
            ),
            id = id
        )
    }
}
