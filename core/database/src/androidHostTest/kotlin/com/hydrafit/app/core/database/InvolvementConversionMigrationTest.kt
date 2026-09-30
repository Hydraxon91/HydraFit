package com.hydrafit.app.core.database

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class InvolvementConversionMigrationTest {

    private lateinit var driver: SqlDriver

    @AfterTest
    fun tearDown() {
        if (::driver.isInitialized) driver.close()
    }

    @Test
    fun migratingFromV19DerivesInvolvementsFromTagsWithoutOverwritingExisting() {
        driver = v19Database()
        exec(
            "INSERT INTO exercise(id, name, requiredEquipment, primaryMuscles, secondaryMuscles, " +
                "movementPattern, isCustom, isUnilateral) " +
                "VALUES ('x', 'X', '', 'CHEST,TRICEPS', 'SHOULDERS', 'HORIZONTAL_PUSH', 0, 0)"
        )
        exec(
            "INSERT INTO exercise(id, name, requiredEquipment, primaryMuscles, secondaryMuscles, " +
                "movementPattern, isCustom, isUnilateral) " +
                "VALUES ('y', 'Y', '', 'QUADS', '', 'SQUAT', 0, 0)"
        )
        exec(
            "INSERT INTO exercise(id, name, requiredEquipment, primaryMuscles, secondaryMuscles, " +
                "movementPattern, isCustom, isUnilateral, involvements) " +
                "VALUES ('z', 'Z', '', 'CORE', '', 'CORE', 0, 0, 'CORE:0.9')"
        )
        exec(
            "INSERT INTO workoutSet(exerciseId, reps, weightKg, performedAt, isWarmup, " +
                "primaryMuscles, secondaryMuscles) VALUES ('x', 5, 50.0, 1, 0, 'BACK', 'BICEPS')"
        )
        exec(
            "INSERT INTO exerciseOverride(exerciseId, primaryMuscles, secondaryMuscles) " +
                "VALUES ('x', 'CHEST', 'SHOULDERS')"
        )

        HydraFitDatabase.Schema.migrate(driver, 19, HydraFitDatabase.Schema.version)

        val database = HydraFitDatabase(driver)
        val x = database.exerciseQueries.selectById("x").executeAsOne()
        val y = database.exerciseQueries.selectById("y").executeAsOne()
        val z = database.exerciseQueries.selectById("z").executeAsOne()
        val set = database.workoutLogQueries.selectAllSets().executeAsOne()
        val override = database.exerciseOverrideQueries.selectById("x").executeAsOne()

        assertEquals("SHOULDERS:0.5,CHEST:1.0,TRICEPS:1.0", x.involvements)
        assertEquals("QUADS:1.0", y.involvements)
        assertEquals("CORE:0.9", z.involvements)
        assertEquals("BICEPS:0.5,BACK:1.0", set.involvements)
        assertEquals("SHOULDERS:0.5,CHEST:1.0", override.involvements)
    }

    private fun exec(sql: String) {
        driver.execute(identifier = null, sql = sql, parameters = 0)
    }

    /** The v19 shape: involvements exists (may be null); the tag columns are still present. */
    private fun v19Database(): SqlDriver {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        val statements = listOf(
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
                "primaryMuscles TEXT, secondaryMuscles TEXT, involvements TEXT)"
        )
        statements.forEach { driver.execute(identifier = null, sql = it, parameters = 0) }
        return driver
    }
}
