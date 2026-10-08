package com.hydrafit.app.core.database

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.hydrafit.app.core.domain.fatigue.MuscleGroup
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CustomExerciseDedupeTest {

    private lateinit var driver: JdbcSqliteDriver
    private lateinit var database: HydraFitDatabase

    @BeforeTest
    fun setUp() {
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        HydraFitDatabase.Schema.create(driver)
        database = HydraFitDatabase(driver)
        SeedExerciseCatalog(database).seed()
        SeedEquipmentCatalog(database).seed()
    }

    @AfterTest
    fun tearDown() {
        driver.close()
    }

    @Test
    fun normalizesCaseWhitespaceAndPadding() {
        assertEquals(
            "barbell bench press",
            normalizeExerciseName("  Barbell \t Bench   Press  ")
        )
    }

    @Test
    fun seededNamesHaveNoNormalizedCollisions() {
        val normalized = DefaultExercises.all.map { normalizeExerciseName(it.name) }

        assertEquals(normalized.size, normalized.toSet().size)
    }

    @Test
    fun matchesCustomNameAcrossCaseAndWhitespace() {
        insertCustom(id = "user-trap", name = "  trap   bar DEADLIFT ")

        CustomExerciseDedupe(database).run()

        assertNull(database.exerciseQueries.selectById("user-trap").executeAsOneOrNull())
        assertNotNull(database.exerciseQueries.selectById("trap-bar-deadlift").executeAsOneOrNull())
    }

    @Test
    fun materializesDifferingEquipmentAsCanonicalOverride() {
        insertCustom(equipment = "BODYWEIGHT", involvements = trapBarSeedInvolvements())

        CustomExerciseDedupe(database).run()

        val override = overrideFor("trap-bar-deadlift")
        assertEquals("BODYWEIGHT", override.requiredEquipment)
        assertNull(override.movementPattern)
        assertNull(override.isUnilateral)
        assertNull(override.involvements)
    }

    @Test
    fun doesNotMaterializeDefaultOrBackfilledValues() {
        insertCustom(
            equipment = "",
            pattern = "CORE",
            unilateral = 0,
            involvements = trapBarSeedInvolvements()
        )

        CustomExerciseDedupe(database).run()

        assertNull(overrideOrNull("trap-bar-deadlift"))
        assertNull(customExerciseOrNull("user-trap-bar-deadlift"))
    }

    @Test
    fun materializesDifferingPatternAndUnilateralAsCanonicalOverride() {
        insertCustom(
            equipment = "",
            pattern = "SQUAT",
            unilateral = 1,
            involvements = trapBarSeedInvolvements()
        )

        CustomExerciseDedupe(database).run()

        val override = overrideFor("trap-bar-deadlift")
        assertEquals("SQUAT", override.movementPattern)
        assertEquals(1L, override.isUnilateral)
        assertNull(override.requiredEquipment)
        assertNull(override.involvements)
    }

    @Test
    fun materializesDifferingInvolvementsAsCanonicalOverride() {
        insertCustom(
            equipment = "",
            pattern = "CORE",
            unilateral = 0,
            involvements = "LOWER_BACK:1.0"
        )

        CustomExerciseDedupe(database).run()

        val override = overrideFor("trap-bar-deadlift")
        assertEquals("LOWER_BACK:1.0", override.involvements)
    }

    @Test
    fun keepsExistingOverrideForFieldsTheCustomDoesNotDifferOn() {
        database.exerciseOverrideQueries.upsert(
            exerciseId = "trap-bar-deadlift",
            name = null,
            requiredEquipment = "BODYWEIGHT",
            movementPattern = null,
            isUnilateral = null,
            loadCapability = null,
            involvements = "ABS:0.9"
        )
        insertCustom(
            equipment = "",
            pattern = "SQUAT",
            unilateral = 0,
            involvements = trapBarSeedInvolvements()
        )

        CustomExerciseDedupe(database).run()

        val override = overrideFor("trap-bar-deadlift")
        assertEquals("BODYWEIGHT", override.requiredEquipment)
        assertEquals("ABS:0.9", override.involvements)
        assertEquals("SQUAT", override.movementPattern)
    }

    @Test
    fun customValueWinsOverExistingOverrideOnDifferingField() {
        database.exerciseOverrideQueries.upsert(
            exerciseId = "trap-bar-deadlift",
            name = null,
            requiredEquipment = "BODYWEIGHT",
            movementPattern = null,
            isUnilateral = null,
            loadCapability = null,
            involvements = null
        )
        insertCustom(equipment = "BARBELL", involvements = trapBarSeedInvolvements())

        CustomExerciseDedupe(database).run()

        val override = overrideFor("trap-bar-deadlift")
        assertEquals("BARBELL", override.requiredEquipment)
    }

    @Test
    fun secondRunIsANoOp() {
        insertCustom(
            equipment = "BODYWEIGHT",
            involvements = trapBarSeedInvolvements()
        )
        database.workoutLogQueries.insertSet(
            exerciseId = "user-trap-bar-deadlift",
            reps = 5,
            weightKg = 100.0,
            performedAt = 1,
            isWarmup = 0,
            involvements = "BACK:1.0",
            weekNumber = null,
            cycleNumber = null,
            dayIndex = null,
            rir = null,
            sessionId = null,
            loadKind = "EXTERNAL"
        )

        CustomExerciseDedupe(database).run()
        val exercises = database.exerciseQueries.selectAll().executeAsList()
        val sets = database.workoutLogQueries.selectAllSets().executeAsList()
        val overrides = database.exerciseOverrideQueries.selectAll().executeAsList()

        CustomExerciseDedupe(database).run()

        assertEquals(exercises, database.exerciseQueries.selectAll().executeAsList())
        assertEquals(sets, database.workoutLogQueries.selectAllSets().executeAsList())
        assertEquals(overrides, database.exerciseOverrideQueries.selectAll().executeAsList())
    }

    @Test
    fun rollsBackAllMergesWhenAStepFails() {
        insertCustom(equipment = "BODYWEIGHT", involvements = trapBarSeedInvolvements())
        database.workoutLogQueries.insertSet(
            exerciseId = "user-trap-bar-deadlift",
            reps = 5,
            weightKg = 100.0,
            performedAt = 1,
            isWarmup = 0,
            involvements = "BACK:1.0",
            weekNumber = null,
            cycleNumber = null,
            dayIndex = null,
            rir = null,
            sessionId = null,
            loadKind = "EXTERNAL"
        )
        database.personalRecordQueries.upsert(
            exerciseId = "user-trap-bar-deadlift",
            weightKg = 140.0,
            reps = 3,
            loadKind = "EXTERNAL",
            updatedAt = 1
        )
        insertPlanHistoryEntry("user-trap-bar-deadlift")
        driver.execute(
            identifier = null,
            sql = "CREATE TRIGGER fail_override_insert BEFORE INSERT ON exerciseOverride " +
                "BEGIN SELECT RAISE(ABORT, 'boom'); END",
            parameters = 0
        )

        assertFailsWith<Exception> { CustomExerciseDedupe(database).run() }

        assertEquals(
            "user-trap-bar-deadlift",
            database.workoutLogQueries.selectAllSets().executeAsOne().exerciseId
        )
        assertEquals(
            "user-trap-bar-deadlift",
            database.personalRecordQueries.selectAll().executeAsOne().exerciseId
        )
        assertEquals(
            "user-trap-bar-deadlift",
            database.planHistoryQueries.selectAllEntries().executeAsOne().exerciseId
        )
        assertNotNull(
            database.exerciseQueries.selectById("user-trap-bar-deadlift").executeAsOneOrNull()
        )
        assertNull(overrideOrNull("trap-bar-deadlift"))
    }

    @Test
    fun orphanCustomOverrideIsNotUsedAsSource() {
        database.exerciseOverrideQueries.upsert(
            exerciseId = "user-trap-bar-deadlift",
            name = null,
            requiredEquipment = "BODYWEIGHT",
            movementPattern = null,
            isUnilateral = null,
            loadCapability = null,
            involvements = null
        )
        insertCustom(involvements = trapBarSeedInvolvements())

        CustomExerciseDedupe(database).run()

        assertNull(overrideOrNull("trap-bar-deadlift"))
        assertNull(overrideOrNull("user-trap-bar-deadlift"))
    }

    @Test
    fun movesSoleCustomPersonalRecordToCanonical() {
        insertCustom(involvements = trapBarSeedInvolvements())
        database.personalRecordQueries.upsert(
            exerciseId = "user-trap-bar-deadlift",
            weightKg = 100.0,
            reps = 5,
            loadKind = "EXTERNAL",
            updatedAt = 1
        )

        CustomExerciseDedupe(database).run()

        val record = database.personalRecordQueries.selectById("trap-bar-deadlift").executeAsOne()
        assertEquals(100.0, record.weightKg)
        assertEquals(5L, record.reps)
        assertEquals(1L, record.updatedAt)
    }

    @Test
    fun keepsHeavierCustomPersonalRecord() {
        insertCustom(involvements = trapBarSeedInvolvements())
        database.personalRecordQueries.upsert(
            exerciseId = "user-trap-bar-deadlift",
            weightKg = 140.0,
            reps = 3,
            loadKind = "EXTERNAL",
            updatedAt = 1
        )
        database.personalRecordQueries.upsert(
            exerciseId = "trap-bar-deadlift",
            weightKg = 100.0,
            reps = 10,
            loadKind = "EXTERNAL",
            updatedAt = 1
        )

        CustomExerciseDedupe(database).run()

        val record = database.personalRecordQueries.selectById("trap-bar-deadlift").executeAsOne()
        assertEquals(140.0, record.weightKg)
        assertEquals(3L, record.reps)
    }

    @Test
    fun keepsCanonicalPersonalRecordWhenHeavier() {
        insertCustom(involvements = trapBarSeedInvolvements())
        database.personalRecordQueries.upsert(
            exerciseId = "user-trap-bar-deadlift",
            weightKg = 100.0,
            reps = 5,
            loadKind = "EXTERNAL",
            updatedAt = 1
        )
        database.personalRecordQueries.upsert(
            exerciseId = "trap-bar-deadlift",
            weightKg = 140.0,
            reps = 3,
            loadKind = "EXTERNAL",
            updatedAt = 1
        )

        CustomExerciseDedupe(database).run()

        val record = database.personalRecordQueries.selectById("trap-bar-deadlift").executeAsOne()
        assertEquals(140.0, record.weightKg)
        assertEquals(3L, record.reps)
    }

    @Test
    fun breaksEqualWeightByReps() {
        insertCustom(involvements = trapBarSeedInvolvements())
        database.personalRecordQueries.upsert(
            exerciseId = "user-trap-bar-deadlift",
            weightKg = 100.0,
            reps = 5,
            loadKind = "EXTERNAL",
            updatedAt = 1
        )
        database.personalRecordQueries.upsert(
            exerciseId = "trap-bar-deadlift",
            weightKg = 100.0,
            reps = 3,
            loadKind = "EXTERNAL",
            updatedAt = 2
        )

        CustomExerciseDedupe(database).run()

        val record = database.personalRecordQueries.selectById("trap-bar-deadlift").executeAsOne()
        assertEquals(5L, record.reps)
    }

    @Test
    fun breaksEqualWeightAndRepsByUpdatedAt() {
        insertCustom(involvements = trapBarSeedInvolvements())
        database.personalRecordQueries.upsert(
            exerciseId = "user-trap-bar-deadlift",
            weightKg = 100.0,
            reps = 5,
            loadKind = "EXTERNAL",
            updatedAt = 2
        )
        database.personalRecordQueries.upsert(
            exerciseId = "trap-bar-deadlift",
            weightKg = 100.0,
            reps = 5,
            loadKind = "EXTERNAL",
            updatedAt = 1
        )

        CustomExerciseDedupe(database).run()

        val record = database.personalRecordQueries.selectById("trap-bar-deadlift").executeAsOne()
        assertEquals(2L, record.updatedAt)
    }

    @Test
    fun deletesCustomPersonalRecordAfterMerge() {
        insertCustom(involvements = trapBarSeedInvolvements())
        database.personalRecordQueries.upsert(
            exerciseId = "user-trap-bar-deadlift",
            weightKg = 100.0,
            reps = 5,
            loadKind = "EXTERNAL",
            updatedAt = 1
        )

        CustomExerciseDedupe(database).run()

        assertNull(
            database.personalRecordQueries.selectById("user-trap-bar-deadlift").executeAsOneOrNull()
        )
    }

    @Test
    fun movesACustomsSolePreferenceToTheCanonicalExercise() {
        insertCustom(involvements = trapBarSeedInvolvements())
        database.exercisePreferenceQueries.upsert(
            exerciseId = "user-trap-bar-deadlift",
            preference = "PREFER"
        )

        CustomExerciseDedupe(database).run()

        assertEquals(
            "PREFER",
            database.exercisePreferenceQueries.selectById("trap-bar-deadlift").executeAsOne()
        )
        assertNull(
            database.exercisePreferenceQueries.selectById("user-trap-bar-deadlift")
                .executeAsOneOrNull()
        )
    }

    @Test
    fun keepsTheCanonicalPreferenceWhenBothAreExplicit() {
        insertCustom(involvements = trapBarSeedInvolvements())
        database.exercisePreferenceQueries.upsert(
            exerciseId = "user-trap-bar-deadlift",
            preference = "PREFER"
        )
        database.exercisePreferenceQueries.upsert(
            exerciseId = "trap-bar-deadlift",
            preference = "PREFER_LESS"
        )

        CustomExerciseDedupe(database).run()

        // The canonical exercise's explicit choice wins, including an explicit NEUTRAL.
        assertEquals(
            "PREFER_LESS",
            database.exercisePreferenceQueries.selectById("trap-bar-deadlift").executeAsOne()
        )
        assertNull(
            database.exercisePreferenceQueries.selectById("user-trap-bar-deadlift")
                .executeAsOneOrNull()
        )
    }

    @Test
    fun movesACustomsExclusionToTheCanonicalExercise() {
        insertCustom(involvements = trapBarSeedInvolvements())
        database.exerciseExclusionQueries.upsert(
            exerciseId = "user-trap-bar-deadlift",
            expiresAt = null
        )

        CustomExerciseDedupe(database).run()

        assertNull(
            database.exerciseExclusionQueries.selectById(
                "trap-bar-deadlift"
            ).executeAsOne().expiresAt
        )
        assertNull(
            database.exerciseExclusionQueries.selectById("user-trap-bar-deadlift")
                .executeAsOneOrNull()
        )
    }

    @Test
    fun indefiniteExclusionWinsOverADatedCanonicalExclusion() {
        insertCustom(involvements = trapBarSeedInvolvements())
        database.exerciseExclusionQueries.upsert(
            exerciseId = "user-trap-bar-deadlift",
            expiresAt = null
        )
        database.exerciseExclusionQueries.upsert(
            exerciseId = "trap-bar-deadlift",
            expiresAt = 1_000L
        )

        CustomExerciseDedupe(database).run()

        assertNull(
            database.exerciseExclusionQueries.selectById(
                "trap-bar-deadlift"
            ).executeAsOne().expiresAt
        )
    }

    @Test
    fun laterDatedExclusionWinsOnMerge() {
        insertCustom(involvements = trapBarSeedInvolvements())
        database.exerciseExclusionQueries.upsert(
            exerciseId = "user-trap-bar-deadlift",
            expiresAt = 2_000L
        )
        database.exerciseExclusionQueries.upsert(
            exerciseId = "trap-bar-deadlift",
            expiresAt = 1_000L
        )

        CustomExerciseDedupe(database).run()

        assertEquals(
            2_000L,
            database.exerciseExclusionQueries.selectById(
                "trap-bar-deadlift"
            ).executeAsOne().expiresAt
        )
    }

    @Test
    fun mergesACustomExerciseIntoTheSeededOneAndMovesItsHistory() {
        insertLegacyCustom()
        database.workoutLogQueries.insertSet(
            exerciseId = "user-trap-bar-deadlift",
            reps = 5,
            weightKg = 100.0,
            performedAt = 1,
            isWarmup = 0,
            involvements = "BACK:1.0,GLUTES:1.0",
            weekNumber = null,
            cycleNumber = null,
            dayIndex = null,
            rir = null,
            sessionId = null,
            loadKind = "EXTERNAL"
        )
        database.personalRecordQueries.upsert(
            exerciseId = "user-trap-bar-deadlift",
            weightKg = 140.0,
            reps = 3,
            loadKind = "EXTERNAL",
            updatedAt = 1
        )
        insertPlanHistoryEntry("user-trap-bar-deadlift")

        CustomExerciseDedupe(database).run()

        assertNull(
            database.exerciseQueries.selectById("user-trap-bar-deadlift").executeAsOneOrNull()
        )
        assertTrue(
            database.exerciseQueries.selectById("trap-bar-deadlift").executeAsOneOrNull() != null
        )
        assertEquals(
            "trap-bar-deadlift",
            database.workoutLogQueries.selectAllSets().executeAsOne().exerciseId
        )
        assertEquals(
            "trap-bar-deadlift",
            database.personalRecordQueries.selectAll().executeAsOne().exerciseId
        )
        assertEquals(
            "trap-bar-deadlift",
            database.planHistoryQueries.selectAllEntries().executeAsOne().exerciseId
        )
        assertEquals(
            encodeInvolvements(
                mapOf(MuscleGroup.LOWER_BACK to 1.0, MuscleGroup.GLUTES to 1.0)
            ),
            overrideFor("trap-bar-deadlift").involvements
        )
    }

    @Test
    fun leavesCustomExercisesWithNoSeededMatchAlone() {
        database.exerciseQueries.insertCustom(
            id = "user-my-thing",
            name = "My Thing",
            requiredEquipment = "BARBELL",
            movementPattern = "HINGE",
            isUnilateral = 0,
            loadCapability = "EXTERNAL",
            involvements = encodeInvolvements(mapOf(MuscleGroup.LATS to 1.0))
        )

        CustomExerciseDedupe(database).run()

        assertTrue(
            database.exerciseQueries.selectById("user-my-thing").executeAsOneOrNull() != null
        )
    }

    @Test
    fun doesNotOverwriteExistingOverrideWhenInvolvementsMatchDerivedSeed() {
        database.exerciseOverrideQueries.upsert(
            exerciseId = "barbell-row",
            name = null,
            requiredEquipment = null,
            movementPattern = null,
            isUnilateral = null,
            loadCapability = null,
            involvements = "LATS:0.9"
        )
        val seed = DefaultExercises.all.first { it.id == "barbell-row" }
        insertCustom(
            id = "user-barbell-row",
            name = "Barbell Row",
            equipment = "",
            pattern = "CORE",
            unilateral = 0,
            involvements = encodeInvolvements(seed.effectiveInvolvements)
        )

        CustomExerciseDedupe(database).run()

        assertEquals("LATS:0.9", overrideFor("barbell-row").involvements)
    }

    @Test
    fun legacyCustomInvolvementsArePreservedAsCanonicalOverride() {
        insertCustom(involvements = "BACK:1.0,GLUTES:1.0")

        CustomExerciseDedupe(database).run()

        val override = overrideFor("trap-bar-deadlift")
        assertEquals("BACK:1.0,GLUTES:1.0", override.involvements)
        assertEquals(
            decodeInvolvements("BACK:1.0,GLUTES:1.0"),
            decodeInvolvements(override.involvements)
        )
    }

    @Test
    fun treatsSubEpsilonWeightDifferenceAsTie() {
        insertCustom(involvements = trapBarSeedInvolvements())
        database.personalRecordQueries.upsert(
            exerciseId = "user-trap-bar-deadlift",
            weightKg = 100.0,
            reps = 8,
            loadKind = "EXTERNAL",
            updatedAt = 1
        )
        database.personalRecordQueries.upsert(
            exerciseId = "trap-bar-deadlift",
            weightKg = 100.0 + 5e-10,
            reps = 5,
            loadKind = "EXTERNAL",
            updatedAt = 3
        )

        CustomExerciseDedupe(database).run()

        val record = database.personalRecordQueries.selectById("trap-bar-deadlift").executeAsOne()
        assertEquals(8L, record.reps)
    }

    @Test
    fun usesWeightWhenDifferenceExceedsTolerance() {
        insertCustom(involvements = trapBarSeedInvolvements())
        database.personalRecordQueries.upsert(
            exerciseId = "user-trap-bar-deadlift",
            weightKg = 100.0000001,
            reps = 5,
            loadKind = "EXTERNAL",
            updatedAt = 1
        )
        database.personalRecordQueries.upsert(
            exerciseId = "trap-bar-deadlift",
            weightKg = 100.0,
            reps = 8,
            loadKind = "EXTERNAL",
            updatedAt = 2
        )

        CustomExerciseDedupe(database).run()

        val record = database.personalRecordQueries.selectById("trap-bar-deadlift").executeAsOne()
        assertEquals(100.0000001, record.weightKg)
    }

    private fun insertCustom(
        id: String = "user-trap-bar-deadlift",
        name: String = "Trap Bar Deadlift",
        equipment: String = "",
        pattern: String = "CORE",
        unilateral: Long = 0,
        involvements: String? = null
    ) {
        database.exerciseQueries.insertCustom(
            id = id,
            name = name,
            requiredEquipment = equipment,
            movementPattern = pattern,
            isUnilateral = unilateral,
            loadCapability = "EXTERNAL",
            involvements = involvements
        )
    }

    private fun trapBarSeedInvolvements(): String =
        encodeInvolvements(DefaultExercises.all.first { it.id == "trap-bar-deadlift" }.involvements)

    private fun overrideFor(id: String) =
        database.exerciseOverrideQueries.selectById(id).executeAsOne()

    private fun overrideOrNull(id: String) =
        database.exerciseOverrideQueries.selectById(id).executeAsOneOrNull()

    private fun customExerciseOrNull(id: String) =
        database.exerciseQueries.selectById(id).executeAsOneOrNull()

    private fun insertPlanHistoryEntry(exerciseId: String) {
        database.planHistoryQueries.insertPlan(
            engineId = "deterministic",
            acceptedAt = 1,
            weekNumber = 1,
            cycleNumber = 1
        )
        val planId = database.planHistoryQueries.lastInsertedPlanId().executeAsOne()
        database.planHistoryQueries.insertDay(planId = planId, dayIndex = 0, focus = "FULL_BODY")
        val dayId = database.planHistoryQueries.selectAllDays().executeAsOne().id
        database.planHistoryQueries.insertEntry(
            dayId = dayId,
            position = 0,
            exerciseId = exerciseId,
            sets = 3,
            reps = 5,
            exerciseName = "Trap Bar Deadlift",
            movementPattern = "HINGE",
            suggestedWeightKg = 100.0,
            loadCapability = "EXTERNAL",
            loadKind = "EXTERNAL"
        )
    }

    private fun insertLegacyCustom() {
        database.exerciseQueries.insertCustom(
            id = "user-trap-bar-deadlift",
            name = "Trap Bar Deadlift",
            requiredEquipment = "TRAP_BAR",
            movementPattern = "HINGE",
            isUnilateral = 0,
            loadCapability = "UNSPECIFIED",
            involvements = encodeInvolvements(
                mapOf(MuscleGroup.LOWER_BACK to 1.0, MuscleGroup.GLUTES to 1.0)
            )
        )
    }
}
