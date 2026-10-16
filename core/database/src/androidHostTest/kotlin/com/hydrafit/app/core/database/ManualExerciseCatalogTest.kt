package com.hydrafit.app.core.database

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.hydrafit.app.core.domain.backup.BackupValidator
import com.hydrafit.app.core.domain.engine.DeterministicWorkoutPlannerEngine
import com.hydrafit.app.core.domain.engine.PlanRequest
import com.hydrafit.app.core.domain.engine.PlannerCandidateEligibility
import com.hydrafit.app.core.domain.engine.WeeklyPlanSanitizer
import com.hydrafit.app.core.domain.equipment.EquipmentTag
import com.hydrafit.app.core.domain.equipment.ExerciseLoadCapability
import com.hydrafit.app.core.domain.equipment.MovementPattern
import com.hydrafit.app.core.domain.fatigue.FatigueCalculator
import com.hydrafit.app.core.domain.fatigue.MuscleGroup
import com.hydrafit.app.core.domain.fatigue.TrainingCoverageCalculator
import com.hydrafit.app.core.domain.workout.WorkoutSet
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest

class ManualExerciseCatalogTest {
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
    fun tearDown() = driver.close()

    @Test
    fun freshAndRepeatedSeedingProducesSixExactManualProfiles() = runTest {
        val equipment = mapOf(
            "dumbbell-shrug" to EquipmentTag.DUMBBELL,
            "ez-bar-shrug" to EquipmentTag.EZ_BAR,
            "trap-bar-shrug" to EquipmentTag.TRAP_BAR,
            "barbell-shrug" to EquipmentTag.BARBELL,
            "forehead-neck-bridge" to EquipmentTag.BODYWEIGHT,
            "self-resisted-neck-lateral-flexion" to EquipmentTag.BODYWEIGHT
        )
        assertEquals(equipment.keys, DefaultExercisesCatalogManual.all.map { it.id }.toSet())
        val before = database.exerciseQueries.selectAll().executeAsList()
        SeedExerciseCatalog(database).seed()
        SeedExerciseCatalog(database).seed()
        assertEquals(before, database.exerciseQueries.selectAll().executeAsList())
        val catalog = SqlDelightExerciseCatalog(database).all().associateBy { it.id }
        equipment.forEach { (id, tag) ->
            val exercise = catalog.getValue(id)
            val neck = tag == EquipmentTag.BODYWEIGHT
            assertEquals(setOf(tag), exercise.requiredEquipment)
            assertEquals(
                if (neck) MovementPattern.NECK_ISOLATION else MovementPattern.TRAP_ISOLATION,
                exercise.movementPattern
            )
            assertEquals(
                if (neck) {
                    ExerciseLoadCapability.BODYWEIGHT_ONLY
                } else {
                    ExerciseLoadCapability.EXTERNAL
                },
                exercise.loadCapability
            )
            assertEquals(
                mapOf((if (neck) MuscleGroup.NECK else MuscleGroup.TRAPS) to 1.0),
                exercise.effectiveInvolvements
            )
            assertEquals(id == "self-resisted-neck-lateral-flexion", exercise.isUnilateral)
            assertTrue(exercise.isAvailableWith(setOf(tag)))
            if (!neck) assertFalse(exercise.isAvailableWith(emptySet()))
        }
    }

