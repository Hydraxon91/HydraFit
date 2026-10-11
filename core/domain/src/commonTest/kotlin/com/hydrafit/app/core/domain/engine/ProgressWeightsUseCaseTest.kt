package com.hydrafit.app.core.domain.engine

import com.hydrafit.app.core.domain.workout.WorkoutSet
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProgressWeightsUseCaseTest {

    private val useCase = ProgressWeightsUseCase()
    private val prescription = mapOf(
        "bench-press" to Prescription("bench-press", sets = 3, reps = 8, weightKg = 80.0)
    )

    @Test
    fun leavesTheBaselineAloneWithoutAPrescription() {
        val result = useCase(
            baseline = mapOf("bench-press" to 80.0),
            prescriptions = emptyMap(),
            sets = completed(day = 1)
        )

        assertEquals(80.0, result.getValue("bench-press"))
    }

    @Test
    fun leavesTheBaselineAloneBelowTheSuccessStreak() {
        val result = useCase(
            baseline = mapOf("bench-press" to 80.0),
            prescriptions = prescription,
            sets = completed(day = 3) + completed(day = 2)
        )

        assertEquals(80.0, result.getValue("bench-press"))
    }

    @Test
    fun addsOneIncrementAfterThreeCompletedSessions() {
        val result = useCase(
            baseline = mapOf("bench-press" to 80.0),
            prescriptions = prescription,
            sets = completed(day = 3) + completed(day = 2) + completed(day = 1)
        )

        assertEquals(82.5, result.getValue("bench-press"))
    }

    @Test
    fun addsOneIncrementPerCompletedBlock() {
        val result = useCase(
            baseline = mapOf("bench-press" to 80.0),
            prescriptions = prescription,
            sets = (6 downTo 1).flatMap { completed(day = it) }
        )

        assertEquals(85.0, result.getValue("bench-press"))
    }

    @Test
    fun doesNotExceedTheIncrementCap() {
        val result = useCase(
            baseline = mapOf("bench-press" to 80.0),
            prescriptions = prescription,
            sets = (100 downTo 1).flatMap { completed(day = it) }
        )

        assertEquals(92.5, result.getValue("bench-press"))
    }

    @Test
    fun deloadsAfterThreeMissedSessionsButNeverBelowTheBaseline() {
        val progressed = useCase(
            baseline = mapOf("bench-press" to 80.0),
            prescriptions = prescription,
            sets = (6 downTo 1).flatMap { completed(day = it) }
        )
        assertEquals(85.0, progressed.getValue("bench-press"))

        val deloaded = useCase(
            baseline = mapOf("bench-press" to 80.0),
            prescriptions = prescription,
            sets = (9 downTo 1).flatMap { if (it <= 3) missed(day = it) else completed(day = it) }
        )
        assertEquals(82.5, deloaded.getValue("bench-press"))

        val floored = useCase(
            baseline = mapOf("bench-press" to 80.0),
            prescriptions = prescription,
            sets = missed(day = 3) + missed(day = 2) + missed(day = 1)
        )
        assertEquals(80.0, floored.getValue("bench-press"))
    }

    @Test
    fun aMissBetweenSessionsBreaksTheSuccessStreak() {
        val result = useCase(
            baseline = mapOf("bench-press" to 80.0),
            prescriptions = prescription,
            sets = completed(day = 5) + completed(day = 4) +
                missed(day = 3) +
                completed(day = 2) + completed(day = 1)
        )

        assertEquals(80.0, result.getValue("bench-press"))
    }

    @Test
    fun ignoresUnweightedSets() {
        val result = useCase(
            baseline = mapOf("bench-press" to 80.0),
            prescriptions = prescription,
            sets = (6 downTo 1).map { day ->
                WorkoutSet(
                    exerciseId = "bench-press",
                    reps = 8,
                    weightKg = null,
                    performedAtMillis = millisOf(day)
                )
            }
        )

        assertEquals(80.0, result.getValue("bench-press"))
    }

    @Test
    fun countsUnderTargetSetsAsAMiss() {
        val result = useCase(
            baseline = mapOf("bench-press" to 80.0),
            prescriptions = prescription,
            sets = completed(day = 3) + completed(day = 2) + completed(day = 1).dropLast(1)
        )

        assertEquals(80.0, result.getValue("bench-press"))
    }

    @Test
    fun pausesIncrementsWhenRequested() {
        val result = useCase(
            baseline = mapOf("bench-press" to 80.0),
            prescriptions = prescription,
            sets = (6 downTo 1).flatMap { completed(day = it) },
            pauseIncrements = true
        )

        assertEquals(80.0, result.getValue("bench-press"))
    }

    @Test
    fun omitsExercisesWithoutABaseline() {
        val result = useCase(
            baseline = emptyMap(),
            prescriptions = prescription,
            sets = (3 downTo 1).flatMap { completed(day = it) }
        )

        assertTrue(result.isEmpty())
    }

    @Test
    fun explainsAnAppliedAdjustmentWithTheRuleThatProducedIt() {
        val explanation = useCase.explain(
            baseline = mapOf("bench-press" to 80.0),
            prescriptions = prescription,
            sets = (3 downTo 1).flatMap { completed(day = it) }
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
            prescriptions = emptyMap(),
            sets = completed(day = 1)
        ).getValue("bench-press")
        assertEquals(ProgressionOutcome.NONE_NO_TYPED_PRESCRIPTION, incomplete.outcome)
        assertEquals(0, incomplete.increments)

        val belowThreshold = useCase.explain(
            baseline = mapOf("bench-press" to 80.0),
            prescriptions = prescription,
            sets = completed(day = 2) + completed(day = 1)
        ).getValue("bench-press")
        assertEquals(ProgressionOutcome.NONE_NO_STREAK_YET, belowThreshold.outcome)
        assertEquals(0.0, belowThreshold.deltaKg)
    }

    @Test
    fun aHoldCausedByMissedSessionsIsNotReportedAsThinEvidence() {
        val hold = useCase.explain(
            baseline = mapOf("bench-press" to 80.0),
            prescriptions = prescription,
            sets = missed(day = 3) + missed(day = 2) + missed(day = 1)
        ).getValue("bench-press")

        assertEquals(ProgressionOutcome.HELD_AFTER_MISSED_SESSIONS, hold.outcome)
        assertEquals(0, hold.increments)
        assertEquals(0.0, hold.deltaKg)
    }

    @Test
    fun aDeloadPauseIsReportedRatherThanReconstructedAsNoEvidence() {
        val resolved = useCase.resolve(
            baseline = mapOf("bench-press" to 80.0),
            prescriptions = prescription,
            sets = (6 downTo 1).flatMap { completed(day = it) },
            pauseIncrements = true
        ).getValue("bench-press")

        assertEquals(ProgressionOutcome.PAUSED_DELOAD, resolved.explanation.outcome)
        assertEquals(0.0, resolved.explanation.deltaKg)
        assertEquals(80.0, resolved.progressedKg)
    }

    @Test
    fun theRecordedReasonAlwaysMatchesTheReturnedValue() {
        val baseline = mapOf("bench-press" to 80.0)
        val sets = (6 downTo 1).flatMap { completed(day = it) }

        val values = useCase(baseline, prescription, sets)
        val resolved = useCase.resolve(baseline, prescription, sets).getValue("bench-press")

        assertEquals(values.getValue("bench-press"), resolved.progressedKg)
        assertEquals(
            baseline.getValue("bench-press") + resolved.explanation.deltaKg,
            resolved.progressedKg
        )
    }

    private fun completed(day: Int): List<WorkoutSet> = List(3) {
        WorkoutSet(
            exerciseId = "bench-press",
            reps = 8,
            weightKg = 80.0,
            performedAtMillis = millisOf(day)
        )
    }

    private fun missed(day: Int): List<WorkoutSet> = List(3) {
        WorkoutSet(
            exerciseId = "bench-press",
            reps = 8,
            weightKg = 70.0,
            performedAtMillis = millisOf(day)
        )
    }

    private fun millisOf(day: Int): Long = day.toLong() * 24L * 60L * 60L * 1000L
}
