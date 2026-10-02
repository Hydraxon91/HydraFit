package com.hydrafit.app.core.database

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.hydrafit.app.core.domain.workout.WorkoutSession
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest

class SqlDelightWorkoutSessionRepositoryTest {

    private lateinit var driver: SqlDriver
    private lateinit var repository: SqlDelightWorkoutSessionRepository

    @BeforeTest
    fun setUp() {
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        HydraFitDatabase.Schema.create(driver)
        repository = SqlDelightWorkoutSessionRepository(HydraFitDatabase(driver))
    }

    @AfterTest
    fun tearDown() {
        driver.close()
    }

    @Test
    fun createsAndReadsBackASession() = runTest {
        val session = WorkoutSession(id = "a", startedAtMillis = 100L, localEpochDay = 5L)

        repository.create(session)

        assertEquals(session, repository.all().single())
    }

    @Test
    fun openReturnsTheLatestUnclosedSession() = runTest {
        repository.create(WorkoutSession(id = "old", startedAtMillis = 1L, localEpochDay = 1L))
        repository.create(WorkoutSession(id = "new", startedAtMillis = 2L, localEpochDay = 1L))

        assertEquals("new", repository.open()?.id)
        assertEquals("new", repository.openFlow().first()?.id)
    }

    @Test
    fun endClosesTheSessionSoItIsNoLongerOpen() = runTest {
        repository.create(WorkoutSession(id = "a", startedAtMillis = 1L, localEpochDay = 1L))

        repository.end("a", 50L)

        assertNull(repository.open())
        assertEquals(50L, repository.all().single().endedAtMillis)
    }
}
