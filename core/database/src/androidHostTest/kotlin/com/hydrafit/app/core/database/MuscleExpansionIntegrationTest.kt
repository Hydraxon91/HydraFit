package com.hydrafit.app.core.database

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.hydrafit.app.core.domain.backup.BackupValidator
import com.hydrafit.app.core.domain.fatigue.FatigueCalculator
import com.hydrafit.app.core.domain.fatigue.MuscleGroup
import com.hydrafit.app.core.domain.fatigue.TrainingCoverageCalculator
import com.hydrafit.app.core.domain.workout.WorkoutSet
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest

class MuscleExpansionIntegrationTest {
    private lateinit var driver: JdbcSqliteDriver
    private lateinit var database: HydraFitDatabase
    private val additions = mapOf(
        "push-up" to (MuscleGroup.SERRATUS_ANTERIOR to 0.5),
        "push-up-wide" to (MuscleGroup.SERRATUS_ANTERIOR to 0.5),
        "single-arm-push-up" to (MuscleGroup.SERRATUS_ANTERIOR to 0.5),
        "push-up-to-side-plank" to (MuscleGroup.SERRATUS_ANTERIOR to 0.5),
        "incline-push-up" to (MuscleGroup.SERRATUS_ANTERIOR to 0.3),
        "incline-push-up-medium" to (MuscleGroup.SERRATUS_ANTERIOR to 0.3),
        "incline-push-up-wide" to (MuscleGroup.SERRATUS_ANTERIOR to 0.3),
        "hanging-leg-raise" to (MuscleGroup.HIP_FLEXORS to 0.7),
        "scissor-kick" to (MuscleGroup.HIP_FLEXORS to 0.7),
        "sit-up" to (MuscleGroup.HIP_FLEXORS to 0.5),
        "bicycle-crunch" to (MuscleGroup.HIP_FLEXORS to 0.5),
        "step-up-with-knee-raise" to (MuscleGroup.HIP_FLEXORS to 0.3)
    )

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
    fun exactHistoricalProfilesAndOnlyApprovedAdditionsArePreserved() = runTest {
        val catalog = SqlDelightExerciseCatalog(database).all().associateBy { it.id }
        val profiles = SqlDelightBackupCatalog().seedProfiles()
        assertEquals(additions.keys, MuscleExpansionCompatibility.previousProfiles.keys)
        additions.forEach { (id, addition) ->
            val previous = MuscleExpansionCompatibility.previousProfiles.getValue(id)
            val exercise = catalog.getValue(id)
            val current = exercise.effectiveInvolvements
            assertEquals(addition.second, current.getValue(addition.first), id)
            assertEquals(
                decodeInvolvements(previous.substringAfterLast('|')),
                current - addition.first,
                id
            )
            assertEquals(
                previous.substringBeforeLast('|'),
                profiles.getValue(id).substringBeforeLast('|'),
                id
            )
        }
        assertTrue(
            catalog.values.none { MuscleGroup.TIBIALIS_ANTERIOR in it.effectiveInvolvements }
        )
        assertFalse(MuscleGroup.HIP_FLEXORS in catalog.getValue("leg-lift").effectiveInvolvements)
    }

    @Test
    fun upgradeIsIdempotentAndPreservesOverridesCustomsAndRecordedSnapshots() = runTest {
        val log = SqlDelightWorkoutLogRepository(database)
        database.workoutSessionQueries.insertSession("test-session", 1L, 1L, 0L)
        additions.forEach { (id, _) ->
            val previous = MuscleExpansionCompatibility.previousProfiles.getValue(id)
            setInvolvements(id, previous.substringAfterLast('|'))
            log.add(
                WorkoutSet(
                    exerciseId = id,
                    reps = 8,
                    weightKg = null,
                    performedAtMillis = 1L,
                    sessionId = "test-session"
                )
            )
        }
        database.exerciseOverrideQueries.upsert("push-up", null, null, null, null, null, "")
        database.exerciseOverrideQueries.upsert("sit-up", null, null, null, null, null, "ABS:0.85")
        insertShinExercise()
        val sets = database.workoutLogQueries.selectAllSets().executeAsList()
        val overrides = database.exerciseOverrideQueries.selectAll().executeAsList()
        val custom = database.exerciseQueries.selectById("user-shin").executeAsOne()
        val oldFatigue = log.loggedSets()
        SeedExerciseCatalog(database).seed()
        SeedExerciseCatalog(database).seed()
        assertEquals(sets, database.workoutLogQueries.selectAllSets().executeAsList())
        assertEquals(overrides, database.exerciseOverrideQueries.selectAll().executeAsList())
        assertEquals(custom, database.exerciseQueries.selectById("user-shin").executeAsOne())
        assertEquals(oldFatigue, log.loggedSets())
        additions.forEach { (id, addition) ->
            val row = database.exerciseQueries.selectById(id).executeAsOne()
            assertEquals(addition.second, decodeInvolvements(row.involvements)[addition.first])
        }
        val catalog = SqlDelightExerciseCatalog(database).all().associateBy { it.id }
        assertTrue(catalog.getValue("push-up").effectiveInvolvements.isEmpty())
        assertEquals(
            mapOf(MuscleGroup.ABS to 0.85),
            catalog.getValue("sit-up").effectiveInvolvements
        )
        val backup = SqlDelightBackupRepository(database, SqlDelightBackupCatalog())
        val exported = backup.export("test", 1L)
        BackupValidator(SqlDelightBackupCatalog()).validate(exported)
        backup.restore(exported)
        SeedExerciseCatalog(database).seed()
        assertEquals(exported, backup.export("test", 1L))
    }

