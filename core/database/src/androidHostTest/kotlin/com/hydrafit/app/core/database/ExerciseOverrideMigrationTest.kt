package com.hydrafit.app.core.database

import app.cash.sqldelight.db.SqlDriver
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
        driver = HistoricalDatabaseFixtures.v13()
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
        driver = HistoricalDatabaseFixtures.v13()
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
        HistoricalDatabaseFixtures.exec(driver, sql)
    }

    private fun migrateToLatest() {
        HydraFitDatabase.Schema.migrate(driver, 13, HydraFitDatabase.Schema.version)
    }
}
