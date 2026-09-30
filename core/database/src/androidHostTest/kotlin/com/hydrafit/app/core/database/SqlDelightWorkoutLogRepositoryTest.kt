package com.hydrafit.app.core.database

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.hydrafit.app.core.domain.equipment.EquipmentTag
import com.hydrafit.app.core.domain.fatigue.MuscleGroup
import com.hydrafit.app.core.domain.fatigue.MuscleInvolvement
import com.hydrafit.app.core.domain.workout.WorkoutSet as DomainWorkoutSet
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest

class SqlDelightWorkoutLogRepositoryTest {

    private lateinit var driver: SqlDriver
    private lateinit var database: HydraFitDatabase
    private lateinit var repository: SqlDelightWorkoutLogRepository

    @BeforeTest
    fun setUp() {
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        HydraFitDatabase.Schema.create(driver)
        database = HydraFitDatabase(driver)
        SeedExerciseCatalog(database).seed()
        repository = SqlDelightWorkoutLogRepository(database)
    }

    @AfterTest
    fun tearDown() {
        driver.close()
    }

    @Test
    fun storesAndReturnsSetsInPerformedOrder() = runTest {
        repository.add(set(exerciseId = "barbell-bench-press", performedAt = 200))
        repository.add(set(exerciseId = "back-squat", performedAt = 100))

        val all = repository.all()

        assertEquals(listOf("back-squat", "barbell-bench-press"), all.map { it.exerciseId })
    }

    @Test
    fun preservesRepsWeightAndWarmupFlag() = runTest {
        repository.add(
            DomainWorkoutSet(
                exerciseId = "back-squat",
                reps = 5,
                weightKg = 100.0,
                performedAtMillis = 1,
                isWarmup = false
            )
        )
        repository.add(
            DomainWorkoutSet(
                exerciseId = "push-up",
                reps = 20,
                weightKg = null,
                performedAtMillis = 2,
                isWarmup = true
            )
        )

        val all = repository.all()
        val working = all.first { it.exerciseId == "back-squat" }
        val warmup = all.first { it.exerciseId == "push-up" }

        assertEquals(5, working.reps)
        assertEquals(100.0, working.weightKg)
        assertEquals(false, working.isWarmup)
        assertEquals(null, warmup.weightKg)
        assertTrue(warmup.isWarmup)
    }

    @Test
    fun mapsSetsToFatigueLoggedSetsUsingPrimaryAndSecondaryMuscles() = runTest {
        // Dumbbell bench has no explicit involvement weights, so it exercises the legacy fallback.
        repository.add(set(exerciseId = "dumbbell-bench-press", performedAt = 1))

        val logged = repository.loggedSets().single()

        val byMuscle = logged.targets.associate { it.muscle to it.weight }
        assertEquals(MuscleInvolvement.PRIMARY.volumeWeight, byMuscle[MuscleGroup.CHEST])
        assertEquals(MuscleInvolvement.SECONDARY.volumeWeight, byMuscle[MuscleGroup.TRICEPS])
        assertEquals(MuscleInvolvement.SECONDARY.volumeWeight, byMuscle[MuscleGroup.SHOULDERS])
    }

    @Test
    fun ignoresSetsReferencingUnknownExercises() = runTest {
        repository.add(set(exerciseId = "does-not-exist", performedAt = 1))

        assertTrue(repository.loggedSets().isEmpty())
    }

    @Test
    fun loggedSetsFlowEmitsMappedSets() = runTest {
        repository.add(set(exerciseId = "barbell-bench-press", performedAt = 1))

        val logged = repository.loggedSetsFlow().first().single()

        val byMuscle = logged.targets.associate { it.muscle to it.weight }
        assertEquals(MuscleInvolvement.PRIMARY.volumeWeight, byMuscle[MuscleGroup.CHEST])
        assertEquals(1L, logged.timestampMillis)
    }

    @Test
    fun setsFlowEmitsStoredSetsWithWeights() = runTest {
        repository.add(set(exerciseId = "back-squat", performedAt = 1))

        val emitted = repository.setsFlow().first().single()

        assertEquals("back-squat", emitted.exerciseId)
        assertEquals(50.0, emitted.weightKg)
    }

