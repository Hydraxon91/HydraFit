package com.hydrafit.app.feature.logger

import com.hydrafit.app.core.domain.workout.PersistedRestCountdown
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RestCountdownCleanupTest {
    private val countdown = PersistedRestCountdown(
        deadlineElapsedMillis = 120_000L,
        durationMillis = 120_000L,
        bootIdentity = "boot-a",
        sessionId = "session-a",
        occurrenceId = 7L,
        exerciseId = "squat"
    )
    private val identity = countdown.notificationIdentity()

    @Test
    fun identityMirrorsTheCountdownItTargets() {
        assertEquals(
            RestCountdownNotificationIdentity(
                deadlineElapsedMillis = 120_000L,
                bootIdentity = "boot-a",
                sessionId = "session-a",
                occurrenceId = 7L,
                exerciseId = "squat"
            ),
            identity
        )
    }

    @Test
    fun eligibleOnlyForTheDisplayedIdentityOnTheSameBootAtOrAfterTheDeadline() {
        assertTrue(
            isCountdownCleanupEligible(
                expected = identity,
                displayed = identity,
                currentBootIdentity = "boot-a",
                elapsedNowMillis = 120_000L
            )
        )
        assertTrue(
            isCountdownCleanupEligible(
                expected = identity,
                displayed = identity,
                currentBootIdentity = "boot-a",
                elapsedNowMillis = 130_000L
            )
        )
    }

    @Test
    fun earlyDeliveryDoesNotRemoveTheDisplayedCountdown() {
        assertFalse(
            isCountdownCleanupEligible(
                expected = identity,
                displayed = identity,
                currentBootIdentity = "boot-a",
                elapsedNowMillis = 119_999L
            )
        )
    }

    @Test
    fun newerOrDifferentDisplayedCountdownSurvivesAStaleDelivery() {
        val replacement = countdown.copy(
            deadlineElapsedMillis = 200_000L,
            occurrenceId = 8L
        ).notificationIdentity()
        assertFalse(
            isCountdownCleanupEligible(
                expected = identity,
                displayed = replacement,
                currentBootIdentity = "boot-a",
                elapsedNowMillis = 200_000L
            )
        )
    }

    @Test
    fun missingDisplayedNotificationOrTargetIsNotEligible() {
        assertFalse(
            isCountdownCleanupEligible(
                expected = identity,
                displayed = null,
                currentBootIdentity = "boot-a",
                elapsedNowMillis = 130_000L
            )
        )
        assertFalse(
            isCountdownCleanupEligible(
                expected = null,
                displayed = identity,
                currentBootIdentity = "boot-a",
                elapsedNowMillis = 130_000L
            )
        )
    }

    @Test
    fun bootChangeFailsClosedEvenAfterTheDeadline() {
        assertFalse(
            isCountdownCleanupEligible(
                expected = identity,
                displayed = identity,
                currentBootIdentity = "boot-b",
                elapsedNowMillis = 130_000L
            )
        )
        assertFalse(
            isCountdownCleanupEligible(
                expected = identity,
                displayed = identity,
                currentBootIdentity = null,
                elapsedNowMillis = 130_000L
            )
        )
    }
}
