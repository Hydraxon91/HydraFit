package com.hydrafit.app

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AdaptiveLayoutTest {
    @Test
    fun navigationUsesAvailableWindowWidth() {
        assertFalse(usesNavigationRail(411.dp))
        assertFalse(usesNavigationRail(599.dp))
        assertTrue(usesNavigationRail(600.dp))
        assertTrue(usesNavigationRail(1280.dp))
    }
}
