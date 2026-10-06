package com.hydrafit.app.core.domain.workout

import com.hydrafit.app.core.domain.fatigue.LoggedSet
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.time.Duration.Companion.hours
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest

class LogWorkoutSetUseCaseTest {

    @Test
    fun addsTheSetToTheRepository() = runTest {
        val logs = FakeWorkoutLogRepository()
        val sessions = FakeWorkoutSessionRepository()

        useCase(logs, sessions)(set(performedAtMillis = 42L), utcOffsetMillis = 0L)

        assertEquals(1, logs.all().size)
    }

    @Test
    fun autoStartsASessionForTheFirstSetAndStampsIt() = runTest {
        val logs = FakeWorkoutLogRepository()
        val sessions = FakeWorkoutSessionRepository()

        useCase(logs, sessions)(set(performedAtMillis = 1_000L), utcOffsetMillis = 0L)

        val open = sessions.open()
        assertNotNull(open)
        assertEquals(1_000L, open.startedAtMillis)
        assertEquals(open.id, logs.all().single().sessionId)
    }

    @Test
    fun reusesTheOpenSessionWithinTheSameDayAndWindow() = runTest {
        val logs = FakeWorkoutLogRepository()
        val sessions = FakeWorkoutSessionRepository()
        val useCase = useCase(logs, sessions)

        useCase(set(performedAtMillis = 1_000L), utcOffsetMillis = 0L)
        val first = sessions.open()
        useCase(set(performedAtMillis = 1_000L + 1.hours.inWholeMilliseconds), utcOffsetMillis = 0L)

        assertEquals(first, sessions.open())
        assertEquals(1, sessions.all().size)
        assertEquals(setOf(first!!.id), logs.all().map { it.sessionId }.toSet())
    }

    @Test
    fun exactlyAtTheInactivityWindowDoesNotCloseTheSession() = runTest {
        val logs = FakeWorkoutLogRepository()
        val sessions = FakeWorkoutSessionRepository()
        val useCase = useCase(logs, sessions)

        useCase(set(performedAtMillis = 10_000L), utcOffsetMillis = 0L)
        val first = sessions.open()
        useCase(
            set(performedAtMillis = 10_000L + window.inWholeMilliseconds),
            utcOffsetMillis = 0L
        )

        assertEquals(first, sessions.open())
        assertEquals(1, sessions.all().size)
    }

    @Test
    fun oneMillisecondOverTheInactivityWindowClosesAndStartsNew() = runTest {
        val logs = FakeWorkoutLogRepository()
        val sessions = FakeWorkoutSessionRepository()
        val useCase = useCase(logs, sessions)

        useCase(set(performedAtMillis = 10_000L), utcOffsetMillis = 0L)
        val first = sessions.open()!!
        val secondAt = 10_000L + window.inWholeMilliseconds + 1
        useCase(set(performedAtMillis = secondAt), utcOffsetMillis = 0L)

        val second = sessions.open()
        assertNotNull(second)
        assertNotEquals(first.id, second.id)
        // The prior session is closed at its last set, not at the new set's time.
        assertEquals(10_000L, sessions.all().first { it.id == first.id }.endedAtMillis)
        // The new set carries the new session.
        assertEquals(second.id, logs.all().maxByOrNull { it.performedAtMillis }!!.sessionId)
    }

    @Test
    fun aNewLocalDayClosesThePriorSessionAndStartsNew() = runTest {
        val logs = FakeWorkoutLogRepository()
        val sessions = FakeWorkoutSessionRepository()
        val useCase = useCase(logs, sessions)

        useCase(set(performedAtMillis = DAY - 1.hours.inWholeMilliseconds), utcOffsetMillis = 0L)
        val first = sessions.open()!!
        useCase(set(performedAtMillis = DAY + 1.hours.inWholeMilliseconds), utcOffsetMillis = 0L)

        val second = sessions.open()
        assertNotNull(second)
        assertNotEquals(first.id, second.id)
        assertEquals(1L, second.localEpochDay)
    }

