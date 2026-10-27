package com.hydrafit.app.core.database

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.hydrafit.app.core.domain.cardio.CardioActivity
import com.hydrafit.app.core.domain.cardio.CardioActivityType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class SqlDelightCardioRepositoryTest {
    @Test
    fun crudPreservesIdentityAndDoesNotWriteResistanceSets() = runTest {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        try {
            HydraFitDatabase.Schema.create(driver)
            val database = HydraFitDatabase(driver)
            val repository = SqlDelightCardioRepository(database)
            val original = CardioActivity(
                identity = "cardio-test",
                type = CardioActivityType.WALKING,
                startedAtMillis = 1000L,
                durationSeconds = 1200,
                distanceMeters = 2000,
                note = "Walk"
            )
            val id = repository.add(original)
            assertEquals(original.copy(id = id), repository.byIdentity(original.identity))
            assertFailsWith<IllegalArgumentException> { repository.add(original) }
            val corrected = original.copy(
                id = id,
                durationSeconds = 900,
                distanceMeters = null,
                note = null
            )
            repository.update(corrected)
            assertEquals(corrected, repository.all().single())
            assertTrue(database.workoutLogQueries.selectAllSets().executeAsList().isEmpty())
            repository.delete(id)
            assertTrue(repository.all().isEmpty())
            assertNull(repository.byIdentity(original.identity))
        } finally {
            driver.close()
        }
    }
}
