package com.hydrafit.app.core.domain.workout

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest

class StartWorkoutSessionUseCaseTest {

    @Test
    fun createsASessionWithAGeneratedIdAndNoEndTime() = runTest {
        val repository = RecordingWorkoutSessionRepository()

        val session = StartWorkoutSessionUseCase(repository)(
            startedAtMillis = 100L,
            localEpochDay = 5L
        )

        assertEquals(session, repository.created.single())
        assertTrue(session.id.isNotBlank())
        assertNull(session.endedAtMillis)
        assertEquals(100L, session.startedAtMillis)
        assertEquals(5L, session.localEpochDay)
    }

    @Test
    fun createsAClosedSessionWhenAnEndTimeIsProvided() = runTest {
        val repository = RecordingWorkoutSessionRepository()

        val session = StartWorkoutSessionUseCase(repository)(
            startedAtMillis = 100L,
            localEpochDay = 5L,
            endedAtMillis = 200L
        )

        assertEquals(200L, session.endedAtMillis)
        assertEquals(session, repository.created.single())
    }

    @Test
    fun generatesADistinctIdPerSession() = runTest {
        val repository = RecordingWorkoutSessionRepository()
        val useCase = StartWorkoutSessionUseCase(repository)

        val first = useCase(startedAtMillis = 1L, localEpochDay = 1L)
        val second = useCase(startedAtMillis = 2L, localEpochDay = 1L)

        assertTrue(first.id != second.id)
    }

    private class RecordingWorkoutSessionRepository : WorkoutSessionRepository {
        val created = mutableListOf<WorkoutSession>()

        override suspend fun create(session: WorkoutSession) {
            created.add(session)
        }

        override suspend fun end(id: String, endedAtMillis: Long) = Unit

        override suspend fun open(): WorkoutSession? = null

        override fun openFlow(): Flow<WorkoutSession?> = flowOf(null)

        override suspend fun all(): List<WorkoutSession> = created.toList()
    }
}
