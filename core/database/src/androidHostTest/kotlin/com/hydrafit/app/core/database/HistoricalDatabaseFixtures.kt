package com.hydrafit.app.core.database

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver

/**
 * Shared scaffolding for the scoped migration tests.
 *
 * Each `vN()` returns an in-memory database in the shape it had *before* `N.sqm` ran, so a test can
 * populate it, migrate forward, and read columns the current generated queries may not yet know
 * about. Only the tables a test's migration step touches are created; the query helpers are raw SQL
 * so a scoped end version never trips over a later column.
 */
internal object HistoricalDatabaseFixtures {

    fun empty(): SqlDriver = driverOf(emptyList())

    fun driverOf(statements: List<String>): SqlDriver {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        statements.forEach { exec(driver, it) }
        return driver
    }

    fun exec(driver: SqlDriver, sql: String) {
        driver.execute(identifier = null, sql = sql, parameters = 0)
    }

    fun text(driver: SqlDriver, sql: String): String? = driver.executeQuery(
        identifier = null,
        sql = sql,
        mapper = { cursor ->
            cursor.next()
            QueryResult.Value(cursor.getString(0))
        },
        parameters = 0
    ).value

    fun long(driver: SqlDriver, sql: String): Long = driver.executeQuery(
        identifier = null,
        sql = sql,
        mapper = { cursor ->
            cursor.next()
            QueryResult.Value(cursor.getLong(0) ?: 0L)
        },
        parameters = 0
    ).value

    fun double(driver: SqlDriver, sql: String): Double? = driver.executeQuery(
        identifier = null,
        sql = sql,
        mapper = { cursor ->
            cursor.next()
            QueryResult.Value(cursor.getDouble(0))
        },
        parameters = 0
    ).value

    fun count(driver: SqlDriver, table: String, where: String? = null): Long =
        long(driver, "SELECT COUNT(*) FROM $table" + (where?.let { " WHERE $it" } ?: ""))

    /**
     * v1: only the `exercise` catalog table existed. Every other table is created by a migration,
     * so migrating this forward exercises the whole `1.sqm..N.sqm` chain end to end.
     */
    fun v1(): SqlDriver = driverOf(
        listOf(
            "CREATE TABLE exercise (id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, " +
                "requiredEquipment TEXT NOT NULL, primaryMuscles TEXT NOT NULL, " +
                "secondaryMuscles TEXT NOT NULL)"
        )
    )

    /** v13: separate `exerciseEdit`/`exerciseMuscleEdit` override tables; no `exerciseOverride`. */
    fun v13(): SqlDriver = driverOf(
        listOf(
            "CREATE TABLE exercise (id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, " +
                "requiredEquipment TEXT NOT NULL, primaryMuscles TEXT NOT NULL, " +
                "secondaryMuscles TEXT NOT NULL, movementPattern TEXT NOT NULL, " +
                "isCustom INTEGER NOT NULL DEFAULT 0)",
            "CREATE TABLE equipment (id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, " +
                "isBuiltIn INTEGER NOT NULL DEFAULT 0)",
            "CREATE TABLE workoutSet (id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, " +
                "exerciseId TEXT NOT NULL, reps INTEGER NOT NULL, weightKg REAL, " +
                "performedAt INTEGER NOT NULL, isWarmup INTEGER NOT NULL DEFAULT 0, " +
                "primaryMuscles TEXT, secondaryMuscles TEXT)",
            "CREATE TABLE exerciseEdit (exerciseId TEXT NOT NULL PRIMARY KEY, " +
                "requiredEquipment TEXT NOT NULL)",
            "CREATE TABLE exerciseMuscleEdit (exerciseId TEXT NOT NULL PRIMARY KEY, " +
                "primaryMuscles TEXT NOT NULL, secondaryMuscles TEXT NOT NULL)",
            "CREATE TABLE plannerEngine (id INTEGER NOT NULL PRIMARY KEY, " +
                "engineId TEXT NOT NULL, daysPerWeek INTEGER NOT NULL DEFAULT 4, " +
                "trainingGoal TEXT NOT NULL DEFAULT 'BALANCED', " +
                "shareWorkoutData INTEGER NOT NULL DEFAULT 0)",
            "CREATE TABLE planHistory (id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, " +
                "engineId TEXT NOT NULL, acceptedAt INTEGER NOT NULL)",
            // Entries predate v13 and no migration in the range creates them; later migrations alter.
            "CREATE TABLE planHistoryEntry (id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT)"
        )
    )

