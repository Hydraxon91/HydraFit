package com.hydrafit.app.core.domain.workout

import com.hydrafit.app.core.domain.fatigue.LoggedSet
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest

class CorrectWorkoutSetTimeUseCaseTest {

    @Test
    fun forwardsTheSetIdAndNewTimeToTheRepository() = runTest {
        val repository = RecordingWorkoutLogRepository()

        CorrectWorkoutSetTimeUseCase(repository)(7L, 1_234L)

        assertEquals(listOf(7L to 1_234L), repository.corrections)
    }

    private class RecordingWorkoutLogRepository : WorkoutLogRepository {
        val corrections = mutableListOf<Pair<Long, Long>>()

        override suspend fun add(set: WorkoutSet) = Unit

        override suspend fun assignSession(setId: Long, sessionId: String) = Unit

        override suspend fun delete(id: Long) = Unit

        override suspend fun updateSetPerformedAt(setId: Long, performedAtMillis: Long) {
            corrections.add(setId to performedAtMillis)
        }

        override suspend fun all(): List<WorkoutSet> = emptyList()

        override fun setsFlow(): Flow<List<WorkoutSet>> = flowOf(emptyList())

        override suspend fun loggedSets(): List<LoggedSet> = emptyList()

        override fun loggedSetsFlow(): Flow<List<LoggedSet>> = flowOf(emptyList())

        override suspend fun clear() = Unit
    }
}
