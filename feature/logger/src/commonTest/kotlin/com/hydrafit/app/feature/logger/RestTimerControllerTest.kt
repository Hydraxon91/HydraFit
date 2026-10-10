package com.hydrafit.app.feature.logger

import com.hydrafit.app.core.domain.workout.PersistedRestCountdown
import com.hydrafit.app.core.domain.workout.RestCountdownRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class RestTimerControllerTest {
    @Test
    fun delayedTicksUseElapsedDeadlineAndDurationEditsKeepOriginalStart() = runTest {
        var elapsed = 10_000L
        val controller = RestTimerController(CoroutineScope(coroutineContext), { elapsed })

        controller.start(120_000L)
        assertEquals(120_000L, controller.state.value?.remainingMillis)

        elapsed = 70_000L
        advanceTimeBy(250L)
        runCurrent()
        assertEquals(60_000L, controller.state.value?.remainingMillis)

        controller.updateDuration(180_000L)
        assertEquals(120_000L, controller.state.value?.remainingMillis)

        elapsed = 200_000L
        advanceTimeBy(250L)
        runCurrent()
        assertTrue(controller.state.value?.isFinished == true)
        controller.cancel()
    }

    @Test
    fun cancelClearsCountdownAndStartingAgainReplacesIt() = runTest {
        var elapsed = 1L
        val controller = RestTimerController(CoroutineScope(coroutineContext), { elapsed })
        controller.start(10_000L)
        controller.start(5_000L)
        assertEquals(5_000L, controller.state.value?.remainingMillis)

        controller.cancel()

        assertNull(controller.state.value)
    }

    @Test
    fun restoresRemainingTimeOnlyForMatchingBootAndWorkoutContext() = runTest {
        var elapsed = 100_000L
        val repository = FakeRestCountdownRepository(null)
        val firstController = RestTimerController(
            CoroutineScope(coroutineContext),
            { elapsed },
            { "boot-a" },
            repository
        )
        firstController.start(
            durationMillis = 120_000L,
            completedAtElapsedMillis = 40_000L,
            sessionId = "session-a",
            occurrenceId = 5L,
            exerciseId = "squat"
        )
        runCurrent()
        firstController.cancel(clearPersisted = false)
        val controller = RestTimerController(
            CoroutineScope(coroutineContext),
            { elapsed },
            { "boot-a" },
            repository
        )

        val restored = controller.restore("session-a", 5L, setOf("squat"))

        assertEquals("squat", restored?.exerciseId)
        assertEquals(60_000L, controller.state.value?.remainingMillis)
        elapsed = 160_000L
        advanceTimeBy(250L)
        runCurrent()
        assertTrue(controller.state.value?.isFinished == true)
    }

    @Test
    fun rebootMismatchAndExpiredCountdownAreSilentlyCleared() = runTest {
        var elapsed = 100_000L
        val stored = PersistedRestCountdown(
            deadlineElapsedMillis = 160_000L,
            durationMillis = 120_000L,
            bootIdentity = "old-boot",
            sessionId = "session-a",
            occurrenceId = 5L,
            exerciseId = "squat"
        )
        val repository = FakeRestCountdownRepository(stored)
        val controller = RestTimerController(
            CoroutineScope(coroutineContext),
            { elapsed },
            { "new-boot" },
            repository
        )

        assertNull(controller.restore("session-a", 5L, setOf("squat")))
        assertNull(repository.countdown)

        repository.countdown = stored.copy(
            bootIdentity = "new-boot",
            deadlineElapsedMillis = 99_000L
        )
        elapsed = 100_000L
        assertNull(controller.restore("session-a", 5L, setOf("squat")))
        assertNull(repository.countdown)
        assertNull(controller.state.value)
    }

    @Test
    fun missingBootIdentityFailsClosed() = runTest {
        val repository = FakeRestCountdownRepository(
            PersistedRestCountdown(
                deadlineElapsedMillis = 160_000L,
                durationMillis = 120_000L,
                bootIdentity = "boot-a",
                sessionId = "session-a",
                occurrenceId = 5L,
                exerciseId = "squat"
            )
        )
        val controller = RestTimerController(
            CoroutineScope(coroutineContext),
            { 100_000L },
            { null },
            repository
        )

        assertNull(controller.restore("session-a", 5L, setOf("squat")))
        assertNull(repository.countdown)
    }

    @Test
    fun malformedDeadlineIsSilentlyCleared() = runTest {
        val stored = PersistedRestCountdown(
            deadlineElapsedMillis = 10_000L,
            durationMillis = 20_000L,
            bootIdentity = "boot-a",
            sessionId = "session-a",
            occurrenceId = 5L,
            exerciseId = "squat"
        )
        val repository = FakeRestCountdownRepository(stored)
        val controller = RestTimerController(
            CoroutineScope(coroutineContext),
            { 1_000L },
            { "boot-a" },
            repository
        )

        assertNull(controller.restore("session-a", 5L, setOf("squat")))
        assertNull(repository.countdown)
        assertNull(controller.state.value)
    }

    @Test
    fun sessionOccurrenceAndExerciseContextMustStillMatch() = runTest {
        val stored = PersistedRestCountdown(
            deadlineElapsedMillis = 160_000L,
            durationMillis = 120_000L,
            bootIdentity = "boot-a",
            sessionId = "session-a",
            occurrenceId = 5L,
            exerciseId = "squat"
        )
        val repository = FakeRestCountdownRepository(stored)
        val controller = RestTimerController(
            CoroutineScope(coroutineContext),
            { 100_000L },
            { "boot-a" },
            repository
        )

        assertNull(controller.restore("session-b", 5L, setOf("squat")))
        repository.countdown = stored
        assertNull(controller.restore("session-a", 6L, setOf("squat")))
        repository.countdown = stored
        assertNull(controller.restore("session-a", 5L, setOf("bench")))
        assertNull(repository.countdown)
    }

    private class FakeRestCountdownRepository(var countdown: PersistedRestCountdown?) :
        RestCountdownRepository {
        override suspend fun load(): PersistedRestCountdown? = countdown

        override suspend fun save(countdown: PersistedRestCountdown) {
            this.countdown = countdown
        }

        override suspend fun clear() {
            countdown = null
        }
    }
}
