package com.hydrafit.app.feature.logger

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RestCountdownCleanupConcurrencyTest {
    private val identityA = RestCountdownNotificationIdentity(
        deadlineElapsedMillis = 100_000L,
        bootIdentity = "boot-a",
        sessionId = "session-a",
        occurrenceId = 1L,
        exerciseId = "squat"
    )
    private val identityB = identityA.copy(deadlineElapsedMillis = 200_000L, occurrenceId = 2L)

    @Test
    fun publicationCannotCommitBetweenCleanupCheckAndRemoval() {
        val state = RestCountdownNotificationState()
        val displayed = AtomicReference(identityA)
        val removed = AtomicBoolean(false)
        val published = AtomicBoolean(false)
        val insideRemoval = CountDownLatch(1)
        val releaseRemoval = CountDownLatch(1)
        val publishStarted = CountDownLatch(1)

        val cleanup = Thread {
            state.cleanup(
                expected = identityA,
                currentBootIdentity = "boot-a",
                elapsedNowMillis = 100_000L,
                displayed = { displayed.get() },
                remove = {
                    removed.set(true)
                    insideRemoval.countDown()
                    releaseRemoval.await()
                }
            )
        }
        cleanup.start()
        assertTrue(insideRemoval.await(5, TimeUnit.SECONDS), "cleanup did not enter its removal")

        val publish = Thread {
            publishStarted.countDown()
            state.withPublicationLock {
                displayed.set(identityB)
                published.set(true)
            }
        }
        publish.start()
        assertTrue(publishStarted.await(5, TimeUnit.SECONDS))

        // Let the replacement reach the shared lock, then confirm it is held out of the critical
        // section rather than committed inside it.
        while (publish.state == Thread.State.RUNNABLE) Thread.yield()
        assertEquals(
            Thread.State.BLOCKED,
            publish.state,
            "replacement committed inside the cleanup critical section"
        )
        assertFalse(published.get())

        releaseRemoval.countDown()
        cleanup.join()
        publish.join()

        assertTrue(removed.get())
        assertTrue(published.get())
        assertEquals(identityB, displayed.get())
    }

    @Test
    fun staleCleanupIsANoOpForANewerDisplayedCountdown() {
        val state = RestCountdownNotificationState()
        val displayed = AtomicReference(identityB)
        val removed = AtomicBoolean(false)

        state.cleanup(
            expected = identityA,
            currentBootIdentity = "boot-a",
            elapsedNowMillis = 200_000L,
            displayed = { displayed.get() },
            remove = { removed.set(true) }
        )

        assertFalse(removed.get(), "stale cleanup removed the newer countdown")
        assertEquals(identityB, displayed.get())
    }

    @Test
    fun restartCleanupMatchesTheDisplayedNotificationWithoutInMemoryState() {
        // A fresh instance stands in for a process started after death: the OS still displays the
        // old timer, so the displayed read alone must authorize removal.
        val state = RestCountdownNotificationState()
        val displayed = AtomicReference(identityA)
        val removed = AtomicBoolean(false)

        state.cleanup(
            expected = identityA,
            currentBootIdentity = "boot-a",
            elapsedNowMillis = 100_000L,
            displayed = { displayed.get() },
            remove = { removed.set(true) }
        )

        assertTrue(removed.get())
    }

    @Test
    fun earlyOrWrongBootCleanupIsANoOp() {
        val state = RestCountdownNotificationState()
        val displayed = AtomicReference(identityA)
        val removed = AtomicBoolean(false)

        state.cleanup(
            expected = identityA,
            currentBootIdentity = "boot-a",
            elapsedNowMillis = 99_999L,
            displayed = { displayed.get() },
            remove = { removed.set(true) }
        )
        state.cleanup(
            expected = identityA,
            currentBootIdentity = "boot-b",
            elapsedNowMillis = 100_000L,
            displayed = { displayed.get() },
            remove = { removed.set(true) }
        )

        assertFalse(removed.get())
    }
}
