package com.hydrafit.app.core.domain.time

enum class DayOfWeek {
    MONDAY,
    TUESDAY,
    WEDNESDAY,
    THURSDAY,
    FRIDAY,
    SATURDAY,
    SUNDAY
}

private const val MILLIS_PER_DAY = 24L * 60L * 60L * 1000L

/** Epoch day 0 (1970-01-01) was a Thursday, which is index 3 with Monday as 0. */
fun dayOfWeek(epochMillis: Long): DayOfWeek {
    val epochDay = floorDiv(epochMillis, MILLIS_PER_DAY)
    val index = floorMod(epochDay + 3L, 7L).toInt()
    return DayOfWeek.entries[index]
}

/** The weekday in the user's local time zone given its [utcOffsetMillis]. */
fun localDayOfWeek(epochMillis: Long, utcOffsetMillis: Long): DayOfWeek =
    dayOfWeek(epochMillis + utcOffsetMillis)

/** The local calendar-day index (epoch days shifted into local time). */
fun localEpochDay(epochMillis: Long, utcOffsetMillis: Long): Long =
    floorDiv(epochMillis + utcOffsetMillis, MILLIS_PER_DAY)

/** The weekday of a local calendar-day index (days since the Unix epoch). */
fun dayOfWeekForEpochDay(epochDay: Long): DayOfWeek = dayOfWeek(epochDay * MILLIS_PER_DAY)

private fun floorDiv(dividend: Long, divisor: Long): Long {
    val quotient = dividend / divisor
    val roundsTowardsZero = dividend % divisor != 0L && (dividend < 0) != (divisor < 0)
    return if (roundsTowardsZero) quotient - 1 else quotient
}

private fun floorMod(dividend: Long, divisor: Long): Long {
    val remainder = dividend % divisor
    val isNegative = remainder != 0L && (remainder < 0) != (divisor < 0)
    return if (isNegative) remainder + divisor else remainder
}
