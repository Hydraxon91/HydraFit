package com.hydrafit.app.core.database

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.hydrafit.app.core.domain.equipment.CatalogProfileMatch
import com.hydrafit.app.core.domain.equipment.CatalogProfileMatcher
import com.hydrafit.app.core.domain.equipment.EquipmentTag
import com.hydrafit.app.core.domain.equipment.ExerciseLoadCapability
import com.hydrafit.app.core.domain.equipment.MovementPattern
import com.hydrafit.app.core.domain.fatigue.MuscleGroup
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
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
            primary = setOf(MuscleGroup.CHEST_UPPER),
            secondary = setOf(MuscleGroup.TRICEPS, MuscleGroup.SIDE_DELTS),
            pattern = MovementPattern.HORIZONTAL_PUSH
        )

        val exercise = catalog.all().single()

        assertEquals("bench-press", exercise.id)
        assertEquals("Bench Press", exercise.name)
        assertEquals(setOf(EquipmentTag.BARBELL, EquipmentTag.BENCH), exercise.requiredEquipment)
        assertEquals(setOf(MuscleGroup.CHEST_UPPER), exercise.primaryMuscles)
        assertEquals(setOf(MuscleGroup.TRICEPS, MuscleGroup.SIDE_DELTS), exercise.secondaryMuscles)
        assertEquals(MovementPattern.HORIZONTAL_PUSH, exercise.movementPattern)
    }

    @Test
    fun treatsBlankSetsAsEmpty() = runTest {
        insert(
            id = "push-up",
            name = "Push Up",
            equipment = emptySet(),
            primary = setOf(MuscleGroup.CHEST_UPPER),
            secondary = emptySet()
        )

        val exercise = catalog.all().single()

        assertTrue(exercise.requiredEquipment.isEmpty())
        assertTrue(exercise.secondaryMuscles.isEmpty())
    }

    @Test
    fun returnsExercisesOrderedById() = runTest {
        insert("b-exercise", "B", emptySet(), setOf(MuscleGroup.LATS), emptySet())
        insert("a-exercise", "A", emptySet(), setOf(MuscleGroup.CHEST_UPPER), emptySet())

        val ids = catalog.all().map { it.id }

        assertEquals(listOf("a-exercise", "b-exercise"), ids)
    }

    @Test
    fun observeAllAppliesExerciseOverrides() = runTest {
        insert(
            id = "bench-press",
            name = "Bench Press",
            equipment = setOf(EquipmentTag.BARBELL),
            primary = setOf(MuscleGroup.CHEST_UPPER),
            secondary = emptySet()
        )
        SqlDelightExerciseOverrideRepository(database).update(
            exerciseId = "bench-press",
            name = "Flat Bench",
            requiredEquipment = setOf(EquipmentTag.BARBELL),
            movementPattern = MovementPattern.HORIZONTAL_PUSH,
            involvements = mapOf(MuscleGroup.CHEST_UPPER to 1.0, MuscleGroup.SIDE_DELTS to 0.7)
        )

        val exercise = catalog.observeAll().first().single()

        assertEquals("Flat Bench", exercise.name)
        assertEquals(
            setOf(MuscleGroup.CHEST_UPPER, MuscleGroup.SIDE_DELTS),
            exercise.primaryMuscles
        )
    }

    @Test
    fun readsInvolvementWeightsFromAnOverride() = runTest {
        insert(
            id = "bench",
            name = "Bench",
            equipment = setOf(EquipmentTag.BARBELL),
            primary = setOf(MuscleGroup.CHEST_UPPER),
            secondary = emptySet()
        )
        SqlDelightExerciseOverrideRepository(database).update(
            exerciseId = "bench",
            name = null,
            requiredEquipment = setOf(EquipmentTag.BARBELL),
            movementPattern = null,
            involvements = mapOf(MuscleGroup.CHEST_UPPER to 1.0, MuscleGroup.SIDE_DELTS to 0.4)
        )

        val exercise = catalog.all().single { it.id == "bench" }

        assertEquals(
            mapOf(MuscleGroup.CHEST_UPPER to 1.0, MuscleGroup.SIDE_DELTS to 0.4),
            exercise.involvements
        )
        assertEquals(0.4, exercise.effectiveInvolvements.getValue(MuscleGroup.SIDE_DELTS))
    }

    @Test
    fun clearingEveryMuscleOnABuiltInOverridePersists() = runTest {
        insert(
            id = "bench-press",
            name = "Bench Press",
            equipment = setOf(EquipmentTag.BARBELL),
            primary = setOf(MuscleGroup.CHEST_UPPER),
            secondary = setOf(MuscleGroup.TRICEPS)
        )
        SqlDelightExerciseOverrideRepository(database).update(
            exerciseId = "bench-press",
            name = "Bench Press",
            requiredEquipment = setOf(EquipmentTag.BARBELL),
            movementPattern = MovementPattern.HORIZONTAL_PUSH,
            involvements = emptyMap()
        )

        val exercise = catalog.all().single { it.id == "bench-press" }

        assertTrue(exercise.involvements.isEmpty())
        assertTrue(exercise.primaryMuscles.isEmpty())
        assertTrue(exercise.secondaryMuscles.isEmpty())
        assertTrue(exercise.effectiveInvolvements.isEmpty())
    }

    @Test
    fun profileReadKeepsCanonicalAndAliasesWithExactEffectiveOverrides() = runTest {
        SeedExerciseCatalog(database).seed()
        SeedEquipmentCatalog(database).seed()
        ExerciseLoadCapability.entries.forEach { capability ->
            SqlDelightExerciseOverrideRepository(database).update(
                exerciseId = "barbell-bench-press",
                name = "My Flat Bench",
                requiredEquipment = setOf(EquipmentTag.DUMBBELL),
                movementPattern = MovementPattern.CORE,
                unilateral = true,
                loadCapability = capability,
                involvements = mapOf(MuscleGroup.CHEST_UPPER to 0.83, MuscleGroup.TRICEPS to 0.17)
            )
            val before = database.exerciseOverrideQueries.selectAll().executeAsList()
            val snapshot = catalog.profileCandidates()
            val names = listOf("Barbell Bench Press", "My Flat Bench", "Langhantel-Bankdrücken")
            names.forEach { name ->
                val candidate = assertIs<CatalogProfileMatch.Unique>(
                    CatalogProfileMatcher.match(name, snapshot)
                ).candidate
                assertEquals("barbell-bench-press", candidate.catalogId)
                assertEquals("Barbell Bench Press", candidate.canonicalName)
                assertEquals("My Flat Bench", candidate.displayName)
                assertEquals(setOf(EquipmentTag.DUMBBELL), candidate.profile.equipment)
                assertEquals(MovementPattern.CORE, candidate.profile.movementPattern)
                assertEquals(capability, candidate.profile.loadCapability)
                assertTrue(candidate.profile.isUnilateral)
                assertEquals(
                    mapOf(MuscleGroup.CHEST_UPPER to 0.83, MuscleGroup.TRICEPS to 0.17),
                    candidate.profile.involvements
                )
            }
            assertEquals(before, database.exerciseOverrideQueries.selectAll().executeAsList())
        }
    }

    @Test
    fun profileCandidatesExcludeCustomAndNonSeedRowsAndPreserveRuntimeCollisions() = runTest {
        SeedExerciseCatalog(database).seed()
        database.exerciseQueries.insertCustom(
            "user-pullup",
            "Pullup",
            "",
            "CORE",
            0L,
            "EXTERNAL",
            "ABS:1.0"
        )
        insert("non-seed", "Pullup", emptySet(), setOf(MuscleGroup.ABS), emptySet())
        SqlDelightExerciseOverrideRepository(database).update(
            exerciseId = "chin-up",
            name = "Pullup",
            requiredEquipment = setOf(EquipmentTag.PULL_UP_BAR),
            movementPattern = null,
            involvements = mapOf(MuscleGroup.BICEPS to 1.0)
        )
        val snapshot = catalog.profileCandidates()
        assertTrue(snapshot.none { it.catalogId in setOf("user-pullup", "non-seed") })
        val match = assertIs<CatalogProfileMatch.Ambiguous>(
            CatalogProfileMatcher.match("Pullup", snapshot)
        )
        assertEquals(setOf("pull-up", "chin-up"), match.candidates.map { it.catalogId }.toSet())
    }

    @Test
    fun p7CanonicalProfilesAreAvailableToExactProfileMatching() = runTest {
        SeedExerciseCatalog(database).seed()

        val candidate = assertIs<CatalogProfileMatch.Unique>(
            CatalogProfileMatcher.match("Single-Arm Cable Crossover", catalog.profileCandidates())
        ).candidate

        assertEquals("single-arm-cable-crossover", candidate.catalogId)
        assertEquals(MovementPattern.CHEST_FLY, candidate.profile.movementPattern)
        assertEquals(setOf(EquipmentTag.CABLE_MACHINE), candidate.profile.equipment)
        assertTrue(candidate.profile.isUnilateral)
        assertEquals(
            mapOf(MuscleGroup.CHEST_UPPER to 0.7, MuscleGroup.CHEST_LOWER to 0.7),
            candidate.profile.involvements
        )
    }

    @Test
    fun profileReadDoesNotInventMissingSeedsOrRestoreClearedInvolvements() = runTest {
        assertTrue(catalog.profileCandidates().isEmpty())
        SeedExerciseCatalog(database).seed()
        SqlDelightExerciseOverrideRepository(database).update(
            exerciseId = "pull-up",
            name = null,
            requiredEquipment = emptySet(),
            movementPattern = null,
            involvements = emptyMap()
        )
        val profile = catalog.profileCandidates().single { it.catalogId == "pull-up" }.profile
        assertTrue(profile.involvements.isEmpty())
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
            movementPattern = pattern.name,
            loadCapability = "EXTERNAL"
        )
        database.exerciseQueries.updateInvolvements(
            involvements = encodeInvolvements(
                primary.associateWith { 1.0 } + secondary.associateWith { 0.5 }
            ),
            id = id
        )
    }
}
