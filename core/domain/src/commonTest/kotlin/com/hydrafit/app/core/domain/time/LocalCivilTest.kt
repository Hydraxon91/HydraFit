package com.hydrafit.app.core.domain.time

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals

class LocalCivilTest {

    @Test
    fun epochCivilMidnightAtZeroOffsetIsZero() {
        assertEquals(0L, localCivilToEpochMillis(1970, 1, 1, 0, 0, 0L))
    }

    @Test
    fun appliesTheUtcOffset() {
        // 2024-01-01T00:00 local at +02:00 is 2023-12-31T22:00Z.
        assertEquals(
            1_704_067_200_000L - 2 * HOUR,
            localCivilToEpochMillis(2024, 1, 1, 0, 0, 2 * HOUR)
        )
    }

    @Test
    fun includesTheHoursAndMinutes() {
        val midnight = daysFromCivil(2024, 1, 1) * DAY
        assertEquals(
            midnight + 13 * HOUR + 45 * MINUTE,
            localCivilToEpochMillis(2024, 1, 1, 13, 45, 0L)
        )
    }

    @Test
    fun roundTripsTheLocalDayForHalfHourAndNegativeOffsets() {
        val dates = listOf(
            Triple(2024, 2, 29),
            Triple(2024, 1, 1),
            Triple(1969, 12, 31)
        )
        val offsets = listOf(
            5 * HOUR + 30 * MINUTE,
            5 * HOUR + 45 * MINUTE,
            -(3 * HOUR + 30 * MINUTE)
        )
        dates.forEach { (year, month, day) ->
            offsets.forEach { offset ->
                val millis = localCivilToEpochMillis(year, month, day, 12, 0, offset)
                assertEquals(daysFromCivil(year, month, day), localEpochDay(millis, offset))
            }
        }
    }

    @Test
    fun mapsTheLocalDayAcrossTheUtcBoundaryWithAFixedOffset() {
        // 00:30 local at +05:30 is the previous UTC day; localEpochDay still lands on the same day.
        val offset = 5 * HOUR + 30 * MINUTE
        val millis = localCivilToEpochMillis(2024, 1, 1, 0, 30, offset)

        assertEquals(daysFromCivil(2024, 1, 1), localEpochDay(millis, offset))
        assertNotEquals(daysFromCivil(2024, 1, 1), localEpochDay(millis, 0L))
    }

    @Test
    fun rejectsOutOfRangeTime() {
        assertFailsWith<IllegalArgumentException> { localCivilToEpochMillis(2024, 1, 1, 24, 0, 0L) }
        assertFailsWith<IllegalArgumentException> { localCivilToEpochMillis(2024, 1, 1, 0, 60, 0L) }
    }

    private companion object {
        const val DAY = 24L * 60L * 60L * 1000L
        const val HOUR = 60L * 60L * 1000L
        const val MINUTE = 60L * 1000L
    }
}
