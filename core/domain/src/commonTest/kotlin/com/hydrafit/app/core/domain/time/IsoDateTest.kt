package com.hydrafit.app.core.domain.time

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class IsoDateTest {

    @Test
    fun formatsTheEpoch() {
        assertEquals("1970-01-01", isoDateUtc(0L))
    }

    @Test
    fun formatsALaterDate() {
        // 2024-01-01T00:00:00Z
        assertEquals("2024-01-01", isoDateUtc(1_704_067_200_000L))
    }

    @Test
    fun formatsADateLateInAYear() {
        // 1999-12-31T00:00:00Z
        assertEquals("1999-12-31", isoDateUtc(946_598_400_000L))
    }

    @Test
    fun daysFromCivilMatchesKnownEpochDays() {
        assertEquals(0L, daysFromCivil(1970, 1, 1))
        assertEquals(-1L, daysFromCivil(1969, 12, 31))
        assertEquals(19_723L, daysFromCivil(2024, 1, 1))
        assertEquals(19_782L, daysFromCivil(2024, 2, 29))
    }

    @Test
    fun daysFromCivilRoundTripsThroughIsoDateUtc() {
        val dates = listOf(
            Triple(1970, 1, 1),
            Triple(2024, 1, 1),
            Triple(2024, 2, 29),
            Triple(1999, 12, 31),
            Triple(1969, 12, 31)
        )
        dates.forEach { (year, month, day) ->
            val formatted = "$year-" + month.toString().padStart(2, '0') +
                "-" + day.toString().padStart(2, '0')
            assertEquals(formatted, isoDateUtc(daysFromCivil(year, month, day) * MILLIS_PER_DAY))
        }
    }

    @Test
    fun civilFromDaysRoundTripsThroughDaysFromCivil() {
        val dates = listOf(
            Triple(1970, 1, 1),
            Triple(2024, 1, 1),
            Triple(2024, 2, 29),
            Triple(1999, 12, 31),
            Triple(1969, 12, 31)
        )
        dates.forEach { (year, month, day) ->
            assertEquals(Triple(year, month, day), civilFromDays(daysFromCivil(year, month, day)))
        }
    }

    @Test
    fun daysFromCivilRejectsAnOutOfRangeMonth() {
        assertFailsWith<IllegalArgumentException> { daysFromCivil(2024, 0, 1) }
        assertFailsWith<IllegalArgumentException> { daysFromCivil(2024, 13, 1) }
    }

    @Test
    fun daysFromCivilRejectsAnOutOfRangeDay() {
        assertFailsWith<IllegalArgumentException> { daysFromCivil(2024, 1, 0) }
        assertFailsWith<IllegalArgumentException> { daysFromCivil(2024, 1, 32) }
    }

    @Test
    fun formatsLocalCivilDateTime() {
        val base = daysFromCivil(2026, 10, 7) * MILLIS_PER_DAY
        val instant = base + 16L * 60L * 60L * 1000L + 9L * 60L * 1000L
        assertEquals("2026-10-07 16:09", isoLocalDateTime(instant, 0L))
        // A +02:00 offset moves the same instant to 18:09 local.
        assertEquals("2026-10-07 18:09", isoLocalDateTime(instant, 2L * 60L * 60L * 1000L))
    }

    @Test
    fun padsSingleDigitHourAndMinute() {
        val base = daysFromCivil(2026, 10, 7) * MILLIS_PER_DAY
        val instant = base + 3L * 60L * 60L * 1000L + 5L * 60L * 1000L
        assertEquals("2026-10-07 03:05", isoLocalDateTime(instant, 0L))
    }

    private companion object {
        const val MILLIS_PER_DAY = 24L * 60L * 60L * 1000L
    }
}
