package com.hydrafit.app.feature.splitbuilder

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SplitBuilderSupersetTest {

    @Test
    fun pairingIsOffUntilTheUserOptsIn() {
        assertNull(plannerSupersetPreference(false, "0", "120"))
        assertEquals(0, plannerSupersetPreference(true, "0", "120")?.transitionSeconds)
        assertEquals(120, plannerSupersetPreference(true, "0", "120")?.pairRestSeconds)
    }

    @Test
    fun invalidOrOutOfRangeRestDisablesPairingInsteadOfGuessing() {
        listOf(
            "abc" to "120",
            "0" to "0",
            "-5" to "120",
            "0" to "99999",
            "" to "120",
            "0" to ""
        ).forEach { (transition, pairRest) ->
            assertNull(plannerSupersetPreference(true, transition, pairRest))
        }
    }

    @Test
    fun restFieldsKeepDigitsOnlyAndStayBounded() {
        assertEquals("0120", "abc0120xyz".filterDigits())
        assertEquals("12345", "123456".filterDigits())
    }
}
