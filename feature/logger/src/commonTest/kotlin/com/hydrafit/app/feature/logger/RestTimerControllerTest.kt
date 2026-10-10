package com.hydrafit.app.feature.logger

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
        val controller = RestTimerController(CoroutineScope(coroutineContext)) { elapsed }

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
        val controller = RestTimerController(CoroutineScope(coroutineContext)) { elapsed }
        controller.start(10_000L)
        controller.start(5_000L)
        assertEquals(5_000L, controller.state.value?.remainingMillis)

        controller.cancel()

        assertNull(controller.state.value)
    }
}
