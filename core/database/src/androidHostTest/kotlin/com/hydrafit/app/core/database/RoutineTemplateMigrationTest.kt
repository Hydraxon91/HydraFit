package com.hydrafit.app.core.database

import app.cash.sqldelight.db.QueryResult
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

        // Read the scoped old schema directly: the current generated query expects `loadKind`,
        // which 27.sqm adds later and a 25→26 end version does not create.
        assertTrue(countOf("routineTemplate") == 0L)
        assertTrue(countOf("routineWorkout") == 0L)
        assertTrue(countOf("routineEntry") == 0L)
    }

    private fun countOf(table: String): Long = driver.executeQuery(
        identifier = null,
        sql = "SELECT COUNT(*) FROM $table",
        mapper = { cursor ->
            cursor.next()
            QueryResult.Value(cursor.getLong(0) ?: 0L)
        },
        parameters = 0
    ).value
}
