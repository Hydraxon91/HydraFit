package com.hydrafit.app.core.database

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.hydrafit.app.core.domain.engine.PersonalRecord
import com.hydrafit.app.core.domain.time.TimeProvider
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest

class SqlDelightPersonalRecordRepositoryTest {

    private lateinit var driver: SqlDriver
    private lateinit var database: HydraFitDatabase
    private lateinit var repository: SqlDelightPersonalRecordRepository

    @BeforeTest
    fun setUp() {
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        HydraFitDatabase.Schema.create(driver)
        database = HydraFitDatabase(driver)
        repository = SqlDelightPersonalRecordRepository(database, TimeProvider { 42L })
    }

    @AfterTest
    fun tearDown() {
        driver.close()
    }

    @Test
    fun storesUpdatesAndClearsARecord() = runTest {
        repository.set(PersonalRecord("back-squat", 100.0, 5))
        assertEquals(listOf(PersonalRecord("back-squat", 100.0, 5)), repository.observe().first())

        repository.set(PersonalRecord("back-squat", 110.0, 3))
        assertEquals(listOf(PersonalRecord("back-squat", 110.0, 3)), repository.observe().first())

        repository.clear("back-squat")
        assertTrue(repository.observe().first().isEmpty())
    }
}
