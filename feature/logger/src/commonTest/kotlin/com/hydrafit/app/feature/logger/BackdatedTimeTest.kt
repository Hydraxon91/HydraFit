package com.hydrafit.app.feature.logger

import com.hydrafit.app.core.domain.time.daysFromCivil
import com.hydrafit.app.core.domain.time.localEpochDay
import kotlin.test.Test
import kotlin.test.assertEquals

class BackdatedTimeTest {

    @Test
    fun combinesAPickedDateAndTimeAtZeroOffset() {
        val picked = pickedLocalDateTimeToEpochMillis(dateOf(2024, 1, 1), 13, 45, 0L)

        assertEquals(1_704_067_200_000L + 13 * HOUR + 45 * MINUTE, picked)
    }

    @Test
    fun appliesTheOffsetAndKeepsThePickedLocalDay() {
        val offsets = listOf(
            5 * HOUR + 30 * MINUTE,
            5 * HOUR + 45 * MINUTE,
            -(3 * HOUR + 30 * MINUTE)
        )
        offsets.forEach { offset ->
            val picked = pickedLocalDateTimeToEpochMillis(dateOf(2024, 1, 1), 8, 15, offset)

            assertEquals(daysFromCivil(2024, 1, 1), localEpochDay(picked, offset))
        }
    }

    @Test
    fun derivesTheLocalDateTimeParts() {
        val parts = localDateTimeParts(1_704_067_200_000L + 13 * HOUR + 45 * MINUTE, 0L)

        assertEquals(LocalDateTimeParts(2024, 1, 1, 13, 45), parts)
    }

    @Test
    fun startOfDayRoundTripsForAPickedDate() {
        val offset = 5 * HOUR + 30 * MINUTE
        val picked = pickedLocalDateTimeToEpochMillis(dateOf(2024, 1, 1), 20, 5, offset)

        assertEquals(dateOf(2024, 1, 1), localDateStartOfDayUtcMillis(picked, offset))
    }

    private fun dateOf(year: Int, month: Int, day: Int): Long =
        daysFromCivil(year, month, day) * MILLIS_PER_DAY

    private companion object {
        const val HOUR = 60L * 60L * 1000L
        const val MINUTE = 60L * 1000L
    }
}
