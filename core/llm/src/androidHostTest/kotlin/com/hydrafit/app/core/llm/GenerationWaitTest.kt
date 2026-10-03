package com.hydrafit.app.core.llm

import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class GenerationWaitTest {

    @Test
    fun returnsWithoutCancellingWhenTheLatchCountsDownInTime() {
        val done = CountDownLatch(1)
        done.countDown()
        val cancelled = AtomicInteger(0)

        awaitGeneration(done, timeoutMillis = 1_000L) { cancelled.incrementAndGet() }

        assertEquals(0, cancelled.get())
    }

    @Test
    fun cancelsAndThrowsWhenTheLatchNeverCountsDown() {
        val done = CountDownLatch(1)
        val cancelled = AtomicInteger(0)

        assertFailsWith<GenerationTimeoutException> {
            awaitGeneration(done, timeoutMillis = 20L, graceMillis = 20L) {
                cancelled.incrementAndGet()
            }
        }

        assertEquals(1, cancelled.get())
    }

    @Test
    fun stillThrowsWhenTheLatchSettlesOnlyDuringTheGracePeriod() {
        val done = CountDownLatch(1)
        val cancelled = AtomicInteger(0)

        assertFailsWith<GenerationTimeoutException> {
            awaitGeneration(done, timeoutMillis = 20L, graceMillis = 1_000L) {
                cancelled.incrementAndGet()
                // The cancel unwinds the native loop during the grace period.
                done.countDown()
            }
        }

        assertEquals(1, cancelled.get())
    }
}
