package com.hydrafit.app.core.database

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.hydrafit.app.core.domain.unit.WeightUnit
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest

class SqlDelightWeightUnitRepositoryTest {

    private lateinit var driver: SqlDriver
    private lateinit var database: HydraFitDatabase
    private lateinit var repository: SqlDelightWeightUnitRepository

    @BeforeTest
    fun setUp() {
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        HydraFitDatabase.Schema.create(driver)
        database = HydraFitDatabase(driver)
        repository = SqlDelightWeightUnitRepository(database)
    }

    @AfterTest
    fun tearDown() {
        driver.close()
    }

    @Test
    fun defaultsToKilogramsWhenUnset() = runTest {
        assertEquals(WeightUnit.KG, repository.selectedUnit())
    }

    @Test
    fun persistsTheSelectedUnit() = runTest {
        repository.setUnit(WeightUnit.LB)

        assertEquals(WeightUnit.LB, repository.selectedUnit())
        assertEquals(WeightUnit.LB, repository.unitFlow().first())
    }

    @Test
    fun fallsBackToKilogramsForAnUnknownStoredValue() = runTest {
        database.plannerEngineQueries.insertIgnoreRow("DETERMINISTIC")
        database.plannerEngineQueries.updateWeightUnit("STONE")

        assertEquals(WeightUnit.KG, repository.selectedUnit())
    }
}
