package com.hydrafit.app.core.database

import app.cash.sqldelight.db.SqlDriver
import com.hydrafit.app.core.domain.equipment.ExerciseLoadCapability
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest

/**
 * EX-02 migration and backfill. A populated pre-EX-02 (v27) database must upgrade with every
 * recorded load marked legacy/unspecified while its ids, links and null/zero/positive values are
 * preserved; the seed then backfills curated capability onto built-in rows only.
 */
class ExerciseLoadSemanticsMigrationTest {

    private lateinit var driver: SqlDriver

    @AfterTest
    fun tearDown() {
        if (::driver.isInitialized) driver.close()
    }

    @Test
    fun migratingFromV27MarksExistingLoadRowsLegacyAndPreservesValues() {
        driver = HistoricalDatabaseFixtures.v27()
        populateV27Rows()

        HydraFitDatabase.Schema.migrate(driver, 27, HydraFitDatabase.Schema.version)

        // Every pre-existing load-bearing row is marked legacy/unspecified.
        assertEquals(
            "LEGACY_UNSPECIFIED",
            value("SELECT loadKind FROM workoutSet WHERE id = 1")
        )
        assertEquals(
            "UNKNOWN",
            value("SELECT timingProvenance FROM workoutSet WHERE id = 1")
        )
        assertEquals(
            "LEGACY_UNSPECIFIED",
            value("SELECT loadKind FROM personalRecord WHERE exerciseId = 'barbell-bench-press'")
        )
        assertEquals(
            "LEGACY_UNSPECIFIED",
            value("SELECT loadKind FROM planHistoryEntry WHERE id = 1")
        )
        assertEquals(
            "LEGACY_UNSPECIFIED",
            value("SELECT loadKind FROM routineEntry WHERE id = 1")
        )
        assertEquals(
            "LEGACY_UNSPECIFIED",
            value("SELECT loadKind FROM activationEntry WHERE id = 1")
        )
        assertEquals(
            "LEGACY_UNSPECIFIED",
            value("SELECT loadKind FROM occurrenceEntry WHERE id = 1")
        )

        // Capability is left unknown on catalog/override and frozen snapshots.
        assertEquals(
            "UNSPECIFIED",
            value("SELECT loadCapability FROM exercise WHERE id = 'barbell-bench-press'")
        )
        assertNull(
            value(
                "SELECT loadCapability FROM exerciseOverride " +
                    "WHERE exerciseId = 'barbell-bench-press'"
            )
        )
        assertEquals(
            "UNSPECIFIED",
            value("SELECT loadCapability FROM planHistoryEntry WHERE id = 1")
        )
        assertEquals(
            "UNSPECIFIED",
            value("SELECT loadCapability FROM activationEntry WHERE id = 1")
        )
        assertEquals(
            "UNSPECIFIED",
            value("SELECT loadCapability FROM occurrenceEntry WHERE id = 1")
        )

        // Recorded numbers and identities are preserved exactly: null, explicit zero and positive.
        assertNull(value("SELECT weightKg FROM workoutSet WHERE id = 1"))
        assertEquals(0.0, decimal("SELECT weightKg FROM workoutSet WHERE id = 2"))
        assertEquals(32.5, decimal("SELECT weightKg FROM workoutSet WHERE id = 3"))
        assertEquals("s1", value("SELECT sessionId FROM workoutSet WHERE id = 1"))
        assertEquals(4L, number("SELECT occurrenceId FROM workoutSet WHERE id = 3"))
        assertEquals(
            80.0,
            decimal("SELECT suggestedWeightKg FROM planHistoryEntry WHERE id = 1")
        )
    }

    @Test
    fun seedBackfillsBuiltInCapabilityAndLeavesCustomAndOverrideAlone() = runTest {
        driver = HistoricalDatabaseFixtures.empty()
        HydraFitDatabase.Schema.create(driver)
        val database = HydraFitDatabase(driver)

        // Simulate an upgraded install: a built-in row still carrying the legacy UNSPECIFIED.
        database.exerciseQueries.insertIgnore(
            id = "ab-roll",
            name = "Ab Roll",
            requiredEquipment = "BODYWEIGHT,AB_ROLLER",
            movementPattern = "CORE",
            loadCapability = "UNSPECIFIED"
        )
        // A custom row with its own explicit capability and weights.
        database.exerciseQueries.insertCustom(
            id = "user-x",
            name = "User X",
            requiredEquipment = "BARBELL",
            movementPattern = "SQUAT",
            isUnilateral = 0,
            loadCapability = "BODYWEIGHT_ADDABLE",
            involvements = "QUADS:1.0"
        )
        // A user override adding capability to a built-in.
        database.exerciseOverrideQueries.upsert(
            exerciseId = "barbell-bench-press",
            name = null,
            requiredEquipment = "BARBELL",
            movementPattern = null,
            isUnilateral = null,
            loadCapability = "BODYWEIGHT_ONLY",
            involvements = null
        )

        SeedExerciseCatalog(database).seed()
        SeedExerciseCatalog(database).seed() // idempotent

        val catalog = SqlDelightExerciseCatalog(database).all().associateBy { it.id }
        // The curated default reaches the ab-roll row that started UNSPECIFIED.
        assertEquals(
            ExerciseLoadCapability.BODYWEIGHT_ONLY,
            catalog.getValue("ab-roll").loadCapability
        )
        // A normal built-in resolves to EXTERNAL.
        assertEquals(
            ExerciseLoadCapability.EXTERNAL,
            catalog.getValue("back-squat").loadCapability
        )
        // The custom selection survives the seed.
        assertEquals(
            ExerciseLoadCapability.BODYWEIGHT_ADDABLE,
            catalog.getValue("user-x").loadCapability
        )
        assertEquals(
            "QUADS:1.0",
            database.exerciseQueries.selectById("user-x").executeAsOne().involvements
        )
        // The built-in override wins over the seed default.
        assertEquals(
            ExerciseLoadCapability.BODYWEIGHT_ONLY,
            catalog.getValue("barbell-bench-press").loadCapability
        )
    }

