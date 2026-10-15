package com.hydrafit.app.feature.logger

import com.hydrafit.app.core.domain.workout.PersistedRestCountdown
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class RestCompletionPresentationTest {
    private val countdown = PersistedRestCountdown(10_000L, 9_000L, "boot", "session", 1L, "squat")

    @Test
    fun foregroundAndReceiverRaceProducesOneSignalAndNoSystemPost() = runTest {
        var elapsed = 1_000L
        val presentation = RestCompletionPresentation { elapsed }
        var signals = 0
        var posts = 0
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            presentation.completions.collect { signals++ }
        }
        presentation.setVisible(true)
        presentation.update(countdown, true)
        elapsed = 10_000L
        val timer = async { presentation.complete(countdown, true) }
        val receiver = async { presentation.complete(countdown, true) { posts++ } }
        timer.await()
        receiver.await()
        assertEquals(1, signals)
        assertEquals(0, posts)
    }

    @Test
    fun returningAfterExpiryDoesNotSignalAndReceiverStillUsesBackgroundOptIn() = runTest {
        var elapsed = 1_000L
        val presentation = RestCompletionPresentation { elapsed }
        var signals = 0
        var posts = 0
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            presentation.completions.collect { signals++ }
        }
        presentation.update(countdown, true)
        elapsed = 11_000L
        presentation.setVisible(true)
        presentation.complete(countdown, true)
        assertEquals(0, signals)
        presentation.complete(countdown, true) { posts++ }
        presentation.complete(countdown, true) { posts++ }
        assertEquals(1, posts)
    }

    @Test
    fun enablingAfterExpiryAndExpiredRestorationNeverReplay() = runTest {
        var elapsed = 11_000L
        val presentation = RestCompletionPresentation { elapsed }
        var signals = 0
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            presentation.completions.collect { signals++ }
        }
        presentation.setVisible(true)
        presentation.update(countdown, true)
        presentation.complete(countdown, true)
        assertEquals(0, signals)
        elapsed = 12_000L
        val next = countdown.copy(deadlineElapsedMillis = 20_000L)
        presentation.update(next, true)
        presentation.update(next, false)
        elapsed = 20_000L
        presentation.update(next, true)
        presentation.complete(next, true)
        assertEquals(0, signals)
    }

    @Test
    fun backgroundExpiryWithoutAnAlertCallbackIsNotConsumed() = runTest {
        var elapsed = 1_000L
        val presentation = RestCompletionPresentation { elapsed }
        var posts = 0
        presentation.update(countdown, true)
        elapsed = 10_000L
        presentation.complete(countdown, true)
        presentation.complete(countdown, false) { posts++ }
        assertEquals(1, posts)
    }

    @Test
    fun clearingContextOrDisablingBeforeExpiryPreventsAForegroundSignal() = runTest {
        var elapsed = 1_000L
        val presentation = RestCompletionPresentation { elapsed }
        var signals = 0
        var posts = 0
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            presentation.completions.collect { signals++ }
        }
        presentation.setVisible(true)
        presentation.update(countdown, true)
        presentation.update(null, false)
        elapsed = 10_000L
        presentation.complete(countdown, true)
        assertEquals(0, signals)

        elapsed = 11_000L
        val next = countdown.copy(deadlineElapsedMillis = 20_000L)
        presentation.update(next, true)
        presentation.update(next, false)
        elapsed = 20_000L
        presentation.complete(next, false) { posts++ }
        assertEquals(0, signals)
        assertEquals(1, posts)
    }

    @Test
    fun aSuspendedSystemPostSerializesWithTheForegroundTimer() = runTest {
        var elapsed = 1_000L
        val presentation = RestCompletionPresentation { elapsed }
        val posting = kotlinx.coroutines.CompletableDeferred<Unit>()
        val finish = kotlinx.coroutines.CompletableDeferred<Unit>()
        var posts = 0
        var signals = 0
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            presentation.completions.collect { signals++ }
        }
        presentation.update(countdown, true)
        elapsed = 10_000L
        val receiver = async {
            presentation.complete(countdown, true) {
                posting.complete(Unit)
                finish.await()
                posts++
            }
        }
        posting.await()
        presentation.setVisible(true) // Returning after expiry cannot steal delivery.
        val timer = async { presentation.complete(countdown, true) }
        finish.complete(Unit)
        receiver.await()
        timer.await()
        assertEquals(1, posts)
        assertEquals(0, signals)
    }
}
