package com.hydrafit.app.feature.logger

import com.hydrafit.app.core.domain.routine.ClusterKind
import com.hydrafit.app.core.domain.routine.ClusterPrescription
import com.hydrafit.app.core.domain.routine.ClusterSegmentTarget
import com.hydrafit.app.core.domain.unit.WeightUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class ClusterDraftStateTest {

    private fun dropSet(vararg loads: Double) = ClusterPrescription(
        kind = ClusterKind.DROP_SET,
        segments = loads.mapIndexed { index, load ->
            ClusterSegmentTarget(reps = 8 - index * 2, weightKg = load)
        }
    )

    private fun restPause(reps: List<Int>, load: Double) = ClusterPrescription(
        kind = ClusterKind.REST_PAUSE,
        segments = reps.map { ClusterSegmentTarget(reps = it, weightKg = load) },
        restSeconds = 20
    )

    @Test
    fun laterSegmentInputsAreSeededFromThePrescriptionWithoutRoundingTheLoad() {
        val inputs = dropSet(100.0, 82.25, 60.0).laterSegmentInputs(WeightUnit.KG)

        assertEquals(listOf("6", "4"), inputs.map { it.reps })
        assertEquals(listOf("82.25", "60"), inputs.map { it.weightInput })
    }

    @Test
    fun anUntouchedDraftUsesThePrescribedSegments() {
        val outcome = laterClusterSegments(dropSet(100.0, 80.0, 60.0), null, WeightUnit.KG)

        val segments = assertIs<ClusterSegments.Ready>(outcome).segments
        assertEquals(listOf(1, 2), segments.map { it.position })
        assertEquals(listOf(80.0, 60.0), segments.map { it.weightKg })
    }

    @Test
    fun aBlankSegmentFieldIsMissing() {
        val outcome = laterClusterSegments(
            dropSet(100.0, 80.0, 60.0),
            listOf(ClusterSegmentInput(reps = "6", weightInput = "80"), ClusterSegmentInput()),
            WeightUnit.KG
        )

        assertEquals(
            ClusterDraftError.MISSING_SEGMENT,
            assertIs<ClusterSegments.Refused>(outcome).error
        )
    }

    @Test
    fun aSegmentCountThatDoesNotMatchThePrescriptionIsMissing() {
        val outcome = laterClusterSegments(
            dropSet(100.0, 80.0, 60.0),
            listOf(ClusterSegmentInput(reps = "6", weightInput = "80")),
            WeightUnit.KG
        )

        assertEquals(
            ClusterDraftError.MISSING_SEGMENT,
            assertIs<ClusterSegments.Refused>(outcome).error
        )
    }

    @Test
    fun nonPositiveSegmentValuesAreRefused() {
        val zeroReps = laterClusterSegments(
            dropSet(100.0, 80.0),
            listOf(ClusterSegmentInput(reps = "0", weightInput = "80")),
            WeightUnit.KG
        )
        val zeroLoad = laterClusterSegments(
            dropSet(100.0, 80.0),
            listOf(ClusterSegmentInput(reps = "6", weightInput = "0")),
            WeightUnit.KG
        )

        assertEquals(
            ClusterDraftError.NON_POSITIVE_SEGMENT,
            assertIs<ClusterSegments.Refused>(zeroReps).error
        )
        assertEquals(
            ClusterDraftError.NON_POSITIVE_SEGMENT,
            assertIs<ClusterSegments.Refused>(zeroLoad).error
        )
    }

    @Test
    fun aDropSetRefusesALaterLoadThatDoesNotDecrease() {
        val prescription = dropSet(100.0, 80.0, 60.0)

        assertEquals(
            ClusterDraftError.INVALID_CLUSTER,
            clusterDraftRefusal(
                prescription = prescription,
                parentReps = 8,
                parentWeightKg = 100.0,
                parentRir = null,
                inputs = listOf(
                    ClusterSegmentInput(reps = "6", weightInput = "120"),
                    ClusterSegmentInput(reps = "4", weightInput = "60")
                ),
                weightUnit = WeightUnit.KG
            )
        )
        assertNull(
            clusterDraftRefusal(
                prescription = prescription,
                parentReps = 8,
                parentWeightKg = 100.0,
                parentRir = null,
                inputs = listOf(
                    ClusterSegmentInput(reps = "6", weightInput = "80"),
                    ClusterSegmentInput(reps = "4", weightInput = "60")
                ),
                weightUnit = WeightUnit.KG
            )
        )
    }

    @Test
    fun aRestPauseRefusesASegmentWithADifferentLoad() {
        val prescription = restPause(reps = listOf(8, 4, 2), load = 50.0)

        assertNull(
            clusterDraftRefusal(
                prescription = prescription,
                parentReps = 8,
                parentWeightKg = 50.0,
                parentRir = 2,
                inputs = null,
                weightUnit = WeightUnit.KG
            )
        )
        assertEquals(
            ClusterDraftError.INVALID_CLUSTER,
            clusterDraftRefusal(
                prescription = prescription,
                parentReps = 8,
                parentWeightKg = 50.0,
                parentRir = 2,
                inputs = listOf(
                    ClusterSegmentInput(reps = "4", weightInput = "45"),
                    ClusterSegmentInput(reps = "2", weightInput = "50")
                ),
                weightUnit = WeightUnit.KG
            )
        )
    }

    @Test
    fun anEditedLoadIsReadInTheEditorsOwnDisplayUnit() {
        val outcome = laterClusterSegments(
            dropSet(100.0, 80.0),
            listOf(ClusterSegmentInput(reps = "6", weightInput = "176.4")),
            WeightUnit.LB
        )

        val segments = assertIs<ClusterSegments.Ready>(outcome).segments
        assertEquals(WeightUnit.LB.displayToKilograms(176.4), segments.single().weightKg)
    }
}
