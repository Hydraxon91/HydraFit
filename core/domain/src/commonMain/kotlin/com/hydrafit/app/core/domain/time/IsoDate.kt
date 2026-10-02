package com.hydrafit.app.core.domain.time

private const val MILLIS_PER_DAY = 24L * 60L * 60L * 1000L

/** Formats an instant as an ISO-8601 calendar date (UTC) without pulling in a date library. */
fun isoDateUtc(epochMillis: Long): String {
    val (year, month, day) = civilFromDays(floorDiv(epochMillis, MILLIS_PER_DAY))
    return "$year-" + month.toString().padStart(2, '0') + "-" + day.toString().padStart(2, '0')
}

/** Days-since-epoch to civil date (Howard Hinnant's algorithm), the inverse of [daysFromCivil]. */
fun civilFromDays(daysSinceEpoch: Long): Triple<Int, Int, Int> {
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

/**
 * Civil date to days-since-epoch (Howard Hinnant's algorithm), the inverse of [civilFromDays].
 *
 * [month] must be 1..12 and [day] 1..31; an impossible day for its month (e.g. 2024-02-30) is not
 * rejected and normalizes forward.
 */
fun daysFromCivil(year: Int, month: Int, day: Int): Long {
    require(month in 1..12) { "month must be in 1..12, was $month" }
    require(day in 1..31) { "day must be in 1..31, was $day" }
    val y = if (month <= 2) year - 1 else year
    val era = (if (y >= 0) y else y - 399) / 400
    val yearOfEra = y - era * 400
    val dayOfYear = (153 * (if (month > 2) month - 3 else month + 9) + 2) / 5 + day - 1
    val dayOfEra = yearOfEra * 365 + yearOfEra / 4 - yearOfEra / 100 + dayOfYear
    return era.toLong() * 146_097L + dayOfEra.toLong() - 719_468L
}

private fun floorDiv(dividend: Long, divisor: Long): Long {
    val quotient = dividend / divisor
    val roundsTowardsZero = dividend % divisor != 0L && (dividend < 0) != (divisor < 0)
    return if (roundsTowardsZero) quotient - 1 else quotient
}
