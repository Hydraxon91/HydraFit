package com.hydrafit.app.core.domain.time

private const val MILLIS_PER_DAY = 24L * 60L * 60L * 1000L

/** Formats an instant as an ISO-8601 calendar date (UTC) without pulling in a date library. */
fun isoDateUtc(epochMillis: Long): String {
    val (year, month, day) = civilFromDays(floorDiv(epochMillis, MILLIS_PER_DAY))
    return "$year-" + month.toString().padStart(2, '0') + "-" + day.toString().padStart(2, '0')
}

/** Days-since-epoch to civil date (Howard Hinnant's algorithm). */
private fun civilFromDays(daysSinceEpoch: Long): Triple<Int, Int, Int> {
    var z = daysSinceEpoch + 719468
    val era = (if (z >= 0) z else z - 146096) / 146097
    val dayOfEra = z - era * 146097
    val yearOfEra = (dayOfEra - dayOfEra / 1460 + dayOfEra / 36524 - dayOfEra / 146096) / 365
    val year = yearOfEra + era * 400
    val dayOfYear = dayOfEra - (365 * yearOfEra + yearOfEra / 4 - yearOfEra / 100)
    val monthPrime = (5 * dayOfYear + 2) / 153
    val day = dayOfYear - (153 * monthPrime + 2) / 5 + 1
    val month = if (monthPrime < 10) monthPrime + 3 else monthPrime - 9
    return Triple((if (month <= 2) year + 1 else year).toInt(), month.toInt(), day.toInt())
}

private fun floorDiv(dividend: Long, divisor: Long): Long {
    val quotient = dividend / divisor
    val roundsTowardsZero = dividend % divisor != 0L && (dividend < 0) != (divisor < 0)
    return if (roundsTowardsZero) quotient - 1 else quotient
}
