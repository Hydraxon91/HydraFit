package com.hydrafit.app.core.domain.routine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ClusterPrescriptionTest {

    private fun dropSet(
        loads: List<Double> = listOf(100.0, 80.0, 60.0),
        reps: List<Int> = listOf(8, 8, 8),
        restSeconds: Int? = 0
    ) = ClusterPrescription(
        kind = ClusterKind.DROP_SET,
        segments = loads.indices.map { ClusterSegmentTarget(reps[it], loads[it]) },
        restSeconds = restSeconds
    )

    private fun restPause(
        loads: List<Double> = listOf(100.0, 100.0, 100.0),
        reps: List<Int> = listOf(6, 3, 3),
        restSeconds: Int? = 20
    ) = ClusterPrescription(
        kind = ClusterKind.REST_PAUSE,
        segments = loads.indices.map { ClusterSegmentTarget(reps[it], loads[it]) },
        restSeconds = restSeconds
    )

    @Test
    fun aDropSetNeedsAStrictlyDecreasingLoad() {
        dropSet().requireValid()

        val flat = assertFailsWith<IllegalArgumentException> {
            dropSet(loads = listOf(100.0, 100.0)).requireValid()
        }
        assertEquals(
            "A drop set needs a strictly decreasing load across its segments",
            flat.message
        )

        assertFailsWith<IllegalArgumentException> {
            dropSet(loads = listOf(100.0, 120.0)).requireValid()
        }
    }

    @Test
    fun aRestPauseClusterRepeatsOneLoadAndNeedsItsRest() {
        restPause().requireValid()

        assertFailsWith<IllegalArgumentException> {
            restPause(loads = listOf(100.0, 90.0)).requireValid()
        }
        assertFailsWith<IllegalArgumentException> {
            restPause(restSeconds = null).requireValid()
        }
        assertFailsWith<IllegalArgumentException> {
            restPause(restSeconds = 0).requireValid()
        }
    }

    @Test
    fun aClusterIsBoundedToItsSupportedSegmentRange() {
        assertFailsWith<IllegalArgumentException> {
            dropSet(loads = listOf(100.0), reps = listOf(8)).requireValid()
        }
        val tooMany = ClusterPrescription(
            kind = ClusterKind.DROP_SET,
            segments = (1..ClusterPrescription.MAX_SEGMENTS + 1).map {
                ClusterSegmentTarget(5, 200.0 - it * 10.0)
            }
        )
        assertFailsWith<IllegalArgumentException> { tooMany.requireValid() }
    }

    @Test
    fun segmentsRejectNonPositiveValuesAndOutOfRangeEffort() {
        assertFailsWith<IllegalArgumentException> {
            ClusterSegmentTarget(0, 100.0).requireValid()
        }
        assertFailsWith<IllegalArgumentException> {
            ClusterSegmentTarget(8, 0.0).requireValid()
        }
        assertFailsWith<IllegalArgumentException> {
            ClusterSegmentTarget(8, Double.NaN).requireValid()
        }
        assertFailsWith<IllegalArgumentException> {
            ClusterSegmentTarget(8, 100.0, rir = 5).requireValid()
        }
        ClusterSegmentTarget(8, 100.0, rir = 4).requireValid()
    }

    @Test
    fun theOpeningSegmentAndTotalsDescribeThePrescription() {
        val prescription = restPause(reps = listOf(6, 3, 3))

        assertEquals(ClusterSegmentTarget(6, 100.0), prescription.openingSegment)
        assertEquals(12, prescription.totalReps)
    }

    @Test
    fun theStoredEncodingRoundTripsAndRejectsACorruptValue() {
        val prescription = restPause()

        assertEquals(
            prescription,
            ClusterPrescriptionJson.decode(ClusterPrescriptionJson.encode(prescription))
        )

        assertFailsWith<IllegalArgumentException> {
            ClusterPrescriptionJson.decode(
                """{"kind":"REST_PAUSE","segments":[{"reps":6,"weightKg":100.0}],"restSeconds":null}"""
            )
        }
    }

    @Test
    fun aDecodedPrescriptionKeepsItsKindAndOrder() {
        val prescription = ClusterPrescriptionJson.decode(
            ClusterPrescriptionJson.encode(dropSet(loads = listOf(120.0, 90.0)))
        )

        assertEquals(ClusterKind.DROP_SET, prescription.kind)
        assertTrue(prescription.segments.map { it.weightKg } == listOf(120.0, 90.0))
    }
}
