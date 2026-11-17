package com.hydrafit.app.core.domain.workout

import com.hydrafit.app.core.domain.engine.AcceptedDay
import com.hydrafit.app.core.domain.engine.AcceptedPlan
import com.hydrafit.app.core.domain.engine.PeriodizationConfig
import com.hydrafit.app.core.domain.engine.PlanHistoryRepository
import com.hydrafit.app.core.domain.engine.PlannerEngineId
import com.hydrafit.app.core.domain.engine.SplitFocus
import com.hydrafit.app.core.domain.equipment.ExerciseLoadCapability
import com.hydrafit.app.core.domain.time.TimeProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest

class WeeklyTrainingSummaryTest {

    // 2026-11-16 is a Monday: epoch day 20,773.
    private val monday = 20_773L

    private fun session(id: String, day: Long) =
        WorkoutSession(id = id, startedAtMillis = day * DAY, localEpochDay = day)

    @Test
    fun weeksStartOnMondayAndTheEpochAnchorIsCorrect() {
        assertEquals(monday, WeeklyTrainingCounts.mondayOf(monday))
        assertEquals(monday, WeeklyTrainingCounts.mondayOf(monday + 6))
        assertEquals(monday + 7, WeeklyTrainingCounts.mondayOf(monday + 7))
        assertEquals(monday - 7, WeeklyTrainingCounts.mondayOf(monday - 1))
        // 1970-01-05 was the first Monday after the epoch; the epoch itself was a Thursday.
        assertEquals(4L, WeeklyTrainingCounts.mondayOf(4L))
        assertEquals(-3L, WeeklyTrainingCounts.mondayOf(0L))
        assertEquals(-3L, WeeklyTrainingCounts.mondayOf(-1L))
    }

    @Test
    fun countsSessionsInTheLastFourWeeksOldestFirstWithTheCurrentWeekLast() {
        val today = monday + 2 // Wednesday
        val sessions = listOf(
            session("old", monday - 22), // before the first of four weeks
            session("w1a", monday - 21), // Monday three weeks ago: first day of the window
            session("w1b", monday - 15), // the Sunday that closes that same week
            session("w2", monday - 8), // the Sunday that closes the second week
            session("w4a", monday),
            session("w4b", monday + 2),
            session("future", monday + 3)
        )

        assertEquals(listOf(2, 1, 0, 2), WeeklyTrainingCounts.sessionsPerWeek(sessions, today))
    }

    @Test
    fun aSundayBelongsToTheWeekThatStartedOnTheMondayBefore() {
        val sunday = monday + 6
        val counts = WeeklyTrainingCounts.sessionsPerWeek(
            listOf(session("sun", sunday), session("mon", sunday + 1)),
            todayEpochDay = sunday + 1
        )

        assertEquals(listOf(0, 0, 1, 1), counts)
    }

    @Test
    fun anEmptyHistoryIsAllZeroAndADuplicateSessionCountsOnce() {
        assertEquals(listOf(0, 0, 0, 0), WeeklyTrainingCounts.sessionsPerWeek(emptyList(), monday))
        assertEquals(
            listOf(0, 0, 0, 1),
            WeeklyTrainingCounts.sessionsPerWeek(
                listOf(session("a", monday), session("a", monday)),
                monday
            )
        )
    }

    @Test
    fun sessionsThisWeekReadsTheLastEntry() {
        assertEquals(3, WeeklyTrainingSummary(listOf(0, 1, 2, 3), 4).sessionsThisWeek)
        assertEquals(0, WeeklyTrainingSummary(emptyList(), null).sessionsThisWeek)
    }

    private companion object {
        const val DAY = 86_400_000L
    }
}

class ObserveWeeklyTrainingSummaryUseCaseTest {

    // 2026-11-18, a Wednesday, noon UTC.
    private val nowMillis = (20_775L * 86_400_000L) + 12 * 3_600_000L
    private val monday = 20_773L

    private fun session(id: String, day: Long) = WorkoutSession(
        id = id,
        startedAtMillis = day * 86_400_000L,
        localEpochDay = day
    )

    private fun plan(weekNumber: Int, days: Int) = AcceptedPlan(
        engine = PlannerEngineId.DETERMINISTIC,
        acceptedAtMillis = 0L,
        days = List(days) { AcceptedDay(it, SplitFocus.FULL_BODY, emptyList()) },
        weekNumber = weekNumber
    )

