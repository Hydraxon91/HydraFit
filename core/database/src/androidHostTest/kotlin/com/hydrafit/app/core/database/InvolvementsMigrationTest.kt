package com.hydrafit.app.core.database

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertNull

class InvolvementsMigrationTest {

    private lateinit var driver: SqlDriver

    @AfterTest
    fun tearDown() {
        if (::driver.isInitialized) driver.close()
    }

    @Test
    fun migratingFromV18DefaultsInvolvementsToNull() {
        driver = v18Database()
        exec(
            "INSERT INTO exercise(id, name, requiredEquipment, primaryMuscles, secondaryMuscles, " +
                "movementPattern, isCustom, isUnilateral) " +
                "VALUES ('x', 'X', '', 'CHEST', '', 'SQUAT', 0, 0)"
        )
        exec("INSERT INTO exerciseOverride(exerciseId, name) VALUES ('x', 'Y')")
        exec(
            "INSERT INTO workoutSet(exerciseId, reps, weightKg, performedAt, isWarmup, " +
                "primaryMuscles, secondaryMuscles) VALUES ('x', 5, 50.0, 1, 0, 'CHEST', '')"
        )

        // Scoped to 18→19: this test asserts the columns default to null, before the v20 conversion.
        HydraFitDatabase.Schema.migrate(driver, 18, 19)

        // Read the scoped old schema directly: the current generated queries expect columns added
        // later (workoutSet.rir, exercise.loadCapability), which do not exist at v19.
        assertNull(column("SELECT involvements FROM exercise WHERE id = 'x'"))
        assertNull(column("SELECT involvements FROM exerciseOverride WHERE exerciseId = 'x'"))
        assertNull(column("SELECT involvements FROM workoutSet"))
    }

    private fun column(sql: String): String? = driver.executeQuery(
        identifier = null,
        sql = sql,
        mapper = { cursor ->
            cursor.next()
            QueryResult.Value(cursor.getString(0))
        },
        parameters = 0
    ).value

    private fun exec(sql: String) {
        driver.execute(identifier = null, sql = sql, parameters = 0)
    }

    /** The v18 shape: none of the three tables has the involvements column yet. */
    private fun v18Database(): SqlDriver {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        val statements = listOf(
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
        statements.forEach { driver.execute(identifier = null, sql = it, parameters = 0) }
        return driver
    }
}
