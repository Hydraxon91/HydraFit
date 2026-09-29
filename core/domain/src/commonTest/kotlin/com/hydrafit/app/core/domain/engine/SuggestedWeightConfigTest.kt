package com.hydrafit.app.core.domain.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SuggestedWeightConfigTest {

    private val config = SuggestedWeightConfig()

    @Test
    fun followsTheNscaCurveDiscountedByTheRirBuffer() {
        // 6 reps -> NSCA 85% -> x 0.9 buffer = 76.5%
        assertEquals(0.765, config.intensityForReps(6), absoluteTolerance = 1e-9)
        // 3 reps -> NSCA 93% -> x 0.9 = 83.7%
        assertEquals(0.837, config.intensityForReps(3), absoluteTolerance = 1e-9)
        // 12 reps -> NSCA 70% -> x 0.9 = 63%
        assertEquals(0.63, config.intensityForReps(12), absoluteTolerance = 1e-9)
    }

    @Test
    fun fewerRepsAreHeavierThanMoreReps() {
        assertTrue(config.intensityForReps(3) > config.intensityForReps(8))
        assertTrue(config.intensityForReps(8) > config.intensityForReps(15))
    }

    @Test
    fun clampsRepsToTheCurvesBounds() {
        assertEquals(config.intensityForReps(1), config.intensityForReps(0))
        assertEquals(config.intensityForReps(20), config.intensityForReps(30))
    }

    @Test
    fun aZeroBufferUsesTheRawCurve() {
        val raw = SuggestedWeightConfig(rirBuffer = 0.0)

        assertEquals(0.85, raw.intensityForReps(6), absoluteTolerance = 1e-9)
    }
}
