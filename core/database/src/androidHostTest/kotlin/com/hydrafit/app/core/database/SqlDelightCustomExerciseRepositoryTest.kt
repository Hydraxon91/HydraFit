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
            name = "Trap Bar Deadlift",
            requiredEquipment = setOf(EquipmentTag.BARBELL),
            primaryMuscles = setOf(MuscleGroup.BACK, MuscleGroup.GLUTES),
            secondaryMuscles = setOf(MuscleGroup.HAMSTRINGS),
            movementPattern = MovementPattern.HINGE
        )

        assertEquals("user-trap-bar-deadlift", created.id)
        assertTrue(created.isCustom)
        val stored = catalog.all().first { it.id == created.id }
        assertEquals("Trap Bar Deadlift", stored.name)
        assertEquals(setOf(MuscleGroup.BACK, MuscleGroup.GLUTES), stored.primaryMuscles)
        assertTrue(stored.isCustom)
        assertTrue(catalog.all().first { it.id == "back-squat" }.isCustom.not())
    }

    @Test
    fun updatesACustomExerciseInPlace() = runTest {
        val created = repository.add(
            name = "My Curl",
            requiredEquipment = setOf(EquipmentTag.DUMBBELL),
            primaryMuscles = setOf(MuscleGroup.BICEPS),
            secondaryMuscles = emptySet(),
            movementPattern = MovementPattern.BICEPS_ISOLATION
        )

        repository.update(
            id = created.id,
            name = "My Hammer Curl",
            requiredEquipment = setOf(EquipmentTag.DUMBBELL),
            primaryMuscles = setOf(MuscleGroup.BICEPS),
            secondaryMuscles = setOf(MuscleGroup.BACK),
            movementPattern = MovementPattern.BICEPS_ISOLATION
        )

        val stored = catalog.all().first { it.id == created.id }
        assertEquals("My Hammer Curl", stored.name)
        assertEquals(setOf(MuscleGroup.BACK), stored.secondaryMuscles)
    }

    @Test
    fun persistsTheUnilateralFlagAndCanFlipIt() = runTest {
        val created = repository.add(
            name = "My Hammer Curl",
            requiredEquipment = setOf(EquipmentTag.DUMBBELL),
            primaryMuscles = setOf(MuscleGroup.BICEPS),
            secondaryMuscles = emptySet(),
            movementPattern = MovementPattern.BICEPS_ISOLATION,
            isUnilateral = true
        )
        assertTrue(created.isUnilateral)
        assertTrue(catalog.all().first { it.id == created.id }.isUnilateral)

        repository.update(
            id = created.id,
            name = "My Hammer Curl",
            requiredEquipment = setOf(EquipmentTag.DUMBBELL),
            primaryMuscles = setOf(MuscleGroup.BICEPS),
            secondaryMuscles = emptySet(),
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
            primaryMuscles = setOf(MuscleGroup.CORE),
            secondaryMuscles = emptySet(),
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
            primaryMuscles = setOf(MuscleGroup.CHEST),
            secondaryMuscles = emptySet(),
            movementPattern = MovementPattern.CHEST_FLY
        )
        val second = repository.add(
            name = "Cable Fly?",
            requiredEquipment = setOf(EquipmentTag.CABLE_MACHINE),
            primaryMuscles = setOf(MuscleGroup.CHEST),
            secondaryMuscles = emptySet(),
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
            primary: Set<MuscleGroup> = setOf(MuscleGroup.CORE),
            secondary: Set<MuscleGroup> = emptySet()
        ) = repository.add(
            name = name,
            requiredEquipment = equipment,
            primaryMuscles = primary,
            secondaryMuscles = secondary,
            movementPattern = MovementPattern.CORE
        )

        assertFailsWith<CustomExerciseException> { add(name = "   ") }
        assertFailsWith<CustomExerciseException> { add(primary = emptySet()) }
        assertFailsWith<CustomExerciseException> {
            add(primary = setOf(MuscleGroup.CHEST), secondary = setOf(MuscleGroup.CHEST))
        }
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
            primaryMuscles = setOf(MuscleGroup.CORE),
            secondaryMuscles = emptySet(),
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
            primaryMuscles = setOf(MuscleGroup.SHOULDERS),
            secondaryMuscles = setOf(MuscleGroup.TRICEPS),
            movementPattern = MovementPattern.VERTICAL_PUSH
        )

        SqlDelightWorkoutLogRepository(database).add(
            WorkoutSet(exerciseId = created.id, reps = 8, weightKg = 20.0, performedAtMillis = 1L)
        )

        val logged = SqlDelightWorkoutLogRepository(database).loggedSets().single()
        val byMuscle = logged.targets.associate { it.muscle to it.involvement }
        assertEquals(setOf(MuscleGroup.SHOULDERS, MuscleGroup.TRICEPS), byMuscle.keys)
    }
}
