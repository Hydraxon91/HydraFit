package com.hydrafit.app.core.database

import app.cash.sqldelight.db.SqlDriver
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertTrue

class RoutineTemplateMigrationTest {

    private lateinit var driver: SqlDriver

    @AfterTest
    fun tearDown() {
        if (::driver.isInitialized) driver.close()
    }

    @Test
    fun migratingFromV25CreatesTheRoutineTables() {
        driver = HistoricalDatabaseFixtures.empty()

        HydraFitDatabase.Schema.migrate(driver, 25, 26)

        // Read the scoped old schema directly: the current generated query expects `loadKind`,
        // which 27.sqm adds later and a 25→26 end version does not create.
        assertTrue(HistoricalDatabaseFixtures.count(driver, "routineTemplate") == 0L)
        assertTrue(HistoricalDatabaseFixtures.count(driver, "routineWorkout") == 0L)
        assertTrue(HistoricalDatabaseFixtures.count(driver, "routineEntry") == 0L)
    }
}
