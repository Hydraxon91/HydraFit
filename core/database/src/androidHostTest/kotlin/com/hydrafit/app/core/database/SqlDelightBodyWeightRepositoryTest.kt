package com.hydrafit.app.core.database

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.hydrafit.app.core.domain.time.TimeProvider
import com.hydrafit.app.core.userdata.measurements.BodyWeightException
import com.hydrafit.app.core.userdata.measurements.BodyWeightFailureReason
import com.hydrafit.app.core.userdata.measurements.BodyWeightMeasurement
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

class SqlDelightBodyWeightRepositoryTest {
    private lateinit var driver: JdbcSqliteDriver
    private lateinit var database: HydraFitDatabase
    private lateinit var repository: SqlDelightBodyWeightRepository
    private val day = 86_400_000L
    private val today = 20_000L

    @BeforeTest
    fun setUp() {
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        HydraFitDatabase.Schema.create(driver)
        database = HydraFitDatabase(driver)
        repository = SqlDelightBodyWeightRepository(database, TimeProvider { today * day })
    }

    @AfterTest
    fun tearDown() {
        driver.close()
    }

    @Test
    fun addValidatesWeightAndFutureDaysThenRequiresExplicitReplacement() = runBlocking {
        assertReason(BodyWeightFailureReason.INVALID_WEIGHT) { repository.add(today, 0.0) }
        assertReason(BodyWeightFailureReason.INVALID_WEIGHT) { repository.add(today, Double.NaN) }
        assertReason(BodyWeightFailureReason.FUTURE_DATE) { repository.add(today + 1, 80.0) }
        assertEquals(emptyList(), repository.measurements())

        repository.add(today, 82.1234567890123)
        assertEquals(
            82.1234567890123,
            repository.measurements().single().weightKg
        )
        assertReason(BodyWeightFailureReason.DUPLICATE_DATE) { repository.add(today, 90.0) }
        assertEquals(82.1234567890123, repository.measurements().single().weightKg)

        repository.add(today, 90.0, replace = true)
        assertEquals(90.0, repository.measurements().single().weightKg)
    }

    @Test
    fun updateEditsInPlaceMovesToFreeDaysAndNeverOverwrites() = runBlocking {
        repository.add(today - 2, 80.0)
        repository.add(today - 1, 81.0)

        assertReason(BodyWeightFailureReason.DATE_OCCUPIED) {
            repository.update(today - 2, today - 1, 82.0)
        }
        assertEquals(
            listOf(BodyWeightMeasurement(today - 2, 80.0), BodyWeightMeasurement(today - 1, 81.0)),
            repository.measurements()
        )

        repository.update(today - 2, today - 2, 80.25)
        assertEquals(80.25, repository.measurements().first().weightKg)

        repository.update(today - 2, today, 80.25)
        assertEquals(
            listOf(BodyWeightMeasurement(today - 1, 81.0), BodyWeightMeasurement(today, 80.25)),
            repository.measurements()
        )

        assertReason(BodyWeightFailureReason.MISSING_MEASUREMENT) {
            repository.update(today - 9, today - 9, 80.0)
        }
        repository.delete(today - 1)
        assertEquals(listOf(BodyWeightMeasurement(today, 80.25)), repository.measurements())
    }

    @Test
    fun goalIsOptionalReplaceableAndClearable() = runBlocking {
        assertNull(repository.observeGoal().first())
        repository.setGoal(78.5)
        assertEquals(78.5, repository.observeGoal().first())
        repository.setGoal(77.0)
        assertEquals(77.0, repository.observeGoal().first())
        repository.setGoal(null)
        assertNull(repository.observeGoal().first())
        assertReason(BodyWeightFailureReason.INVALID_WEIGHT) { repository.setGoal(-1.0) }
    }

    @Test
    fun writesInvalidateTheMeasurementAndGoalFlowsWithoutCountDeduplication() = runBlocking {
        val emissions = Channel<List<BodyWeightMeasurement>>(Channel.UNLIMITED)
        val job = launch(Dispatchers.Default) {
            repository.observeMeasurements().collect { emissions.send(it) }
        }
        suspend fun next() = withTimeout(5_000) { emissions.receive() }
        try {
            assertEquals(emptyList(), next())
            repository.add(today, 82.0)
            assertEquals(listOf(BodyWeightMeasurement(today, 82.0)), next())
            repository.update(today, today, 83.0)
            assertEquals(listOf(BodyWeightMeasurement(today, 83.0)), next())
            repository.delete(today)
            assertEquals(emptyList(), next())
        } finally {
            job.cancel()
            job.join()
            emissions.close()
        }
    }

    private suspend fun assertReason(reason: BodyWeightFailureReason, block: suspend () -> Unit) {
        val failure = assertFailsWith<BodyWeightException> { block() }
        assertEquals(reason, failure.reason)
    }
}
