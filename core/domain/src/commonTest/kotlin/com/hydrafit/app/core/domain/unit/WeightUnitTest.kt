package com.hydrafit.app.core.domain.unit

import kotlin.test.Test
import kotlin.test.assertEquals

class WeightUnitTest {

    @Test
    fun kilogramsRoundTripUnchanged() {
        assertEquals(100.0, WeightUnit.KG.kilogramsToDisplay(100.0))
        assertEquals(100.0, WeightUnit.KG.displayToKilograms(100.0))
    }

    @Test
    fun poundsConvertToAndFromKilograms() {
        assertEquals(220.462, WeightUnit.LB.kilogramsToDisplay(100.0), absoluteTolerance = 0.01)
        assertEquals(100.0, WeightUnit.LB.displayToKilograms(220.462), absoluteTolerance = 0.01)
    }

    @Test
    fun roundsDisplayValuesToTheUnitStep() {
        assertEquals(100.0, WeightUnit.KG.roundDisplay(101.2))
        assertEquals(220.0, WeightUnit.LB.roundDisplay(222.0))
    }

    @Test
    fun formatsWholeNumbersWithoutADecimal() {
        assertEquals("100", formatWeight(100.0))
        assertEquals("102.5", formatWeight(102.5))
        assertEquals("220.5", formatWeight(220.46))
    }
}
