package com.hydrafit.app.core.domain.workout

import com.hydrafit.app.core.domain.fatigue.LoggedSet
import com.hydrafit.app.core.domain.time.TimeProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest

/**
 * The performed-set metric contract: a set carries either a positive rep count or positive whole
 * seconds, and a malformed shape is refused at every write boundary before any set, session or
 * resegmentation side effect.
 */
class TimedWorkoutSetTest {

    @Test
    fun aTimedSetIsStoredAsSecondsAndNeverAsReps() = runTest {
        val logs = RecordingWorkoutLog()
        val sessions = RecordingWorkoutSessions()

        action(logs, sessions)(set(reps = null, durationSeconds = 45), utcOffsetMillis = 0L)

        val stored = logs.added.single()
        assertNull(stored.reps)
        assertEquals(45, stored.durationSeconds)
        assertEquals(SetMeasurement.Duration(45), stored.measurement)
    }

    @Test
    fun aRepCountedSetKeepsItsRepsAndCarriesNoSeconds() = runTest {
        val logs = RecordingWorkoutLog()
        val sessions = RecordingWorkoutSessions()

        action(logs, sessions)(set(reps = 8), utcOffsetMillis = 0L)

        val stored = logs.added.single()
        assertEquals(8, stored.reps)
        assertNull(stored.durationSeconds)
        assertEquals(SetMeasurement.Repetitions(8), stored.measurement)
    }

    @Test
    fun everyEntryPointRefusesAMalformedMetricBeforeAnyWrite() = runTest {
        val logs = RecordingWorkoutLog()
        val sessions = RecordingWorkoutSessions()
        val useCase = action(logs, sessions)
        val malformed = listOf(
            set(reps = null, durationSeconds = null),
            set(reps = 0),
            set(reps = -5),
            set(durationSeconds = 0),
            set(durationSeconds = -3),
            set(reps = 8, durationSeconds = 30)
        )

        malformed.forEach { illegal ->
            assertFailsWith<IllegalArgumentException> {
                useCase(illegal, utcOffsetMillis = 0L)
            }
            assertFailsWith<IllegalArgumentException> {
                useCase.logInto(illegal, sessionId = "session")
            }
            assertFailsWith<IllegalArgumentException> {
                useCase.logBackdated(illegal, utcOffsetMillis = 0L, forceNewSession = true)
            }
        }

        // Nothing was written and no session was opened for any refused shape.
        assertEquals(emptyList(), logs.added)
        assertEquals(emptyList(), sessions.created)
    }

    @Test
    fun aBatchIntoAnExplicitSessionKeepsTheWritesMetric() = runTest {
        val logs = RecordingWorkoutLog()
        val sessions = RecordingWorkoutSessions()
        val useCase = action(logs, sessions)

        useCase.logInto(set(reps = null, durationSeconds = 60), sessionId = "session-1")
        useCase.logInto(set(reps = 12), sessionId = "session-1")

        assertEquals(listOf(null, 12), logs.added.map { it.reps })
        assertEquals(listOf(60, null), logs.added.map { it.durationSeconds })
        assertEquals(setOf("session-1"), logs.added.map { it.sessionId }.toSet())
    }

    @Test
    fun aCorrectionWritesOnlyTheSelectedMetricAndRefusesBoth() = runTest {
        val resegmenter = RecordingResegmenter()
        val useCase = CorrectWorkoutSetUseCase(resegmenter, TimeProvider { 1_000L })
        val timed = WorkoutSetCorrection(
            reps = null,
            weightKg = null,
            rir = null,
            performedAtMillis = 500L,
            durationSeconds = 90
        )

        useCase(7L, timed)

        val applied = resegmenter.corrections.single()
        assertNull(applied.reps)
        assertEquals(90, applied.durationSeconds)

        val refused = listOf(
            timed.copy(durationSeconds = 0),
            timed.copy(reps = 5),
            timed.copy(reps = 0, durationSeconds = null)
        )
        refused.forEach { invalid ->
            assertFailsWith<IllegalArgumentException> { useCase(7L, invalid) }
        }
        assertEquals(1, resegmenter.corrections.size)
    }

    private fun set(
        reps: Int? = 8,
        durationSeconds: Int? = null,
        performedAtMillis: Long = 1_000L
    ) = WorkoutSet(
        exerciseId = "plank",
        reps = reps,
        weightKg = null,
        performedAtMillis = performedAtMillis,
        durationSeconds = durationSeconds
    )

    private fun action(logs: WorkoutLogRepository, sessions: WorkoutSessionRepository) =
        LogWorkoutSetUseCase(
            logs,
            StartWorkoutSessionUseCase(sessions),
            EndWorkoutSessionUseCase(sessions),
            ObserveOpenWorkoutSessionUseCase(sessions),
            SessionConfig()
        )

    private class RecordingWorkoutLog : WorkoutLogRepository {
        val added = mutableListOf<WorkoutSet>()

        override suspend fun add(set: WorkoutSet) {
            added.add(set)
        }

        override suspend fun addCluster(set: WorkoutSet, cluster: WorkoutCluster) = add(
            set.copy(
                cluster = WorkoutClusterSummary(
                    kind = cluster.kind,
                    plannedSegments = cluster.plannedSegments,
                    performedSegments = cluster.performedSegments
                )
            )
        )

        override suspend fun appendClusterSegments(
            setId: Long,
            segments: List<PerformedClusterSegment>
        ) = Unit

        override suspend fun assignSession(setId: Long, sessionId: String) = Unit

        override suspend fun delete(id: Long) = Unit

        override suspend fun updateSetPerformedAt(setId: Long, performedAtMillis: Long) = Unit

        override suspend fun all(): List<WorkoutSet> = added

        override suspend fun lastSetBySession(sessionId: String): WorkoutSet? =
            added.lastOrNull { it.sessionId == sessionId }

        override suspend fun setsForOccurrence(occurrenceId: Long): List<WorkoutSet> =
            added.filter { it.occurrenceId == occurrenceId }

        override fun setsFlow(): Flow<List<WorkoutSet>> = flowOf(added)

        override suspend fun loggedSets(): List<LoggedSet> = emptyList()

        override fun loggedSetsFlow(): Flow<List<LoggedSet>> = flowOf(emptyList())

        override suspend fun clear() = Unit
    }

    private class RecordingWorkoutSessions : WorkoutSessionRepository {
        val created = mutableListOf<WorkoutSession>()

        override suspend fun create(session: WorkoutSession) {
            created.add(session)
        }

        override suspend fun end(id: String, endedAtMillis: Long) = Unit

        /** No open session, so the first set has to start one. */
        override suspend fun open(): WorkoutSession? =
            created.lastOrNull { it.endedAtMillis == null }

        override fun openFlow(): Flow<WorkoutSession?> = flowOf(openOrNull())

        override suspend fun all(): List<WorkoutSession> = created

        private fun openOrNull(): WorkoutSession? = created.lastOrNull { it.endedAtMillis == null }
    }

    private class RecordingResegmenter : SessionResegmenter {
        val corrections = mutableListOf<WorkoutSetCorrection>()

        override suspend fun resegmentAfterSetCorrection(
            setId: Long,
            correction: WorkoutSetCorrection,
            utcOffsetMillis: Long
        ) {
            corrections.add(correction)
        }

        override suspend fun resegmentAfterTimeCorrection(
            setId: Long,
            performedAtMillis: Long,
            utcOffsetMillis: Long
        ) = error("Not exercised by the metric contract")
    }
}
