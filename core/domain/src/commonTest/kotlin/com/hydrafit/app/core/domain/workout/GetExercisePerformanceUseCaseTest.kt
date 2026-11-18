package com.hydrafit.app.core.domain.workout

import com.hydrafit.app.core.domain.fatigue.LoggedSet
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest

class GetExercisePerformanceUseCaseTest {

    private fun set(
        id: Long,
        at: Long,
        session: String?,
        weight: Double? = 80.0,
        reps: Int? = 8,
        exercise: String = "back-squat",
        warmup: Boolean = false
    ) = WorkoutSet(
        id = id,
        exerciseId = exercise,
        reps = reps,
        weightKg = weight,
        performedAtMillis = at,
        isWarmup = warmup,
        sessionId = session
    )

    private fun useCase(vararg sets: WorkoutSet) =
        GetExercisePerformanceUseCase(FakeLog(sets.toList()))

    @Test
    fun anExerciseNeverLoggedHasNoPerformance() = runTest {
        assertEquals(ExercisePerformance.NONE, useCase()("back-squat"))
        assertEquals(
            ExercisePerformance.NONE,
            useCase(set(1, 10, "s1", exercise = "plank"))("back-squat")
        )
    }

    @Test
    fun lastTimeIsTheNewestSessionsWorkingSetsOldestFirst() = runTest {
        val result = useCase(
            set(1, 100, "s1", weight = 100.0),
            set(2, 200, "s2", weight = 80.0, reps = 8),
            set(3, 300, "s2", weight = 82.5, reps = 6),
            set(4, 250, "s2", weight = 70.0, warmup = true)
        )("back-squat")

        assertEquals(listOf(2L, 3L), result.lastSessionSets.map { it.id })
        assertEquals(300L, result.lastPerformedAtMillis)
    }

    @Test
    fun theHeaviestSetSpansTheRecentWindowNotJustTheLastSession() = runTest {
        val result = useCase(
            set(1, 100, "s1", weight = 100.0, reps = 3),
            set(2, 100, "s1", weight = 100.0, reps = 5),
            set(3, 200, "s2", weight = 80.0)
        )("back-squat")

        assertEquals(2L, result.heaviestSet?.id)
    }

    @Test
    fun aBodyweightOrTimedExerciseHasNoHeaviestSet() = runTest {
        val result = useCase(
            set(1, 100, "s1", weight = null, reps = null, exercise = "plank"),
            set(2, 200, "s1", weight = null, reps = null, exercise = "plank")
        )("plank")

        assertNull(result.heaviestSet)
        assertEquals(2, result.lastSessionSets.size)
    }

    @Test
    fun setsWithNoSessionAreNeverMergedIntoOneWorkout() = runTest {
        val result = useCase(
            set(1, 100, null),
            set(2, 200, null),
            set(3, 300, null)
        )("back-squat")

        assertEquals(listOf(3L), result.lastSessionSets.map { it.id })
    }

    @Test
    fun theReadIsBoundedByTheSetLimit() = runTest {
        val sets = (1L..100L).map { set(it, it, "s$it") }
        val repository = FakeLog(sets)

        GetExercisePerformanceUseCase(repository)("back-squat")

        assertEquals(60, repository.lastLimit)
    }

    private class FakeLog(private val sets: List<WorkoutSet>) : WorkoutLogRepository {
        var lastLimit: Int? = null

        override suspend fun recentWorkingSetsForExercise(
            exerciseId: String,
            limit: Int
        ): List<WorkoutSet> {
            lastLimit = limit
            return super.recentWorkingSetsForExercise(exerciseId, limit)
        }

        override suspend fun all(): List<WorkoutSet> = sets

        override suspend fun add(set: WorkoutSet) = Unit

        override suspend fun addCluster(set: WorkoutSet, cluster: WorkoutCluster) = Unit

        override suspend fun appendClusterSegments(
            setId: Long,
            segments: List<PerformedClusterSegment>
        ) = Unit

        override suspend fun assignSession(setId: Long, sessionId: String) = Unit

        override suspend fun delete(id: Long) = Unit

        override suspend fun updateSetPerformedAt(setId: Long, performedAtMillis: Long) = Unit

        override suspend fun lastSetBySession(sessionId: String): WorkoutSet? = null

        override suspend fun setsForOccurrence(occurrenceId: Long): List<WorkoutSet> = emptyList()

        override fun setsFlow(): Flow<List<WorkoutSet>> = flowOf(sets)

        override suspend fun loggedSets(): List<LoggedSet> = emptyList()

        override fun loggedSetsFlow(): Flow<List<LoggedSet>> = flowOf(emptyList())

        override suspend fun clear() = Unit
    }
}
