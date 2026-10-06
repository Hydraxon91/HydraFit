package com.hydrafit.app.core.llm

import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class GenerationWaitTest {

    @Test
    fun returnsNullWithoutCancellingWhenNativeCompletesInTime() {
        val done = CountDownLatch(1)
        done.countDown()
        val cancelled = AtomicInteger(0)

        val reason = awaitGeneration(
            done = done,
            timeoutMillis = 1_000L,
            stallTimeoutMillis = 1_000L,
            lastProgressNanos = { System.nanoTime() },
            onCancel = { cancelled.incrementAndGet() }
        )

        assertNull(reason)
        assertEquals(0, cancelled.get())
    }

    @Test
    fun cancelsAndReturnsAStallReasonWhenOutputNeverGrows() {
        val done = CountDownLatch(1)
        val cancelled = AtomicInteger(0)

        val reason = awaitGeneration(
            done = done,
            timeoutMillis = 10_000L,
            stallTimeoutMillis = 10L,
            // Pretend the last output happened a minute ago so the stall trips immediately.
            lastProgressNanos = { System.nanoTime() - 60_000L * NANOS_PER_MILLI },
            graceMillis = 5L,
            pollMillis = 5L,
            onCancel = { cancelled.incrementAndGet() }
        )

        assertNotNull(reason)
        assertEquals(1, cancelled.get())
    }

    @Test
    fun cancelsAndReturnsATimeoutReasonAtTheAbsoluteDeadline() {
        val done = CountDownLatch(1)
        val cancelled = AtomicInteger(0)

        val reason = awaitGeneration(
            done = done,
            timeoutMillis = 10L,
            stallTimeoutMillis = 10_000L,
            lastProgressNanos = { System.nanoTime() },
            graceMillis = 5L,
            pollMillis = 5L,
            onCancel = { cancelled.incrementAndGet() }
        )

        assertNotNull(reason)
        assertEquals(1, cancelled.get())
    }

    @Test
    fun stillReturnsTheReasonWhenNativeSettlesDuringTheGracePeriod() {
        val done = CountDownLatch(1)
        val cancelled = AtomicInteger(0)

        val reason = awaitGeneration(
            done = done,
            timeoutMillis = 10L,
            stallTimeoutMillis = 10_000L,
            lastProgressNanos = { System.nanoTime() },
            graceMillis = 1_000L,
            pollMillis = 5L,
            onCancel = {
                cancelled.incrementAndGet()
                // The cancel unwinds the native loop during the grace period.
                done.countDown()
            }
        )

        // Completion during grace must not turn an explicit abort into a silent success.
        assertNotNull(reason)
        assertEquals(1, cancelled.get())
    }
}
