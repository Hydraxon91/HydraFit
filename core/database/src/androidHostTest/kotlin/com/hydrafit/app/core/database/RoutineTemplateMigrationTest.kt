package com.hydrafit.app.core.database

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
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
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)

        HydraFitDatabase.Schema.migrate(driver, 25, 26)

        val queries = HydraFitDatabase(driver).routineTemplateQueries
        assertTrue(queries.selectAllTemplates().executeAsList().isEmpty())
        assertTrue(queries.selectAllWorkouts().executeAsList().isEmpty())
        assertTrue(queries.selectAllEntries().executeAsList().isEmpty())
    }
}
