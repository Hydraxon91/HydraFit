package com.hydrafit.app.core.database

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.hydrafit.app.core.domain.settings.TrainingGuidancePreferences
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest

class TrainingGuidancePreferenceRepositoryTest {
    @Test
    fun defaultsOffAndUpdatesEachOptInIndependently() = runTest {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        try {
            HydraFitDatabase.Schema.create(driver)
            val repository =
                SqlDelightTrainingGuidancePreferenceRepository(HydraFitDatabase(driver))
            assertEquals(TrainingGuidancePreferences(), repository.observe().first())
            repository.setRestAlertsEnabled(true)
            repository.setShortRestCoachingEnabled(true)
            assertEquals(TrainingGuidancePreferences(true, true), repository.get())
            repository.setRestAlertsEnabled(false)
            assertEquals(TrainingGuidancePreferences(false, true), repository.observe().first())
            repository.setShortRestCoachingEnabled(false)
            assertEquals(TrainingGuidancePreferences(), repository.get())
        } finally {
            driver.close()
        }
    }
}
