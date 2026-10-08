package com.hydrafit.app.core.database

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.hydrafit.app.core.domain.equipment.ExercisePreference
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest

class SqlDelightExercisePreferenceRepositoryTest {

    private lateinit var driver: JdbcSqliteDriver
    private lateinit var repository: SqlDelightExercisePreferenceRepository

    @BeforeTest
    fun setUp() {
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        HydraFitDatabase.Schema.create(driver)
        repository = SqlDelightExercisePreferenceRepository(HydraFitDatabase(driver))
    }

    @AfterTest
    fun tearDown() {
        driver.close()
    }

    @Test
    fun roundTripsExplicitPreferencesAndDefaultsMissingIdsToNeutral() = runTest {
        assertEquals(ExercisePreference.NEUTRAL, repository.preference("back-squat"))

        repository.set("back-squat", ExercisePreference.PREFER)
        repository.set("bench-press", ExercisePreference.PREFER_LESS)

        assertEquals(ExercisePreference.PREFER, repository.preference("back-squat"))
        assertEquals(
            mapOf(
                "back-squat" to ExercisePreference.PREFER,
                "bench-press" to ExercisePreference.PREFER_LESS
            ),
            repository.observe().first()
        )
    }

    /**
     * An explicitly stored NEUTRAL is distinct from never having set a preference, so it survives a
     * round-trip and is reported by [observe] rather than disappearing as the absent default.
     */
    @Test
    fun storesAnExplicitNeutralAsItsOwnChoice() = runTest {
        repository.set("back-squat", ExercisePreference.PREFER)
        repository.set("back-squat", ExercisePreference.NEUTRAL)

        assertEquals(ExercisePreference.NEUTRAL, repository.preference("back-squat"))
        assertEquals(
            mapOf("back-squat" to ExercisePreference.NEUTRAL),
            repository.observe().first()
        )
    }

    @Test
    fun editingFromDislikeToLikeOverwritesTheStoredValue() = runTest {
        repository.set("bench-press", ExercisePreference.PREFER_LESS)
        repository.set("bench-press", ExercisePreference.PREFER)

        assertEquals(ExercisePreference.PREFER, repository.preference("bench-press"))
        assertEquals(1, repository.observe().first().size)
    }
}
