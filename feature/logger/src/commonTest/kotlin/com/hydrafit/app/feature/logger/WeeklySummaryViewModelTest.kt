package com.hydrafit.app.feature.logger

import com.hydrafit.app.core.domain.engine.AcceptedPlan
import com.hydrafit.app.core.domain.engine.PeriodizationConfig
import com.hydrafit.app.core.domain.engine.PlanHistoryRepository
import com.hydrafit.app.core.domain.equipment.ExerciseLoadCapability
import com.hydrafit.app.core.domain.time.TimeProvider
import com.hydrafit.app.core.domain.workout.LoadKind
import com.hydrafit.app.core.domain.workout.ObserveWeeklyTrainingSummaryUseCase
import com.hydrafit.app.core.domain.workout.WorkoutSession
import com.hydrafit.app.core.domain.workout.WorkoutSessionRepository
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
class WeeklySummaryViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private val open = MutableStateFlow<WorkoutSession?>(null)
    private val sessions = mutableListOf<WorkoutSession>()
    private var failReads = false

    private val useCase = ObserveWeeklyTrainingSummaryUseCase(
        sessions = object : WorkoutSessionRepository {
            override suspend fun all(): List<WorkoutSession> {
                check(!failReads) { "read failed" }
                return sessions.toList()
            }

            override suspend fun create(session: WorkoutSession) = error("unused")

            override suspend fun end(id: String, endedAtMillis: Long) = error("unused")

            override suspend fun open(): WorkoutSession? = open.value

            override fun openFlow(): Flow<WorkoutSession?> = open
        },
        planHistory = object : PlanHistoryRepository {
            override fun observeLatest(): Flow<AcceptedPlan?> = flowOf(null)

            override fun observeHistory() = error("unused")

            override suspend fun latest(): AcceptedPlan? = null

            override suspend fun accept(plan: AcceptedPlan) = error("unused")

            override suspend fun substitute(
                planId: Long,
                dayIndex: Int,
                position: Int,
                newExerciseId: String,
                newExerciseName: String,
                newWeightKg: Double?,
                newLoadCapability: ExerciseLoadCapability,
                newLoadKind: LoadKind
            ) = error("unused")

            override suspend fun setMeasure(
                planId: Long,
                dayIndex: Int,
                position: Int,
                reps: Int?,
                durationSeconds: Int?,
                distanceMeters: Int?,
                forceNewtons: Double?
            ) = error("unused")

            override suspend fun delete(planId: Long) = error("unused")

            override suspend fun clear() = error("unused")
        },
        timeProvider = object : TimeProvider {
            override fun nowMillis() = 20_775L * 86_400_000L
            override fun utcOffsetMillis() = 0L
        },
        periodization = PeriodizationConfig()
    )

    @Test
    fun publishesTheSummaryAndRefreshesWhenASessionOpensOrCloses() = runTest(dispatcher) {
        val viewModel = WeeklySummaryViewModel(useCase)
        val collector = viewModel.summary.onEach { }.launchIn(backgroundScope)
        advanceUntilIdle()
        assertEquals(listOf(0, 0, 0, 0), viewModel.summary.value?.sessionsPerWeek)

        sessions += WorkoutSession("a", 20_774L * 86_400_000L, localEpochDay = 20_774L)
        open.value = sessions.last()
        advanceUntilIdle()

        assertEquals(listOf(0, 0, 0, 1), viewModel.summary.value?.sessionsPerWeek)
        collector.cancel()
    }

    @Test
    fun aFailedReadLeavesNoSummaryInsteadOfCrashing() = runTest(dispatcher) {
        failReads = true
        val viewModel = WeeklySummaryViewModel(useCase)
        val collector = viewModel.summary.onEach { }.launchIn(backgroundScope)
        advanceUntilIdle()

        assertNull(viewModel.summary.value)
        collector.cancel()
    }
}
