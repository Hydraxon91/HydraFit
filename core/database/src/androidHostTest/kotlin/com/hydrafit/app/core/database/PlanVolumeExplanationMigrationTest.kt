package com.hydrafit.app.core.database

import app.cash.sqldelight.db.SqlDriver
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * C4 / OF-03 migration 30 -> 31. Explicit end version (31) keeps a later migration out of this
 * scoped test. The new `planVolumeExplanation` table is additive.
 */
class PlanVolumeExplanationMigrationTest {

    private lateinit var driver: SqlDriver

    @AfterTest
    fun tearDown() {
        if (::driver.isInitialized) driver.close()
    }

    @Test
    fun migratingFromV30AddsTheExplanationTableWithoutTouchingExistingRows() {
        driver = HistoricalDatabaseFixtures.empty()
        exec(
            "CREATE TABLE personalRecord (exerciseId TEXT NOT NULL PRIMARY KEY, " +
                "weightKg REAL NOT NULL, reps INTEGER NOT NULL, updatedAt INTEGER NOT NULL)"
        )
        exec(
            "INSERT INTO personalRecord(exerciseId, weightKg, reps, updatedAt) " +
                "VALUES ('back-squat', 100.0, 5, 1)"
        )

        HydraFitDatabase.Schema.migrate(driver, 30, 31)

        assertEquals(0L, HistoricalDatabaseFixtures.count(driver, "planVolumeExplanation"))
        assertEquals(1L, HistoricalDatabaseFixtures.count(driver, "personalRecord"))

        exec(
            "INSERT INTO planVolumeExplanation(planId, muscle, targetSets, isTargetEnforced, " +
                "directIsolationSets, estimatedOtherInvolvementCredits, unmetReason, " +
                "attribution) VALUES (1, 'BICEPS', 4, 1, 4, 1.5, NULL, 'DETERMINISTIC')"
        )
        assertEquals(
            4L,
            HistoricalDatabaseFixtures.long(
                driver,
                "SELECT directIsolationSets FROM planVolumeExplanation WHERE planId = 1"
            )
        )
        assertNull(
            HistoricalDatabaseFixtures.text(
                driver,
                "SELECT unmetReason FROM planVolumeExplanation WHERE planId = 1"
            )
        )
    }

    private fun exec(sql: String) = HistoricalDatabaseFixtures.exec(driver, sql)
}