    @Test
    fun localDayUsesTheUtcOffsetNotTheUtcDay() = runTest {
        val logs = FakeWorkoutLogRepository()
        val sessions = FakeWorkoutSessionRepository()
        val useCase = useCase(logs, sessions)
        val offset = 2.hours.inWholeMilliseconds

        // 23:30 UTC day 0 is 01:30 local day 1; 00:30 UTC day 1 is 02:30 local day 1.
        useCase(set(performedAtMillis = DAY - 30L * 60_000L), utcOffsetMillis = offset)
        val first = sessions.open()
        useCase(set(performedAtMillis = DAY + 30L * 60_000L), utcOffsetMillis = offset)

        // The UTC day changed but the local day did not, so the session is reused.
        assertEquals(first, sessions.open())
        assertEquals(1, sessions.all().size)
    }

    @Test
    fun aBatchOfSetsAtTheSameInstantSharesOneSession() = runTest {
        val logs = FakeWorkoutLogRepository()
        val sessions = FakeWorkoutSessionRepository()
        val useCase = useCase(logs, sessions)

        repeat(3) { useCase(set(performedAtMillis = 5_000L), utcOffsetMillis = 0L) }

        assertEquals(1, sessions.all().size)
        assertEquals(1, logs.all().map { it.sessionId }.toSet().size)
    }

    @Test
    fun endSessionThenTheNextSetStartsANewSession() = runTest {
        val logs = FakeWorkoutLogRepository()
        val sessions = FakeWorkoutSessionRepository()
        val useCase = useCase(logs, sessions)

        useCase(set(performedAtMillis = 1_000L), utcOffsetMillis = 0L)
        val first = sessions.open()!!
        useCase.endSession(endedAtMillis = 2_000L)

        assertNull(sessions.open())

        useCase(set(performedAtMillis = 3_000L), utcOffsetMillis = 0L)

        val second = sessions.open()
        assertNotNull(second)
        assertNotEquals(first.id, second.id)
        assertEquals(2_000L, sessions.all().first { it.id == first.id }.endedAtMillis)
    }

    @Test
    fun startNewSessionWithNoOpenSessionOpensOne() = runTest {
        val logs = FakeWorkoutLogRepository()
        val sessions = FakeWorkoutSessionRepository()
        val useCase = useCase(logs, sessions)

        val created = useCase.startNewSession(startedAtMillis = 1_000L, utcOffsetMillis = 0L)

        assertEquals(created, sessions.open())
        assertEquals(1, sessions.all().size)
    }

    @Test
    fun startNewSessionClosesTheCurrentAndOpensAFresh() = runTest {
        val logs = FakeWorkoutLogRepository()
        val sessions = FakeWorkoutSessionRepository()
        val useCase = useCase(logs, sessions)

        useCase(set(performedAtMillis = 1_000L), utcOffsetMillis = 0L)
        val first = sessions.open()!!

        val created = useCase.startNewSession(startedAtMillis = 2_000L, utcOffsetMillis = 0L)

        assertNotEquals(first.id, created.id)
        assertEquals(created, sessions.open())
        assertEquals(2_000L, sessions.all().first { it.id == first.id }.endedAtMillis)
    }

    @Test
    fun expireOpenSessionClosesAnIdleSession() = runTest {
        val logs = FakeWorkoutLogRepository()
        val sessions = FakeWorkoutSessionRepository()
        val useCase = useCase(logs, sessions)

        useCase(set(performedAtMillis = 1_000L), utcOffsetMillis = 0L)
        val first = sessions.open()!!

        useCase.expireOpenSession(
            nowMillis = 1_000L + window.inWholeMilliseconds + 1,
            utcOffsetMillis = 0L
        )

        assertNull(sessions.open())
        assertEquals(1_000L, sessions.all().single().endedAtMillis)
        assertEquals(first.id, sessions.all().single().id)
    }

