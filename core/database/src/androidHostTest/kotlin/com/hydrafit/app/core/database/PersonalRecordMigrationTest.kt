package com.hydrafit.app.core.database

import app.cash.sqldelight.db.SqlDriver
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertTrue

class PersonalRecordMigrationTest {

    private lateinit var driver: SqlDriver

    @AfterTest
    fun tearDown() {
        if (::driver.isInitialized) driver.close()
    }

    @Test
    fun migratingFromV21CreatesThePersonalRecordTable() {
        driver = HistoricalDatabaseFixtures.empty()

        HydraFitDatabase.Schema.migrate(driver, 21, 22)

        // Read the scoped old schema directly: the current generated query expects `loadKind`,
        // which 27.sqm adds later and a 21→22 end version does not create.
        assertTrue(HistoricalDatabaseFixtures.count(driver, "personalRecord") == 0L)
    }
}
