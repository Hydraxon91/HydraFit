package com.hydrafit.app.core.domain.time

import kotlin.test.Test
import kotlin.test.assertEquals

class DayOfWeekTest {

    @Test
    fun reportsEpochStartAsThursday() {
        assertEquals(DayOfWeek.THURSDAY, dayOfWeek(0L))
    }

    @Test
    fun reportsKnownDates() {
        assertEquals(DayOfWeek.MONDAY, dayOfWeek(1_704_067_200_000L))
        assertEquals(DayOfWeek.SUNDAY, dayOfWeek(1_704_585_600_000L))
    }

    @Test
    fun rollsOverToMondayAfterTheWeekEnds() {
        assertEquals(DayOfWeek.MONDAY, dayOfWeek(1_704_672_000_000L))
    }

    @Test
    fun handlesDaysBeforeTheEpoch() {
        assertEquals(DayOfWeek.WEDNESDAY, dayOfWeek(-1L))
    }

    @Test
    fun advancesOneDayAtATime() {
        val monday = 4L * MILLIS_PER_DAY
        DayOfWeek.entries.forEachIndexed { index, expected ->
            assertEquals(expected, dayOfWeek(monday + index * MILLIS_PER_DAY))
        }
    }

    private companion object {
        const val MILLIS_PER_DAY = 24L * 60L * 60L * 1000L
    }
}
