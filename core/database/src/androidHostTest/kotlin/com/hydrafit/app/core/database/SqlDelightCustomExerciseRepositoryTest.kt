package com.hydrafit.app.core.database

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.hydrafit.app.core.domain.equipment.EquipmentTag
import com.hydrafit.app.core.domain.equipment.MovementPattern
import com.hydrafit.app.core.domain.fatigue.MuscleGroup
import com.hydrafit.app.core.domain.workout.WorkoutSet
import com.hydrafit.app.core.userdata.equipment.CustomExerciseException
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class SqlDelightCustomExerciseRepositoryTest {

    private lateinit var driver: SqlDriver
    private lateinit var database: HydraFitDatabase
    private lateinit var repository: SqlDelightCustomExerciseRepository
    private lateinit var catalog: SqlDelightExerciseCatalog

    @BeforeTest
    fun setUp() {
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        HydraFitDatabase.Schema.create(driver)
        database = HydraFitDatabase(driver)
        SeedExerciseCatalog(database).seed()
        SeedEquipmentCatalog(database).seed()
        repository = SqlDelightCustomExerciseRepository(database)
        catalog = SqlDelightExerciseCatalog(database)
    }

    @AfterTest
    fun tearDown() {
        driver.close()
    }

    @Test
    fun addsACustomExerciseThatAppearsInTheCatalog() = runTest {
        val created = repository.add(
            name = "My Trap Bar Deadlift",
            requiredEquipment = setOf(EquipmentTag.BARBELL),
            involvements = mapOf(
                MuscleGroup.LATS to 1.0,
                MuscleGroup.GLUTES to 1.0,
                MuscleGroup.HAMSTRINGS to 0.5
            ),
            movementPattern = MovementPattern.HINGE
        )

        assertEquals("user-my-trap-bar-deadlift", created.id)
        assertTrue(created.isCustom)
        val stored = catalog.all().first { it.id == created.id }
        assertEquals("My Trap Bar Deadlift", stored.name)
        assertEquals(setOf(MuscleGroup.LATS, MuscleGroup.GLUTES), stored.primaryMuscles)
        assertTrue(stored.isCustom)
        assertTrue(catalog.all().first { it.id == "back-squat" }.isCustom.not())
    }

    @Test
    fun updatesACustomExerciseInPlace() = runTest {
        val created = repository.add(
            name = "My Curl",
            requiredEquipment = setOf(EquipmentTag.DUMBBELL),
            involvements = mapOf(MuscleGroup.BICEPS to 1.0),
            movementPattern = MovementPattern.BICEPS_ISOLATION
        )

        repository.update(
            id = created.id,
            name = "My Hammer Curl",
            requiredEquipment = setOf(EquipmentTag.DUMBBELL),
            involvements = mapOf(MuscleGroup.BICEPS to 1.0, MuscleGroup.LATS to 0.5),
            movementPattern = MovementPattern.BICEPS_ISOLATION
        )

        val stored = catalog.all().first { it.id == created.id }
        assertEquals("My Hammer Curl", stored.name)
        assertEquals(setOf(MuscleGroup.LATS), stored.secondaryMuscles)
    }

    @Test
    fun persistsTheUnilateralFlagAndCanFlipIt() = runTest {
        val created = repository.add(
            name = "My Hammer Curl",
            requiredEquipment = setOf(EquipmentTag.DUMBBELL),
            involvements = mapOf(MuscleGroup.BICEPS to 1.0),
            movementPattern = MovementPattern.BICEPS_ISOLATION,
            isUnilateral = true
        )
        assertTrue(created.isUnilateral)
        assertTrue(catalog.all().first { it.id == created.id }.isUnilateral)

        repository.update(
            id = created.id,
            name = "My Hammer Curl",
            requiredEquipment = setOf(EquipmentTag.DUMBBELL),
            involvements = mapOf(MuscleGroup.BICEPS to 1.0),
            movementPattern = MovementPattern.BICEPS_ISOLATION,
            isUnilateral = false
        )

        assertTrue(catalog.all().first { it.id == created.id }.isUnilateral.not())
    }

    @Test
    fun deletingWorksOnlyWithoutLoggedSets() = runTest {
        val created = repository.add(
            name = "Disposable",
            requiredEquipment = emptySet(),
            involvements = mapOf(MuscleGroup.ABS to 1.0),
            movementPattern = MovementPattern.CORE
        )
        SqlDelightWorkoutLogRepository(database).add(
            WorkoutSet(exerciseId = created.id, reps = 5, weightKg = 10.0, performedAtMillis = 1L)
        )

        val failure = assertFailsWith<CustomExerciseException> { repository.delete(created.id) }
        assertTrue(failure.message!!.contains("logged sets"))

        database.workoutLogQueries.deleteAllSets()
        repository.delete(created.id)
        assertTrue(catalog.all().none { it.id == created.id })
    }

    @Test
    fun appendsASuffixWhenTheSlugCollides() = runTest {
        // A different name that slugs to the same base, avoiding the duplicate-name rule.
        val first = repository.add(
            name = "Cable Fly!",
            requiredEquipment = setOf(EquipmentTag.CABLE_MACHINE),
            involvements = mapOf(MuscleGroup.CHEST_UPPER to 1.0),
            movementPattern = MovementPattern.CHEST_FLY
        )
        val second = repository.add(
            name = "Cable Fly?",
            requiredEquipment = setOf(EquipmentTag.CABLE_MACHINE),
            involvements = mapOf(MuscleGroup.CHEST_UPPER to 1.0),
            movementPattern = MovementPattern.CHEST_FLY
        )

        assertEquals("user-cable-fly", first.id)
        assertEquals("user-cable-fly-2", second.id)
    }

    @Test
    fun rejectsInvalidInput() = runTest {
        suspend fun add(
            name: String = "Ok Name",
            equipment: Set<EquipmentTag> = emptySet(),
            muscles: Map<MuscleGroup, Double> = mapOf(MuscleGroup.ABS to 1.0)
        ) = repository.add(
            name = name,
            requiredEquipment = equipment,
            involvements = muscles,
            movementPattern = MovementPattern.CORE
        )

        assertFailsWith<CustomExerciseException> { add(name = "   ") }
        assertFailsWith<CustomExerciseException> { add(muscles = emptyMap()) }
        assertFailsWith<CustomExerciseException> {
            add(equipment = setOf(EquipmentTag("NOT_REAL")))
        }
        assertFailsWith<CustomExerciseException> { add(name = "Back Squat") }
    }

    @Test
    fun reseedingDoesNotTouchCustomExercises() = runTest {
        val created = repository.add(
            name = "Mine",
            requiredEquipment = emptySet(),
            involvements = mapOf(MuscleGroup.ABS to 1.0),
            movementPattern = MovementPattern.CORE
        )

        SeedExerciseCatalog(database).seed()

        val stored = catalog.all().first { it.id == created.id }
        assertEquals("Mine", stored.name)
        assertTrue(stored.isCustom)
    }

    @Test
    fun loggedSetsSnapshotACustomExercisesMuscles() = runTest {
        val created = repository.add(
            name = "Custom Press",
            requiredEquipment = setOf(EquipmentTag.DUMBBELL),
            involvements = mapOf(MuscleGroup.SIDE_DELTS to 1.0, MuscleGroup.TRICEPS to 0.5),
            movementPattern = MovementPattern.VERTICAL_PUSH
        )

        SqlDelightWorkoutLogRepository(database).add(
            WorkoutSet(exerciseId = created.id, reps = 8, weightKg = 20.0, performedAtMillis = 1L)
        )

        val logged = SqlDelightWorkoutLogRepository(database).loggedSets().single()
        val byMuscle = logged.targets.associate { it.muscle to it.weight }
        assertEquals(setOf(MuscleGroup.SIDE_DELTS, MuscleGroup.TRICEPS), byMuscle.keys)
    }
}
