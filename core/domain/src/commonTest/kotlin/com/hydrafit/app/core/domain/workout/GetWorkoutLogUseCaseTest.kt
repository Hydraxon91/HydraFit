package com.hydrafit.app.core.domain.workout

import com.hydrafit.app.core.domain.fatigue.LoggedSet
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest

class GetWorkoutLogUseCaseTest {

    @Test
    fun returnsTheRepositoryContents() = runTest {
        val expected = listOf(
            WorkoutSet(
                exerciseId = "back-squat",
                reps = 5,
                weightKg = 100.0,
                performedAtMillis = 1L
            )
        )
        val repository = FakeWorkoutLogRepository(expected)

        assertEquals(expected, GetWorkoutLogUseCase(repository)())
    }

    private fun row(id: Long, at: Long, session: String?, warmup: Boolean = false) = WorkoutSet(
        id = id,
        exerciseId = "ex-$id",
        reps = 5,
        weightKg = 50.0,
        performedAtMillis = at,
        isWarmup = warmup,
        sessionId = session
    )

    @Test
    fun recentSessionsReadsTheOpenSessionAndOnlyTheLatestOtherOne() = runTest {
        val repository = FakeWorkoutLogRepository(
            listOf(
                row(1, 100, "old"),
                row(2, 200, "last"),
                row(3, 300, "last"),
                row(4, 400, null),
                row(5, 500, "open")
            )
        )

        val recent = GetWorkoutLogUseCase(repository).recentSessions("open")

        assertEquals(listOf(5L), recent.current.map { it.id })
        assertEquals(listOf(3L, 2L), recent.last.map { it.id })
        assertEquals(
            emptyList(),
            GetWorkoutLogUseCase(repository).recentSessions(null).current
        )
        assertEquals(
            listOf(5L),
            GetWorkoutLogUseCase(repository).recentSessions(null).last.map { it.id }
        )
    }

    @Test
    fun theLoggedTodayWindowFollowsTheUtcOffsetAndIsHalfOpen() = runTest {
        val day = 86_400_000L
        val offset = 13L * 3_600_000L
        // Local day 10 under UTC+13 spans [10 * day - offset, 11 * day - offset).
        val start = 10 * day - offset
        val repository = FakeWorkoutLogRepository(
            listOf(
                row(1, start - 1, "s"),
                row(2, start, "s"),
                row(3, start + day - 1, "s"),
                row(4, start + day, "s"),
                row(5, start + 10, "s", warmup = true)
            )
        )

        val ids = GetWorkoutLogUseCase(repository).exerciseIdsLoggedOn(10L, offset)

        assertEquals(setOf("ex-2", "ex-3"), ids)
    }

    private class FakeWorkoutLogRepository(private val sets: List<WorkoutSet>) :
        WorkoutLogRepository {
        override suspend fun add(set: WorkoutSet) = Unit

        override suspend fun addCluster(set: WorkoutSet, cluster: WorkoutCluster) = Unit

        override suspend fun appendClusterSegments(
            setId: Long,
            segments: List<PerformedClusterSegment>
        ) = Unit

        override suspend fun assignSession(setId: Long, sessionId: String) = Unit

        override suspend fun delete(id: Long) = Unit

        override suspend fun updateSetPerformedAt(setId: Long, performedAtMillis: Long) = Unit

        override suspend fun all(): List<WorkoutSet> = sets

        override suspend fun lastSetBySession(sessionId: String): WorkoutSet? =
            sets.filter { it.sessionId == sessionId }.maxByOrNull { it.performedAtMillis }

        override suspend fun setsForOccurrence(occurrenceId: Long): List<WorkoutSet> =
            sets.filter { it.occurrenceId == occurrenceId }

        override fun setsFlow(): Flow<List<WorkoutSet>> = flowOf(sets)

        override suspend fun loggedSets(): List<LoggedSet> = emptyList()

        override fun loggedSetsFlow(): Flow<List<LoggedSet>> = flowOf(emptyList())

        override suspend fun clear() = Unit
    }
}
