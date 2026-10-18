package com.hydrafit.app.core.database

import app.cash.sqldelight.db.SqlDriver
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WorkoutPrescriptionSnapshotMigrationTest {
    private lateinit var driver: SqlDriver

    @AfterTest
    fun tearDown() {
        if (::driver.isInitialized) driver.close()
    }

    @Test
    fun migrationPreservesActualsAndDoesNotInventTargets() {
        driver = HistoricalDatabaseFixtures.v1()
        HydraFitDatabase.Schema.migrate(driver, 1, 56)
        HistoricalDatabaseFixtures.exec(
            driver,
            "INSERT INTO exercise(id,name,requiredEquipment,movementPattern,loadCapability) " +
                "VALUES ('snapshot-test','Snapshot test','','HORIZONTAL_PUSH','EXTERNAL')"
        )
        HistoricalDatabaseFixtures.exec(
            driver,
            "INSERT INTO workoutSet(id,exerciseId,reps,weightKg,performedAt,loadKind) " +
                "VALUES (100,'snapshot-test',7,0.0,10,'EXTERNAL')"
        )
        HydraFitDatabase.Schema.migrate(driver, 56, HydraFitDatabase.Schema.version)
        val row = HydraFitDatabase(driver).workoutLogQueries.selectSetById(100).executeAsOne()
        assertEquals(7L, row.reps)
        assertEquals(0.0, row.weightKg)
        assertEquals(10L, row.performedAt)
        assertNull(row.prescriptionSnapshot)
    }
}
