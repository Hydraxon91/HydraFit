package com.hydrafit.app.core.database

import app.cash.sqldelight.db.SqlDriver
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
        driver = HistoricalDatabaseFixtures.v19()
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
        HistoricalDatabaseFixtures.exec(driver, sql)
    }
}
