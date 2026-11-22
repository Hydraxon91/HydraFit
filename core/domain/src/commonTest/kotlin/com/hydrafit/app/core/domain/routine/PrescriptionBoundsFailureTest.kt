package com.hydrafit.app.core.domain.routine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * The UI shows a localized sentence chosen by [RoutineTemplateException.reason], so every refusal
 * of an out-of-bounds slot has to carry the reason that matches the rule it broke.
 */
class PrescriptionBoundsFailureTest {
    private fun reasonOf(block: () -> Unit): RoutineTemplateFailure? =
        assertFailsWith<RoutineTemplateException> { block() }.reason

    @Test
    fun setsOutsideTheBoundsAreRefusedWithTheirReason() {
        assertEquals(
            RoutineTemplateFailure.SETS_OUT_OF_RANGE,
            reasonOf {
                PrescriptionBounds.validate(PrescriptionBounds.MAX_SETS + 1, 8, null, null)
            }
        )
    }

    @Test
    fun repsOutsideTheBoundsAreRefusedWithTheirReason() {
        assertEquals(
            RoutineTemplateFailure.REPS_OUT_OF_RANGE,
            reasonOf {
                PrescriptionBounds.validate(3, PrescriptionBounds.MAX_REPS + 1, null, null)
            }
        )
    }

    @Test
    fun aSlotWithNoMeasurementIsRefusedWithTheMeasurementReason() {
        assertEquals(
            RoutineTemplateFailure.SLOT_NEEDS_ONE_MEASUREMENT,
            reasonOf { PrescriptionBounds.validate(3, null, null, null) }
        )
    }

    @Test
    fun distanceAndForceOutsideTheBoundsAreRefusedWithTheirReasons() {
        assertEquals(
            RoutineTemplateFailure.DISTANCE_OUT_OF_RANGE,
            reasonOf {
                PrescriptionBounds.validate(
                    sets = 3,
                    reps = null,
                    durationSeconds = null,
                    weightKg = null,
                    distanceMeters = PrescriptionBounds.MAX_DISTANCE_METERS + 1
                )
            }
        )
        assertEquals(
            RoutineTemplateFailure.FORCE_OUT_OF_RANGE,
            reasonOf {
                PrescriptionBounds.validate(
                    sets = 3,
                    reps = null,
                    durationSeconds = null,
                    weightKg = null,
                    forceNewtons = PrescriptionBounds.MAX_FORCE_NEWTONS + 1.0
                )
            }
        )
    }

    @Test
    fun aNegativeWeightIsRefusedWithTheWeightReason() {
        assertEquals(
            RoutineTemplateFailure.WEIGHT_NOT_VALID,
            reasonOf { PrescriptionBounds.validate(3, 8, null, -1.0) }
        )
    }

    @Test
    fun aForceSlotCannotCarryALoad() {
        assertEquals(
            RoutineTemplateFailure.FORCE_CANNOT_CARRY_LOAD,
            reasonOf {
                PrescriptionBounds.validate(
                    sets = 3,
                    reps = null,
                    durationSeconds = null,
                    weightKg = 20.0,
                    forceNewtons = 100.0
                )
            }
        )
    }
}
