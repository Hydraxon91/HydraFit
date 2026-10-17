package com.hydrafit.app.core.database

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.hydrafit.app.core.domain.workout.WarmupAction
import com.hydrafit.app.core.domain.workout.WarmupPreparationItem
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest

/**
 * The persisted warm-up preparation overrides: one row per action, holding a positive rep count or
 * positive whole seconds, never both and never a zero sentinel.
 */
class SqlDelightWarmupPreparationOverrideRepositoryTest {
    private lateinit var driver: JdbcSqliteDriver
    private lateinit var database: HydraFitDatabase
    private lateinit var repository: SqlDelightWarmupPreparationOverrideRepository

    @BeforeTest
    fun setUp() {
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        HydraFitDatabase.Schema.create(driver)
        database = HydraFitDatabase(driver)
        repository = SqlDelightWarmupPreparationOverrideRepository(database)
    }

    @AfterTest
    fun tearDown() = driver.close()

    @Test
    fun anEmptyStoreHasNoOverrides() = runTest {
        assertEquals(emptyMap(), repository.observeAll().first())
    }

    @Test
    fun countedAndTimedOverridesRoundTripPerAction() = runTest {
        repository.set(WarmupPreparationItem(WarmupAction.LOWER_BODY_PRACTICE, reps = 15))
        repository.set(
            WarmupPreparationItem(WarmupAction.EASY_GENERAL_MOVEMENT, durationSeconds = 300)
        )

        val overrides = repository.observeAll().first()
        assertEquals(
            WarmupPreparationItem(WarmupAction.LOWER_BODY_PRACTICE, reps = 15),
            overrides[WarmupAction.LOWER_BODY_PRACTICE]
        )
        assertEquals(
            WarmupPreparationItem(WarmupAction.EASY_GENERAL_MOVEMENT, durationSeconds = 300),
            overrides[WarmupAction.EASY_GENERAL_MOVEMENT]
        )

        // The stored row keeps the exclusive measure: the unused column is null, not a sentinel.
        val counted = database.warmupPreparationOverrideQueries.selectAll().executeAsList()
            .single { it.action == WarmupAction.LOWER_BODY_PRACTICE.name }
        assertEquals(15L, counted.reps)
        assertNull(counted.durationSeconds)
        val timed = database.warmupPreparationOverrideQueries.selectAll().executeAsList()
            .single { it.action == WarmupAction.EASY_GENERAL_MOVEMENT.name }
        assertNull(timed.reps)
        assertEquals(300L, timed.durationSeconds)
    }

    @Test
    fun setReplacesAnExistingOverrideForTheSameAction() = runTest {
        repository.set(WarmupPreparationItem(WarmupAction.LOWER_BODY_PRACTICE, reps = 12))
        repository.set(WarmupPreparationItem(WarmupAction.LOWER_BODY_PRACTICE, reps = 18))

        assertEquals(
            WarmupPreparationItem(WarmupAction.LOWER_BODY_PRACTICE, reps = 18),
            repository.observeAll().first()[WarmupAction.LOWER_BODY_PRACTICE]
        )
        assertEquals(1L, HistoricalDatabaseFixtures.count(driver, "warmupPreparationOverride"))
    }

    @Test
    fun clearRemovesOnlyTheNamedActionAndAbsenceMeansDefault() = runTest {
        repository.set(WarmupPreparationItem(WarmupAction.LOWER_BODY_PRACTICE, reps = 15))
        repository.set(
            WarmupPreparationItem(WarmupAction.EASY_GENERAL_MOVEMENT, durationSeconds = 300)
        )

        repository.clear(WarmupAction.LOWER_BODY_PRACTICE)

        val overrides = repository.observeAll().first()
        assertNull(overrides[WarmupAction.LOWER_BODY_PRACTICE])
        assertEquals(
            WarmupPreparationItem(WarmupAction.EASY_GENERAL_MOVEMENT, durationSeconds = 300),
            overrides[WarmupAction.EASY_GENERAL_MOVEMENT]
        )

        // Clearing an action that holds no override is a no-op, not an error.
        repository.clear(WarmupAction.SHOULDER_CIRCLES)
        assertEquals(1L, HistoricalDatabaseFixtures.count(driver, "warmupPreparationOverride"))
    }

    @Test
    fun setRejectsANonPositiveMeasure() = runTest {
        assertFailsWith<IllegalArgumentException> {
            repository.set(WarmupPreparationItem(WarmupAction.LOWER_BODY_PRACTICE, reps = 0))
        }
        assertFailsWith<IllegalArgumentException> {
            repository.set(
                WarmupPreparationItem(WarmupAction.EASY_GENERAL_MOVEMENT, durationSeconds = -60)
            )
        }
        assertEquals(0L, HistoricalDatabaseFixtures.count(driver, "warmupPreparationOverride"))
    }
}
