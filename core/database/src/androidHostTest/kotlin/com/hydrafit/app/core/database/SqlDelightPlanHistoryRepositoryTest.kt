package com.hydrafit.app.core.database

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.hydrafit.app.core.domain.engine.AcceptedDay
import com.hydrafit.app.core.domain.engine.AcceptedExercise
import com.hydrafit.app.core.domain.engine.AcceptedPlan
import com.hydrafit.app.core.domain.engine.PlannerEngineId
import com.hydrafit.app.core.domain.engine.SplitFocus
import com.hydrafit.app.core.domain.equipment.MovementPattern
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest

class SqlDelightPlanHistoryRepositoryTest {

    private lateinit var driver: SqlDriver
    private lateinit var database: HydraFitDatabase
    private lateinit var repository: SqlDelightPlanHistoryRepository

    @BeforeTest
    fun setUp() {
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        HydraFitDatabase.Schema.create(driver)
        database = HydraFitDatabase(driver)
        repository = SqlDelightPlanHistoryRepository(database)
    }

    @AfterTest
    fun tearDown() {
        driver.close()
    }

    @Test
    fun isNullBeforeAnythingIsAccepted() = runTest {
        assertNull(repository.latest())
        assertNull(repository.observeLatest().first())
    }

    @Test
    fun persistsAnAcceptedPlanAndReadsItBack() = runTest {
        repository.accept(
            plan(
                engine = PlannerEngineId.GEMINI_API,
                acceptedAt = 100L,
                weekNumber = 3,
                cycleNumber = 2
            )
        )

        val latest = requireNotNull(repository.latest())

        assertEquals(PlannerEngineId.GEMINI_API, latest.engine)
        assertEquals(100L, latest.acceptedAtMillis)
        assertEquals(3, latest.weekNumber)
        assertEquals(2, latest.cycleNumber)
        assertEquals(2, latest.days.size)
        val day = latest.days.first()
        assertEquals(SplitFocus.PUSH, day.focus)
        assertEquals(listOf("bench-press", "overhead-press"), day.exercises.map { it.exerciseId })
        assertEquals("Barbell Bench Press", day.exercises.first().name)
        assertEquals(MovementPattern.HORIZONTAL_PUSH, day.exercises.first().movementPattern)
        assertEquals(82.5, day.exercises.first().suggestedWeightKg)
        assertEquals(SplitFocus.LEGS, latest.days.last().focus)
        assertEquals(emptyList(), latest.days.last().exercises)
    }

    @Test
    fun latestIsTheMostRecentlyAcceptedPlan() = runTest {
        repository.accept(plan(engine = PlannerEngineId.DETERMINISTIC, acceptedAt = 1L))
        repository.accept(plan(engine = PlannerEngineId.LOCAL_LLM, acceptedAt = 2L))

        assertEquals(PlannerEngineId.LOCAL_LLM, requireNotNull(repository.latest()).engine)
    }

    @Test
    fun observeLatestEmitsTheAcceptedPlan() = runTest {
        repository.accept(plan(engine = PlannerEngineId.DETERMINISTIC, acceptedAt = 5L))

        val observed = requireNotNull(repository.observeLatest().first())

        assertEquals(PlannerEngineId.DETERMINISTIC, observed.engine)
        assertEquals(5L, observed.acceptedAtMillis)
    }

    @Test
    fun observeHistoryEmitsPlansNewestFirst() = runTest {
        repository.accept(plan(engine = PlannerEngineId.DETERMINISTIC, acceptedAt = 1L))
        repository.accept(plan(engine = PlannerEngineId.LOCAL_LLM, acceptedAt = 2L))

        val history = repository.observeHistory().first()

        assertEquals(
            listOf(PlannerEngineId.LOCAL_LLM, PlannerEngineId.DETERMINISTIC),
            history.map { it.engine }
        )
    }

    @Test
    fun clearRemovesTheHistory() = runTest {
        repository.accept(plan(engine = PlannerEngineId.DETERMINISTIC, acceptedAt = 1L))

        repository.clear()

        assertNull(repository.latest())
    }

    private fun plan(
        engine: PlannerEngineId,
        acceptedAt: Long,
        weekNumber: Int = 1,
        cycleNumber: Int = 1
    ) = AcceptedPlan(
        engine = engine,
        acceptedAtMillis = acceptedAt,
        weekNumber = weekNumber,
        cycleNumber = cycleNumber,
        days = listOf(
            AcceptedDay(
                dayIndex = 0,
                focus = SplitFocus.PUSH,
                exercises = listOf(
                    exercise(
                        id = "bench-press",
                        name = "Barbell Bench Press",
                        pattern = MovementPattern.HORIZONTAL_PUSH,
                        suggestedWeightKg = 82.5
                    ),
                    exercise(
                        id = "overhead-press",
                        name = "Overhead Press",
                        pattern = MovementPattern.VERTICAL_PUSH,
                        suggestedWeightKg = null
                    )
                )
            ),
            AcceptedDay(
                dayIndex = 1,
                focus = SplitFocus.LEGS,
                exercises = emptyList()
            )
        )
    )

    private fun exercise(
        id: String,
        name: String,
        pattern: MovementPattern,
        suggestedWeightKg: Double?
    ) = AcceptedExercise(
        exerciseId = id,
        sets = 3,
        reps = 8,
        name = name,
        movementPattern = pattern,
        suggestedWeightKg = suggestedWeightKg
    )
}