    @Test
    fun unknownAndExplicitlyEmptySeedMapsAreNotReplaced() {
        setInvolvements("push-up", "ABS:0.85")
        setInvolvements("sit-up", "")
        SeedExerciseCatalog(database).seed()
        assertEquals(
            "ABS:0.85",
            database.exerciseQueries.selectById("push-up").executeAsOne().involvements
        )
        assertEquals("", database.exerciseQueries.selectById("sit-up").executeAsOne().involvements)
    }

    @Test
    fun newlyLoggedSeedAndCustomMappingsReachFatigueAndCoverage() = runTest {
        val log = SqlDelightWorkoutLogRepository(database)
        insertShinExercise()
        listOf("push-up", "hanging-leg-raise", "user-shin").forEach { id ->
            log.add(WorkoutSet(exerciseId = id, reps = 8, weightKg = null, performedAtMillis = 1L))
        }
        val scores = FatigueCalculator().calculate(log.loggedSets(), 1L)
        val coverage = TrainingCoverageCalculator.calculate(
            SqlDelightTrainingCoverageRepository(database).observeSets().first(),
            1L,
            0L
        )
        listOf(
            MuscleGroup.SERRATUS_ANTERIOR,
            MuscleGroup.HIP_FLEXORS,
            MuscleGroup.TIBIALIS_ANTERIOR
        ).forEach { muscle ->
            assertTrue(scores.getValue(muscle) > 0.0)
            assertEquals(1, coverage.entries.single { it.muscle == muscle }.workingSets)
        }
        database.exerciseOverrideQueries.upsert("push-up", null, null, null, null, null, "")
        log.add(
            WorkoutSet(exerciseId = "push-up", reps = 8, weightKg = null, performedAtMillis = 2L)
        )
        val last = database.workoutLogQueries.selectAllSets().executeAsList().last()
        assertEquals("", last.involvements)
        assertEquals(
            1,
            log.loggedSets().count { set ->
                set.targets.any { it.muscle == MuscleGroup.SERRATUS_ANTERIOR }
            }
        )
    }

    @Test
    fun legacyNullFallbackChangesButCoverageRemainsUnknownAndEmptyRemainsEmpty() = runTest {
        val log = SqlDelightWorkoutLogRepository(database)
        log.add(
            WorkoutSet(exerciseId = "push-up", reps = 8, weightKg = null, performedAtMillis = 1L)
        )
        driver.execute(null, "UPDATE workoutSet SET involvements = NULL", 0)
        assertTrue(
            log.loggedSets().single().targets.any { it.muscle == MuscleGroup.SERRATUS_ANTERIOR }
        )
        val coverage = SqlDelightTrainingCoverageRepository(database)
        assertEquals(null, coverage.observeSets().first().single().involvements)
        driver.execute(null, "UPDATE workoutSet SET involvements = ''", 0)
        assertTrue(log.loggedSets().single().targets.isEmpty())
        assertEquals(emptyMap(), coverage.observeSets().first().single().involvements)
    }

    @Test
    fun exactOldBackupProfilesRestoreButUnknownProfileChangesFail() = runTest {
        val backup = SqlDelightBackupRepository(database, SqlDelightBackupCatalog())
        val exported = backup.export("test", 1L)
        val historical = exported.copy(
            catalog = exported.catalog.copy(
                seedProfiles = exported.catalog.seedProfiles.map {
                    val previous = MuscleExpansionCompatibility.previousProfiles[it.id]
                        ?: it.profile
                    it.copy(profile = previous)
                }
            )
        )
        val validator = BackupValidator(SqlDelightBackupCatalog())
        validator.validate(historical)
        backup.restore(historical)
        assertEquals(exported, backup.export("test", 1L))
        val invalid = historical.copy(
            catalog = historical.catalog.copy(
                seedProfiles = historical.catalog.seedProfiles.map {
                    if (it.id == "push-up") it.copy(profile = it.profile + ",ABS:0.123") else it
                }
            )
        )
        assertFails { validator.validate(invalid) }
        assertEquals(exported, backup.export("test", 1L))
    }

    private fun setInvolvements(id: String, value: String) {
        driver.execute(null, "UPDATE exercise SET involvements = ? WHERE id = ?", 2) {
            bindString(0, value)
            bindString(1, id)
        }
    }

    private fun insertShinExercise() {
        database.exerciseQueries.insertCustom(
            "user-shin",
            "My shin exercise",
            "",
            "LEG_ISOLATION",
            0L,
            "BODYWEIGHT_ONLY",
            "TIBIALIS_ANTERIOR:1.0"
        )
    }
}
