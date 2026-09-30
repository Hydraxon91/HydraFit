package com.hydrafit.app.core.domain.fatigue

import kotlin.test.Test
import kotlin.test.assertEquals

class FatigueCalculatorTest {

    private val calculator = FatigueCalculator()

    @Test
    fun noSetsYieldsZeroForEveryMuscleGroup() {
        val scores = calculator.calculate(emptyList(), nowMillis = T0)

        assertEquals(MuscleGroup.entries.size, scores.size)
        assertEquals(0.0, scores.getValue(MuscleGroup.CHEST), TOLERANCE)
    }

    @Test
    fun singleSessionNormalizesAgainstReferenceVolume() {
        val sets = List(12) { loggedSet(MuscleGroup.CHEST) }

        val score = calculator.calculate(sets, nowMillis = T0).getValue(MuscleGroup.CHEST)

        assertEquals(0.5, score, TOLERANCE)
    }

    @Test
    fun scoreIsClampedToOne() {
        val sets = List(48) { loggedSet(MuscleGroup.CHEST) }

        val score = calculator.calculate(sets, nowMillis = T0).getValue(MuscleGroup.CHEST)

        assertEquals(1.0, score, TOLERANCE)
    }

    @Test
    fun decaysByHalfAfterOneHalfLife() {
        val sets = List(24) { loggedSet(MuscleGroup.CHEST) }

        val score = calculator.calculate(sets, nowMillis = T0 + 48 * HOUR_MILLIS)
            .getValue(MuscleGroup.CHEST)

        assertEquals(0.5, score, TOLERANCE)
    }

    @Test
    fun decaysToAQuarterAfterTwoHalfLives() {
        val sets = List(24) { loggedSet(MuscleGroup.CHEST) }

        val score = calculator.calculate(sets, nowMillis = T0 + 96 * HOUR_MILLIS)
            .getValue(MuscleGroup.CHEST)

        assertEquals(0.25, score, TOLERANCE)
    }

    @Test
    fun secondaryInvolvementCountsHalf() {
        val sets = List(24) {
            loggedSet(MuscleGroup.SHOULDERS, weight = MuscleInvolvement.SECONDARY.volumeWeight)
        }

        val score = calculator.calculate(sets, nowMillis = T0).getValue(MuscleGroup.SHOULDERS)

        assertEquals(0.5, score, TOLERANCE)
    }

    @Test
    fun warmupSetsAreIgnored() {
        val sets = List(24) { loggedSet(MuscleGroup.CHEST, isWarmup = true) }

        val score = calculator.calculate(sets, nowMillis = T0).getValue(MuscleGroup.CHEST)

        assertEquals(0.0, score, TOLERANCE)
    }

    @Test
    fun laterSessionsStackOnDecayedVolume() {
        val sets = List(8) { loggedSet(MuscleGroup.CHEST, timestampMillis = T0) } +
            List(8) { loggedSet(MuscleGroup.CHEST, timestampMillis = T0 + 48 * HOUR_MILLIS) }

        val score = calculator.calculate(sets, nowMillis = T0 + 48 * HOUR_MILLIS)
            .getValue(MuscleGroup.CHEST)

        assertEquals(0.5, score, TOLERANCE)
    }

    @Test
    fun setOrderingDoesNotAffectTheResult() {
        val ascending = listOf(
            loggedSet(MuscleGroup.CHEST, timestampMillis = T0),
            loggedSet(MuscleGroup.CHEST, timestampMillis = T0 + 2 * HOUR_MILLIS),
            loggedSet(MuscleGroup.CHEST, timestampMillis = T0 + HOUR_MILLIS)
        )

        val shuffled = listOf(ascending[2], ascending[0], ascending[1])

        assertEquals(
            calculator.calculate(ascending, nowMillis = T0 + 3 * HOUR_MILLIS),
            calculator.calculate(shuffled, nowMillis = T0 + 3 * HOUR_MILLIS)
        )
    }

    @Test
    fun nowComesFromTheParameterNotTheClock() {
        val sets = List(24) { loggedSet(MuscleGroup.CHEST) }

        val fresh = calculator.calculate(sets, nowMillis = T0).getValue(MuscleGroup.CHEST)
        val recovered = calculator.calculate(sets, nowMillis = T0 + 48 * HOUR_MILLIS)
            .getValue(MuscleGroup.CHEST)

        assertEquals(1.0, fresh, TOLERANCE)
        assertEquals(0.5, recovered, TOLERANCE)
    }

    @Test
    fun untrainedMusclesStayAtZeroWhileTrainedOnesRise() {
        val sets = List(12) { loggedSet(MuscleGroup.QUADS) }

        val scores = calculator.calculate(sets, nowMillis = T0)

        assertEquals(0.5, scores.getValue(MuscleGroup.QUADS), TOLERANCE)
        assertEquals(0.0, scores.getValue(MuscleGroup.CHEST), TOLERANCE)
    }

    private companion object {
        const val TOLERANCE = 1e-9
        const val HOUR_MILLIS = 60L * 60L * 1000L
        const val T0 = 0L

        fun loggedSet(
            muscle: MuscleGroup,
            timestampMillis: Long = T0,
            weight: Double = MuscleInvolvement.PRIMARY.volumeWeight,
            isWarmup: Boolean = false
        ) = LoggedSet(
            timestampMillis = timestampMillis,
            targets = listOf(MuscleTarget(muscle, weight)),
            isWarmup = isWarmup
        )
    }
}
