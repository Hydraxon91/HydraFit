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
        assertEquals("CHEST", rows.getValue("e").primaryMuscles)
        assertEquals("TRICEPS", rows.getValue("e").secondaryMuscles)
        assertEquals(null, rows.getValue("f").requiredEquipment)
        assertEquals("QUADS", rows.getValue("f").primaryMuscles)
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
                "VALUES ('x', 'CHEST', '')"
        )

        migrateToLatest()
        val exercise = SqlDelightExerciseCatalog(HydraFitDatabase(driver)).all()
            .single { it.id == "x" }

        assertEquals(setOf(EquipmentTag.DUMBBELL), exercise.requiredEquipment)
        assertEquals(setOf(MuscleGroup.CHEST), exercise.primaryMuscles)
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
            "CREATE TABLE exerciseEdit (exerciseId TEXT NOT NULL PRIMARY KEY, " +
                "requiredEquipment TEXT NOT NULL)",
            "CREATE TABLE exerciseMuscleEdit (exerciseId TEXT NOT NULL PRIMARY KEY, " +
                "primaryMuscles TEXT NOT NULL, secondaryMuscles TEXT NOT NULL)"
        )
        statements.forEach { driver.execute(identifier = null, sql = it, parameters = 0) }
        return driver
    }
}
