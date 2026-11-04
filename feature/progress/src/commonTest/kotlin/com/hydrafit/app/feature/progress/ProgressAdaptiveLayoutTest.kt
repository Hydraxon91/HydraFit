package com.hydrafit.app.feature.progress

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProgressAdaptiveLayoutTest {

    @Test
    fun twoPaneNeedsTheFullWidthBoundary() {
        assertFalse(progressUsesTwoPane(availableWidth = 839.dp, availableHeight = 480.dp))
        assertTrue(progressUsesTwoPane(availableWidth = 840.dp, availableHeight = 480.dp))
        assertTrue(progressUsesTwoPane(availableWidth = 841.dp, availableHeight = 480.dp))
    }

    @Test
    fun twoPaneNeedsTheFullHeightBoundary() {
        assertFalse(progressUsesTwoPane(availableWidth = 840.dp, availableHeight = 479.dp))
        assertTrue(progressUsesTwoPane(availableWidth = 840.dp, availableHeight = 480.dp))
        assertTrue(progressUsesTwoPane(availableWidth = 840.dp, availableHeight = 481.dp))
    }

    @Test
    fun eitherDimensionBelowItsBoundaryKeepsTheCompactLayout() {
        assertFalse(progressUsesTwoPane(availableWidth = 839.dp, availableHeight = 479.dp))
        assertFalse(progressUsesTwoPane(availableWidth = 1200.dp, availableHeight = 479.dp))
        assertFalse(progressUsesTwoPane(availableWidth = 839.dp, availableHeight = 1000.dp))
        assertTrue(progressUsesTwoPane(availableWidth = 1200.dp, availableHeight = 1000.dp))
    }
}
