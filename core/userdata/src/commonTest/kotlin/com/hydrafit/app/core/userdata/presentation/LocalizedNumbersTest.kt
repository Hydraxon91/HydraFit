package com.hydrafit.app.core.userdata.presentation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LocalizedNumbersTest {

    private val comma = PresentationNumberFormat(',')
    private val dot = PresentationNumberFormat('.')

    // ── Parsing ───────────────────────────────────────────────────────────

    @Test
    fun bothTheLocaleSeparatorAndTheDotParseToTheSameValue() {
        assertEquals(12.5, parseLocalizedDecimal("12,5", comma))
        assertEquals(12.5, parseLocalizedDecimal("12.5", comma))
        assertEquals(12.5, parseLocalizedDecimal("12.5", dot))
    }

    @Test
    fun aCommaIsGroupingUnderADotLocaleAndIsRejected() {
        // "12,5" under en-US is ambiguous with a thousands separator, so it is refused, not guessed.
        assertNull(parseLocalizedDecimal("12,5", dot))
    }

    @Test
    fun signsAndLeadingSeparatorsAreAccepted() {
        assertEquals(-12.5, parseLocalizedDecimal("-12,5", comma))
        assertEquals(12.5, parseLocalizedDecimal("+12.5", comma))
        assertEquals(0.5, parseLocalizedDecimal(",5", comma))
        assertEquals(-0.5, parseLocalizedDecimal("-.5", comma))
    }

    @Test
    fun groupingSeparatorsAreRejectedRatherThanGuessed() {
        assertNull(parseLocalizedDecimal("1,234.5", comma))
        assertNull(parseLocalizedDecimal("1.234,5", dot))
        assertNull(parseLocalizedDecimal("1 234,5", comma))
        assertNull(parseLocalizedDecimal("1\u00A0234,5", comma))
        assertNull(parseLocalizedDecimal("1'234,5", comma))
        assertNull(parseLocalizedDecimal("1_234,5", comma))
    }

    @Test
    fun aSecondSeparatorIsRejected() {
        assertNull(parseLocalizedDecimal("12,5,5", comma))
        assertNull(parseLocalizedDecimal("12.5.5", comma))
        assertNull(parseLocalizedDecimal("12,5.5", comma))
    }

    @Test
    fun blankSignOnlyAndNonNumericTextProduceNoValue() {
        assertNull(parseLocalizedDecimal("", comma))
        assertNull(parseLocalizedDecimal("   ", comma))
        assertNull(parseLocalizedDecimal("-", comma))
        assertNull(parseLocalizedDecimal("+", comma))
        assertNull(parseLocalizedDecimal(".", comma))
        assertNull(parseLocalizedDecimal(",", comma))
        assertNull(parseLocalizedDecimal("abc", comma))
        assertNull(parseLocalizedDecimal("12,5 kg", comma))
    }

    @Test
    fun nonFiniteAndOverflowingTextProduceNoValue() {
        assertNull(parseLocalizedDecimal("NaN", comma))
        assertNull(parseLocalizedDecimal("Infinity", comma))
        assertNull(parseLocalizedDecimal("1e999", comma))
    }

    @Test
    fun trailingAndLeadingWhitespaceIsIgnored() {
        assertEquals(12.5, parseLocalizedDecimal("  12,5  ", comma))
    }

    @Test
    fun unicodeDecimalDigitsParseAsTheirNumericValue() {
        // Arabic-Indic digits with a comma separator, as a localised keyboard may emit.
        assertEquals(12.5, parseLocalizedDecimal("\u0661\u0662,5", comma))
    }

    // ── Display formatting ────────────────────────────────────────────────

    @Test
    fun displayUsesTheLocaleSeparatorAndDropsRedundantZeros() {
        assertEquals("12,5", formatLocalizedDecimal(12.5, comma))
        assertEquals("12.5", formatLocalizedDecimal(12.5, dot))
        assertEquals("12", formatLocalizedDecimal(12.0, comma))
        assertEquals("80", formatLocalizedDecimal(80.0, dot))
    }

    @Test
    fun displayRoundsOnceToTheRequestedPrecision() {
        assertEquals("247,4", formatLocalizedDecimal(247.35, comma))
        assertEquals("247,4", formatLocalizedDecimal(247.36, comma, maxDecimals = 1))
        assertEquals("247", formatLocalizedDecimal(247.4, comma, maxDecimals = 0))
        assertEquals("0,25", formatLocalizedDecimal(0.25, comma, maxDecimals = 2))
    }

    @Test
    fun displayHandlesVerySmallAndVeryLargeValuesWithoutExponentialOutput() {
        assertEquals("0,00001", formatLocalizedDecimal(1.0E-5, comma, maxDecimals = 5))
        val large = formatLocalizedDecimal(100000000000000000000.0, comma)
        assertEquals(false, large.contains("E"))
        assertEquals(false, large.contains("e"))
    }

    // ── Exact editable text ───────────────────────────────────────────────

    @Test
    fun exactTextRoundTripsThroughParsingWithoutDrifting() {
        listOf(247.35, 0.5, 80.0, -12.5, 0.1, 1234.5678).forEach { value ->
            val commaText = exactLocalizedDecimalText(value, comma)
            assertEquals(
                value,
                parseLocalizedDecimal(commaText, comma),
                "comma round trip for $value"
            )

            val dotText = exactLocalizedDecimalText(value, dot)
            assertEquals(value, parseLocalizedDecimal(dotText, dot), "dot round trip for $value")
        }
    }

    @Test
    fun exactTextNeverEmitsGroupingSeparators() {
        assertEquals("1234,5678", exactLocalizedDecimalText(1234.5678, comma))
    }

    @Test
    fun exactTextExpandsExponentialOutputIntoPlainDigits() {
        val text = exactLocalizedDecimalText(1.0E-5, comma)
        assertEquals(false, text.contains("E"))
        assertEquals(false, text.contains("e"))
        assertEquals(1.0E-5, parseLocalizedDecimal(text, comma))
    }

    @Test
    fun exactTextKeepsATrailingZeroOnlyWhenItCarriesMagnitude() {
        assertEquals("80", exactLocalizedDecimalText(80.0, comma))
        assertEquals("0,5", exactLocalizedDecimalText(0.5, comma))
    }

    // ── Format invariants ─────────────────────────────────────────────────

    @Test
    fun aDigitCannotBeTheDecimalSeparator() {
        kotlin.test.assertFailsWith<IllegalArgumentException> {
            PresentationNumberFormat('5')
        }
    }
}
