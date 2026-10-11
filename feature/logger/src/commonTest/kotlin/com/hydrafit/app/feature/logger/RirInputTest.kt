package com.hydrafit.app.feature.logger

import kotlin.test.Test
import kotlin.test.assertEquals

class RirInputTest {
    @Test
    fun quickPickReplacesAnotherValueAndTappingItAgainClearsIt() {
        assertEquals("2", nextRirQuickPickValue("7", 2))
        assertEquals("", nextRirQuickPickValue("2", 2))
    }

    @Test
    fun zeroQuickPickRemainsAnExplicitValue() {
        assertEquals("0", nextRirQuickPickValue("", 0))
        assertEquals("", nextRirQuickPickValue("0", 0))
    }
}
