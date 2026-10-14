package com.hydrafit.app.core.domain.setup

import com.hydrafit.app.core.domain.time.DayOfWeek
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SetupStateTest {
    @Test
    fun freshStateStartsAtWelcomeAndRejectsUnsupportedVersionOrRevision() {
        assertEquals(SetupDisposition.PENDING, SetupState().disposition)
        assertEquals(SetupStep.WELCOME, SetupState().step)
        assertFailsWith<IllegalArgumentException> { SetupState(version = 2) }
        assertFailsWith<IllegalArgumentException> { SetupState(revision = -1L) }
    }

    @Test
    fun weekdayPreferencesAreUndecidedOrTwoThroughSixDays() {
        requirePreferredWeekdays(null)
        (2..6).forEach { requirePreferredWeekdays(DayOfWeek.entries.take(it).toSet()) }
        listOf(0, 1, 7).forEach { count ->
            assertFailsWith<IllegalArgumentException> {
                requirePreferredWeekdays(DayOfWeek.entries.take(count).toSet())
            }
        }
    }
}
