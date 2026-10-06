package com.hydrafit.app.core.domain.workout

import com.hydrafit.app.core.domain.fatigue.LoggedSet
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest

class DeleteWorkoutSetUseCaseTest {

    @Test
    fun removesTheRequestedSetFromTheRepository() = runTest {
        val repository = RecordingWorkoutLogRepository()

        DeleteWorkoutSetUseCase(repository)(7L)

        assertEquals(listOf(7L), repository.deletedIds)
    }

    private class RecordingWorkoutLogRepository : WorkoutLogRepository {
        val deletedIds = mutableListOf<Long>()

        override suspend fun add(set: WorkoutSet) = Unit

        override suspend fun assignSession(setId: Long, sessionId: String) = Unit

        override suspend fun delete(id: Long) {
            deletedIds.add(id)
        }

        override suspend fun updateSetPerformedAt(setId: Long, performedAtMillis: Long) = Unit

        override suspend fun all(): List<WorkoutSet> = emptyList()

        override suspend fun lastSetBySession(sessionId: String): WorkoutSet? = null

        override fun setsFlow(): Flow<List<WorkoutSet>> = flowOf(emptyList())

        override suspend fun loggedSets(): List<LoggedSet> = emptyList()

        override fun loggedSetsFlow(): Flow<List<LoggedSet>> = flowOf(emptyList())

        override suspend fun clear() = Unit
    }
}
