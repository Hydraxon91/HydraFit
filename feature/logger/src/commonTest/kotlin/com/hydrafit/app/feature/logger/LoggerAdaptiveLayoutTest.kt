package com.hydrafit.app.feature.logger

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LoggerAdaptiveLayoutTest {
    @Test
    fun twoPaneRequiresBothWindowDimensions() {
        assertFalse(loggerUsesTwoPane(839.dp, 480.dp, true, true))
        assertFalse(loggerUsesTwoPane(840.dp, 479.dp, true, true))
        assertTrue(loggerUsesTwoPane(840.dp, 480.dp, true, true))
        assertTrue(loggerUsesTwoPane(1200.dp, 800.dp, true, true))
    }

    @Test
    fun aWideWindowDoesNotStartTrainingOrInventAWorkout() {
        assertFalse(loggerUsesTwoPane(1200.dp, 800.dp, false, true))
        assertFalse(loggerUsesTwoPane(1200.dp, 800.dp, true, false))
        assertFalse(loggerUsesTwoPane(1200.dp, 800.dp, false, false))
    }
}
