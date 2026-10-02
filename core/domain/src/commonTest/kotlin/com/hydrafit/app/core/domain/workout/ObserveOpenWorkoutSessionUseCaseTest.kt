package com.hydrafit.app.core.domain.workout

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest

class ObserveOpenWorkoutSessionUseCaseTest {

    @Test
    fun emitsTheRepositoriesOpenSession() = runTest {
        val session = WorkoutSession(id = "session-a", startedAtMillis = 1L, localEpochDay = 2L)
        val repository = FakeWorkoutSessionRepository(session)

        val emitted = ObserveOpenWorkoutSessionUseCase(repository)().first()

        assertEquals(session, emitted)
    }

    @Test
    fun emitsNullWhenNoSessionIsOpen() = runTest {
        val repository = FakeWorkoutSessionRepository(open = null)

        val emitted = ObserveOpenWorkoutSessionUseCase(repository)().first()

        assertEquals(null, emitted)
    }

    private class FakeWorkoutSessionRepository(private val open: WorkoutSession?) :
        WorkoutSessionRepository {
        override suspend fun create(session: WorkoutSession) = Unit

        override suspend fun end(id: String, endedAtMillis: Long) = Unit

        override suspend fun open(): WorkoutSession? = open

        override fun openFlow(): Flow<WorkoutSession?> = flowOf(open)

        override suspend fun all(): List<WorkoutSession> = emptyList()
    }
}
