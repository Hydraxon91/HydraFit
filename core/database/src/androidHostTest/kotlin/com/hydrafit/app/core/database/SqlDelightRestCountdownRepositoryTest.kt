package com.hydrafit.app.core.database

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.hydrafit.app.core.domain.workout.PersistedRestCountdown
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest

class SqlDelightRestCountdownRepositoryTest {
    private lateinit var driver: JdbcSqliteDriver
    private lateinit var repository: SqlDelightRestCountdownRepository

    @BeforeTest
    fun setUp() {
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        HydraFitDatabase.Schema.create(driver)
        repository = SqlDelightRestCountdownRepository(HydraFitDatabase(driver))
    }

    @AfterTest
    fun tearDown() {
        driver.close()
    }

    @Test
    fun countdownCanBeReplacedAndCleared() = runTest {
        val countdown = PersistedRestCountdown(
            deadlineElapsedMillis = 180_000L,
            durationMillis = 120_000L,
            bootIdentity = "boot-1",
            sessionId = "session-1",
            occurrenceId = 12L,
            exerciseId = "back-squat"
        )
        repository.save(countdown)
        assertEquals(countdown, repository.load())

        val updated = countdown.copy(deadlineElapsedMillis = 240_000L, durationMillis = 180_000L)
        repository.save(updated)
        assertEquals(updated, repository.load())

        repository.clear()
        assertNull(repository.load())
    }
}
