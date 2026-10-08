package com.hydrafit.app.core.database

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.hydrafit.app.core.domain.equipment.ExerciseExclusion
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest

class SqlDelightExerciseExclusionRepositoryTest {

    private lateinit var driver: JdbcSqliteDriver
    private lateinit var repository: SqlDelightExerciseExclusionRepository

    @BeforeTest
    fun setUp() {
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        HydraFitDatabase.Schema.create(driver)
        repository = SqlDelightExerciseExclusionRepository(HydraFitDatabase(driver))
    }

    @AfterTest
    fun tearDown() {
        driver.close()
    }

    @Test
    fun roundTripsDatedAndIndefiniteExclusions() = runTest {
        assertNull(repository.exclusion("back-squat"))

        repository.set(ExerciseExclusion("back-squat", expiresAtMillis = 1_000L))
        repository.set(ExerciseExclusion("bench-press", expiresAtMillis = null))

        assertEquals(ExerciseExclusion("back-squat", 1_000L), repository.exclusion("back-squat"))
        assertEquals(ExerciseExclusion("bench-press", null), repository.exclusion("bench-press"))
        assertEquals(
            listOf(
                ExerciseExclusion("back-squat", 1_000L),
                ExerciseExclusion("bench-press", null)
            ),
            repository.observe().first()
        )
    }

    @Test
    fun reExcludingReplacesThePreviousWindow() = runTest {
        repository.set(ExerciseExclusion("back-squat", expiresAtMillis = 1_000L))
        repository.set(ExerciseExclusion("back-squat", expiresAtMillis = null))

        assertEquals(ExerciseExclusion("back-squat", null), repository.exclusion("back-squat"))
        assertEquals(1, repository.observe().first().size)
    }

    @Test
    fun clearRemovesTheExclusion() = runTest {
        repository.set(ExerciseExclusion("back-squat", expiresAtMillis = 1_000L))

        repository.clear("back-squat")

        assertNull(repository.exclusion("back-squat"))
        assertEquals(emptyList(), repository.observe().first())
    }
}
