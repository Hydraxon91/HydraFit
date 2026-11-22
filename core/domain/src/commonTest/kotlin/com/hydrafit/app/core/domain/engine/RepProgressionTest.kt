package com.hydrafit.app.core.domain.engine

import kotlin.test.Test
import kotlin.test.assertEquals

class RepProgressionTest {
    private val range = 8..12
    private val step = 2.5

    private fun last(reps: Int, outcome: ProgressionExposureOutcome, weight: Double? = 60.0) =
        RepProgressionHistory(reps = reps, weightKg = weight, outcome = outcome)

    @Test
    fun noHistoryOrATargetOutsideTheRangeStartsAtTheBottomWithoutAHeldLoad() {
        val expected = RepProgressionTarget(8, null, RepProgressionStep.FIRST)
        assertEquals(expected, RepProgression.next(null, range, step))
        assertEquals(
            expected,
            RepProgression.next(last(5, ProgressionExposureOutcome.SUCCESS), range, step)
        )
    }

    @Test
    fun aMetTargetBelowTheTopGainsARepAtTheSameLoad() {
        assertEquals(
            RepProgressionTarget(10, 60.0, RepProgressionStep.ADD_REP),
            RepProgression.next(last(9, ProgressionExposureOutcome.SUCCESS), range, step)
        )
    }

    @Test
    fun aMetTargetAtTheTopStepsTheLoadAndReturnsToTheBottom() {
        assertEquals(
            RepProgressionTarget(8, 62.5, RepProgressionStep.STEP_LOAD),
            RepProgression.next(last(12, ProgressionExposureOutcome.SUCCESS), range, step)
        )
    }

    @Test
    fun aMissAnUnjudgedSessionOrADeloadRepeatsTheLastTarget() {
        val repeat = RepProgressionTarget(10, 60.0, RepProgressionStep.REPEAT)
        assertEquals(
            repeat,
            RepProgression.next(last(10, ProgressionExposureOutcome.MISS), range, step)
        )
        assertEquals(
            repeat,
            RepProgression.next(last(10, ProgressionExposureOutcome.UNKNOWN), range, step)
        )
        assertEquals(
            repeat,
            RepProgression.next(
                last(10, ProgressionExposureOutcome.SUCCESS),
                range,
                step,
                holdForDeload = true
            )
        )
    }
}
