package com.hydrafit.app.core.domain.fatigue

import kotlin.test.Test
import kotlin.test.assertEquals

class FatigueReplayTest {
    @Test
    fun ledgerReproducesApprovedPhaseBFigures() {
        val calculator = FatigueCalculator()
        val sets = FatigueReplayFixture.sets
        assertEquals(39, sets.count { !it.isWarmup })
        assertEquals(3, sets.count { it.isWarmup })
        assertEquals(
            0.825504,
            calculator.calculate(sets, FatigueReplayFixture.PEAK_MILLIS).getValue(MuscleGroup.BACK),
            0.0000005
        )
        assertEquals(
            0.652960,
            calculator.calculate(sets, FatigueReplayFixture.EVALUATION_MILLIS)
                .getValue(MuscleGroup.BACK),
            0.0000005
        )
    }
}
