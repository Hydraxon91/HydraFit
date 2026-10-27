package com.hydrafit.app.core.domain.cardio

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest

class CardioActionsTest {
    @Test
    fun createValidatesAndGeneratesIdentityWhileEditPreservesIt() = runTest {
        val repository = RecordingRepository()
        val activity = CardioActivity(
            identity = "",
            type = CardioActivityType.WALKING,
            startedAtMillis = 1000L,
            durationSeconds = 600
        )
        assertEquals(1L, AddCardioActivityUseCase(repository)(activity))
        val inserted = repository.rows.single()
        assertTrue(inserted.identity.isNotBlank())
        val corrected = inserted.copy(id = 1, durationSeconds = 300)
        EditCardioActivityUseCase(repository)(corrected)
        assertEquals(corrected, repository.rows.single())
        DeleteCardioActivityUseCase(repository)(1)
        assertTrue(repository.rows.isEmpty())
        assertFailsWith<IllegalArgumentException> {
            AddCardioActivityUseCase(repository)(activity.copy(durationSeconds = 0))
        }
        assertTrue(repository.rows.isEmpty())
        assertFailsWith<IllegalArgumentException> {
            EditCardioActivityUseCase(repository)(activity)
        }
    }

    private class RecordingRepository : CardioRepository {
        val rows = mutableListOf<CardioActivity>()
        override fun activitiesFlow() = flowOf(rows.toList())
        override suspend fun all() = rows.toList()
        override suspend fun add(activity: CardioActivity): Long {
            rows += activity
            return 1L
        }
        override suspend fun update(activity: CardioActivity) {
            rows[0] = activity
        }
        override suspend fun delete(id: Long) {
            rows.clear()
        }
        override suspend fun byIdentity(identity: String) = rows.firstOrNull {
            it.identity ==
                identity
        }
    }
}
