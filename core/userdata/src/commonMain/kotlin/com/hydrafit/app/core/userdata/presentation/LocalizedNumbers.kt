package com.hydrafit.app.core.userdata.presentation

import kotlin.math.pow
import kotlin.math.round

/**
 * The decimal separator a presentation locale expects in human-facing numbers.
 *
 * Only the separator is locale-specific here. Rendered output always uses ASCII digits, while
 * parsing accepts any Unicode decimal digit through [Char.digitToIntOrNull], so text pasted from a
 * localised keyboard still parses. Grouping separators are never interpreted or emitted: a
 * thousands separator is rejected rather than guessed, because `1.234` means one-and-a-bit under
 * `de-DE` and one thousand two hundred thirty-four under `en-US`.
 */
data class PresentationNumberFormat(val decimalSeparator: Char) {
    init {
        require(!decimalSeparator.isDigit()) {
            "the decimal separator must not be a digit: $decimalSeparator"
        }
    }

    /** True when [character] is the locale separator or the always-accepted ASCII dot. */
    fun isDecimalSeparator(character: Char): Boolean =
        character == decimalSeparator || character == '.'
}

/**
 * Parses human-entered decimal text, or returns null when the text is not a single finite number.
 *
 * Accepted: an optional leading sign, Unicode decimal digits, the locale decimal separator and the
 * ASCII dot. Rejected: grouping separators (including spaces and apostrophes), letters, a second
 * separator (`1,2,3` and `1.2.3`), a sign-only value and non-finite results. Both `12,5` and `12.5`
 * therefore mean 12.5 under a comma locale; nothing is rounded or unit-converted here, and callers
 * keep the existing range and capability validation for the field they are parsing.
 */
fun parseLocalizedDecimal(text: String, format: PresentationNumberFormat): Double? {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) return null

    var index = 0
    val negative = when (trimmed[0]) {
        '-' -> {
            index = 1
            true
        }
        '+' -> {
            index = 1
            false
        }
        else -> false
    }

    val digits = StringBuilder(trimmed.length)
    var separators = 0
    while (index < trimmed.length) {
        val character = trimmed[index]
        if (format.isDecimalSeparator(character)) {
            if (separators > 0) return null
            separators++
            digits.append('.')
        } else {
            val digit = character.digitToIntOrNull() ?: return null
            digits.append(digit)
        }
        index++
    }

    if (digits.isEmpty() || digits.all { it == '.' }) return null
    val value = digits.toString().toDoubleOrNull() ?: return null
    if (!value.isFinite()) return null
    return if (negative) -value else value
}

/**
 * Renders [value] for display with at most [maxDecimals] decimal places and the locale separator.
 *
 * Rounding happens once, here, on a copy; the caller's canonical value is not modified. Trailing
 * fractional zeros are dropped, so a whole number renders as `12` rather than `12.0`. This is a
 * display formatter, not an entry prefill: use [exactLocalizedDecimalText] when the text will be
 * read back into an editable field.
 */
fun formatLocalizedDecimal(
    value: Double,
    format: PresentationNumberFormat,
    maxDecimals: Int = 1
): String {
    require(maxDecimals >= 0) { "maxDecimals must not be negative: $maxDecimals" }
    if (!value.isFinite()) return value.toString()

    val scale = 10.0.pow(maxDecimals)
    val rounded = if (maxDecimals == 0) round(value) else round(value * scale) / scale
    val plain = expandExponent(rounded.toString())
    return localizeDecimalPoint(plain, format)
}

/**
 * Renders [value] as editable text that parses back to the *same* double under [format].
 *
 * A one-decimal display formatter would rewrite an untouched `247.35` as `247.4`, so an editor
 * prefill uses this instead: the shortest round-tripping representation, with exponential output
 * expanded to plain digits because the entry fields accept digits and a separator only.
 */
fun exactLocalizedDecimalText(value: Double, format: PresentationNumberFormat): String {
    if (!value.isFinite()) return value.toString()
    return localizeDecimalPoint(expandExponent(value.toString()), format)
}

/** Replaces the ASCII decimal point with the locale separator, leaving integers untouched. */
private fun localizeDecimalPoint(text: String, format: PresentationNumberFormat): String =
    if (format.decimalSeparator == '.') text else text.replace('.', format.decimalSeparator)

/**
 * Expands `1.0E-5`-style output into plain decimal digits.
 *
 * Trailing zeros are only redundant after a decimal point; stripping them from a plain integer
 * would change its magnitude.
 */
private fun expandExponent(text: String): String {
    if ('E' !in text && 'e' !in text) return text.removeSuffix(".0")
    val marker = text.indexOfFirst { it == 'E' || it == 'e' }
    val mantissa = text.substring(0, marker)
    val exponent = text.substring(marker + 1).toInt()
    val negative = mantissa.startsWith("-")
    val unsigned = mantissa.removePrefix("-")
    val dot = unsigned.indexOf('.')
    val digits = unsigned.replace(".", "")
    val integerDigits = if (dot < 0) unsigned.length else dot
    val point = integerDigits + exponent
    val raw = when {
        point <= 0 -> "0." + "0".repeat(-point) + digits
        point >= digits.length -> digits + "0".repeat(point - digits.length)
        else -> digits.substring(0, point) + "." + digits.substring(point)
    }
    val expanded = if ('.' in raw) raw.trimEnd('0').trimEnd('.') else raw
    return if (negative) "-$expanded" else expanded
}

/** The number format for the app's current presentation locale. */
expect fun currentPresentationNumberFormat(): PresentationNumberFormat
