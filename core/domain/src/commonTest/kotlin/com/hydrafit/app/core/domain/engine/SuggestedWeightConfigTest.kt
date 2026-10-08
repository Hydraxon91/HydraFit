package com.hydrafit.app.core.domain.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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

    @Test
    fun hasUsableEstimateUsesTheConfiguredBound() {
        assertTrue(config.hasUsableEstimate(1))
        assertTrue(config.hasUsableEstimate(config.maxRepsForEstimate))
        assertFalse(config.hasUsableEstimate(0))
        assertFalse(config.hasUsableEstimate(config.maxRepsForEstimate + 1))
        assertFalse(SuggestedWeightConfig(maxRepsForEstimate = 3).hasUsableEstimate(4))
    }

    @Test
    fun workingWeightForConvertsAndRoundsWithTheConfiguredPolicy() {
        // 126.667 x intensity(6) 0.765 = 96.9 -> nearest 2.5 = 97.5
        assertEquals(
            97.5,
            config.workingWeightFor(126.6666666667, reps = 6),
            absoluteTolerance = 1e-9
        )
        // Deload scale applies before rounding: 129.167 x 0.765 x 0.8 = 79.05 -> 80.0
        assertEquals(
            80.0,
            config.workingWeightFor(129.1666666667, reps = 6, intensityScale = 0.8),
            absoluteTolerance = 1e-9
        )
    }

    @Test
    fun workingWeightForRoundsToTheNearestIncrement() {
        val exact = SuggestedWeightConfig(rirBuffer = 0.0)
        // intensity(6) = 0.85; 100 x 0.85 = 85.0 -> already on the grid.
        assertEquals(85.0, exact.workingWeightFor(100.0, reps = 6), absoluteTolerance = 1e-9)
        // 102 x 0.85 = 86.7 -> nearest 2.5 = 87.5 (round half up at the halfway point).
        assertEquals(87.5, exact.workingWeightFor(102.0, reps = 6), absoluteTolerance = 1e-9)
    }
}
