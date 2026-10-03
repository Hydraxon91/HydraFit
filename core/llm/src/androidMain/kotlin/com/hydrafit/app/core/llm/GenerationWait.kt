package com.hydrafit.app.core.llm

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Thrown when an on-device generation does not complete within the bounded wait. */
internal class GenerationTimeoutException(message: String) : Exception(message)

/** How long a cancelled native loop is given to unwind before the conversation is closed. */
internal const val CANCEL_GRACE_MILLIS = 2_000L

/**
 * Waits for [done] up to [timeoutMillis]. On timeout it runs [onTimeout] (which cancels the native
 * generation), gives the cancelled loop a short grace period to unwind, then throws
 * [GenerationTimeoutException] so the caller can fall back instead of blocking forever.
 */
internal fun awaitGeneration(
    done: CountDownLatch,
    timeoutMillis: Long,
    graceMillis: Long = CANCEL_GRACE_MILLIS,
    onTimeout: () -> Unit
) {
    if (done.await(timeoutMillis, TimeUnit.MILLISECONDS)) return
    onTimeout()
    done.await(graceMillis, TimeUnit.MILLISECONDS)
    throw GenerationTimeoutException("On-device generation timed out after $timeoutMillis ms")
}