    @Test
    fun expireOpenSessionKeepsASessionWithinTheWindow() = runTest {
        val logs = FakeWorkoutLogRepository()
        val sessions = FakeWorkoutSessionRepository()
        val useCase = useCase(logs, sessions)

        useCase(set(performedAtMillis = 1_000L), utcOffsetMillis = 0L)
        val first = sessions.open()

        useCase.expireOpenSession(
            nowMillis = 1_000L + 1.hours.inWholeMilliseconds,
            utcOffsetMillis = 0L
        )

        assertSame(first, sessions.open())
    }

    @Test
    fun backdatedOnTheSameLocalDayAttachesToTheOpenSession() = runTest {
        val logs = FakeWorkoutLogRepository()
        val sessions = FakeWorkoutSessionRepository()
        val useCase = useCase(logs, sessions)

        useCase(set(performedAtMillis = 10_000L), utcOffsetMillis = 0L)
        val open = sessions.open()!!

        val target = useCase.logBackdated(
            set(performedAtMillis = 10_000L + 1.hours.inWholeMilliseconds),
            utcOffsetMillis = 0L,
            forceNewSession = false
        )

        assertEquals(open.id, target.id)
        assertEquals(open, sessions.open())
        assertEquals(1, sessions.all().size)
        assertEquals(open.id, logs.all().maxByOrNull { it.performedAtMillis }!!.sessionId)
    }

    @Test
    fun backdatedOnADifferentLocalDayCreatesAClosedSessionAnchoredAtTheChosenTime() = runTest {
        val logs = FakeWorkoutLogRepository()
        val sessions = FakeWorkoutSessionRepository()
        val useCase = useCase(logs, sessions)

        useCase(set(performedAtMillis = DAY + 1_000L), utcOffsetMillis = 0L)
        val open = sessions.open()!!

        val chosen = DAY - 1.hours.inWholeMilliseconds
        val target = useCase.logBackdated(
            set(performedAtMillis = chosen),
            utcOffsetMillis = 0L,
            forceNewSession = false
        )

        assertNotEquals(open.id, target.id)
        assertEquals(chosen, target.startedAtMillis)
        assertEquals(chosen, target.endedAtMillis)
        assertEquals(0L, target.localEpochDay)
        // The live open session is left untouched and open.
        assertEquals(open, sessions.open())
        assertEquals(target.id, logs.all().single { it.performedAtMillis == chosen }.sessionId)
    }

    @Test
    fun aBackdatedTimeBeforeTheOpenSessionStartDoesNotAttach() = runTest {
        val logs = FakeWorkoutLogRepository()
        val sessions = FakeWorkoutSessionRepository()
        val useCase = useCase(logs, sessions)

        val start = 10_000L
        useCase(set(performedAtMillis = start), utcOffsetMillis = 0L)
        val open = sessions.open()!!

        val chosen = start - 1_000L
        val target = useCase.logBackdated(
            set(performedAtMillis = chosen),
            utcOffsetMillis = 0L,
            forceNewSession = false
        )

        assertNotEquals(open.id, target.id)
        assertEquals(chosen, target.startedAtMillis)
        assertEquals(chosen, target.endedAtMillis)
        assertEquals(open, sessions.open())
    }

    @Test
    fun forceNewBackdatedSessionCreatesAFreshClosedSession() = runTest {
        val logs = FakeWorkoutLogRepository()
        val sessions = FakeWorkoutSessionRepository()
        val useCase = useCase(logs, sessions)

        val start = 10_000L
        useCase(set(performedAtMillis = start), utcOffsetMillis = 0L)
        val open = sessions.open()!!

        val chosen = start + 1_000L
        val target = useCase.logBackdated(
            set(performedAtMillis = chosen),
            utcOffsetMillis = 0L,
            forceNewSession = true
        )

        assertNotEquals(open.id, target.id)
        assertEquals(chosen, target.startedAtMillis)
        assertEquals(chosen, target.endedAtMillis)
        assertEquals(open, sessions.open())
    }

