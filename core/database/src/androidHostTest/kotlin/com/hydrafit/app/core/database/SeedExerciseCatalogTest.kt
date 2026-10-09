package com.hydrafit.app.core.database

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.hydrafit.app.core.domain.equipment.MovementPatternGuardrail
import com.hydrafit.app.core.domain.fatigue.MuscleGroup
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
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
    fun seedsP7ProfilesAndCapabilityConsistentlyAndIdempotently() = runTest {
        val seeder = SeedExerciseCatalog(database)
        seeder.seed()
        val first = SqlDelightExerciseCatalog(database).all().associateBy { it.id }
        seeder.seed()
        val second = SqlDelightExerciseCatalog(database).all().associateBy { it.id }

        DefaultExercisesCatalogP7.all.forEach { expected ->
            val actual = requireNotNull(second[expected.id])
            assertEquals(expected.name, actual.name)
            assertEquals(expected.requiredEquipment, actual.requiredEquipment)
            assertEquals(expected.movementPattern, actual.movementPattern)
            assertEquals(expected.isUnilateral, actual.isUnilateral)
            assertEquals(expected.loadCapability, actual.loadCapability)
            assertEquals(expected.effectiveInvolvements, actual.effectiveInvolvements)
            assertEquals(actual, first[expected.id])
        }
        assertEquals(
            com.hydrafit.app.core.domain.equipment.ExerciseLoadCapability.BODYWEIGHT_ONLY,
            second.getValue("bicycle-crunch").loadCapability
        )
    }

    @Test
    fun backfillsMovementPatternsForExistingRows() = runTest {
        val bench = DefaultExercises.all.first { it.id == "barbell-bench-press" }
        database.exerciseQueries.insert(
            id = bench.id,
            name = bench.name,
            requiredEquipment = encodeEquipment(bench.requiredEquipment),
            movementPattern = "CORE",
            loadCapability = "UNSPECIFIED"
        )

        SeedExerciseCatalog(database).seed()

        val seeded = SqlDelightExerciseCatalog(database)
            .all()
            .first { it.id == "barbell-bench-press" }
        assertEquals(bench.movementPattern, seeded.movementPattern)
    }

    @Test
    fun seededExercisePreservesMusclesAndEquipment() = runTest {
        SeedExerciseCatalog(database).seed()

        val squat = SqlDelightExerciseCatalog(database).all().first { it.id == "back-squat" }

        assertEquals(setOf("BARBELL"), squat.requiredEquipment.map { it.id }.toSet())
        assertEquals(setOf("QUADS", "GLUTES"), squat.primaryMuscles.map { it.name }.toSet())
    }

    @Test
    fun backfillsInvolvementWeightsForExistingRows() = runTest {
        val bench = DefaultExercises.all.first { it.id == "barbell-bench-press" }
        database.exerciseQueries.insert(
            id = bench.id,
            name = bench.name,
            requiredEquipment = encodeEquipment(bench.requiredEquipment),
            movementPattern = bench.movementPattern.name,
            loadCapability = "UNSPECIFIED"
        )

        SeedExerciseCatalog(database).seed()

        val seeded = SqlDelightExerciseCatalog(database)
            .all()
            .first { it.id == "barbell-bench-press" }
        assertEquals(0.5, seeded.involvements.getValue(MuscleGroup.FRONT_DELTS))
    }

    @Test
    fun seedingDoesNotOverwriteExistingInvolvementWeights() = runTest {
        SeedExerciseCatalog(database).seed()
        driver.execute(
            identifier = null,
            sql = "UPDATE exercise SET involvements = 'CHEST_UPPER:0.9' " +
                "WHERE id = 'barbell-bench-press'",
            parameters = 0
        )

        SeedExerciseCatalog(database).seed()

        val seeded = SqlDelightExerciseCatalog(database)
            .all()
            .first { it.id == "barbell-bench-press" }
        assertEquals(mapOf(MuscleGroup.CHEST_UPPER to 0.9), seeded.involvements)
    }

    @Test
    fun seedsTheNewInvolvementVariants() = runTest {
        SeedExerciseCatalog(database).seed()

        val ids = SqlDelightExerciseCatalog(database).all().map { it.id }.toSet()

        assertTrue("hammer-curl" in ids)
        assertTrue("wide-grip-pulldown" in ids)
        assertTrue("incline-barbell-press" in ids)
    }

    @Test
    fun marksUnilateralBuiltInsAndLeavesTheRestBilateral() = runTest {
        SeedExerciseCatalog(database).seed()

        val byId = SqlDelightExerciseCatalog(database).all().associateBy { it.id }
        assertTrue(requireNotNull(byId["dumbbell-curl"]).isUnilateral)
        assertTrue(requireNotNull(byId["bulgarian-split-squat"]).isUnilateral)
        assertFalse(requireNotNull(byId["back-squat"]).isUnilateral)
    }

    @Test
    fun normalizesLegacyInvolvementWeightsOnBuiltIns() = runTest {
        SeedExerciseCatalog(database).seed()
        driver.execute(
            identifier = null,
            sql = "UPDATE exercise SET involvements = 'CHEST_UPPER:0.6,FRONT_DELTS:0.4,ABS:0.2' " +
                "WHERE id = 'barbell-bench-press'",
            parameters = 0
        )

        SeedExerciseCatalog(database).seed()

        val seeded = SqlDelightExerciseCatalog(database)
            .all()
            .first { it.id == "barbell-bench-press" }
        assertEquals(0.7, seeded.involvements.getValue(MuscleGroup.CHEST_UPPER))
        assertEquals(0.5, seeded.involvements.getValue(MuscleGroup.FRONT_DELTS))
        assertEquals(0.3, seeded.involvements.getValue(MuscleGroup.ABS))
    }

    @Test
    fun normalizationLeavesNonLegacyAndCustomWeightsUntouched() = runTest {
        SeedExerciseCatalog(database).seed()
        driver.execute(
            identifier = null,
            sql = "UPDATE exercise SET involvements = 'CHEST_UPPER:0.9' " +
                "WHERE id = 'barbell-bench-press'",
            parameters = 0
        )
        database.exerciseQueries.insertCustom(
            id = "user-custom",
            name = "User Custom",
            requiredEquipment = "BARBELL",
            movementPattern = "SQUAT",
            isUnilateral = 0L,
            loadCapability = "EXTERNAL",
            involvements = "QUADS:0.6"
        )

        SeedExerciseCatalog(database).seed()

        val byId = SqlDelightExerciseCatalog(database).all().associateBy { it.id }
        assertEquals(
            mapOf(MuscleGroup.CHEST_UPPER to 0.9),
            byId.getValue("barbell-bench-press").involvements
        )
        assertEquals(
            mapOf(MuscleGroup.QUADS to 0.6),
            byId.getValue("user-custom").involvements
        )
    }

    @Test
    fun repairsLegacyInvolvementNamesOnBuiltIns() = runTest {
        SeedExerciseCatalog(database).seed()
        driver.execute(
            identifier = null,
            sql = "UPDATE exercise SET involvements = 'BACK:1.0,BICEPS:0.5' " +
                "WHERE id = 'dumbbell-row'",
            parameters = 0
        )

        SeedExerciseCatalog(database).seed()

        val row = SqlDelightExerciseCatalog(database).all().first { it.id == "dumbbell-row" }
        assertEquals(
            mapOf(
                MuscleGroup.BICEPS to 0.5,
                MuscleGroup.LATS to 1.0,
                MuscleGroup.UPPER_BACK to 1.0
            ),
            row.involvements
        )
        assertFalse(
            MovementPatternGuardrail.conflicts(row.movementPattern, row.effectiveInvolvements)
        )
    }

    @Test
    fun legacyInvolvementRepairLeavesCustomRowsUntouched() = runTest {
        SeedExerciseCatalog(database).seed()
        database.exerciseQueries.insertCustom(
            id = "user-legacy",
            name = "User Legacy",
            requiredEquipment = "BARBELL",
            movementPattern = "HORIZONTAL_PULL",
            isUnilateral = 0L,
            loadCapability = "EXTERNAL",
            involvements = "BACK:1.0"
        )

        SeedExerciseCatalog(database).seed()

        val row = SqlDelightExerciseCatalog(database).all().first { it.id == "user-legacy" }
        assertEquals(
            mapOf(
                MuscleGroup.LATS to 0.5,
                MuscleGroup.UPPER_BACK to 0.35,
                MuscleGroup.LOWER_BACK to 0.15
            ),
            row.involvements
        )
    }
}
