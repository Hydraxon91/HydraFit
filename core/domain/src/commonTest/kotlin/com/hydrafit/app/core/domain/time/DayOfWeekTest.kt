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

    @Test
    fun localDayAppliesTheUtcOffset() {
        val monday = 1_704_067_200_000L // 2024-01-01T00:00Z, a Monday
        val lateMonday = monday + 23 * 60L * 60L * 1000L

        assertEquals(DayOfWeek.MONDAY, localDayOfWeek(lateMonday, 0L))
        assertEquals(DayOfWeek.TUESDAY, localDayOfWeek(lateMonday, 2 * 60L * 60L * 1000L))
    }

    @Test
    fun localEpochDayShiftsForAPositiveOffset() {
        val day = 1_704_067_200_000L
        val beforeMidnight = day - 1L

        assertEquals(day / MILLIS_PER_DAY - 1, localEpochDay(beforeMidnight, 0L))
        assertEquals(day / MILLIS_PER_DAY, localEpochDay(beforeMidnight, 2 * 60L * 60L * 1000L))
    }

    private companion object {
        const val MILLIS_PER_DAY = 24L * 60L * 60L * 1000L
    }
}
