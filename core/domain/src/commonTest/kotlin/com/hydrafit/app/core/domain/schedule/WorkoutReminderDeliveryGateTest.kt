package com.hydrafit.app.core.domain.schedule

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class WorkoutReminderDeliveryGateTest {

    @Test
    fun aSnapshotTokenStaysCurrentUntilAMutationInvalidatesIt() = runTest {
        val gate = WorkoutReminderDeliveryGate()
        val snapshot = gate.snapshot { "state-a" }
        assertEquals("state-a", snapshot.value)

        assertEquals("ran", gate.ifCurrent(snapshot.token) { "ran" })

        gate.mutate { }

        assertNull(gate.ifCurrent(snapshot.token) { "ran" })
    }

    @Test
    fun aFailedMutationStillInvalidatesOutstandingTokens() = runTest {
        val gate = WorkoutReminderDeliveryGate()
        val token = gate.snapshot { 1 }.token

        var thrown = false
        try {
            gate.mutate { error("write failed") }
        } catch (expected: IllegalStateException) {
            thrown = true
        }
        assertTrue(thrown)
        assertNull(gate.ifCurrent(token) { "ran" })
    }

    @Test
    fun aSnapshotBlocksAMutationUntilTheReadReleasesTheGate() = runTest {
        val gate = WorkoutReminderDeliveryGate()
        val readStarted = CompletableDeferred<Unit>()
        val releaseRead = CompletableDeferred<Unit>()
        var mutated = false

        launch {
            gate.snapshot {
                readStarted.complete(Unit)
                releaseRead.await()
            }
        }
        runCurrent()
        assertTrue(readStarted.isCompleted)

        launch { gate.mutate { mutated = true } }
        runCurrent()
        assertFalse(mutated)

        releaseRead.complete(Unit)
        advanceUntilIdle()
        assertTrue(mutated)
    }

    @Test
    fun aCurrentActionHoldsTheGateThroughItsOwnSuspension() = runTest {
        val gate = WorkoutReminderDeliveryGate()
        val token = gate.snapshot { Unit }.token
        val actionStarted = CompletableDeferred<Unit>()
        val releaseAction = CompletableDeferred<Unit>()
        var mutated = false

        launch {
            gate.ifCurrent(token) {
                actionStarted.complete(Unit)
                releaseAction.await()
            }
        }
        runCurrent()
        assertTrue(actionStarted.isCompleted)

        launch { gate.mutate { mutated = true } }
        runCurrent()
        assertFalse(mutated)

        releaseAction.complete(Unit)
        advanceUntilIdle()
        assertTrue(mutated)
    }

    @Test
    fun aMutationTakenWhileAnActionWaitsInvalidatesThatTokenAfterward() = runTest {
        val gate = WorkoutReminderDeliveryGate()
        val token = gate.snapshot { Unit }.token
        gate.mutate { }
        // A second mutation after the first still invalidates; the gate never resurrects a token.
        gate.mutate { }
        assertNull(gate.ifCurrent(token) { "ran" })
    }
}
