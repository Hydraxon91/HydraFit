package com.hydrafit.app.core.domain.workout

import com.hydrafit.app.core.domain.fatigue.LoggedSet
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest

class LogWorkoutSetUseCaseTest {

    @Test
    fun addsTheSetToTheRepository() = runTest {
        val repository = FakeWorkoutLogRepository()
        val set = WorkoutSet(
            exerciseId = "back-squat",
            reps = 5,
            weightKg = 100.0,
            performedAtMillis = 42L
        )

        LogWorkoutSetUseCase(repository)(set)

        assertEquals(listOf(set), repository.all())
    }

    private class FakeWorkoutLogRepository : WorkoutLogRepository {
        private val sets = mutableListOf<WorkoutSet>()

        override suspend fun add(set: WorkoutSet) {
            sets.add(set)
        }

        override suspend fun assignSession(setId: Long, sessionId: String) = Unit

        override suspend fun delete(id: Long) = Unit

        override suspend fun all(): List<WorkoutSet> = sets.toList()

        override fun setsFlow(): Flow<List<WorkoutSet>> = flowOf(sets.toList())

        override suspend fun loggedSets(): List<LoggedSet> = emptyList()

        override fun loggedSetsFlow(): Flow<List<LoggedSet>> = flowOf(emptyList())

        override suspend fun clear() {
            sets.clear()
        }
    }
}
