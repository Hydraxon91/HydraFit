package com.hydrafit.app.core.database

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertNull

class EquipmentMaxWeightMigrationTest {

    private lateinit var driver: SqlDriver

    @AfterTest
    fun tearDown() {
        if (::driver.isInitialized) driver.close()
    }

    @Test
    fun migratingFromV17DefaultsMaxWeightToNull() {
        driver = v17Database()
        driver.execute(
            identifier = null,
            sql = "INSERT INTO equipment(id, name, isBuiltIn) " +
                "VALUES ('CABLE_MACHINE', 'Cable machine', 1)",
            parameters = 0
        )

        HydraFitDatabase.Schema.migrate(driver, 17, 18)

        val row = HydraFitDatabase(driver).equipmentQueries.selectAll().executeAsOne()
        assertNull(row.maxWeightKg)
    }

    /** The v17 shape: the equipment table has no maxWeightKg column. */
    private fun v17Database(): SqlDriver {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        driver.execute(
            identifier = null,
            sql = "CREATE TABLE equipment (id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, " +
                "isBuiltIn INTEGER NOT NULL DEFAULT 0)",
            parameters = 0
        )
        return driver
    }
}
