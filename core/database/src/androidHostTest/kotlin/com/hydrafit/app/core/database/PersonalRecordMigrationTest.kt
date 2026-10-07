package com.hydrafit.app.core.database

import app.cash.sqldelight.db.QueryResult
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

        // Read the scoped old schema directly: the current generated query expects `loadKind`,
        // which 27.sqm adds later and a 21→22 end version does not create.
        val count = driver.executeQuery(
            identifier = null,
            sql = "SELECT COUNT(*) FROM personalRecord",
            mapper = { cursor ->
                cursor.next()
                QueryResult.Value(cursor.getLong(0) ?: 0L)
            },
            parameters = 0
        ).value

        assertTrue(count == 0L)
    }
}