    /** v16: neither `exercise` nor `exerciseOverride` has the unilateral column. */
    fun v16(): SqlDriver = driverOf(
        listOf(
            "CREATE TABLE exercise (id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, " +
                "requiredEquipment TEXT NOT NULL, primaryMuscles TEXT NOT NULL, " +
                "secondaryMuscles TEXT NOT NULL, movementPattern TEXT NOT NULL, " +
                "isCustom INTEGER NOT NULL DEFAULT 0)",
            "CREATE TABLE exerciseOverride (exerciseId TEXT NOT NULL PRIMARY KEY, name TEXT, " +
                "requiredEquipment TEXT, primaryMuscles TEXT, secondaryMuscles TEXT, " +
                "movementPattern TEXT)",
            "CREATE TABLE equipment (id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, " +
                "isBuiltIn INTEGER NOT NULL DEFAULT 0)",
            "CREATE TABLE workoutSet (id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, " +
                "exerciseId TEXT NOT NULL, reps INTEGER NOT NULL, weightKg REAL, " +
                "performedAt INTEGER NOT NULL, isWarmup INTEGER NOT NULL DEFAULT 0, " +
                "primaryMuscles TEXT, secondaryMuscles TEXT)",
            "CREATE TABLE plannerEngine (id INTEGER NOT NULL PRIMARY KEY, " +
                "engineId TEXT NOT NULL, daysPerWeek INTEGER NOT NULL DEFAULT 4, " +
                "trainingGoal TEXT NOT NULL DEFAULT 'BALANCED', " +
                "shareWorkoutData INTEGER NOT NULL DEFAULT 0, " +
                "weightUnit TEXT NOT NULL DEFAULT 'KG')",
            // See v13: plan entries predate the range and are altered by later migrations.
            "CREATE TABLE planHistoryEntry (id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT)"
        )
    )

    /** v18: no `involvements` column on any of the three tables. */
    fun v18(): SqlDriver = driverOf(
        listOf(
            "CREATE TABLE exercise (id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, " +
                "requiredEquipment TEXT NOT NULL, primaryMuscles TEXT NOT NULL, " +
                "secondaryMuscles TEXT NOT NULL, movementPattern TEXT NOT NULL, " +
                "isCustom INTEGER NOT NULL DEFAULT 0, isUnilateral INTEGER NOT NULL DEFAULT 0)",
            "CREATE TABLE exerciseOverride (exerciseId TEXT NOT NULL PRIMARY KEY, name TEXT, " +
                "requiredEquipment TEXT, primaryMuscles TEXT, secondaryMuscles TEXT, " +
                "movementPattern TEXT, isUnilateral INTEGER)",
            "CREATE TABLE workoutSet (id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, " +
                "exerciseId TEXT NOT NULL, reps INTEGER NOT NULL, weightKg REAL, " +
                "performedAt INTEGER NOT NULL, isWarmup INTEGER NOT NULL DEFAULT 0, " +
                "primaryMuscles TEXT, secondaryMuscles TEXT, " +
                "weekNumber INTEGER, cycleNumber INTEGER, dayIndex INTEGER)"
        )
    )

    /** v19: `involvements` exists (may be null); the tag columns are still present. */
    fun v19(): SqlDriver = driverOf(
        listOf(
            "CREATE TABLE exercise (id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, " +
                "requiredEquipment TEXT NOT NULL, primaryMuscles TEXT NOT NULL, " +
                "secondaryMuscles TEXT NOT NULL, movementPattern TEXT NOT NULL, " +
                "isCustom INTEGER NOT NULL DEFAULT 0, isUnilateral INTEGER NOT NULL DEFAULT 0, " +
                "involvements TEXT)",
            "CREATE TABLE exerciseOverride (exerciseId TEXT NOT NULL PRIMARY KEY, name TEXT, " +
                "requiredEquipment TEXT, primaryMuscles TEXT, secondaryMuscles TEXT, " +
                "movementPattern TEXT, isUnilateral INTEGER, involvements TEXT)",
            "CREATE TABLE workoutSet (id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, " +
                "exerciseId TEXT NOT NULL, reps INTEGER NOT NULL, weightKg REAL, " +
                "performedAt INTEGER NOT NULL, isWarmup INTEGER NOT NULL DEFAULT 0, " +
                "primaryMuscles TEXT, secondaryMuscles TEXT, involvements TEXT)",
            "CREATE TABLE plannerEngine (id INTEGER NOT NULL PRIMARY KEY, " +
                "engineId TEXT NOT NULL, daysPerWeek INTEGER NOT NULL DEFAULT 4, " +
                "trainingGoal TEXT NOT NULL DEFAULT 'BALANCED', " +
                "shareWorkoutData INTEGER NOT NULL DEFAULT 0, " +
                "weightUnit TEXT NOT NULL DEFAULT 'KG')",
            // See v13: plan entries predate the range and are altered by later migrations.
            "CREATE TABLE planHistoryEntry (id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT)"
        )
    )

