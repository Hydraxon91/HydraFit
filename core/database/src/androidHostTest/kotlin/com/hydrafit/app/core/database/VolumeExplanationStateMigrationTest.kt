package com.hydrafit.app.core.database

import app.cash.sqldelight.db.SqlDriver
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * C4 corrective migration 31 -> 32. Backfills an AVAILABLE state row for every stored assessment
 * without touching the assessment rows themselves.
 */
class VolumeExplanationStateMigrationTest {

    private lateinit var driver: SqlDriver

    @AfterTest
    fun tearDown() {
        if (::driver.isInitialized) driver.close()
    }

    @Test
    fun migratingFromV31BackfillsAvailableStateForStoredAssessments() {
        driver = HistoricalDatabaseFixtures.empty()
        HistoricalDatabaseFixtures.exec(
            driver,
            "CREATE TABLE planVolumeExplanation (planId INTEGER NOT NULL, muscle TEXT NOT NULL, " +
                "targetSets INTEGER NOT NULL, isTargetEnforced INTEGER NOT NULL, " +
                "directIsolationSets INTEGER NOT NULL, " +
                "estimatedOtherInvolvementCredits REAL NOT NULL, unmetReason TEXT, " +
                "attribution TEXT NOT NULL, PRIMARY KEY (planId, muscle))"
        )
        HistoricalDatabaseFixtures.exec(
            driver,
            "INSERT INTO planVolumeExplanation VALUES (1, 'BICEPS', 4, 1, 4, 1.0, NULL, 'DETERMINISTIC')"
        )
        HistoricalDatabaseFixtures.exec(
            driver,
            "INSERT INTO planVolumeExplanation VALUES (1, 'TRICEPS', 4, 1, 2, 0.5, NULL, 'DETERMINISTIC')"
        )

        HydraFitDatabase.Schema.migrate(driver, 31, 32)

        assertEquals(
            1L,
            HistoricalDatabaseFixtures.count(driver, "planVolumeExplanationState")
        )
        assertEquals(
            "AVAILABLE",
            HistoricalDatabaseFixtures.text(
                driver,
                "SELECT status FROM planVolumeExplanationState WHERE planId = 1"
            )
        )
        assertEquals(
            1L,
            HistoricalDatabaseFixtures.long(
                driver,
                "SELECT assessmentVersion FROM planVolumeExplanationState WHERE planId = 1"
            )
        )
        // The stored assessment rows are untouched.
        assertEquals(2L, HistoricalDatabaseFixtures.count(driver, "planVolumeExplanation"))
    }
}