    private fun useCase(sessions: List<WorkoutSession>, latest: AcceptedPlan?) =
        ObserveWeeklyTrainingSummaryUseCase(
            sessions = FakeSessions(sessions),
            planHistory = FakePlanHistory(latest),
            timeProvider = object : TimeProvider {
                override fun nowMillis() = this@ObserveWeeklyTrainingSummaryUseCaseTest.nowMillis
                override fun utcOffsetMillis() = 0L
            },
            periodization = PeriodizationConfig()
        )

    @Test
    fun reportsWeeklyCountsThePlannedDaysAndNoDeloadForAnOrdinaryWeek() = runTest {
        val summary = useCase(
            listOf(session("a", monday - 7), session("b", monday), session("c", monday + 1)),
            latest = plan(weekNumber = 2, days = 4)
        )().first().let(::requireNotNull)

        assertEquals(listOf(0, 0, 1, 2), summary.sessionsPerWeek)
        assertEquals(2, summary.sessionsThisWeek)
        assertEquals(4, summary.plannedDaysPerWeek)
        assertFalse(summary.isDeloadWeek)
    }

    @Test
    fun marksTheDeloadWeekAndLeavesPlannedDaysNullWithoutAPlan() = runTest {
        val deload = useCase(
            emptyList(),
            plan(weekNumber = PeriodizationConfig().deloadWeek, days = 3)
        )().first().let(::requireNotNull)
        val noPlan = useCase(emptyList(), latest = null)().first().let(::requireNotNull)

        assertTrue(deload.isDeloadWeek)
        assertEquals(listOf(0, 0, 0, 0), deload.sessionsPerWeek)
        assertNull(noPlan.plannedDaysPerWeek)
        assertFalse(noPlan.isDeloadWeek)
    }

    @Test
    fun aNegativeOffsetPutsTheTodayCutoffOnThePreviousLocalDay() = runTest {
        // 03:00 UTC on Monday is still Sunday evening at UTC-5, so this week is the previous one.
        val mondayEarly = monday * 86_400_000L + 3 * 3_600_000L
        val summary = ObserveWeeklyTrainingSummaryUseCase(
            sessions = FakeSessions(listOf(session("sun", monday - 1), session("mon", monday))),
            planHistory = FakePlanHistory(null),
            timeProvider = object : TimeProvider {
                override fun nowMillis() = mondayEarly
                override fun utcOffsetMillis() = -5 * 3_600_000L
            },
            periodization = PeriodizationConfig()
        )().first()

        // Local today is Sunday (monday - 1): the Sunday session counts and Monday is the future.
        assertEquals(listOf(0, 0, 0, 1), summary?.sessionsPerWeek)
    }

    @Test
    fun aFailedReadEmitsNullOnceAndTheNextChangeRecovers() = runTest {
        val flaky = FlakySessions(listOf(session("a", monday)))
        val emissions = ObserveWeeklyTrainingSummaryUseCase(
            sessions = flaky,
            planHistory = FakePlanHistory(null),
            timeProvider = object : TimeProvider {
                override fun nowMillis() = nowMillis
                override fun utcOffsetMillis() = 0L
            },
            periodization = PeriodizationConfig()
        )().take(2).toList()

        assertNull(emissions[0])
        assertEquals(listOf(0, 0, 0, 1), emissions[1]?.sessionsPerWeek)
    }

    private class FlakySessions(private val sessions: List<WorkoutSession>) :
        WorkoutSessionRepository {
        private var reads = 0
        private val changes = MutableSharedFlow<WorkoutSession?>(replay = 2)

        init {
            changes.tryEmit(null)
            changes.tryEmit(null)
        }

        override suspend fun all(): List<WorkoutSession> {
            check(reads++ > 0) { "first read fails" }
            return sessions
        }

        override suspend fun create(session: WorkoutSession) = error("unused")

        override suspend fun end(id: String, endedAtMillis: Long) = error("unused")

        override suspend fun open(): WorkoutSession? = null

        override fun openFlow(): Flow<WorkoutSession?> = changes
    }

    private class FakeSessions(private val sessions: List<WorkoutSession>) :
        WorkoutSessionRepository {
        override suspend fun all(): List<WorkoutSession> = sessions

        override suspend fun create(session: WorkoutSession) = error("unused")

        override suspend fun end(id: String, endedAtMillis: Long) = error("unused")

        override suspend fun open(): WorkoutSession? = null

        override fun openFlow(): Flow<WorkoutSession?> = flowOf(null)
    }

    private class FakePlanHistory(private val latest: AcceptedPlan?) : PlanHistoryRepository {
        override fun observeLatest(): Flow<AcceptedPlan?> = flowOf(latest)

        override fun observeHistory(): Flow<List<AcceptedPlan>> = flowOf(listOfNotNull(latest))

        override suspend fun latest(): AcceptedPlan? = latest

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
    }
}