    /** Populates the v27 database with representative load-bearing rows before migration. */
    private fun populateV27Rows() {
        exec(
            "INSERT INTO exercise(id, name, requiredEquipment, movementPattern, isCustom, " +
                "isUnilateral, involvements) VALUES ('barbell-bench-press', " +
                "'Barbell Bench Press', 'BARBELL,BENCH', 'HORIZONTAL_PUSH', 0, 0, " +
                "'CHEST_UPPER:0.7')"
        )
        exec(
            "INSERT INTO exerciseOverride(exerciseId, name, requiredEquipment, movementPattern, " +
                "isUnilateral, involvements) VALUES ('barbell-bench-press', NULL, 'BARBELL', " +
                "NULL, NULL, NULL)"
        )
        exec(
            "INSERT INTO personalRecord(exerciseId, weightKg, reps, updatedAt) " +
                "VALUES ('barbell-bench-press', 100.0, 5, 1)"
        )
        exec(
            "INSERT INTO workoutSet(id, exerciseId, reps, weightKg, performedAt, isWarmup, " +
                "involvements, weekNumber, cycleNumber, dayIndex, rir, sessionId, occurrenceId, " +
                "occurrenceEntryId) VALUES (1, 'barbell-bench-press', 5, NULL, 1, 0, " +
                "'CHEST_UPPER:0.7', 1, 1, 0, 2, 's1', NULL, NULL)"
        )
        exec(
            "INSERT INTO workoutSet(id, exerciseId, reps, weightKg, performedAt, isWarmup, " +
                "involvements, weekNumber, cycleNumber, dayIndex, rir, sessionId, occurrenceId, " +
                "occurrenceEntryId) VALUES (2, 'barbell-bench-press', 5, 0.0, 2, 0, " +
                "'CHEST_UPPER:0.7', 1, 1, 0, NULL, 's1', NULL, NULL)"
        )
        exec(
            "INSERT INTO workoutSet(id, exerciseId, reps, weightKg, performedAt, isWarmup, " +
                "involvements, weekNumber, cycleNumber, dayIndex, rir, sessionId, occurrenceId, " +
                "occurrenceEntryId) VALUES (3, 'barbell-bench-press', 5, 32.5, 3, 0, " +
                "'CHEST_UPPER:0.7', 1, 1, 0, NULL, 's1', 4, 5)"
        )
        exec(
            "INSERT INTO planHistoryEntry(id, dayId, position, exerciseId, sets, reps, " +
                "exerciseName, movementPattern, suggestedWeightKg) VALUES (1, 1, 0, " +
                "'barbell-bench-press', 3, 5, 'Barbell Bench Press', 'HORIZONTAL_PUSH', 80.0)"
        )
        exec(
            "INSERT INTO routineEntry(id, workoutId, position, exerciseId, sets, reps, weightKg) " +
                "VALUES (1, 1, 0, 'barbell-bench-press', 3, 8, 60.0)"
        )
        exec(
            "INSERT INTO activationEntry(id, workoutId, position, exerciseId, exerciseName, " +
                "movementPattern, requiredEquipment, involvements, isUnilateral, sets, reps, " +
                "weightKg) VALUES (1, 1, 0, 'barbell-bench-press', 'Barbell Bench Press', " +
                "'HORIZONTAL_PUSH', 'BARBELL', 'CHEST_UPPER:0.7', 0, 3, 8, 60.0)"
        )
        exec(
            "INSERT INTO occurrenceEntry(id, occurrenceId, sourceActivationEntryId, position, " +
                "exerciseId, exerciseName, movementPattern, requiredEquipment, involvements, " +
                "isUnilateral, sets, reps, weightKg, remainingDisposition, " +
                "terminalRemainingSets) " +
                "VALUES (1, 1, 1, 0, 'barbell-bench-press', 'Barbell Bench Press', " +
                "'HORIZONTAL_PUSH', 'BARBELL', 'CHEST_UPPER:0.7', 0, 3, 8, 60.0, NULL, NULL)"
        )
    }

    private fun exec(sql: String) = HistoricalDatabaseFixtures.exec(driver, sql)

    private fun value(sql: String): String? = HistoricalDatabaseFixtures.text(driver, sql)

    private fun number(sql: String): Long = HistoricalDatabaseFixtures.long(driver, sql)

    private fun decimal(sql: String): Double? = HistoricalDatabaseFixtures.double(driver, sql)
}
