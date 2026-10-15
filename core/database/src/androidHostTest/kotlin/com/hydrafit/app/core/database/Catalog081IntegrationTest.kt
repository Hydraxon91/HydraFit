package com.hydrafit.app.core.database

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.hydrafit.app.core.domain.backup.BackupValidator
import com.hydrafit.app.core.domain.engine.DeterministicWorkoutPlannerEngine
import com.hydrafit.app.core.domain.engine.PlanRequest
import com.hydrafit.app.core.domain.equipment.EquipmentTag
import com.hydrafit.app.core.domain.equipment.ExerciseLoadCapability
import com.hydrafit.app.core.domain.equipment.MovementPattern
import com.hydrafit.app.core.domain.fatigue.MuscleGroup
import com.hydrafit.app.core.domain.workout.WorkoutSet
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class Catalog081IntegrationTest {
    private lateinit var driver: JdbcSqliteDriver
    private lateinit var database: HydraFitDatabase

    @BeforeTest
    fun setUp() {
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        HydraFitDatabase.Schema.create(driver)
        database = HydraFitDatabase(driver)
        database.plannerEngineQueries.insertIgnoreRow("DETERMINISTIC")
        SeedEquipmentCatalog(database).seed()
        SeedExerciseCatalog(database).seed()
    }

    @AfterTest
    fun tearDown() {
        driver.close()
    }

    @Test
    fun freshSeedsHaveExactProfilesAndDistinctMachineAvailability() = runTest {
        val catalog = SqlDelightExerciseCatalog(database).all().associateBy { it.id }
        val expected = mapOf(
            "machine-pec-deck-fly" to mapOf(
                MuscleGroup.CHEST_LOWER to 1.0,
                MuscleGroup.CHEST_UPPER to 0.5
            ),
            "reverse-machine-fly" to mapOf(
                MuscleGroup.REAR_DELTS to 1.0,
                MuscleGroup.SIDE_DELTS to 0.7
            ),
            "seated-dumbbell-wrist-curl" to mapOf(MuscleGroup.FOREARMS to 1.0),
            "seated-dumbbell-wrist-extension" to mapOf(MuscleGroup.FOREARMS to 1.0)
        )
        assertEquals(expected.keys, DefaultExercisesCatalog081.all.map { it.id }.toSet())
        expected.forEach { (id, involvement) ->
            val exercise = catalog.getValue(id)
            assertEquals(involvement, exercise.effectiveInvolvements)
            assertEquals(ExerciseLoadCapability.EXTERNAL, exercise.loadCapability)
        }
        val chest = catalog.getValue("machine-pec-deck-fly")
        val reverse = catalog.getValue("reverse-machine-fly")
        assertTrue(chest.isAvailableWith(setOf(EquipmentTag.PEC_DECK_MACHINE)))
        assertFalse(reverse.isAvailableWith(setOf(EquipmentTag.PEC_DECK_MACHINE)))
        assertFalse(chest.isAvailableWith(setOf(EquipmentTag.REVERSE_FLY_MACHINE)))
        assertTrue(reverse.isAvailableWith(setOf(EquipmentTag.REVERSE_FLY_MACHINE)))
        assertTrue(database.userEquipmentQueries.selectAllSelected().executeAsList().isEmpty())
        expected.keys.filter { "wrist" in it }.forEach { id ->
            val wrist = catalog.getValue(id)
            assertEquals(MovementPattern.FOREARM_ISOLATION, wrist.movementPattern)
            assertTrue(wrist.isUnilateral)
            assertFalse(wrist.isAvailableWith(setOf(EquipmentTag.DUMBBELL)))
        }
    }

    @Test
    fun preExistingSameNameCustomsStayEditableAndSurviveBackup() = runTest {
        val batch = DefaultExercisesCatalog081.all
        val ids = batch.joinToString(",") { "'${it.id}'" }
        driver.execute(null, "DELETE FROM exercise WHERE id IN ($ids)", 0)
        batch.forEach { exercise ->
            database.exerciseQueries.insertCustom(
                id = "user-${exercise.id}",
                name = exercise.name,
                requiredEquipment = "DUMBBELL",
                movementPattern = "FOREARM_ISOLATION",
                isUnilateral = 1L,
                loadCapability = "EXTERNAL",
                involvements = "FOREARMS:0.85"
            )
        }
        SeedExerciseCatalog(database).seed()
        val customs = database.exerciseQueries.selectAll().executeAsList().filter {
            it.isCustom == 1L
        }
        CustomExerciseDedupe(database).run()
        CustomExerciseDedupe(database).run()
        assertEquals(
            customs,
            database.exerciseQueries.selectAll().executeAsList().filter { it.isCustom == 1L }
        )
        batch.forEach { exercise ->
            SqlDelightCustomExerciseRepository(database).update(
                id = "user-${exercise.id}",
                name = exercise.name,
                requiredEquipment = setOf(EquipmentTag.DUMBBELL),
                involvements = mapOf(MuscleGroup.FOREARMS to 0.85),
                movementPattern = MovementPattern.FOREARM_ISOLATION,
                isUnilateral = true,
                loadCapability = ExerciseLoadCapability.EXTERNAL
            )
        }
        val backup = SqlDelightBackupRepository(database, SqlDelightBackupCatalog())
        val exported = backup.export("t", 1L)
        BackupValidator(SqlDelightBackupCatalog()).validate(exported)
        backup.restore(exported)
        CustomExerciseDedupe(database).run()
        assertEquals(exported, backup.export("t", 1L))
    }

    @Test
    fun actualSeededAccessoriesReachThePlannerAndPreservePerHandLoggedLoad() = runTest {
        val catalog = SqlDelightExerciseCatalog(database)
        val plan = DeterministicWorkoutPlannerEngine(catalog).generatePlan(
            PlanRequest(
                daysPerWeek = 2,
                availableEquipment = setOf(EquipmentTag.DUMBBELL, EquipmentTag.BENCH),
                muscleFatigue = emptyMap(),
                nowMillis = 0L,
                excludedExerciseIds = DefaultExercises.all
                    .filterNot { it.movementPattern == MovementPattern.FOREARM_ISOLATION }
                    .map { it.id }.toSet()
            )
        )
        assertTrue(plan.days.first().exercises.isNotEmpty())
        assertTrue(plan.days.flatMap { it.exercises }.all { "wrist" in it.exerciseId })
        database.workoutSessionQueries.insertSession("wrist-session", 1L, 1L, 0L)
        val log = SqlDelightWorkoutLogRepository(database)
        log.add(
            WorkoutSet(
                exerciseId = "seated-dumbbell-wrist-curl",
                reps = 12,
                weightKg = 7.5,
                performedAtMillis = 1L,
                sessionId = "wrist-session"
            )
        )
        assertEquals(7.5, log.all().single().weightKg)
        assertEquals(7.5, log.loggedSets().single().weightKg)
        assertFalse(log.loggedSets().single().isCompound)
        assertEquals(
            "FOREARMS:1.0",
            database.workoutLogQueries.selectAllSets().executeAsOne().involvements
        )
        val backup = SqlDelightBackupRepository(database, SqlDelightBackupCatalog())
        val exported = backup.export("t", 2L)
        BackupValidator(SqlDelightBackupCatalog()).validate(exported)
        backup.restore(exported)
        assertEquals(exported, backup.export("t", 2L))
    }
}
