package com.hydrafit.app.core.database

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.hydrafit.app.core.domain.settings.DEFAULT_REST_SECONDS
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest

class SqlDelightRestPreferenceRepositoryTest {
    private lateinit var driver: JdbcSqliteDriver
    private lateinit var database: HydraFitDatabase
    private lateinit var repository: SqlDelightRestPreferenceRepository

    @BeforeTest
    fun setUp() {
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        HydraFitDatabase.Schema.create(driver)
        database = HydraFitDatabase(driver)
        repository = SqlDelightRestPreferenceRepository(database)
    }

    @AfterTest
    fun tearDown() {
        driver.close()
    }

    @Test
    fun defaultsGloballyAndRoundTripsExerciseOverrides() = runTest {
        assertEquals(DEFAULT_REST_SECONDS, repository.globalDefaultSeconds())
        repository.setGlobalDefaultSeconds(180L)
        repository.setExerciseOverrideSeconds("back-squat", 240L)
        repository.setExerciseOverrideSeconds("bench-press", 150L)

        assertEquals(180L, repository.globalDefaultSecondsFlow().first())
        assertEquals(240L, repository.exerciseOverrideSeconds("back-squat"))
        repository.clearExerciseOverride("back-squat")
        assertNull(repository.exerciseOverrideSeconds("back-squat"))
        assertEquals(150L, repository.exerciseOverrideSeconds("bench-press"))
    }
}
