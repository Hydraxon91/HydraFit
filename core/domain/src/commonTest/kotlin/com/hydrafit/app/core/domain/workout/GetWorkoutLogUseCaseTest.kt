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

    private class FakeWorkoutLogRepository(private val sets: List<WorkoutSet>) :
        WorkoutLogRepository {
        override suspend fun add(set: WorkoutSet) = Unit

        override suspend fun assignSession(setId: Long, sessionId: String) = Unit

        override suspend fun delete(id: Long) = Unit

        override suspend fun updateSetPerformedAt(setId: Long, performedAtMillis: Long) = Unit

        override suspend fun all(): List<WorkoutSet> = sets

        override fun setsFlow(): Flow<List<WorkoutSet>> = flowOf(sets)

        override suspend fun loggedSets(): List<LoggedSet> = emptyList()

        override fun loggedSetsFlow(): Flow<List<LoggedSet>> = flowOf(emptyList())

        override suspend fun clear() = Unit
    }
}
