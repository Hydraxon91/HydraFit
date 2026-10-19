package com.hydrafit.app.core.database

import app.cash.sqldelight.db.SqlDriver
import com.hydrafit.app.core.domain.routine.Superset
import com.hydrafit.app.core.domain.routine.SupersetJson
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SupersetMigrationTest {
    private lateinit var driver: SqlDriver

    @AfterTest
    fun tearDown() {
        if (::driver.isInitialized) driver.close()
    }

    @Test
    fun allFourLegacyStoresKeepValuesAndRemainUnpaired() {
        driver = HistoricalDatabaseFixtures.v1()
        HydraFitDatabase.Schema.migrate(driver, 1, 59)
        val inserts = listOf(
            "INSERT INTO routineEntry(id,workoutId,position,exerciseId,sets,reps) " +
                "VALUES (1,1,0,'curl',3,10)",
            "INSERT INTO planHistoryEntry(id,dayId,position,exerciseId,sets,reps,exerciseName," +
                "movementPattern) VALUES (1,1,0,'curl',3,10,'Curl','BICEPS_ISOLATION')",
            "INSERT INTO activationEntry(id,workoutId,position,exerciseId,exerciseName," +
                "movementPattern,requiredEquipment,sets,reps) " +
                "VALUES (1,1,0,'curl','Curl','BICEPS_ISOLATION','',3,10)",
            "INSERT INTO occurrenceEntry(id,occurrenceId,position,exerciseId,exerciseName," +
                "movementPattern,requiredEquipment,sets,reps) " +
                "VALUES (1,1,0,'curl','Curl','BICEPS_ISOLATION','',3,10)"
        )
        inserts.forEach { HistoricalDatabaseFixtures.exec(driver, it) }
        HydraFitDatabase.Schema.migrate(driver, 59, HydraFitDatabase.Schema.version)
        listOf("routineEntry", "planHistoryEntry", "activationEntry", "occurrenceEntry")
            .forEach { table ->
                assertEquals(
                    10L,
                    HistoricalDatabaseFixtures.long(driver, "SELECT reps FROM $table")
                )
                assertNull(HistoricalDatabaseFixtures.text(driver, "SELECT superset FROM $table"))
                val encoded = SupersetJson.encode(Superset("arms", 0, 120))
                HistoricalDatabaseFixtures.exec(driver, "UPDATE $table SET superset = '$encoded'")
                assertEquals(
                    encoded,
                    HistoricalDatabaseFixtures.text(driver, "SELECT superset FROM $table")
                )
            }
    }
}
