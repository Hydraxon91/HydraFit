package com.hydrafit.app.core.userdata.presentation

import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Verifies the platform adapter, not the parsing rules (those live in the common tests). The JVM
 * `DecimalFormatSymbols` is the same API the Android adapter uses, so this runs without Robolectric
 * and without adding a test dependency.
 */
class AndroidPresentationLocaleTest {

    private val previous: Locale = Locale.getDefault()

    @BeforeTest
    fun setUp() {
        Locale.setDefault(Locale.US)
    }

    @AfterTest
    fun tearDown() {
        Locale.setDefault(previous)
    }

    @Test
    fun thePlatformAdapterFollowsTheDefaultLocale() {
        assertEquals('.', currentPresentationNumberFormat().decimalSeparator)

        Locale.setDefault(Locale.GERMANY)
        assertEquals(',', currentPresentationNumberFormat().decimalSeparator)

        Locale.setDefault(Locale.FRANCE)
        assertEquals(',', currentPresentationNumberFormat().decimalSeparator)
    }

    @Test
    fun theAdapterValueParsesAndRendersThroughTheSharedRules() {
        Locale.setDefault(Locale.GERMANY)
        val format = currentPresentationNumberFormat()

        assertEquals(12.5, parseLocalizedDecimal("12,5", format))
        assertEquals("12,5", formatLocalizedDecimal(12.5, format))
    }
}
