package com.hydrafit.app.feature.onboarding

import kotlin.test.Test
import kotlin.test.assertEquals

class OnboardingExitGuardTest {

    /**
     * The first exit is the pop the graph callback performs; it moves the current entry to the route
     * beneath, so a repeat callback for the same destination must not pop that route too.
     */
    @Test
    fun theExitFiresOnceWhileItsEntryIsCurrentAndRepeatsDoNot() {
        val entry = Any()
        val underTheEntry = Any()
        var current: Any? = entry
        var exits = 0
        val guard = OnboardingExitGuard(
            entry = entry,
            currentEntry = { current },
            onExit = {
                exits++
                current = underTheEntry
            }
        )

        guard.exit()
        assertEquals(1, exits)

        guard.exit()
        assertEquals(1, exits)
    }

    /**
     * A callback for a destination that is no longer current - another instance of the same route,
     * or no destination at all - must not pop what is showing now.
     */
    @Test
    fun anEntryThatIsNoLongerCurrentNeverExits() {
        val entry = Any()
        var current: Any? = Any()
        var exits = 0
        val guard = OnboardingExitGuard(
            entry = entry,
            currentEntry = { current },
            onExit = { exits++ }
        )

        guard.exit()
        assertEquals(0, exits)

        current = null
        guard.exit()
        assertEquals(0, exits)
    }

    @Test
    fun aStaleCallStillLeavesTheExitForWhenItsEntryIsCurrentAgain() {
        val entry = Any()
        var current: Any? = Any()
        var exits = 0
        val guard = OnboardingExitGuard(
            entry = entry,
            currentEntry = { current },
            onExit = { exits++ }
        )

        guard.exit()
        assertEquals(0, exits)

        current = entry
        guard.exit()
        assertEquals(1, exits)

        guard.exit()
        assertEquals(1, exits)
    }
}
