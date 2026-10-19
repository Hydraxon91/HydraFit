package com.hydrafit.app.core.domain.engine

import com.hydrafit.app.core.domain.workout.LoadKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class SlotSetTargetsTest {

    @Test
    fun anOrderedPrescriptionRoundTripsThroughItsStrictEncoding() {
        val targets = SlotSetTargets.of(
            listOf(
                SetTarget(reps = 10, loadKind = LoadKind.EXTERNAL, weightKg = 60.0, targetRir = 3),
                SetTarget(reps = 8, loadKind = LoadKind.EXTERNAL, weightKg = 70.0, targetRir = 2),
                SetTarget(reps = 6, loadKind = LoadKind.EXTERNAL, weightKg = 80.0, targetRir = 1)
            )
        )

        val decoded = SlotSetTargetsJson.decode(SlotSetTargetsJson.encode(targets))

        assertEquals(targets, decoded)
        assertEquals(3, targets.sets)
    }

    @Test
    fun aRecipeSnapshotRoundTripsWithItsTargets() {
        val targets = SlotSetTargets.of(
            targets = listOf(SetTarget(reps = 8, loadKind = LoadKind.EXTERNAL, weightKg = 60.0)),
            recipe = SetPrescriptionRecipeSnapshot(
                shape = SetPrescriptionShape.STRAIGHT,
                sets = 1,
                startReps = 8,
                endReps = 8,
                startLoadKg = 60.0,
                endLoadKg = 60.0,
                roundToKg = 2.5
            )
        )

        assertEquals(targets, SlotSetTargetsJson.decode(SlotSetTargetsJson.encode(targets)))
    }

    @Test
    fun theEquivalentRpePresentsTheSameTargetRatherThanASecondValue() {
        assertEquals(7, SetTarget(reps = 8, weightKg = 60.0, targetRir = 3).equivalentRpe)
        assertEquals(10, SetTarget(reps = 8, weightKg = 60.0, targetRir = 0).equivalentRpe)
        assertEquals(6, SetTarget(reps = 8, weightKg = 60.0, targetRir = 4).equivalentRpe)
        assertNull(SetTarget(reps = 8, weightKg = 60.0).equivalentRpe)
    }

    @Test
    fun aPrescribedRirOutsideTheSupportedRangeIsRejected() {
        assertFailsWith<IllegalArgumentException> {
            SetTarget(reps = 8, weightKg = 60.0, targetRir = 5).requireValid()
        }
        assertFailsWith<IllegalArgumentException> {
            SetTarget(reps = 8, weightKg = 60.0, targetRir = -1).requireValid()
        }
    }

    @Test
    fun anOrderedPrescriptionNeedsBetweenOneAndTheSlotMaximum() {
        assertFailsWith<IllegalArgumentException> {
            SlotSetTargets(targets = emptyList()).requireValid()
        }
        assertFailsWith<IllegalArgumentException> {
            SlotSetTargets(
                targets = List(9) {
                    SetTarget(reps = 8, weightKg = 60.0)
                }
            ).requireValid()
        }
    }

    @Test
    fun aSetNeedsExactlyOnePositiveMeasure() {
        assertFailsWith<IllegalArgumentException> {
            SetTarget(reps = null, durationSeconds = null, weightKg = 60.0).requireValid()
        }
        assertFailsWith<IllegalArgumentException> {
            SetTarget(reps = 8, durationSeconds = 30, weightKg = 60.0).requireValid()
        }
    }

    @Test
    fun corruptStoredTextFailsClosedInsteadOfBecomingAnApprovedPlan() {
        assertFailsWith<Exception> { SlotSetTargetsJson.decode("not json") }
        assertFailsWith<Exception> { SlotSetTargetsJson.decode("""{"version":2,"targets":[]}""") }
    }

    @Test
    fun indexingPastThePrescriptionReturnsNullRatherThanGuessing() {
        val targets = SlotSetTargets.of(listOf(SetTarget(reps = 8, weightKg = 60.0)))

        assertEquals(8, targets[0]?.reps)
        assertNull(targets[1])
    }
}
