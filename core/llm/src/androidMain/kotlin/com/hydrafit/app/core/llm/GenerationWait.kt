package com.hydrafit.app.core.llm

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Thrown when an on-device generation is aborted: the absolute timeout or a stalled stream. */
internal class GenerationTimeoutException(message: String) : Exception(message)

/** How long a cancelled native loop is given to unwind before the caller closes the conversation. */
internal const val CANCEL_GRACE_MILLIS = 2_000L

/** How often the waiting thread re-checks completion, the absolute deadline and output progress. */
internal const val PROGRESS_POLL_MILLIS = 100L

internal const val NANOS_PER_MILLI = 1_000_000L

/**
 * Waits for [done] to be counted down (native completion) while the **waiting thread** — not a
 * separate watchdog — watches for the absolute [timeoutMillis] deadline and for no output for
 * [stallTimeoutMillis] (per [lastProgressNanos]).
 *
 * On either abort it runs [onCancel] to stop the native loop, waits up to [graceMillis] for the
 * native side to settle (late `onDone`/`onError` callbacks still land), and returns the reason. It
 * returns null when the native side completes on its own. Native completion is never signalled from
 * here, so the caller always gets the grace period before it closes the conversation and engine.
 */
internal fun awaitGeneration(
    done: CountDownLatch,
    timeoutMillis: Long,
    stallTimeoutMillis: Long,
    lastProgressNanos: () -> Long,
    graceMillis: Long = CANCEL_GRACE_MILLIS,
    pollMillis: Long = PROGRESS_POLL_MILLIS,
    onCancel: () -> Unit
): GenerationTimeoutException? {
    val startNanos = System.nanoTime()
    val timeoutNanos = timeoutMillis * NANOS_PER_MILLI
    val stallNanos = stallTimeoutMillis * NANOS_PER_MILLI
    while (true) {
        if (done.await(pollMillis, TimeUnit.MILLISECONDS)) return null
        val now = System.nanoTime()
        val reason = when {
            now - startNanos >= timeoutNanos ->
                GenerationTimeoutException("On-device generation timed out after $timeoutMillis ms")
            now - lastProgressNanos() >= stallNanos ->
                GenerationTimeoutException("No on-device output for $stallTimeoutMillis ms")
            else -> null
        }
        if (reason != null) {
            onCancel()
            done.await(graceMillis, TimeUnit.MILLISECONDS)
            return reason
        }
    }
}