    /** v23: `workoutSet` has week/cycle/day but no `rir` column yet. */
    fun v23(): SqlDriver = driverOf(
        listOf(
            "CREATE TABLE workoutSet (id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, " +
                "exerciseId TEXT NOT NULL, reps INTEGER NOT NULL, weightKg REAL, " +
                "performedAt INTEGER NOT NULL, isWarmup INTEGER NOT NULL DEFAULT 0, " +
                "involvements TEXT, weekNumber INTEGER, cycleNumber INTEGER, dayIndex INTEGER)"
        )
    )

    /** v26: only the two tables 26.sqm alters need to exist for this scoped fixture. */
    fun v26(): SqlDriver = driverOf(
        listOf(
            "CREATE TABLE workoutSet (id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, " +
                "exerciseId TEXT NOT NULL, reps INTEGER NOT NULL, weightKg REAL, " +
                "performedAt INTEGER NOT NULL, isWarmup INTEGER NOT NULL DEFAULT 0, " +
                "involvements TEXT, weekNumber INTEGER, cycleNumber INTEGER, dayIndex INTEGER, " +
                "rir INTEGER, sessionId TEXT)",
            "CREATE TABLE workoutSession (id TEXT NOT NULL PRIMARY KEY, " +
                "startedAtMillis INTEGER NOT NULL, endedAtMillis INTEGER, " +
                "localEpochDay INTEGER NOT NULL)"
        )
    )

    /**
     * v27: the shape of every table the EX-02 migration (27.sqm) alters, without the EX-02 columns.
     */
    fun v27(): SqlDriver = driverOf(
        listOf(
            "CREATE TABLE exercise (id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, " +
                "requiredEquipment TEXT NOT NULL, movementPattern TEXT NOT NULL, " +
                "isCustom INTEGER NOT NULL DEFAULT 0, isUnilateral INTEGER NOT NULL DEFAULT 0, " +
                "involvements TEXT)",
            "CREATE TABLE exerciseOverride (exerciseId TEXT NOT NULL PRIMARY KEY, name TEXT, " +
                "requiredEquipment TEXT, movementPattern TEXT, isUnilateral INTEGER, " +
                "involvements TEXT)",
            "CREATE TABLE personalRecord (exerciseId TEXT NOT NULL PRIMARY KEY, " +
                "weightKg REAL NOT NULL, reps INTEGER NOT NULL, updatedAt INTEGER NOT NULL)",
            "CREATE TABLE plannerEngine (id INTEGER NOT NULL PRIMARY KEY, " +
                "engineId TEXT NOT NULL, daysPerWeek INTEGER NOT NULL DEFAULT 4, " +
                "trainingGoal TEXT NOT NULL DEFAULT 'BALANCED', " +
                "shareWorkoutData INTEGER NOT NULL DEFAULT 0, " +
                "weightUnit TEXT NOT NULL DEFAULT 'KG')",
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
                "workoutId INTEGER NOT NULL, position INTEGER NOT NULL, " +
                "exerciseId TEXT NOT NULL, sets INTEGER NOT NULL, reps INTEGER NOT NULL, " +
                "weightKg REAL)",
            "CREATE TABLE activationEntry (id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, " +
                "workoutId INTEGER NOT NULL, position INTEGER NOT NULL, " +
                "exerciseId TEXT NOT NULL, exerciseName TEXT NOT NULL, " +
                "movementPattern TEXT NOT NULL, " +
                "requiredEquipment TEXT NOT NULL, involvements TEXT, " +
                "isUnilateral INTEGER NOT NULL DEFAULT 0, sets INTEGER NOT NULL, " +
                "reps INTEGER NOT NULL, weightKg REAL)",
            "CREATE TABLE occurrenceEntry (id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, " +
                "occurrenceId INTEGER NOT NULL, sourceActivationEntryId INTEGER, " +
                "position INTEGER NOT NULL, exerciseId TEXT NOT NULL, " +
                "exerciseName TEXT NOT NULL, movementPattern TEXT NOT NULL, " +
                "requiredEquipment TEXT NOT NULL, " +
                "involvements TEXT, isUnilateral INTEGER NOT NULL DEFAULT 0, " +
                "sets INTEGER NOT NULL, reps INTEGER NOT NULL, weightKg REAL, " +
                "remainingDisposition TEXT, terminalRemainingSets INTEGER)"
        )
    )
}
