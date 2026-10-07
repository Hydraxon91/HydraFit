package com.hydrafit.app.core.database

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
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
        driver = v27Database()
        populateV27Rows()

        HydraFitDatabase.Schema.migrate(driver, 27, HydraFitDatabase.Schema.version)

        // Every pre-existing load-bearing row is marked legacy/unspecified.
        assertEquals("LEGACY_UNSPECIFIED", text("SELECT loadKind FROM workoutSet WHERE id = 1"))
        assertEquals(
            "LEGACY_UNSPECIFIED",
            text("SELECT loadKind FROM personalRecord WHERE exerciseId = 'barbell-bench-press'")
        )
        assertEquals("LEGACY_UNSPECIFIED", text("SELECT loadKind FROM planHistoryEntry WHERE id = 1"))
        assertEquals("LEGACY_UNSPECIFIED", text("SELECT loadKind FROM routineEntry WHERE id = 1"))
        assertEquals(
            "LEGACY_UNSPECIFIED",
            text("SELECT loadKind FROM activationEntry WHERE id = 1")
        )
        assertEquals(
            "LEGACY_UNSPECIFIED",
            text("SELECT loadKind FROM occurrenceEntry WHERE id = 1")
        )

        // Capability is left unknown on catalog/override and frozen snapshots.
        assertEquals("UNSPECIFIED", text("SELECT loadCapability FROM exercise WHERE id = 'barbell-bench-press'"))
        assertNull(
            text("SELECT loadCapability FROM exerciseOverride WHERE exerciseId = 'barbell-bench-press'")
        )
        assertEquals(
            "UNSPECIFIED",
            text("SELECT loadCapability FROM planHistoryEntry WHERE id = 1")
        )
        assertEquals(
            "UNSPECIFIED",
            text("SELECT loadCapability FROM activationEntry WHERE id = 1")
        )
        assertEquals(
            "UNSPECIFIED",
            text("SELECT loadCapability FROM occurrenceEntry WHERE id = 1")
        )

        // Recorded numbers and identities are preserved exactly: null, explicit zero and positive.
        assertNull(double("SELECT weightKg FROM workoutSet WHERE id = 1"))
        assertEquals(0.0, double("SELECT weightKg FROM workoutSet WHERE id = 2"))
        assertEquals(32.5, double("SELECT weightKg FROM workoutSet WHERE id = 3"))
        assertEquals("s1", text("SELECT sessionId FROM workoutSet WHERE id = 1"))
        assertEquals(4L, long("SELECT occurrenceId FROM workoutSet WHERE id = 3"))
        assertEquals(
            80.0,
            double("SELECT suggestedWeightKg FROM planHistoryEntry WHERE id = 1")
        )
    }

    @Test
    fun seedBackfillsBuiltInCapabilityAndLeavesCustomAndOverrideAlone() = runTest {
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
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
                "isUnilateral, involvements) VALUES ('barbell-bench-press', 'Barbell Bench Press', " +
                "'BARBELL,BENCH', 'HORIZONTAL_PUSH', 0, 0, 'CHEST_UPPER:0.7')"
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
                "isUnilateral, sets, reps, weightKg, remainingDisposition, terminalRemainingSets) " +
                "VALUES (1, 1, 1, 0, 'barbell-bench-press', 'Barbell Bench Press', " +
                "'HORIZONTAL_PUSH', 'BARBELL', 'CHEST_UPPER:0.7', 0, 3, 8, 60.0, NULL, NULL)"
        )
    }

    private fun exec(sql: String) {
        driver.execute(identifier = null, sql = sql, parameters = 0)
    }

    private fun text(sql: String): String? = driver.executeQuery(
        identifier = null,
        sql = sql,
        mapper = { cursor ->
            cursor.next()
            QueryResult.Value(cursor.getString(0))
        },
        parameters = 0
    ).value

    private fun long(sql: String): Long = driver.executeQuery(
        identifier = null,
        sql = sql,
        mapper = { cursor ->
            cursor.next()
            QueryResult.Value(cursor.getLong(0) ?: 0L)
        },
        parameters = 0
    ).value

    private fun double(sql: String): Double? = driver.executeQuery(
        identifier = null,
        sql = sql,
        mapper = { cursor ->
            cursor.next()
            QueryResult.Value(cursor.getDouble(0))
        },
        parameters = 0
    ).value

    /**
     * The v27 shape of every table the EX-02 migration alters, without the EX-02 columns. Building
     * only these tables keeps the fixture scoped to the 27→28 step under test.
     */
    private fun v27Database(): SqlDriver {
        val statements = listOf(
            "CREATE TABLE exercise (id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, " +
                "requiredEquipment TEXT NOT NULL, movementPattern TEXT NOT NULL, " +
                "isCustom INTEGER NOT NULL DEFAULT 0, isUnilateral INTEGER NOT NULL DEFAULT 0, " +
                "involvements TEXT)",
            "CREATE TABLE exerciseOverride (exerciseId TEXT NOT NULL PRIMARY KEY, name TEXT, " +
                "requiredEquipment TEXT, movementPattern TEXT, isUnilateral INTEGER, " +
                "involvements TEXT)",
            "CREATE TABLE personalRecord (exerciseId TEXT NOT NULL PRIMARY KEY, weightKg REAL NOT " +
                "NULL, reps INTEGER NOT NULL, updatedAt INTEGER NOT NULL)",
            "CREATE TABLE workoutSet (id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, " +
                "exerciseId TEXT NOT NULL, reps INTEGER NOT NULL, weightKg REAL, " +
                "performedAt INTEGER NOT NULL, isWarmup INTEGER NOT NULL DEFAULT 0, " +
                "involvements TEXT, weekNumber INTEGER, cycleNumber INTEGER, dayIndex INTEGER, " +
                "rir INTEGER, sessionId TEXT, occurrenceId INTEGER, occurrenceEntryId INTEGER)",
            "CREATE TABLE planHistoryEntry (id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, " +
                "dayId INTEGER NOT NULL, position INTEGER NOT NULL, exerciseId TEXT NOT NULL, " +
                "sets INTEGER NOT NULL, reps INTEGER NOT NULL, exerciseName TEXT NOT NULL, " +
                "movementPattern TEXT NOT NULL, suggestedWeightKg REAL)",
            "CREATE TABLE routineEntry (id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, " +
                "workoutId INTEGER NOT NULL, position INTEGER NOT NULL, exerciseId TEXT NOT NULL, " +
                "sets INTEGER NOT NULL, reps INTEGER NOT NULL, weightKg REAL)",
            "CREATE TABLE activationEntry (id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, " +
                "workoutId INTEGER NOT NULL, position INTEGER NOT NULL, exerciseId TEXT NOT NULL, " +
                "exerciseName TEXT NOT NULL, movementPattern TEXT NOT NULL, requiredEquipment TEXT " +
                "NOT NULL, involvements TEXT, isUnilateral INTEGER NOT NULL DEFAULT 0, " +
                "sets INTEGER NOT NULL, reps INTEGER NOT NULL, weightKg REAL)",
            "CREATE TABLE occurrenceEntry (id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, " +
                "occurrenceId INTEGER NOT NULL, sourceActivationEntryId INTEGER, position INTEGER " +
                "NOT NULL, exerciseId TEXT NOT NULL, exerciseName TEXT NOT NULL, movementPattern " +
                "TEXT NOT NULL, requiredEquipment TEXT NOT NULL, involvements TEXT, isUnilateral " +
                "INTEGER NOT NULL DEFAULT 0, sets INTEGER NOT NULL, reps INTEGER NOT NULL, " +
                "weightKg REAL, remainingDisposition TEXT, terminalRemainingSets INTEGER)"
        )
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        statements.forEach { driver.execute(identifier = null, sql = it, parameters = 0) }
        return driver
    }
}
