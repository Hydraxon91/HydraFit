package com.hydrafit.app.core.domain.equipment

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class LoadingAmountsTest {

    @Test
    fun parsesPlainAndFractionalDecimalsExactly() {
        assertEquals(0, parseNativeTicks("0"))
        assertEquals(100_000, parseNativeTicks("100"))
        assertEquals(100_500, parseNativeTicks("100.5"))
        assertEquals(100_050, parseNativeTicks("100.05"))
        assertEquals(100_001, parseNativeTicks("100.001"))
        assertEquals(1, parseNativeTicks("0.001"))
        assertEquals(500, parseNativeTicks(".5"))
        assertEquals(1_250, parseNativeTicks("1.25"))
        assertEquals(MAX_LOADING_TICKS, parseNativeTicks("2000"))
    }

    @Test
    fun rejectsMalformedOverpreciseAndNegativeAmounts() {
        val invalid = listOf(
            "",
            "abc",
            "-5",
            "-0.5",
            "1.2345",
            "1.",
            ". ",
            "1..2",
            "1e3",
            "NaN",
            "Infinity",
            " 1",
            "1 ",
            "1,5",
            "+1",
            "99999999999"
        )
        invalid.forEach { text ->
            assertFailsWith<IllegalArgumentException>(text) { parseNativeTicks(text) }
        }
    }

    @Test
    fun formatsWithoutTrailingZerosAtUpToThreeDecimals() {
        assertEquals("0", formatNativeTicks(0))
        assertEquals("100", formatNativeTicks(100_000))
        assertEquals("100.5", formatNativeTicks(100_500))
        assertEquals("100.05", formatNativeTicks(100_050))
        assertEquals("100.001", formatNativeTicks(100_001))
        assertEquals("0.001", formatNativeTicks(1))
        assertEquals("0.999", formatNativeTicks(999))
        assertEquals("2000", formatNativeTicks(MAX_LOADING_TICKS))
    }

    @Test
    fun parseAndFormatRoundTripExactly() {
        val samples = listOf(0, 1, 500, 999, 1_000, 1_250, 100_001, 100_050, 100_500, 123_456_789)
        samples.forEach { ticks ->
            assertEquals(ticks, parseNativeTicks(formatNativeTicks(ticks)), "ticks $ticks")
        }
    }

    @Test
    fun formattingNegativeTicksIsRejected() {
        assertFailsWith<IllegalArgumentException> { formatNativeTicks(-1) }
    }
}
