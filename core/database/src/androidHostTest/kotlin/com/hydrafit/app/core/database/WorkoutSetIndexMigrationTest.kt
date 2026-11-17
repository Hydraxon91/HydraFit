package com.hydrafit.app.core.database

import app.cash.sqldelight.db.SqlDriver
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class WorkoutSetIndexMigrationTest {
    private lateinit var driver: SqlDriver

    @AfterTest
    fun tearDown() {
        if (::driver.isInitialized) driver.close()
    }

    @Test
    fun migrationAddsTheBoundedReadIndexesAndKeepsExistingRows() {
        driver = HistoricalDatabaseFixtures.v1()
        HydraFitDatabase.Schema.migrate(driver, 1, 66)
        HistoricalDatabaseFixtures.exec(
            driver,
            "INSERT INTO exercise(id,name,requiredEquipment,movementPattern,loadCapability) " +
                "VALUES ('cable-row','Cable Row','CABLE_MACHINE','HORIZONTAL_PULL','EXTERNAL')"
        )
        HistoricalDatabaseFixtures.exec(
            driver,
            "INSERT INTO workoutSet(exerciseId,reps,weightKg,performedAt,loadKind) " +
                "VALUES ('cable-row',10,70.0,100,'EXTERNAL')"
        )
        assertEquals(0L, indexCount("workoutSet_performedAt"))
        assertEquals(0L, indexCount("workoutSet_occurrenceEntryId"))

        HydraFitDatabase.Schema.migrate(driver, 66, HydraFitDatabase.Schema.version)

        assertEquals(67L, HydraFitDatabase.Schema.version)
        assertEquals(1L, indexCount("workoutSet_performedAt"))
        assertEquals(1L, indexCount("workoutSet_occurrenceEntryId"))
        assertEquals(
            1L,
            HistoricalDatabaseFixtures.long(driver, "SELECT count(*) FROM workoutSet")
        )
    }

    @Test
    fun aFreshDatabaseHasTheSameIndexes() {
        driver = HistoricalDatabaseFixtures.v1()
        HydraFitDatabase.Schema.migrate(driver, 1, HydraFitDatabase.Schema.version)

        assertEquals(1L, indexCount("workoutSet_performedAt"))
        assertEquals(1L, indexCount("workoutSet_occurrenceEntryId"))
    }

    private fun indexCount(name: String): Long = HistoricalDatabaseFixtures.long(
        driver,
        "SELECT count(*) FROM sqlite_master WHERE type='index' AND name='$name'"
    )
}
