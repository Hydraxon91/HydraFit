package com.hydrafit.app.core.domain.unit

import kotlin.test.Test
import kotlin.test.assertEquals

/** The metres/feet display unit: conversions round-trip and rendering never invents precision. */
class DistanceUnitTest {

    @Test
    fun metresDisplayWithoutConversion() {
        assertEquals(1500.0, DistanceUnit.METERS.metresToDisplay(1500))
        assertEquals(1500, DistanceUnit.METERS.displayToMetres(1500.0))
        assertEquals(1500, DistanceUnit.METERS.displayWhole(1500))
    }

    @Test
    fun feetConvertBothWaysWithinOneMetre() {
        val metres = 1000
        val feet = DistanceUnit.FEET.metresToDisplay(metres)
        assertEquals(3280.839895, feet)
        // A displayed value that carries no decimals round-trips to the same whole metre count.
        assertEquals(
            metres,
            DistanceUnit.FEET.displayToMetres(DistanceUnit.FEET.displayWhole(metres).toDouble())
        )
    }

    @Test
    fun aWholeFootEntryConvertsToWholeMetres() {
        // 100 ft is 30.48 m, which rounds to the nearest whole metre rather than truncating.
        assertEquals(30, DistanceUnit.FEET.displayToMetres(100.0))
        assertEquals(0, DistanceUnit.FEET.displayToMetres(0.0))
    }

    @Test
    fun formatDistanceStatesTheUnitForWholeMetres() {
        assertEquals("2500 m", formatDistance(2500, DistanceUnit.METERS))
        assertEquals("0 m", formatDistance(0, DistanceUnit.METERS))
    }

    @Test
    fun formatDistanceRendersFeetRoundedToWholeFeet() {
        assertEquals("3281 ft", formatDistance(1000, DistanceUnit.FEET))
        assertEquals("3 ft", formatDistance(1, DistanceUnit.FEET))
    }

    @Test
    fun formatDistanceAcceptsASummedLongTotal() {
        assertEquals("3000000000 m", formatDistance(3_000_000_000L, DistanceUnit.METERS))
        assertEquals(
            "9842519685 ft",
            formatDistance(3_000_000_000L, DistanceUnit.FEET)
        )
    }
}
