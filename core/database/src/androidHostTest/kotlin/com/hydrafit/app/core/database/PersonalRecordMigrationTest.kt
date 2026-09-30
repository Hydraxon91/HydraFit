package com.hydrafit.app.core.database

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
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
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)

        HydraFitDatabase.Schema.migrate(driver, 21, 22)

        val records = HydraFitDatabase(driver).personalRecordQueries.selectAll().executeAsList()

        assertTrue(records.isEmpty())
    }
}
