package com.hydrafit.app.core.database

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.hydrafit.app.core.domain.equipment.EquipmentTag
import com.hydrafit.app.core.domain.fatigue.MuscleGroup
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest

class ExerciseOverrideMigrationTest {

    private lateinit var driver: SqlDriver

    @AfterTest
    fun tearDown() {
        if (::driver.isInitialized) driver.close()
    }

    @Test
    fun migratingFromV13MergesBothOverrideTables() = runTest {
        driver = v13Database()
        exec("INSERT INTO exerciseEdit(exerciseId, requiredEquipment) VALUES ('e', 'DUMBBELL')")
        exec(
            "INSERT INTO exerciseMuscleEdit(exerciseId, primaryMuscles, secondaryMuscles) " +
                "VALUES ('e', 'CHEST', 'TRICEPS')"
        )
        exec(
            "INSERT INTO exerciseMuscleEdit(exerciseId, primaryMuscles, secondaryMuscles) " +
                "VALUES ('f', 'QUADS', '')"
        )

        migrateToLatest()
        val database = HydraFitDatabase(driver)
        val rows = database.exerciseOverrideQueries.selectAll().executeAsList()
            .associateBy { it.exerciseId }

        assertEquals("DUMBBELL", rows.getValue("e").requiredEquipment)
        assertEquals("TRICEPS:0.5,CHEST:1.0", rows.getValue("e").involvements)
        assertEquals(null, rows.getValue("f").requiredEquipment)
        assertEquals("QUADS:1.0", rows.getValue("f").involvements)
    }

    @Test
    fun migratedOverridesFlowThroughTheCatalog() = runTest {
        driver = v13Database()
        exec(
            "INSERT INTO exercise(id, name, requiredEquipment, primaryMuscles, secondaryMuscles, " +
                "movementPattern, isCustom) VALUES ('x', 'X', 'BARBELL', 'QUADS', '', 'SQUAT', 0)"
        )
        exec("INSERT INTO exerciseEdit(exerciseId, requiredEquipment) VALUES ('x', 'DUMBBELL')")
        exec(
            "INSERT INTO exerciseMuscleEdit(exerciseId, primaryMuscles, secondaryMuscles) " +
                "VALUES ('x', 'CHEST_UPPER', '')"
        )

        migrateToLatest()
        val exercise = SqlDelightExerciseCatalog(HydraFitDatabase(driver)).all()
            .single { it.id == "x" }

        assertEquals(setOf(EquipmentTag.DUMBBELL), exercise.requiredEquipment)
        assertEquals(setOf(MuscleGroup.CHEST_UPPER), exercise.primaryMuscles)
    }

    private fun exec(sql: String) {
        driver.execute(identifier = null, sql = sql, parameters = 0)
    }

    private fun migrateToLatest() {
        HydraFitDatabase.Schema.migrate(driver, 13, HydraFitDatabase.Schema.version)
    }

    /** The v13 schema shape: exercise has isCustom but the two separate override tables still exist. */
    private fun v13Database(): SqlDriver {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        val statements = listOf(
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
            // Plan history entries predate v13 and are not created by any migration in the 13→latest
            // range; the EX-02 migration (27.sqm) alters this table, so the fixture must define it.
            "CREATE TABLE planHistoryEntry (id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT)"
        )
        statements.forEach { driver.execute(identifier = null, sql = it, parameters = 0) }
        return driver
    }
}