    @Test
    fun existingSameNameCustomsRemainEditableAndBackupCompatible() = runTest {
        val batch = DefaultExercisesCatalogManual.all
        val ids = batch.joinToString(",") { "'${it.id}'" }
        driver.execute(null, "DELETE FROM exercise WHERE id IN ($ids)", 0)
        batch.forEach { seed ->
            database.exerciseQueries.insertCustom(
                "user-${seed.id}",
                seed.name,
                "BODYWEIGHT",
                "CORE",
                0L,
                "BODYWEIGHT_ONLY",
                "ABS:0.85"
            )
        }
        val customs = database.exerciseQueries.selectAll().executeAsList().filter {
            it.isCustom == 1L
        }
        SeedExerciseCatalog(database).seed()
        CustomExerciseDedupe(database).run()
        assertEquals(
            customs,
            database.exerciseQueries.selectAll().executeAsList().filter { it.isCustom == 1L }
        )
        batch.forEach { seed ->
            SqlDelightCustomExerciseRepository(database).update(
                id = "user-${seed.id}",
                name = seed.name,
                requiredEquipment = setOf(EquipmentTag.BODYWEIGHT),
                involvements = mapOf(MuscleGroup.ABS to 0.85),
                movementPattern = MovementPattern.CORE,
                loadCapability = ExerciseLoadCapability.BODYWEIGHT_ONLY
            )
        }
        val backup = SqlDelightBackupRepository(database, SqlDelightBackupCatalog())
        val exported = backup.export("test", 1L)
        BackupValidator(SqlDelightBackupCatalog()).validate(exported)
        backup.restore(exported)
        SeedExerciseCatalog(database).seed()
        CustomExerciseDedupe(database).run()
        assertEquals(exported, backup.export("test", 1L))
    }

    @Test
    fun manualLoggingSnapshotsReachFatigueAndCoverageAndRetainLoadMeaning() = runTest {
        val log = SqlDelightWorkoutLogRepository(database)
        database.workoutSessionQueries.insertSession("manual-session", 1L, 1L, 0L)
        DefaultExercisesCatalogManual.all.forEach { seed ->
            val weight = if (seed.loadCapability == ExerciseLoadCapability.EXTERNAL) 20.0 else null
            log.add(
                WorkoutSet(
                    exerciseId = seed.id,
                    reps = 8,
                    weightKg = weight,
                    performedAtMillis = 1L,
                    sessionId = "manual-session"
                )
            )
        }
        val fatigue = FatigueCalculator().calculate(log.loggedSets(), 1L)
        assertTrue(fatigue.getValue(MuscleGroup.TRAPS) > 0.0)
        assertTrue(fatigue.getValue(MuscleGroup.NECK) > 0.0)
        val coverage = TrainingCoverageCalculator.calculate(
            SqlDelightTrainingCoverageRepository(database).observeSets().first(),
            1L,
            0L
        )
        assertEquals(4, coverage.entries.single { it.muscle == MuscleGroup.TRAPS }.workingSets)
        assertEquals(2, coverage.entries.single { it.muscle == MuscleGroup.NECK }.workingSets)
        val before = database.workoutLogQueries.selectAllSets().executeAsList()
        assertTrue(before.filter { "neck" in it.exerciseId }.all { it.weightKg == null })
        val backup = SqlDelightBackupRepository(database, SqlDelightBackupCatalog())
        val exported = backup.export("test", 1L)
        BackupValidator(SqlDelightBackupCatalog()).validate(exported)
        backup.restore(exported)
        SeedExerciseCatalog(database).seed()
        assertEquals(before, database.workoutLogQueries.selectAllSets().executeAsList())
    }

    @Test
    fun deterministicAndModelBackedPlansCannotPrescribeManualSeeds() = runTest {
        val catalog = SqlDelightExerciseCatalog(database)
        val manualIds = DefaultExercisesCatalogManual.all.map { it.id }.toSet()
        val request = PlanRequest(
            daysPerWeek = 2,
            availableEquipment = EquipmentTag.BUILT_IN.toSet(),
            muscleFatigue = emptyMap(),
            nowMillis = 0L
        )
        val eligible = PlannerCandidateEligibility.candidates(catalog.all(), request)
        assertTrue(eligible.none { it.id in manualIds })
        val generated = DeterministicWorkoutPlannerEngine(catalog).generatePlan(request)
        assertTrue(generated.days.flatMap { it.exercises }.none { it.exerciseId in manualIds })
        val prototype = generated.days.first().exercises.first()
        val injected = generated.copy(
            days = generated.days.map { day ->
                val manualEntries = manualIds.map { prototype.copy(exerciseId = it) }
                day.copy(exercises = day.exercises + manualEntries)
            }
        )
        val sanitized = assertNotNull(WeeklyPlanSanitizer(catalog).sanitize(injected, request))
        assertTrue(sanitized.days.flatMap { it.exercises }.none { it.exerciseId in manualIds })
    }
}
