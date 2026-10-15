package com.hydrafit.app.core.database

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.hydrafit.app.core.domain.equipment.EquipmentTag
import com.hydrafit.app.core.domain.equipment.MovementPatternGuardrail
import com.hydrafit.app.core.domain.fatigue.MuscleGroup
import com.hydrafit.app.core.domain.workout.LoadKind
import com.hydrafit.app.core.domain.workout.WorkoutSet
import com.hydrafit.app.core.domain.workout.unicodeCaseFold
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
    fun patternCorrectionsPreserveWeightsOverridesAndHistoricalFatigueType() = runTest {
        val seeder = SeedExerciseCatalog(database)
        seeder.seed()
        val oldPatterns = mapOf(
            "upright-barbell-row" to "HORIZONTAL_PULL",
            "cable-deadlifts" to "HINGE"
        )
        val log = SqlDelightWorkoutLogRepository(database)
        oldPatterns.forEach { (id, pattern) ->
            database.exerciseQueries.updateMovementPattern(pattern, id)
            log.add(WorkoutSet(exerciseId = id, reps = 8, weightKg = 20.0, performedAtMillis = 1L))
        }
        database.exerciseOverrideQueries.upsert(
            exerciseId = "upright-barbell-row",
            name = "My upright row",
            requiredEquipment = null,
            movementPattern = "HORIZONTAL_PULL",
            isUnilateral = null,
            loadCapability = null,
            involvements = "SIDE_DELTS:0.8"
        )
        val rowsBefore = oldPatterns.keys.associateWith {
            database.exerciseQueries.selectById(it).executeAsOne()
        }
        val setsBefore = database.workoutLogQueries.selectAllSets().executeAsList()
        val overridesBefore = database.exerciseOverrideQueries.selectAll().executeAsList()
        val fatigueBefore = log.loggedSets()

        seeder.seed()
        seeder.seed()

        assertEquals(
            "VERTICAL_PULL",
            database.exerciseQueries.selectById("upright-barbell-row")
                .executeAsOne().movementPattern
        )
        assertEquals(
            "SQUAT",
            database.exerciseQueries.selectById("cable-deadlifts").executeAsOne().movementPattern
        )
        rowsBefore.forEach { (id, row) ->
            assertEquals(
                row.involvements,
                database.exerciseQueries.selectById(id).executeAsOne().involvements
            )
        }
        assertEquals(setsBefore, database.workoutLogQueries.selectAllSets().executeAsList())
        assertEquals(overridesBefore, database.exerciseOverrideQueries.selectAll().executeAsList())
        assertEquals(fatigueBefore, log.loggedSets())
    }

    @Test
    fun seedingKeepsARenamedBuiltInsEffectiveProjection() {
        database.exerciseOverrideQueries.upsert(
            exerciseId = "back-squat",
            name = "Safety Bar Squat",
            requiredEquipment = null,
            movementPattern = null,
            isUnilateral = null,
            loadCapability = null,
            involvements = null
        )

        SeedExerciseCatalog(database).seed()

        // Seeding rewrites every built-in row, but must not put the seeded name back into the search
        // projection the history browser matches.
        assertEquals(
            unicodeCaseFold("Safety Bar Squat"),
            database.exerciseQueries.selectById("back-squat").executeAsOne().searchName
        )
        assertEquals(
            "Back Squat",
            database.exerciseQueries.selectById("back-squat").executeAsOne().name
        )
    }

    @Test
    fun calfSplitPreservesExistingIdentityOverridesAndLoggedData() = runTest {
        database.exerciseQueries.insert(
            id = "standing-calf-raise",
            name = "Standing Calf Raise",
            requiredEquipment = "DUMBBELL",
            movementPattern = "CALF_RAISE",
            loadCapability = "EXTERNAL"
        )
        database.exerciseOverrideQueries.upsert(
            exerciseId = "standing-calf-raise",
            name = "My calf exercise",
            requiredEquipment = "BARBELL",
            movementPattern = null,
            isUnilateral = null,
            loadCapability = null,
            involvements = null
        )
        SqlDelightWorkoutLogRepository(database).add(
            WorkoutSet(
                exerciseId = "standing-calf-raise",
                reps = 12,
                weightKg = 20.0,
                loadKind = LoadKind.EXTERNAL,
                performedAtMillis = 1L
            )
        )
        val setsBefore = database.workoutLogQueries.selectAllSets().executeAsList()
        val overrideBefore = database.exerciseOverrideQueries.selectAll().executeAsList()

        val seeder = SeedExerciseCatalog(database)
        seeder.seed()
        seeder.seed()

        val original = database.exerciseQueries.selectById("standing-calf-raise").executeAsOne()
        assertEquals("Dumbbell Calf Raise", original.name)
        assertEquals("DUMBBELL", original.requiredEquipment)
        assertEquals(unicodeCaseFold("My calf exercise"), original.searchName)
        assertEquals(overrideBefore, database.exerciseOverrideQueries.selectAll().executeAsList())
        assertEquals(setsBefore, database.workoutLogQueries.selectAllSets().executeAsList())
        val catalog = SqlDelightExerciseCatalog(database).all().associateBy { it.id }
        assertEquals("My calf exercise", catalog.getValue("standing-calf-raise").name)
        assertEquals(
            setOf(EquipmentTag.BARBELL),
            catalog.getValue("standing-calf-raise").requiredEquipment
        )
        val machine = catalog.getValue("standing-calf-raise-machine")
        assertEquals("Standing Calf Raise Machine", machine.name)
        assertEquals(setOf(EquipmentTag.CALF_RAISE_MACHINE), machine.requiredEquipment)
    }

    @Test
    fun calfLabelCorrectionRefreshesSearchAndPreservesCustomRows() {
        database.exerciseQueries.insert(
            "standing-calf-raise",
            "Standing Calf Raise",
            "DUMBBELL",
            "CALF_RAISE",
            "EXTERNAL"
        )
        SeedExerciseCatalog(database).seed()
        assertEquals(
            unicodeCaseFold("Dumbbell Calf Raise"),
            database.exerciseQueries.selectById("standing-calf-raise").executeAsOne().searchName
        )
        // Other custom rows are not candidates for the label correction.
        database.exerciseQueries.insertCustom(
            "custom-calf",
            "Standing Calf Raise",
            "DUMBBELL",
            "CALF_RAISE",
            0L,
            "EXTERNAL",
            null
        )
        val customBefore = database.exerciseQueries.selectById("custom-calf").executeAsOne()
        database.exerciseQueries.clarifyDumbbellCalfRaiseName()
        assertEquals(
            customBefore,
            database.exerciseQueries.selectById("custom-calf").executeAsOne()
        )
    }

    @Test
    fun calfLabelCorrectionPreservesANonSeedStoredLabel() {
        database.exerciseQueries.insert(
            "standing-calf-raise",
            "My stored calf label",
            "DUMBBELL",
            "CALF_RAISE",
            "EXTERNAL"
        )
        SeedExerciseCatalog(database).seed()
        val original = database.exerciseQueries.selectById("standing-calf-raise").executeAsOne()
        assertEquals("My stored calf label", original.name)
        assertEquals(unicodeCaseFold("My stored calf label"), original.searchName)
    }

    @Test
    fun calfLabelCorrectionDoesNotRenameACustomWithTheSeedId() {
        database.exerciseQueries.insertCustom(
            "standing-calf-raise",
            "Standing Calf Raise",
            "DUMBBELL",
            "CALF_RAISE",
            0L,
            "EXTERNAL",
            null
        )
        database.exerciseQueries.clarifyDumbbellCalfRaiseName()
        assertEquals(
            "Standing Calf Raise",
            database.exerciseQueries.selectById("standing-calf-raise").executeAsOne().name
        )
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
