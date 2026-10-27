package com.hydrafit.app.feature.routines

import com.hydrafit.app.core.domain.routine.ClusterKind
import com.hydrafit.app.core.domain.routine.ClusterPrescription
import com.hydrafit.app.core.domain.routine.ClusterSegmentTarget
import com.hydrafit.app.core.domain.routine.Superset
import com.hydrafit.app.core.domain.workout.LoadKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ClusterPrescriptionDialogTest {

    @Test
    fun aNewClusterOpensWithTwoEmptySegmentsAndNoNumbers() {
        val editor = ClusterEditorState(workoutIndex = 0, entryIndex = 0)

        assertEquals(ClusterKind.DROP_SET, editor.kind)
        assertEquals(2, editor.segments.size)
        assertTrue(
            editor.segments.all {
                it.reps.isEmpty() &&
                    it.loadKg.isEmpty() &&
                    it.rir.isEmpty()
            }
        )
        assertEquals("", editor.restSeconds)
        assertEquals(null, editor.error)
    }

    @Test
    fun aValidDropSetBuildsADecreasingPrescription() {
        val result = parseClusterEditor(
            clusterState(
                segment(reps = "8", load = "100"),
                segment(reps = "8", load = "80"),
                segment(reps = "6", load = "60", rir = "1")
            )
        )

        val prescription = assertIs<ClusterParseResult.Valid>(result).prescription
        assertEquals(
            ClusterPrescription(
                kind = ClusterKind.DROP_SET,
                segments = listOf(
                    ClusterSegmentTarget(reps = 8, weightKg = 100.0),
                    ClusterSegmentTarget(reps = 8, weightKg = 80.0),
                    ClusterSegmentTarget(reps = 6, weightKg = 60.0, rir = 1)
                )
            ),
            prescription
        )
    }

    @Test
    fun aValidRestPauseRepeatsOneLoadWithAPositiveRest() {
        val result = parseClusterEditor(
            clusterState(
                segment(load = "60"),
                segment(load = "60"),
                kind = ClusterKind.REST_PAUSE,
                rest = "20"
            )
        )

        val prescription = assertIs<ClusterParseResult.Valid>(result).prescription
        assertEquals(ClusterKind.REST_PAUSE, prescription.kind)
        assertEquals(20, prescription.restSeconds)
        assertEquals(listOf(60.0, 60.0), prescription.segments.map { it.weightKg })
    }

    @Test
    fun fewerThanTwoSegmentsIsRejected() {
        val result = parseClusterEditor(clusterState(segment(load = "100")))

        assertEquals(ClusterError.TOO_FEW_SEGMENTS, errorOf(result))
    }

    @Test
    fun moreThanEightSegmentsIsRejected() {
        val segments = (1..(ClusterPrescription.MAX_SEGMENTS + 1)).map { index ->
            segment(load = (100 - index).toString())
        }

        val result = parseClusterEditor(
            ClusterEditorState(workoutIndex = 0, entryIndex = 0, segments = segments)
        )

        assertEquals(ClusterError.TOO_MANY_SEGMENTS, errorOf(result))
    }

    @Test
    fun aDropSetWhoseLoadDoesNotStrictlyDecreaseIsRejected() {
        val repeated = parseClusterEditor(
            clusterState(segment(load = "100"), segment(load = "100"))
        )
        val increasing = parseClusterEditor(
            clusterState(segment(load = "80"), segment(load = "100"))
        )

        assertEquals(ClusterError.DROP_LOAD_NOT_DECREASING, errorOf(repeated))
        assertEquals(ClusterError.DROP_LOAD_NOT_DECREASING, errorOf(increasing))
    }

    @Test
    fun aRestPauseWhoseLoadsDifferIsRejected() {
        val result = parseClusterEditor(
            clusterState(
                segment(load = "60"),
                segment(load = "70"),
                kind = ClusterKind.REST_PAUSE,
                rest = "20"
            )
        )

        assertEquals(ClusterError.REST_PAUSE_LOAD_NOT_REPEATED, errorOf(result))
    }

    @Test
    fun aRestPauseWithoutPositiveRestIsRejected() {
        val missing = parseClusterEditor(
            clusterState(segment(load = "60"), segment(load = "60"), kind = ClusterKind.REST_PAUSE)
        )
        val zero = parseClusterEditor(
            clusterState(
                segment(load = "60"),
                segment(load = "60"),
                kind = ClusterKind.REST_PAUSE,
                rest = "0"
            )
        )

        assertEquals(ClusterError.REST_PAUSE_NO_REST, errorOf(missing))
        assertEquals(ClusterError.REST_PAUSE_NO_REST, errorOf(zero))
    }

    @Test
    fun aNonPositiveRepCountIsRejected() {
        listOf("0", "-1").forEach { reps ->
            val result = parseClusterEditor(
                clusterState(segment(reps = reps, load = "100"), segment(load = "80"))
            )
            assertEquals(ClusterError.REPS_RANGE, errorOf(result))
        }
        val blank = parseClusterEditor(
            clusterState(segment(reps = "", load = "100"), segment(load = "80"))
        )
        assertEquals(ClusterError.REPS_RANGE, errorOf(blank))
    }

    @Test
    fun aNonPositiveLoadIsRejected() {
        listOf("0", "-5").forEach { load ->
            val result = parseClusterEditor(
                clusterState(segment(load = "100"), segment(load = load))
            )
            assertEquals(ClusterError.LOAD, errorOf(result))
        }
        val blank = parseClusterEditor(clusterState(segment(load = "100"), segment(load = "")))
        assertEquals(ClusterError.LOAD, errorOf(blank))
    }

    @Test
    fun anRirOutsideTheAllowedRangeIsRejected() {
        listOf("5", "-1").forEach { rir ->
            val result = parseClusterEditor(
                clusterState(
                    segment(load = "100", rir = rir),
                    segment(load = "80")
                )
            )
            assertEquals(ClusterError.RIR_RANGE, errorOf(result))
        }
        val opaque = parseClusterEditor(
            clusterState(segment(load = "100", rir = "two"), segment(load = "80"))
        )
        assertEquals(ClusterError.RIR_RANGE, errorOf(opaque))
    }

    @Test
    fun aRestOutsideTheDomainRangeIsRejected() {
        val result = parseClusterEditor(
            clusterState(
                segment(load = "100"),
                segment(load = "80"),
                rest = (Superset.MAX_REST_SECONDS + 1).toString()
            )
        )

        assertEquals(ClusterError.REST_RANGE, errorOf(result))
    }

    @Test
    fun theSummaryShowsSegmentsAndTheLoadSequence() {
        val cluster = ClusterPrescription(
            kind = ClusterKind.DROP_SET,
            segments = listOf(
                ClusterSegmentTarget(reps = 8, weightKg = 100.0),
                ClusterSegmentTarget(reps = 8, weightKg = 80.0),
                ClusterSegmentTarget(reps = 6, weightKg = 60.0)
            )
        )

        assertEquals("100/80/60", clusterSummaryLoads(cluster))
        assertEquals(3, clusterSummarySegmentCount(cluster))
    }

    @Test
    fun onlyRepBasedExternalSlotsCanCarryACluster() {
        val externalReps =
            editorEntry(metric = EntryMetric.REPETITIONS, loadKind = LoadKind.EXTERNAL)

        assertTrue(externalReps.canCarryCluster)
        assertFalse(externalReps.copy(metric = EntryMetric.DURATION).canCarryCluster)
        assertFalse(externalReps.copy(metric = EntryMetric.DISTANCE).canCarryCluster)
        assertFalse(externalReps.copy(metric = EntryMetric.FORCE).canCarryCluster)
        assertFalse(externalReps.copy(loadKind = LoadKind.BODYWEIGHT).canCarryCluster)
        assertFalse(externalReps.copy(loadKind = LoadKind.ADDED).canCarryCluster)
    }

    private fun editorEntry(metric: EntryMetric, loadKind: LoadKind) = EditorEntry(
        exerciseId = "bench-press",
        name = "Barbell Bench Press",
        sets = "3",
        reps = "8",
        weight = "80",
        loadKind = loadKind,
        metric = metric
    )

    private fun segment(reps: String = "8", load: String = "", rir: String = "") =
        ClusterSegmentEditorState(reps = reps, loadKg = load, rir = rir)

    private fun clusterState(
        vararg segments: ClusterSegmentEditorState,
        kind: ClusterKind = ClusterKind.DROP_SET,
        rest: String = ""
    ) = ClusterEditorState(
        workoutIndex = 0,
        entryIndex = 0,
        kind = kind,
        segments = segments.toList(),
        restSeconds = rest
    )

    private fun errorOf(result: ClusterParseResult): ClusterError? =
        (result as? ClusterParseResult.Invalid)?.error
}
