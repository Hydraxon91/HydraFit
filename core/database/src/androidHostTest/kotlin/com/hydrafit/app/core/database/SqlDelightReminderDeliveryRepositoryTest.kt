package com.hydrafit.app.core.database

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class SqlDelightReminderDeliveryRepositoryTest {
    @Test
    fun claimsABlockAndDayOnceAndReleasesItAfterAFailedPost() = runTest {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        try {
            HydraFitDatabase.Schema.create(driver)
            val repository = SqlDelightReminderDeliveryRepository(HydraFitDatabase(driver))

            assertFalse(repository.isDelivered(activationId = 1L, reminderEpochDay = 100L))
            assertTrue(repository.claim(1L, 100L, deliveredAtMillis = 5_000L))
            assertTrue(repository.isDelivered(1L, 100L))

            // A second concurrent delivery must lose the claim instead of posting twice.
            assertFalse(repository.claim(1L, 100L, deliveredAtMillis = 6_000L))

            // Another block and another day are still separate.
            assertFalse(repository.isDelivered(activationId = 2L, reminderEpochDay = 100L))
            assertTrue(repository.claim(2L, 100L, deliveredAtMillis = 5_000L))
            assertFalse(repository.isDelivered(1L, 101L))

            // A failed post gives the day back so a later pass inside the window can retry.
            repository.release(1L, 100L)
            assertFalse(repository.isDelivered(1L, 100L))
            assertTrue(repository.claim(1L, 100L, deliveredAtMillis = 7_000L))
        } finally {
            driver.close()
        }
    }

    @Test
    fun prunesDaysThatCanNoLongerBeConsulted() = runTest {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        try {
            HydraFitDatabase.Schema.create(driver)
            val repository = SqlDelightReminderDeliveryRepository(HydraFitDatabase(driver))

            assertTrue(repository.claim(1L, 100L, deliveredAtMillis = 1L))
            assertTrue(repository.claim(1L, 101L, deliveredAtMillis = 2L))
            assertTrue(repository.isDelivered(1L, 100L))

            // Claiming a much later day drops only the days outside the retained history.
            assertTrue(repository.claim(1L, 200L, deliveredAtMillis = 3L))
            assertFalse(repository.isDelivered(1L, 100L))
            assertFalse(repository.isDelivered(1L, 101L))
            assertTrue(repository.isDelivered(1L, 200L))
        } finally {
            driver.close()
        }
    }
}