    @Test
    fun logIntoStampsAnExplicitNonOpenSession() = runTest {
        val logs = FakeWorkoutLogRepository()
        val sessions = FakeWorkoutSessionRepository()
        val useCase = useCase(logs, sessions)
        val closed = StartWorkoutSessionUseCase(sessions)(
            startedAtMillis = 1_000L,
            localEpochDay = 0L,
            endedAtMillis = 2_000L
        )

        useCase.logInto(set(performedAtMillis = 1_500L), closed.id)

        assertEquals(closed.id, logs.all().single().sessionId)
        assertNull(sessions.open())
    }

    @Test
    fun reopeningTheSameSessionDoesNotReadTheWholeLog() = runTest {
        val logs = FakeWorkoutLogRepository()
        val sessions = FakeWorkoutSessionRepository()
        val useCase = useCase(logs, sessions)
        useCase(set(performedAtMillis = 1_000L), utcOffsetMillis = 0L)

        logs.allCalls = 0
        useCase(set(performedAtMillis = 1_000L + 1.hours.inWholeMilliseconds), utcOffsetMillis = 0L)

        assertEquals(0, logs.allCalls)
        assertEquals(1, sessions.all().size)
    }

    private fun useCase(logs: FakeWorkoutLogRepository, sessions: FakeWorkoutSessionRepository) =
        LogWorkoutSetUseCase(
            repository = logs,
            startWorkoutSession = StartWorkoutSessionUseCase(sessions),
            endWorkoutSession = EndWorkoutSessionUseCase(sessions),
            observeOpenWorkoutSession = ObserveOpenWorkoutSessionUseCase(sessions),
            config = SessionConfig()
        )

    private fun set(performedAtMillis: Long) = WorkoutSet(
        exerciseId = "back-squat",
        reps = 5,
        weightKg = 100.0,
        performedAtMillis = performedAtMillis
    )

    private class FakeWorkoutLogRepository : WorkoutLogRepository {
        private val sets = mutableListOf<WorkoutSet>()

        var allCalls = 0

        override suspend fun add(set: WorkoutSet) {
            sets.add(set)
        }

        override suspend fun assignSession(setId: Long, sessionId: String) = Unit

        override suspend fun delete(id: Long) = Unit

        override suspend fun updateSetPerformedAt(setId: Long, performedAtMillis: Long) = Unit

        override suspend fun all(): List<WorkoutSet> {
            allCalls++
            return sets.toList()
        }

        override suspend fun lastSetBySession(sessionId: String): WorkoutSet? =
            sets.filter { it.sessionId == sessionId }
                .maxWithOrNull(compareBy({ it.performedAtMillis }, { it.id }))

        override fun setsFlow(): Flow<List<WorkoutSet>> = flowOf(sets.toList())

        override suspend fun loggedSets(): List<LoggedSet> = emptyList()

        override fun loggedSetsFlow(): Flow<List<LoggedSet>> = flowOf(emptyList())

        override suspend fun clear() {
            sets.clear()
        }
    }

    private class FakeWorkoutSessionRepository : WorkoutSessionRepository {
        private val sessions = mutableListOf<WorkoutSession>()
        private val openSession = MutableStateFlow<WorkoutSession?>(null)

        override suspend fun create(session: WorkoutSession) {
            sessions.add(session)
            if (session.endedAtMillis == null) openSession.value = session
        }

        override suspend fun end(id: String, endedAtMillis: Long) {
            val index = sessions.indexOfFirst { it.id == id }
            if (index >= 0) sessions[index] = sessions[index].copy(endedAtMillis = endedAtMillis)
            if (openSession.value?.id == id) openSession.value = null
        }

        override suspend fun open(): WorkoutSession? = openSession.value

        override fun openFlow(): Flow<WorkoutSession?> = openSession

        override suspend fun all(): List<WorkoutSession> = sessions.toList()
    }

    private companion object {
        val window = SessionConfig().sessionInactivityWindow
        const val DAY = 24L * 60L * 60L * 1000L
    }
}
