package com.hydrafit.app.core.domain.equipment

/*
 * Parses and formats native equipment amounts without binary-floating-point rounding.
 *
 * Amounts are integer ticks: LOADING_SCALE thousandths of the owning inventory unit. These
 * helpers are unit-agnostic; they never convert between KG and LB and never reinterpret the
 * stored unit. A parsing/formatting round trip is exact for any value with up to three decimal
 * places, so "12.005" stays 12.005 instead of collapsing to the one-decimal display form used
 * elsewhere. Range checks against 0..MAX_LOADING_TICKS belong to the calculators.
 */

private const val MAX_FRACTION_DIGITS = 3
private val FRACTION_MULTIPLIERS = intArrayOf(1, 10, 100, 1_000)
private const val MAX_WHOLE_TICKS_LONG = (Int.MAX_VALUE - (LOADING_SCALE - 1)) / LOADING_SCALE

/**
 * Parses a plain non-negative decimal into ticks.
 *
 * Accepts ASCII digits with at most one `.` and at most three fractional digits, e.g. `"80"`,
 * `"80.5"`, `"80.025"` and `".5"`. Rejects an empty string, a negative sign, a missing or
 * malformed fraction, more than three decimal places (overprecision is never truncated), any
 * exponent/grouping/whitespace character and a value that cannot fit in an `Int` tick count.
 */
fun parseNativeTicks(text: String): Int {
    require(text.isNotEmpty()) { "Native amount must not be empty" }
    var whole = 0L
    var fraction = 0L
    var fractionDigits = 0
    var hasSeparator = false
    var hasDigit = false
    for (char in text) {
        when {
            char == '-' -> throw IllegalArgumentException(
                "Native amount '$text' must not be negative"
            )
            char == '.' -> {
                require(!hasSeparator) {
                    "Native amount '$text' has more than one decimal separator"
                }
                hasSeparator = true
            }
            char in '0'..'9' -> {
                hasDigit = true
                val digit = char - '0'
                if (hasSeparator) {
                    fractionDigits++
                    require(fractionDigits <= MAX_FRACTION_DIGITS) {
                        "Native amount '$text' has more than $MAX_FRACTION_DIGITS decimal places"
                    }
                    fraction = fraction * 10 + digit
                } else {
                    whole = whole * 10 + digit
                    require(whole <= MAX_WHOLE_TICKS_LONG) {
                        "Native amount '$text' is out of range"
                    }
                }
            }
            else -> throw IllegalArgumentException(
                "Native amount '$text' contains an invalid character"
            )
        }
    }
    require(hasDigit) { "Native amount '$text' has no digits" }
    require(!hasSeparator || fractionDigits > 0) {
        "Native amount '$text' has no digits after the decimal separator"
    }
    val fractionTicks = fraction * FRACTION_MULTIPLIERS[MAX_FRACTION_DIGITS - fractionDigits]
    return (whole * LOADING_SCALE + fractionTicks).toInt()
}

/**
 * Formats non-negative ticks as a native decimal with no trailing zeros and at most three
 * decimal places, the exact inverse of [parseNativeTicks]. Unlike `formatWeight`, this keeps
 * third-decimal precision instead of rounding to one decimal.
 */
fun formatNativeTicks(ticks: Int): String {
    require(ticks >= 0) { "Native amount ticks must not be negative" }
    val whole = ticks / LOADING_SCALE
    val fraction = ticks % LOADING_SCALE
    if (fraction == 0) return whole.toString()
    return "$whole." + fraction.toString().padStart(MAX_FRACTION_DIGITS, '0').trimEnd('0')
}
