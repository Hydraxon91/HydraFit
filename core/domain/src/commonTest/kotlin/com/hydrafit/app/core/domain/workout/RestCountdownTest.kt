package com.hydrafit.app.core.domain.workout

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class RestCountdownTest {
    @Test
    fun remainingTimeUsesOriginalCompletionAndExpiresAtDeadline() {
        val countdown = RestCountdown(completedAtElapsedMillis = 1_000L, durationMillis = 120_000L)

        assertEquals(120_000L, countdown.remainingMillis(1_000L))
        assertEquals(60_000L, countdown.remainingMillis(61_000L))
        assertEquals(0L, countdown.remainingMillis(121_000L))
        assertEquals(0L, countdown.remainingMillis(500_000L))
    }

    @Test
    fun durationChangesKeepTheOriginalCompletionInstant() {
        val countdown = RestCountdown(completedAtElapsedMillis = 10_000L, durationMillis = 120_000L)

        val extended = countdown.withDuration(180_000L)
        assertEquals(10_000L, extended.completedAtElapsedMillis)
        assertEquals(170_000L, extended.remainingMillis(20_000L))

        val shortenedPastDeadline = countdown.withDuration(5_000L)
        assertEquals(0L, shortenedPastDeadline.remainingMillis(20_000L))
    }

    @Test
    fun rejectsNegativeStartZeroDurationAndOverflow() {
        assertFailsWith<IllegalArgumentException> {
            RestCountdown(completedAtElapsedMillis = -1L, durationMillis = 1L)
        }
        assertFailsWith<IllegalArgumentException> {
            RestCountdown(completedAtElapsedMillis = 0L, durationMillis = 0L)
        }
        assertFailsWith<IllegalArgumentException> {
            RestCountdown(
                completedAtElapsedMillis = 0L,
                durationMillis = RestCountdown.MAX_DURATION_MILLIS + 1L
            )
        }
        assertFailsWith<IllegalArgumentException> {
            RestCountdown(completedAtElapsedMillis = Long.MAX_VALUE, durationMillis = 1L)
        }
        assertFailsWith<IllegalArgumentException> {
            RestCountdown(completedAtElapsedMillis = 0L, durationMillis = 1L).remainingMillis(-1L)
        }
    }
}
