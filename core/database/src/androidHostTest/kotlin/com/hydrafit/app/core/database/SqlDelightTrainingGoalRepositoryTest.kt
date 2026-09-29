package com.hydrafit.app.core.database

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.hydrafit.app.core.domain.engine.TrainingGoal
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest

class SqlDelightTrainingGoalRepositoryTest {

    private lateinit var driver: SqlDriver
    private lateinit var database: HydraFitDatabase
    private lateinit var repository: SqlDelightTrainingGoalRepository

    @BeforeTest
    fun setUp() {
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        HydraFitDatabase.Schema.create(driver)
        database = HydraFitDatabase(driver)
        repository = SqlDelightTrainingGoalRepository(database)
    }

    @AfterTest
    fun tearDown() {
        driver.close()
    }

    @Test
    fun defaultsToBalancedWhenUnset() = runTest {
        assertEquals(TrainingGoal.BALANCED, repository.selectedGoal())
    }

    @Test
    fun persistsTheSelectedGoal() = runTest {
        repository.setGoal(TrainingGoal.STRENGTH)

        assertEquals(TrainingGoal.STRENGTH, repository.selectedGoal())
        assertEquals(
            TrainingGoal.STRENGTH,
            SqlDelightTrainingGoalRepository(database).selectedGoal()
        )
    }

    @Test
    fun goalFlowEmitsTheStoredGoal() = runTest {
        assertEquals(TrainingGoal.BALANCED, repository.goalFlow().first())

        repository.setGoal(TrainingGoal.ENDURANCE)

        assertEquals(TrainingGoal.ENDURANCE, repository.goalFlow().first())
    }
}
