package com.hydrafit.app.core.domain.workout

import com.hydrafit.app.core.domain.engine.ProgressionConfig
import com.hydrafit.app.core.domain.engine.ProgressionExposureOutcome
import com.hydrafit.app.core.domain.engine.ProgressionHistory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class WorkoutPrescriptionSnapshotTest {
    private val target = WorkoutPrescriptionSnapshot(
        exerciseId = "bench",
        sets = 3,
        reps = 8,
        loadKind = LoadKind.EXTERNAL,
        weightKg = 80.0,
        isDeload = false
    )

    @Test
    fun strictRoundTripPreservesAbsentZeroAndPositiveTargets() {
        for (weight in listOf(null, 0.0, 80.0)) {
            val snapshot = target.copy(weightKg = weight)
            val encoded = WorkoutPrescriptionSnapshotJson.encode(snapshot)
            assertEquals(snapshot, WorkoutPrescriptionSnapshotJson.decode(encoded))
        }
        assertFailsWith<IllegalArgumentException> { target.copy(version = 2).requireValid() }
        assertFailsWith<IllegalArgumentException> { target.copy(reps = null).requireValid() }
        assertFailsWith<IllegalArgumentException> { target.copy(sets = 0).requireValid() }
        assertFailsWith<IllegalArgumentException> {
            target.copy(weightKg = Double.NaN).requireValid()
        }
    }

    @Test
    fun threeClosedSessionsOnOneDateEarnAnIncrement() {
        assertEquals(1, replay(work(1) + work(2) + work(3)).increments)
    }

    @Test
    fun incompleteSessionsCannotBeCombinedToManufactureSuccess() {
        val history = history(work(1).take(2) + work(2).take(1) + work(3))
        assertEquals(
            listOf(
                ProgressionExposureOutcome.UNKNOWN,
                ProgressionExposureOutcome.UNKNOWN,
                ProgressionExposureOutcome.SUCCESS
            ),
            history.exposures("bench", 100).map { it.outcome }
        )
        assertEquals(0, history.replay("bench", 100, ProgressionConfig()).increments)
    }

    @Test
    fun unknownAndChangedTargetsBreakStreakWithoutAMiss() {
        val unknown = work(3).map { it.copy(prescriptionSnapshot = null) }
        val replay = history(work(1) + work(2) + unknown + work(4))
            .replay("bench", 100, ProgressionConfig())
        assertEquals(0, replay.increments)
        assertEquals(false, replay.failureApplied)
        val changed = work(3).map { it.copy(prescriptionSnapshot = target.copy(reps = 7)) }
        assertEquals(0, replay(work(1) + work(2) + changed).increments)
    }

    @Test
    fun partialWorkoutDoesNotInvalidateACompletedExerciseButOpenSessionDoes() {
        val closed = history(work(1))
        assertEquals(
            ProgressionExposureOutcome.SUCCESS,
            closed.exposures("bench", 100).single().outcome
        )
        assertEquals(
            ProgressionExposureOutcome.UNKNOWN,
            closed.copy(sessions = closed.sessions.map { it.copy(endedAtMillis = null) })
                .exposures("bench", 100).single().outcome
        )
    }

    @Test
    fun correctionAndDeletionReplayAgainstRecordedTarget() {
        val all = work(1) + work(2) + work(3)
        assertEquals(1, replay(all).increments)
        assertEquals(0, replay(all.dropLast(1)).increments)
        val corrected = all.map { if (it.sessionId == "s3") it.copy(reps = 7) else it }
        assertEquals(0, replay(corrected).increments)
        val misses = all.map { it.copy(reps = 7) }
        assertEquals(true, replay(misses).failureApplied)
    }

    @Test
    fun warmupsFutureDeloadBlankAndMixedTargetsCannotEarnSuccess() {
        val invalidCases = listOf(
            work(1).map { it.copy(isWarmup = true) },
            work(1).map { it.copy(performedAtMillis = 101) },
            work(1).map { it.copy(prescriptionSnapshot = target.copy(isDeload = true)) },
            work(1).map { it.copy(prescriptionSnapshot = target.copy(weightKg = null)) },
            work(1).mapIndexed { index, set ->
                if (index == 0) set.copy(prescriptionSnapshot = null) else set
            }
        )
        for (invalid in invalidCases) {
            assertEquals(0, replay(invalid).increments)
        }
    }

    private fun work(session: Int): List<WorkoutSet> = List(3) { index ->
        WorkoutSet(
            id = session * 10L + index,
            exerciseId = "bench",
            reps = 8,
            weightKg = 80.0,
            performedAtMillis = session.toLong(),
            sessionId = "s$session",
            prescriptionSnapshot = target
        )
    }

    private fun history(sets: List<WorkoutSet>) = ProgressionHistory(
        sets,
        (1..4).map { WorkoutSession("s$it", it.toLong(), it.toLong(), 0) }
    )

    private fun replay(sets: List<WorkoutSet>) = history(sets).replay(
        "bench",
        100,
        ProgressionConfig()
    )
}
