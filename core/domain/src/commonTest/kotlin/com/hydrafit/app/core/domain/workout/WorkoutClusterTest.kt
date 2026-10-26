package com.hydrafit.app.core.domain.workout

import com.hydrafit.app.core.domain.routine.ClusterKind
import com.hydrafit.app.core.domain.routine.ClusterPrescription
import com.hydrafit.app.core.domain.routine.ClusterSegmentTarget
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WorkoutClusterTest {

    private fun parent(reps: Int = 8, weightKg: Double = 100.0, rir: Int? = 1) = WorkoutSet(
        exerciseId = "back-squat",
        reps = reps,
        weightKg = weightKg,
        performedAtMillis = 1_000L,
        rir = rir
    )

    private fun additional(vararg values: Pair<Int, Double>) = values.mapIndexed { index, value ->
        PerformedClusterSegment(index + 1, value.first, value.second)
    }

    private val prescription = ClusterPrescription(
        kind = ClusterKind.DROP_SET,
        segments = listOf(
            ClusterSegmentTarget(8, 100.0),
            ClusterSegmentTarget(8, 80.0),
            ClusterSegmentTarget(8, 60.0)
        ),
        restSeconds = 0
    )

    @Test
    fun theParentSetIsAlwaysTheOpeningSegment() {
        val cluster = WorkoutCluster.of(
            parent = parent(),
            kind = ClusterKind.DROP_SET,
            plannedSegments = 3,
            additionalSegments = additional(8 to 80.0, 8 to 60.0)
        )

        assertEquals(3, cluster.performedSegments)
        assertEquals(PerformedClusterSegment(0, 8, 100.0, rir = 1), cluster.segments.first())
        assertTrue(cluster.isComplete)
    }

    @Test
    fun aPartialClusterDoesNotSatisfyItsPrescribedPosition() {
        val cluster = WorkoutCluster.of(
            parent = parent(),
            kind = ClusterKind.DROP_SET,
            plannedSegments = 3,
            additionalSegments = additional(8 to 80.0)
        )

        assertEquals(2, cluster.performedSegments)
        assertFalse(cluster.isComplete)
        assertEquals(listOf(ClusterSegmentTarget(8, 60.0)), cluster.remainingSegments(prescription))
    }

    @Test
    fun aPerformedClusterCannotExceedOrSkipItsPrescribedSegments() {
        assertFailsWith<IllegalArgumentException> {
            WorkoutCluster.of(
                parent = parent(),
                kind = ClusterKind.DROP_SET,
                plannedSegments = 2,
                additionalSegments = additional(8 to 80.0, 8 to 60.0)
            )
        }
        assertFailsWith<IllegalArgumentException> {
            WorkoutCluster(
                kind = ClusterKind.DROP_SET,
                plannedSegments = 3,
                segments = listOf(
                    PerformedClusterSegment(0, 8, 100.0),
                    PerformedClusterSegment(2, 8, 60.0)
                )
            ).requireValid()
        }
    }

    @Test
    fun remainingSegmentsRequireTheClusterSOwnPrescription() {
        val cluster = WorkoutCluster.of(
            parent = parent(),
            kind = ClusterKind.DROP_SET,
            plannedSegments = 2,
            additionalSegments = additional(8 to 80.0)
        )

        assertFailsWith<IllegalArgumentException> { cluster.remainingSegments(prescription) }
    }

    @Test
    fun volumeAndRepsSumEveryPerformedSegment() {
        val cluster = WorkoutCluster.of(
            parent = parent(),
            kind = ClusterKind.DROP_SET,
            plannedSegments = 2,
            additionalSegments = additional(10 to 80.0)
        )

        assertEquals(800.0 + 800.0, cluster.volumeKg)
        assertEquals(18, cluster.totalReps)
    }

    @Test
    fun aParentWithoutItsOwnMeasurementIsRefused() {
        val timedParent = WorkoutSet(
            exerciseId = "back-squat",
            reps = null,
            durationSeconds = 30,
            weightKg = 100.0,
            performedAtMillis = 1_000L
        )

        assertFailsWith<IllegalArgumentException> {
            WorkoutCluster.of(
                parent = timedParent,
                kind = ClusterKind.DROP_SET,
                plannedSegments = 2,
                additionalSegments = additional(8 to 80.0)
            )
        }
    }
}
