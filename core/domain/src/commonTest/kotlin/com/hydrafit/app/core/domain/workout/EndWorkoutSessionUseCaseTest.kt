package com.hydrafit.app.core.domain.workout

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest

class EndWorkoutSessionUseCaseTest {

    @Test
    fun closesTheRequestedSessionAtTheGivenTime() = runTest {
        val repository = RecordingWorkoutSessionRepository()

        EndWorkoutSessionUseCase(repository)(sessionId = "session-a", endedAtMillis = 999L)

        assertEquals(listOf("session-a" to 999L), repository.ended)
    }

    private class RecordingWorkoutSessionRepository : WorkoutSessionRepository {
        val ended = mutableListOf<Pair<String, Long>>()

        override suspend fun create(session: WorkoutSession) = Unit

        override suspend fun end(id: String, endedAtMillis: Long) {
            ended.add(id to endedAtMillis)
        }

        override suspend fun open(): WorkoutSession? = null

        override fun openFlow(): Flow<WorkoutSession?> = flowOf(null)

        override suspend fun all(): List<WorkoutSession> = emptyList()
    }
}