    @Test
    fun addStoresTheExerciseMuscleSnapshot() = runTest {
        repository.add(set(exerciseId = "barbell-bench-press", performedAt = 1))

        val row = database.workoutLogQueries.selectAllSets().executeAsList().single()

        assertTrue(requireNotNull(row.involvements).contains("CHEST:1.0"))
        assertTrue(requireNotNull(row.involvements).contains("TRICEPS"))
    }

    @Test
    fun usesStoredTargetsEvenWhenTheExerciseIsNoLongerInTheCatalog() = runTest {
        database.workoutLogQueries.insertSet(
            exerciseId = "ghost",
            reps = 5,
            weightKg = 50.0,
            performedAt = 1,
            isWarmup = 0,
            involvements = "CHEST:1.0,TRICEPS:0.5"
        )

        val logged = repository.loggedSets().single()

        val byMuscle = logged.targets.associate { it.muscle to it.weight }
        assertEquals(MuscleInvolvement.PRIMARY.volumeWeight, byMuscle[MuscleGroup.CHEST])
        assertEquals(MuscleInvolvement.SECONDARY.volumeWeight, byMuscle[MuscleGroup.TRICEPS])
    }

    @Test
    fun fallsBackToTheCatalogForLegacyRowsWithoutASnapshot() = runTest {
        database.workoutLogQueries.insertSet(
            exerciseId = "barbell-bench-press",
            reps = 5,
            weightKg = 50.0,
            performedAt = 1,
            isWarmup = 0,
            involvements = null
        )

        val logged = repository.loggedSets().single()

        val byMuscle = logged.targets.associate { it.muscle to it.weight }
        assertEquals(MuscleInvolvement.PRIMARY.volumeWeight, byMuscle[MuscleGroup.CHEST])
    }

    @Test
    fun snapshotsTheOverriddenMusclesWhenLogging() = runTest {
        SqlDelightExerciseOverrideRepository(database).update(
            exerciseId = "barbell-bench-press",
            name = null,
            requiredEquipment = setOf(EquipmentTag.BARBELL, EquipmentTag.BENCH),
            movementPattern = null,
            involvements = mapOf(MuscleGroup.BACK to 1.0)
        )

        repository.add(set(exerciseId = "barbell-bench-press", performedAt = 1))

        val logged = repository.loggedSets().single()
        val byMuscle = logged.targets.associate { it.muscle to it.weight }
        assertEquals(MuscleInvolvement.PRIMARY.volumeWeight, byMuscle[MuscleGroup.BACK])
        assertNull(byMuscle[MuscleGroup.CHEST])
    }

    @Test
    fun keepsTheLoggedSnapshotWhenTheExerciseMusclesAreLaterEdited() = runTest {
        repository.add(set(exerciseId = "barbell-bench-press", performedAt = 1))

        // Simulate a later catalog edit to the same, still-existing exercise.
        driver.execute(
            identifier = null,
            sql = "UPDATE exercise SET involvements = 'QUADS:1.0' " +
                "WHERE id = 'barbell-bench-press'",
            parameters = 0
        )

        val logged = repository.loggedSets().single()

        val byMuscle = logged.targets.associate { it.muscle to it.weight }
        assertEquals(MuscleInvolvement.PRIMARY.volumeWeight, byMuscle[MuscleGroup.CHEST])
        assertNull(byMuscle[MuscleGroup.QUADS])
    }

    @Test
    fun clearRemovesAllSets() = runTest {
        repository.add(set(exerciseId = "back-squat", performedAt = 1))

        repository.clear()

        assertTrue(repository.all().isEmpty())
    }

    @Test
    fun deleteRemovesOnlyTheTargetSet() = runTest {
        repository.add(set(exerciseId = "back-squat", performedAt = 1))
        repository.add(set(exerciseId = "barbell-bench-press", performedAt = 2))
        val target = repository.all().first { it.exerciseId == "back-squat" }

        repository.delete(target.id)

        assertEquals(listOf("barbell-bench-press"), repository.all().map { it.exerciseId })
    }

    private fun set(exerciseId: String, performedAt: Long) = DomainWorkoutSet(
        exerciseId = exerciseId,
        reps = 5,
        weightKg = 50.0,
        performedAtMillis = performedAt
    )
}
