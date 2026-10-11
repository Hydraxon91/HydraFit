package com.hydrafit.app.core.domain.workout

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ShortRestCoachTest {
    private val completion = LiveRestCompletion("session", 1L, "squat", "boot", 1_000L, 120_000L)

    @Test
    fun requiresTwoConsecutiveShortIntervalsAndShowsOnlyOncePerExerciseSession() {
        val coach = ShortRestCoach()
        coach.completed(completion)
        assertFalse(start(coach))
        assertFalse(start(coach)) // Repeated start has no second completion to consume.
        coach.completed(completion.copy(completedAtElapsedMillis = 20_000L))
        assertFalse(start(coach, now = 30_000L))
        coach.completed(completion.copy(completedAtElapsedMillis = 40_000L))
        assertTrue(start(coach, now = 50_000L))
        coach.completed(completion.copy(completedAtElapsedMillis = 60_000L))
        assertFalse(start(coach, now = 70_000L))
    }

    @Test
    fun equalOrLongerRestAndInvalidatedTimingBreakTheStreak() {
        val coach = ShortRestCoach()
        coach.completed(completion)
        assertFalse(start(coach))
        coach.completed(completion)
        assertFalse(start(coach, now = 121_000L))
        coach.completed(completion)
        assertFalse(start(coach))
        coach.resetEligibility()
        coach.completed(completion)
        assertFalse(start(coach))
        coach.completed(completion)
        assertTrue(start(coach))
    }

    @Test
    fun contextChangesUnknownBootAndNegativeIntervalsAreIneligible() {
        for (invalid in listOf<(ShortRestCoach) -> Boolean>(
            { start(it, session = null) },
            { start(it, session = "other") },
            { start(it, occurrence = 2L) },
            { start(it, exercise = "bench") },
            { start(it, boot = null) },
            { start(it, boot = "new-boot") },
            { start(it, now = 999L) }
        )) {
            val coach = ShortRestCoach()
            coach.completed(completion)
            assertFalse(start(coach))
            coach.completed(completion)
            assertFalse(invalid(coach))
            coach.completed(completion)
            assertFalse(start(coach))
        }
    }

    @Test
    fun usesTheChosenDurationAtCompletionAndRecreationStartsWithoutEvidence() {
        val coach = ShortRestCoach()
        coach.completed(completion.copy(chosenRestMillis = 1_000L))
        assertFalse(start(coach))
        coach.completed(completion)
        assertFalse(start(coach))
        coach.completed(completion)
        assertTrue(start(coach))
        assertFalse(start(ShortRestCoach()))
    }

    private fun start(
        coach: ShortRestCoach,
        now: Long = 10_000L,
        session: String? = "session",
        occurrence: Long = 1L,
        exercise: String = "squat",
        boot: String? = "boot"
    ) = coach.started(session, occurrence, exercise, boot, now)
}
