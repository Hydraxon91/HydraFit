package com.hydrafit.app.core.domain.engine

import com.hydrafit.app.core.domain.routine.ClusterKind
import com.hydrafit.app.core.domain.workout.LoadKind
import com.hydrafit.app.core.domain.workout.WorkoutClusterSummary
import com.hydrafit.app.core.domain.workout.WorkoutPrescriptionSnapshot
import com.hydrafit.app.core.domain.workout.WorkoutSession
import com.hydrafit.app.core.domain.workout.WorkoutSet
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProgressWeightsUseCaseTest {

    private val useCase = ProgressWeightsUseCase()
    private val target = WorkoutPrescriptionSnapshot(
        exerciseId = "bench-press",
        sets = 3,
        reps = 8,
        loadKind = LoadKind.EXTERNAL,
        weightKg = 80.0,
        isDeload = false
    )

    @Test
    fun leavesTheBaselineAloneWithoutARecordedTarget() {
        val result = useCase(
            baseline = mapOf("bench-press" to 80.0),
            history = history(work(1, target = null)),
            nowMillis = NOW
        )

        assertEquals(80.0, result.getValue("bench-press"))
    }

    @Test
    fun leavesTheBaselineAloneBelowTheSuccessStreak() {
        val result = useCase(
            baseline = mapOf("bench-press" to 80.0),
            history = history(completed(day = 3), completed(day = 2)),
            nowMillis = NOW
        )

        assertEquals(80.0, result.getValue("bench-press"))
    }

    @Test
    fun addsOneIncrementAfterThreeCompletedSessions() {
        val result = useCase(
            baseline = mapOf("bench-press" to 80.0),
            history = history(completed(day = 3), completed(day = 2), completed(day = 1)),
            nowMillis = NOW
        )

        assertEquals(82.5, result.getValue("bench-press"))
    }

    @Test
    fun addsOneIncrementPerCompletedBlock() {
        val result = useCase(
            baseline = mapOf("bench-press" to 80.0),
            history = history(*(6 downTo 1).map { completed(day = it) }.toTypedArray()),
            nowMillis = NOW
        )

        assertEquals(85.0, result.getValue("bench-press"))
    }

    @Test
    fun doesNotExceedTheIncrementCap() {
        val result = useCase(
            baseline = mapOf("bench-press" to 80.0),
            history = history(*(100 downTo 1).map { completed(day = it) }.toTypedArray()),
            nowMillis = NOW
        )

        assertEquals(92.5, result.getValue("bench-press"))
    }

    @Test
    fun deloadsAfterThreeMissedSessionsButNeverBelowTheBaseline() {
        val progressed = useCase(
            baseline = mapOf("bench-press" to 80.0),
            history = history(*(6 downTo 1).map { completed(day = it) }.toTypedArray()),
            nowMillis = NOW
        )
        assertEquals(85.0, progressed.getValue("bench-press"))

        val deloaded = useCase(
            baseline = mapOf("bench-press" to 80.0),
            history = history(
                *((9 downTo 1).map { if (it <= 3) missed(day = it) else completed(day = it) })
                    .toTypedArray()
            ),
            nowMillis = NOW
        )
        assertEquals(82.5, deloaded.getValue("bench-press"))

        val floored = useCase(
            baseline = mapOf("bench-press" to 80.0),
            history = history(missed(day = 3), missed(day = 2), missed(day = 1)),
            nowMillis = NOW
        )
        assertEquals(80.0, floored.getValue("bench-press"))
    }

    @Test
    fun aMissBetweenSessionsBreaksTheSuccessStreak() {
        val result = useCase(
            baseline = mapOf("bench-press" to 80.0),
            history = history(
                completed(day = 5),
                completed(day = 4),
                missed(day = 3),
                completed(day = 2),
                completed(day = 1)
            ),
            nowMillis = NOW
        )

        assertEquals(80.0, result.getValue("bench-press"))
    }

    @Test
    fun anIneligibleExposureBreaksTheStreakWithoutBecomingAMiss() {
        val result = useCase(
            baseline = mapOf("bench-press" to 80.0),
            history = history(
                completed(day = 4),
                completed(day = 3),
                work(2, target = null),
                completed(day = 1)
            ),
            nowMillis = NOW
        )

        assertEquals(80.0, result.getValue("bench-press"))
        val explanation = useCase.explain(
            baseline = mapOf("bench-press" to 80.0),
            history = history(
                completed(day = 4),
                completed(day = 3),
                work(2, target = null),
                completed(day = 1)
            ),
            nowMillis = NOW
        ).getValue("bench-press")
        assertEquals(0, explanation.increments)
    }

    @Test
    fun blankLoadsCannotBeJudgedAndNeverBecomeAMiss() {
        val unweighted = (6 downTo 1).flatMap { day ->
            List(3) { index ->
                WorkoutSet(
                    id = day * 10L + index,
                    exerciseId = "bench-press",
                    reps = 8,
                    weightKg = null,
                    performedAtMillis = millisOf(day),
                    sessionId = "s$day",
                    prescriptionSnapshot = target
                )
            }
        }
        val history = history(unweighted)

        assertEquals(
            80.0,
            useCase(mapOf("bench-press" to 80.0), history, nowMillis = NOW)
                .getValue("bench-press")
        )
        // A blank load cannot establish weighted target failure, so six unjudgeable sessions must
        // not remove an increment the way six misses would.
        val explanation = useCase.explain(
            baseline = mapOf("bench-press" to 80.0),
            history = history,
            nowMillis = NOW
        ).getValue("bench-press")
        assertEquals(ProgressionOutcome.NONE_NO_TYPED_PRESCRIPTION, explanation.outcome)
        assertEquals(0, explanation.increments)
    }

    @Test
    fun incompleteWorkIsUnknownAndNeverAMiss() {
        // Three sessions, one of which recorded only two of the three prescribed sets: the short
        // session cannot be judged, so it breaks the streak without removing an increment.
        val result = useCase(
            baseline = mapOf("bench-press" to 80.0),
            history = history(
                completed(day = 3),
                completed(day = 2),
                completed(day = 1).dropLast(1)
            ),
            nowMillis = NOW
        )

        assertEquals(80.0, result.getValue("bench-press"))
    }

    @Test
    fun anUnknownExposureDoesNotCountTowardTheFailureStreak() {
        // Two misses, then an unjudgeable session, then another miss. If the unknown exposure were
        // treated as a miss the failure streak would reach three and remove an increment.
        val sets = missed(day = 4) + missed(day = 3) +
            work(2, target = null, weightKg = 70.0) +
            missed(day = 1)
        val explanation = useCase.explain(
            baseline = mapOf("bench-press" to 80.0),
            history = history(sets),
            nowMillis = NOW
        ).getValue("bench-press")

        assertEquals(0, explanation.increments)
        assertEquals(0.0, explanation.deltaKg)
        assertEquals(ProgressionOutcome.NONE_NO_STREAK_YET, explanation.outcome)
    }

    @Test
    fun pausesIncrementsWhenRequested() {
        val result = useCase(
            baseline = mapOf("bench-press" to 80.0),
            history = history(*(6 downTo 1).map { completed(day = it) }.toTypedArray()),
            pauseIncrements = true,
            nowMillis = NOW
        )

        assertEquals(80.0, result.getValue("bench-press"))
    }

    @Test
    fun addedLoadProgressesWithTheSameStreakRule() {
        val addedTarget = target.copy(loadKind = LoadKind.ADDED, weightKg = 10.0)
        val resolved = useCase.resolve(
            baseline = mapOf("bench-press" to 10.0),
            history = history(
                *(3 downTo 1).map { addedCompleted(day = it, addedTarget) }.toTypedArray()
            ),
            nowMillis = NOW,
            loadKind = LoadKind.ADDED
        ).getValue("bench-press")

        assertEquals(12.5, resolved.progressedKg)
        assertEquals(ProgressionOutcome.APPLIED, resolved.explanation.outcome)
        assertEquals(2.5, resolved.explanation.deltaKg)
    }

    @Test
    fun addedAndExternalWorkNeverJudgeEachOther() {
        val addedTarget = target.copy(loadKind = LoadKind.ADDED, weightKg = 10.0)
        val addedHistory = history(
            *(3 downTo 1).map { addedCompleted(day = it, addedTarget) }.toTypedArray()
        )

        // The added-load run is complete, but it is not evidence for an external prescription.
        assertEquals(
            10.0,
            useCase(mapOf("bench-press" to 10.0), addedHistory, nowMillis = NOW)
                .getValue("bench-press")
        )
        // Nor does external work judge an added-load prescription.
        val externalHistory = history(
            *(3 downTo 1).map { completed(day = it) }.toTypedArray()
        )
        assertEquals(
            10.0,
            useCase.resolve(
                baseline = mapOf("bench-press" to 10.0),
                history = externalHistory,
                nowMillis = NOW,
                loadKind = LoadKind.ADDED
            ).getValue("bench-press").progressedKg
        )
    }

    @Test
    fun omitsExercisesWithoutABaseline() {
        val result = useCase(
            baseline = emptyMap(),
            history = history(completed(day = 3), completed(day = 2), completed(day = 1)),
            nowMillis = NOW
        )

        assertTrue(result.isEmpty())
    }

    @Test
    fun explainsAnAppliedAdjustmentWithTheRuleThatProducedIt() {
        val explanation = useCase.explain(
            baseline = mapOf("bench-press" to 80.0),
            history = history(completed(day = 3), completed(day = 2), completed(day = 1)),
            nowMillis = NOW
        ).getValue("bench-press")

        assertEquals(ProgressionOutcome.APPLIED, explanation.outcome)
        assertEquals(1, explanation.increments)
        assertEquals(2.5, explanation.deltaKg)
        assertEquals(3, explanation.successStreakSize)
        assertEquals(3, explanation.failureStreakSize)
        assertEquals(2.5, explanation.roundToKg)
    }

    @Test
    fun explainsWhyProgressionDidNotApply() {
        val incomplete = useCase.explain(
            baseline = mapOf("bench-press" to 80.0),
            history = history(work(1, target = null)),
            nowMillis = NOW
        ).getValue("bench-press")
        assertEquals(ProgressionOutcome.NONE_NO_TYPED_PRESCRIPTION, incomplete.outcome)
        assertEquals(0, incomplete.increments)

        val belowThreshold = useCase.explain(
            baseline = mapOf("bench-press" to 80.0),
            history = history(completed(day = 2), completed(day = 1)),
            nowMillis = NOW
        ).getValue("bench-press")
        assertEquals(ProgressionOutcome.NONE_NO_STREAK_YET, belowThreshold.outcome)
        assertEquals(0.0, belowThreshold.deltaKg)
    }

    @Test
    fun aHoldCausedByMissedSessionsIsNotReportedAsThinEvidence() {
        val hold = useCase.explain(
            baseline = mapOf("bench-press" to 80.0),
            history = history(missed(day = 3), missed(day = 2), missed(day = 1)),
            nowMillis = NOW
        ).getValue("bench-press")

        assertEquals(ProgressionOutcome.HELD_AFTER_MISSED_SESSIONS, hold.outcome)
        assertEquals(0, hold.increments)
        assertEquals(0.0, hold.deltaKg)
    }

    @Test
    fun aDeloadPauseIsReportedRatherThanReconstructedAsNoEvidence() {
        val resolved = useCase.resolve(
            baseline = mapOf("bench-press" to 80.0),
            history = history(*(6 downTo 1).map { completed(day = it) }.toTypedArray()),
            pauseIncrements = true,
            nowMillis = NOW
        ).getValue("bench-press")

        assertEquals(ProgressionOutcome.PAUSED_DELOAD, resolved.explanation.outcome)
        assertEquals(0.0, resolved.explanation.deltaKg)
        assertEquals(80.0, resolved.progressedKg)
    }

    @Test
    fun theRecordedReasonAlwaysMatchesTheReturnedValue() {
        val baseline = mapOf("bench-press" to 80.0)
        val sessions = history(*(6 downTo 1).map { completed(day = it) }.toTypedArray())

        val values = useCase(baseline, sessions, nowMillis = NOW)
        val resolved = useCase.resolve(baseline, sessions, nowMillis = NOW).getValue("bench-press")

        assertEquals(values.getValue("bench-press"), resolved.progressedKg)
        assertEquals(
            baseline.getValue("bench-press") + resolved.explanation.deltaKg,
            resolved.progressedKg
        )
    }

    @Test
    fun clustersNeitherCountNorBreakOrdinaryProgressionInMixedOrSeparateSessions() {
        for (performed in listOf(1, 2)) {
            val ordinary = history(completed(1), completed(3), completed(5))
            val clusters = (
                work(2, target = null) + work(4, target = target) +
                    completed(3).map { it.copy(id = it.id + 100, reps = 1) }
                ).map {
                it.copy(cluster = WorkoutClusterSummary(ClusterKind.REST_PAUSE, 2, performed))
            }
            val mixed = history(ordinary.sets, clusters)
            assertEquals(
                ordinary.exposures("bench-press", NOW),
                mixed.exposures("bench-press", NOW)
            )
            assertEquals(
                useCase(mapOf("bench-press" to 80.0), ordinary, nowMillis = NOW),
                useCase(mapOf("bench-press" to 80.0), mixed, nowMillis = NOW)
            )
            assertTrue(history(clusters).exposures("bench-press", NOW).isEmpty())
        }
    }

    @Test
    fun orderedExposuresIgnoreCompleteAndPartialClustersIncludingDuplicateSlotIndices() {
        val targets = SlotSetTargets(targets = List(3) { SetTarget(reps = 8, weightKg = 80.0) })
        val ordinary = completed(1).mapIndexed { index, set ->
            set.copy(prescriptionSnapshot = target.copy(setIndex = index))
        }
        for (performed in listOf(1, 2)) {
            val clusters = ordinary.map {
                it.copy(
                    id = it.id + 100,
                    reps = 1,
                    cluster = WorkoutClusterSummary(ClusterKind.REST_PAUSE, 2, performed)
                )
            }
            assertEquals(
                history(ordinary).orderedExposures("bench-press", targets, NOW),
                history(ordinary, clusters).orderedExposures("bench-press", targets, NOW)
            )
            assertTrue(
                history(clusters).orderedExposures("bench-press", targets, NOW).isEmpty()
            )
        }
    }

    private fun completed(day: Int): List<WorkoutSet> = work(day, target = target, weightKg = 80.0)

    private fun missed(day: Int): List<WorkoutSet> = work(day, target = target, weightKg = 70.0)

    private fun addedCompleted(
        day: Int,
        addedTarget: WorkoutPrescriptionSnapshot
    ): List<WorkoutSet> = work(
        day = day,
        target = addedTarget,
        weightKg = requireNotNull(addedTarget.weightKg),
        kind = LoadKind.ADDED
    )

    private fun work(
        day: Int,
        target: WorkoutPrescriptionSnapshot?,
        weightKg: Double = 80.0,
        kind: LoadKind = LoadKind.EXTERNAL
    ): List<WorkoutSet> = List(3) { index ->
        WorkoutSet(
            id = day * 10L + index,
            exerciseId = "bench-press",
            reps = 8,
            weightKg = weightKg,
            loadKind = kind,
            performedAtMillis = millisOf(day),
            sessionId = "s$day",
            prescriptionSnapshot = target
        )
    }

    private fun history(vararg groups: List<WorkoutSet>): ProgressionHistory {
        val sets = groups.toList().flatten()
        val sessions = sets
            .mapNotNull { set -> set.sessionId?.let { it to set } }
            .groupBy({ it.first }, { it.second })
            .map { (id, group) ->
                WorkoutSession(
                    id = id,
                    startedAtMillis = group.minOf { it.performedAtMillis },
                    endedAtMillis = group.maxOf { it.performedAtMillis },
                    localEpochDay = 0
                )
            }
        return ProgressionHistory(sets, sessions)
    }

    private fun millisOf(day: Int): Long = day.toLong() * 24L * 60L * 60L * 1000L

    private companion object {
        val NOW = 100L * 24L * 60L * 60L * 1000L
    }
}
