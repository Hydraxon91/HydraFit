package com.hydrafit.app.core.domain.engine

import kotlin.test.Test
import kotlin.test.assertEquals

class SplitResolverTest {

    @Test
    fun autoResolvesByTrainingFrequency() {
        assertEquals(SplitType.FULL_BODY, SplitResolver.resolveSplitType(SplitType.AUTO, 2))
        assertEquals(SplitType.FULL_BODY, SplitResolver.resolveSplitType(SplitType.AUTO, 3))
        assertEquals(SplitType.UPPER_LOWER, SplitResolver.resolveSplitType(SplitType.AUTO, 4))
        assertEquals(SplitType.PUSH_PULL_LEGS, SplitResolver.resolveSplitType(SplitType.AUTO, 5))
        assertEquals(SplitType.PUSH_PULL_LEGS, SplitResolver.resolveSplitType(SplitType.AUTO, 6))
    }

    @Test
    fun honorsAnExplicitPreference() {
        assertEquals(
            SplitType.UPPER_LOWER,
            SplitResolver.resolveSplitType(SplitType.UPPER_LOWER, 3)
        )
    }

    @Test
    fun cyclesTheSplitFociAcrossTheWeek() {
        assertEquals(
            listOf(SplitFocus.PUSH, SplitFocus.PULL, SplitFocus.LEGS),
            SplitResolver.focusSequence(SplitType.PUSH_PULL_LEGS, 3)
        )
        assertEquals(
            listOf(SplitFocus.PUSH, SplitFocus.PULL, SplitFocus.LEGS, SplitFocus.PUSH),
            SplitResolver.focusSequence(SplitType.PUSH_PULL_LEGS, 4)
        )
        assertEquals(
            listOf(SplitFocus.UPPER, SplitFocus.LOWER),
            SplitResolver.focusSequence(SplitType.UPPER_LOWER, 2)
        )
        assertEquals(
            List(3) { SplitFocus.FULL_BODY },
            SplitResolver.focusSequence(SplitType.FULL_BODY, 3)
        )
    }

    @Test
    fun autoFiveDaysTrainsLegsAsOftenAsPushAndPull() {
        val week = SplitResolver.focusSequence(SplitType.AUTO, 5)
        assertEquals(
            listOf(
                SplitFocus.UPPER,
                SplitFocus.LOWER,
                SplitFocus.PUSH,
                SplitFocus.PULL,
                SplitFocus.LEGS
            ),
            week
        )
        // Each lower-body session counts once; push and pull get one dedicated day plus an upper day.
        assertEquals(2, week.count { it == SplitFocus.LOWER || it == SplitFocus.LEGS })
        assertEquals(2, week.count { it == SplitFocus.UPPER || it == SplitFocus.PUSH })
    }

    @Test
    fun autoOtherFrequenciesAndExplicitFiveDaySplitsKeepTheirCycle() {
        assertEquals(
            listOf(SplitFocus.UPPER, SplitFocus.LOWER, SplitFocus.UPPER, SplitFocus.LOWER),
            SplitResolver.focusSequence(SplitType.AUTO, 4)
        )
        assertEquals(
            listOf(
                SplitFocus.PUSH,
                SplitFocus.PULL,
                SplitFocus.LEGS,
                SplitFocus.PUSH,
                SplitFocus.PULL
            ),
            SplitResolver.focusSequence(SplitType.PUSH_PULL_LEGS, 5)
        )
        assertEquals(6, SplitResolver.focusSequence(SplitType.AUTO, 6).size)
    }
}
