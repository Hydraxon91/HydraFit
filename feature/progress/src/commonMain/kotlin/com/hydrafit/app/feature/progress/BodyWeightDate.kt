package com.hydrafit.app.feature.progress

import com.hydrafit.app.core.domain.time.daysFromCivil

private val BODY_WEIGHT_DATE = Regex("""(\d{4})-(\d{2})-(\d{2})""")

/**
 * Parses a `YYYY-MM-DD` civil date into a local epoch day, or returns null when the text is not that
 * exact shape or names an impossible day.
 *
 * The days-from-civil conversion is the shared `:core:domain` algorithm; this helper only adds the
 * strict zero-padded shape and a month-specific day bound, so `2024-02-30` is rejected instead of
 * being normalized forward into March.
 */
internal fun parseBodyWeightDate(text: String): Long? {
    val match = BODY_WEIGHT_DATE.matchEntire(text) ?: return null
    val year = match.groupValues[1].toInt()
    val month = match.groupValues[2].toInt()
    val day = match.groupValues[3].toInt()
    if (month !in 1..12) return null
    if (day !in 1..daysInMonth(year, month)) return null
    return daysFromCivil(year, month, day)
}

/** The number of days in [month] of [year]; [month] must be 1..12. */
private fun daysInMonth(year: Int, month: Int): Int = when (month) {
    1, 3, 5, 7, 8, 10, 12 -> 31
    4, 6, 9, 11 -> 30
    else -> if (isLeapYear(year)) 29 else 28
}

private fun isLeapYear(year: Int): Boolean = year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)
