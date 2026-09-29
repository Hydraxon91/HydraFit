package com.hydrafit.app.core.database

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.hydrafit.app.core.domain.engine.PlannerEngineId
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest

class SqlDelightEnginePreferenceRepositoryTest {

    private lateinit var driver: SqlDriver
    private lateinit var database: HydraFitDatabase
    private lateinit var repository: SqlDelightEnginePreferenceRepository

    @BeforeTest
    fun setUp() {
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        HydraFitDatabase.Schema.create(driver)
        database = HydraFitDatabase(driver)
        repository = SqlDelightEnginePreferenceRepository(database)
    }

    @AfterTest
    fun tearDown() {
        driver.close()
    }

    @Test
    fun defaultsToDeterministicWhenUnset() = runTest {
        assertEquals(PlannerEngineId.DETERMINISTIC, repository.selectedEngine())
    }

    @Test
    fun persistsTheSelectedEngine() = runTest {
        repository.setEngine(PlannerEngineId.GEMINI_API)

        assertEquals(PlannerEngineId.GEMINI_API, repository.selectedEngine())
        assertEquals(
            PlannerEngineId.GEMINI_API,
            SqlDelightEnginePreferenceRepository(database).selectedEngine()
        )
    }

    @Test
    fun engineFlowEmitsTheStoredEngine() = runTest {
        assertEquals(PlannerEngineId.DETERMINISTIC, repository.engineFlow().first())

        repository.setEngine(PlannerEngineId.GEMINI_API)

        assertEquals(PlannerEngineId.GEMINI_API, repository.engineFlow().first())
    }

    @Test
    fun daysPerWeekFlowDefaultsToFour() = runTest {
        assertEquals(4, repository.daysPerWeekFlow().first())

        repository.setDaysPerWeek(5)

        assertEquals(5, repository.daysPerWeekFlow().first())
    }

    @Test
    fun workoutDataSharingDefaultsToOffAndPersists() = runTest {
        assertEquals(false, repository.isWorkoutDataSharingEnabled())
        assertEquals(false, repository.workoutDataSharingFlow().first())

        repository.setWorkoutDataSharingEnabled(true)

        assertEquals(true, repository.isWorkoutDataSharingEnabled())
        assertEquals(true, repository.workoutDataSharingFlow().first())
        assertEquals(
            true,
            SqlDelightEnginePreferenceRepository(database).isWorkoutDataSharingEnabled()
        )
    }

    @Test
    fun fallsBackToDeterministicForUnknownStoredValue() = runTest {
        database.plannerEngineQueries.insertIgnoreRow(PlannerEngineId.DETERMINISTIC.name)
        database.plannerEngineQueries.updateEngine("NOT_AN_ENGINE")

        assertEquals(PlannerEngineId.DETERMINISTIC, repository.selectedEngine())
    }
}
