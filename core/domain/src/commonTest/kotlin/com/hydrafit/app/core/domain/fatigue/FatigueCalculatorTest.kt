package com.hydrafit.app.core.domain.fatigue

import com.hydrafit.app.core.domain.engine.OneRepMax
import kotlin.math.ln
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

class FatigueCalculatorTest {

    private val calculator = FatigueCalculator()

    @Test
    fun noSetsYieldsZeroForEveryMuscleGroup() {
        val scores = calculator.calculate(emptyList(), nowMillis = T0)

        assertEquals(MuscleGroup.entries.size, scores.size)
        assertEquals(0.0, scores.getValue(MuscleGroup.CHEST), TOLERANCE)
    }

    @Test
    fun singleSessionUsesBoundedAdditionAndDiminishingReturns() {
        val sets = List(12) { loggedSet(MuscleGroup.CHEST) }

        val score = calculator.calculate(sets, nowMillis = T0).getValue(MuscleGroup.CHEST)

        assertEquals(2.0 / 3.0, score, TOLERANCE)
    }

    @Test
    fun extremeVolumeStaysBelowOneAndRecoversImmediatelyWithoutAPlateau() {
        val sets = List(100_000) { loggedSet(MuscleGroup.CHEST, reps = 100) }

        val score = calculator.calculate(sets, nowMillis = T0).getValue(MuscleGroup.CHEST)

        val recovered = calculator.calculate(sets, nowMillis = T0 + 1L).getValue(MuscleGroup.CHEST)
        assertTrue(score > 0.99 && score < 1.0)
        assertTrue(recovered >= 0.0 && recovered < score)
    }

    @Test
    fun decaysByHalfAfterEachMusclesHalfLife() {
        for (muscle in MuscleGroup.entries) {
            val hours = when (muscle) {
                MuscleGroup.SHOULDERS -> 21
                MuscleGroup.BICEPS, MuscleGroup.TRICEPS, MuscleGroup.CALVES, MuscleGroup.CORE -> 18
                else -> 24
            }
            val sets = List(24) { loggedSet(muscle) }
            val score = calculator.calculate(sets, nowMillis = T0 + hours * HOUR_MILLIS)
                .getValue(muscle)
            assertEquals(0.4, score, TOLERANCE, muscle.name)
        }
    }

    @Test
    fun decaysToAQuarterAfterTwoHalfLives() {
        val sets = List(24) { loggedSet(MuscleGroup.CHEST) }

        val score = calculator.calculate(sets, nowMillis = T0 + 48 * HOUR_MILLIS)
            .getValue(MuscleGroup.CHEST)

        assertEquals(0.2, score, TOLERANCE)
    }

    @Test
    fun secondaryInvolvementHalvesStimulusBeforeTheNonlinearResponse() {
        val sets = List(24) {
            loggedSet(MuscleGroup.SHOULDERS, weight = MuscleInvolvement.SECONDARY.volumeWeight)
        }

        val score = calculator.calculate(sets, nowMillis = T0).getValue(MuscleGroup.SHOULDERS)

        assertEquals(2.0 / 3.0, score, TOLERANCE)
    }

    @Test
    fun warmupSetsAreIgnored() {
        val sets = List(24) { loggedSet(MuscleGroup.CHEST, isWarmup = true) }

        val score = calculator.calculate(sets, nowMillis = T0).getValue(MuscleGroup.CHEST)

        assertEquals(0.0, score, TOLERANCE)
    }

    @Test
    fun laterSessionsUseRecoveredHeadroomAndResetTheDiscount() {
        val sets = List(8) { loggedSet(MuscleGroup.CHEST, timestampMillis = T0) } +
            List(8) { loggedSet(MuscleGroup.CHEST, timestampMillis = T0 + 24 * HOUR_MILLIS) }

        val score = calculator.calculate(sets, nowMillis = T0 + 24 * HOUR_MILLIS)
            .getValue(MuscleGroup.CHEST)

        assertEquals(34.0 / 49.0, score, TOLERANCE)
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
        val recovered = calculator.calculate(sets, nowMillis = T0 + 24 * HOUR_MILLIS)
            .getValue(MuscleGroup.CHEST)

        assertEquals(0.8, fresh, TOLERANCE)
        assertEquals(0.4, recovered, TOLERANCE)
    }

    @Test
    fun untrainedMusclesStayAtZeroWhileTrainedOnesRise() {
        val sets = List(12) { loggedSet(MuscleGroup.QUADS) }

        val scores = calculator.calculate(sets, nowMillis = T0)

        assertEquals(2.0 / 3.0, scores.getValue(MuscleGroup.QUADS), TOLERANCE)
        assertEquals(0.0, scores.getValue(MuscleGroup.CHEST), TOLERANCE)
    }

    @Test
    fun thirdSetHasGreaterMarginalDoseThanTheTenth() {
        val scores = (0..10).map { count ->
            calculator.calculate(List(count) { loggedSet(MuscleGroup.CHEST) }, T0)
                .getValue(MuscleGroup.CHEST)
        }
        val thirdDose = -6.0 * ln((1.0 - scores[3]) / (1.0 - scores[2]))
        val tenthDose = -6.0 * ln((1.0 - scores[10]) / (1.0 - scores[9]))
        assertEquals(6.0 * ln(9.0 / 8.0), thirdDose, TOLERANCE)
        assertEquals(6.0 * ln(16.0 / 15.0), tenthDose, TOLERANCE)
        assertTrue(thirdDose > tenthDose)
        assertTrue(scores[3] - scores[2] > scores[10] - scores[9])
    }

    @Test
    fun differentExercisesShareTheMusclesSessionDiscount() {
        // LoggedSet has no exercise id: different exercise snapshots feed the same muscle V.
        val chestOnly = loggedSet(MuscleGroup.CHEST)
        val chestAndShoulders = chestOnly.copy(
            targets = chestOnly.targets + MuscleTarget(MuscleGroup.SHOULDERS, 0.5),
            timestampMillis = T0 + 1L
        )
        val score = calculator.calculate(listOf(chestOnly, chestAndShoulders), T0 + 1L)
            .getValue(MuscleGroup.CHEST)
        val recoveredFirst = (1.0 / 7.0) * 2.0.pow(-1.0 / (24 * HOUR_MILLIS))
        assertEquals(recoveredFirst + (1.0 - recoveredFirst) / 8.0, score, TOLERANCE)
    }

    @Test
    fun legacyNullSessionIdsFallBackToTheGap() {
        for (gap in listOf(2 * HOUR_MILLIS - 1L, 2 * HOUR_MILLIS, 2 * HOUR_MILLIS + 1L)) {
            val sets = listOf(
                loggedSet(MuscleGroup.CHEST),
                loggedSet(MuscleGroup.CHEST, timestampMillis = gap)
            )
            val recoveredFirst = (1.0 / 7.0) * 2.0.pow(-gap.toDouble() / (24 * HOUR_MILLIS))
            val secondResponse = if (gap < 2 * HOUR_MILLIS) 1.0 / 8.0 else 1.0 / 7.0
            assertEquals(
                recoveredFirst + (1.0 - recoveredFirst) * secondResponse,
                calculator.calculate(sets, gap).getValue(MuscleGroup.CHEST),
                TOLERANCE
            )
        }
    }

    @Test
    fun sessionIdChangeResetsTheDiscountRegardlessOfTheGap() {
        val gap = HOUR_MILLIS
        val sets = listOf(
            loggedSet(MuscleGroup.CHEST).copy(sessionId = "session-a"),
            loggedSet(MuscleGroup.CHEST, timestampMillis = gap).copy(sessionId = "session-b")
        )
        val recoveredFirst = (1.0 / 7.0) * 2.0.pow(-gap.toDouble() / (24 * HOUR_MILLIS))
        assertEquals(
            recoveredFirst + (1.0 - recoveredFirst) / 7.0,
            calculator.calculate(sets, gap).getValue(MuscleGroup.CHEST),
            TOLERANCE
        )
    }

    @Test
    fun sameSessionIdDoesNotResetAcrossTheLegacyGap() {
        val gap = 24 * HOUR_MILLIS
        val sets = listOf(
            loggedSet(MuscleGroup.CHEST).copy(sessionId = "session-a"),
            loggedSet(MuscleGroup.CHEST, timestampMillis = gap).copy(sessionId = "session-a")
        )
        val recoveredFirst = (1.0 / 7.0) * 2.0.pow(-gap.toDouble() / (24 * HOUR_MILLIS))
        assertEquals(
            recoveredFirst + (1.0 - recoveredFirst) / 8.0,
            calculator.calculate(sets, gap).getValue(MuscleGroup.CHEST),
            TOLERANCE
        )
    }

    @Test
    fun nullNonNullBoundaryResetsInBothDirections() {
        val gap = HOUR_MILLIS
        val recoveredFirst = (1.0 / 7.0) * 2.0.pow(-gap.toDouble() / (24 * HOUR_MILLIS))
        val resetSecond = recoveredFirst + (1.0 - recoveredFirst) / 7.0

        val nullThenId = listOf(
            loggedSet(MuscleGroup.CHEST),
            loggedSet(MuscleGroup.CHEST, timestampMillis = gap).copy(sessionId = "session-a")
        )
        // S2's interim rule: a later null row must not re-join the identified session.
        val idThenNull = listOf(
            loggedSet(MuscleGroup.CHEST).copy(sessionId = "session-a"),
            loggedSet(MuscleGroup.CHEST, timestampMillis = gap)
        )
        assertEquals(
            resetSecond,
            calculator.calculate(nullThenId, gap).getValue(MuscleGroup.CHEST),
            TOLERANCE
        )
        assertEquals(
            resetSecond,
            calculator.calculate(idThenNull, gap).getValue(MuscleGroup.CHEST),
            TOLERANCE
        )
    }

    @Test
    fun sameTimestampDifferentSessionIdsSplitDeterministically() {
        val split = listOf(
            loggedSet(MuscleGroup.CHEST).copy(sessionId = "session-a"),
            loggedSet(MuscleGroup.CHEST).copy(sessionId = "session-b")
        )
        val expected = 1.0 - (1.0 - 1.0 / 7.0) * (1.0 - 1.0 / 7.0)
        assertEquals(
            expected,
            calculator.calculate(split, T0).getValue(MuscleGroup.CHEST),
            TOLERANCE
        )
        assertEquals(
            calculator.calculate(split, T0),
            calculator.calculate(split.reversed(), T0)
        )
    }

    @Test
    fun otherMusclesWorkingSetsKeepTheWholeLogSessionOpen() {
        val sets = listOf(
            loggedSet(MuscleGroup.CHEST),
            loggedSet(MuscleGroup.QUADS, timestampMillis = HOUR_MILLIS),
            loggedSet(MuscleGroup.CHEST, timestampMillis = 2 * HOUR_MILLIS)
        )
        val recoveredFirst = (1.0 / 7.0) * 2.0.pow(-2.0 / 24.0)
        assertEquals(
            recoveredFirst + (1.0 - recoveredFirst) / 8.0,
            calculator.calculate(sets, 2 * HOUR_MILLIS).getValue(MuscleGroup.CHEST),
            TOLERANCE
        )
    }

    @Test
    fun midnightDoesNotResetTheSession() {
        val sameDay = listOf(
            loggedSet(MuscleGroup.CHEST, timestampMillis = 10 * HOUR_MILLIS),
            loggedSet(MuscleGroup.CHEST, timestampMillis = 11 * HOUR_MILLIS)
        )
        val spanningMidnight = sameDay.map {
            it.copy(timestampMillis = it.timestampMillis + 13 * HOUR_MILLIS + HOUR_MILLIS / 2)
        }
        assertEquals(
            calculator.calculate(sameDay, sameDay.last().timestampMillis),
            calculator.calculate(spanningMidnight, spanningMidnight.last().timestampMillis)
        )
    }

    @Test
    fun warmupsNeitherAddStimulusNorBridgeTheSessionGap() {
        val working = listOf(
            loggedSet(MuscleGroup.CHEST),
            loggedSet(MuscleGroup.CHEST, timestampMillis = 2 * HOUR_MILLIS)
        )
        val warmup = loggedSet(
            MuscleGroup.CHEST,
            timestampMillis = HOUR_MILLIS,
            isWarmup = true,
            reps = 100
        )
        assertEquals(
            calculator.calculate(working, 2 * HOUR_MILLIS),
            calculator.calculate(working + warmup, 2 * HOUR_MILLIS)
        )
    }

    @Test
    fun equalTimestampBatchesAreExactlyOrderIndependent() {
        val sets = listOf(0.3, 1.0, 0.5, 0.7).flatMap { weight ->
            listOf(1, 5, 8, 10, 20).map { reps ->
                loggedSet(MuscleGroup.CHEST, weight = weight, reps = reps)
            }
        } + loggedSet(MuscleGroup.CHEST, timestampMillis = HOUR_MILLIS)
        assertEquals(
            calculator.calculate(sets, HOUR_MILLIS),
            calculator.calculate(sets.reversed(), HOUR_MILLIS)
        )
        assertEquals(
            calculator.calculate(sets, HOUR_MILLIS),
            calculator.calculate(sets.drop(7) + sets.take(7), HOUR_MILLIS)
        )
    }

    @Test
    fun repsFactorUsesTheReferenceAndClampsBothEnds() {
        for ((reps, multiplier) in listOf(1 to 0.5, 2 to 0.5, 8 to 1.0, 18 to 1.5, 100 to 1.5)) {
            val score = calculator.calculate(listOf(loggedSet(MuscleGroup.CHEST, reps = reps)), T0)
                .getValue(MuscleGroup.CHEST)
            assertEquals(multiplier / (6.0 + multiplier), score, TOLERANCE)
        }
        assertEquals(8, loggedSet(MuscleGroup.CHEST).reps)
    }

    @Test
    fun compactSessionsMatchTheFourApprovedHypotheticalCases() {
        for ((count, reps, expected) in listOf(
            Triple(3, 12, 0.379796),
            Triple(6, 5, 0.441518),
            Triple(4, 5, 0.345141),
            Triple(4, 10, 0.427051)
        )) {
            val sets = List(count) { loggedSet(MuscleGroup.BACK, reps = reps) }
            assertEquals(
                expected,
                calculator.calculate(sets, T0).getValue(MuscleGroup.BACK),
                0.0000005
            )
            assertEquals(
                expected / 2.0,
                calculator.calculate(sets, 24 * HOUR_MILLIS).getValue(MuscleGroup.BACK),
                0.0000005
            )
        }
    }

    @Test
    fun customCalibrationAndFallbackHalfLifeAreUsed() {
        val config = FatigueConfig(
            capacityScale = 3.0,
            diminishingScale = 4.0,
            referenceReps = 10,
            repExponent = 1.0,
            minRepMultiplier = 0.25,
            maxRepMultiplier = 2.0,
            sessionGap = 1.hours,
            halfLives = emptyMap(),
            fallbackHalfLife = 12.hours
        )
        val custom = FatigueCalculator(config)
        val sets = listOf(
            loggedSet(MuscleGroup.CHEST, reps = 20),
            loggedSet(MuscleGroup.CHEST, timestampMillis = HOUR_MILLIS, reps = 1)
        )
        val first = (1.0 - (4.0 / 6.0).pow(4.0 / 3.0)) * 2.0.pow(-1.0 / 12.0)
        val expected = 1.0 - (1.0 - first) * (4.0 / 4.25).pow(4.0 / 3.0)
        assertEquals(
            expected,
            custom.calculate(sets, HOUR_MILLIS).getValue(MuscleGroup.CHEST),
            TOLERANCE
        )
        assertEquals(
            expected / 2.0,
            custom.calculate(sets, 13 * HOUR_MILLIS).getValue(MuscleGroup.CHEST),
            TOLERANCE
        )
    }

    @Test
    fun compoundWorkRecoversSlowerThanIsolation() {
        val isolation = List(12) { loggedSet(MuscleGroup.CHEST) }
        val compound = List(12) { loggedSet(MuscleGroup.CHEST, isCompound = true) }

        assertEquals(
            calculator.calculate(isolation, T0).getValue(MuscleGroup.CHEST),
            calculator.calculate(compound, T0).getValue(MuscleGroup.CHEST),
            TOLERANCE
        )
        assertTrue(
            calculator.calculate(compound, T0 + 24 * HOUR_MILLIS).getValue(MuscleGroup.CHEST) >
                calculator.calculate(isolation, T0 + 24 * HOUR_MILLIS).getValue(MuscleGroup.CHEST)
        )
    }

    @Test
    fun compoundHalfLifeIsTwentyFivePercentLonger() {
        val set = loggedSet(MuscleGroup.CHEST, isCompound = true)

        val fresh = calculator.calculate(listOf(set), T0).getValue(MuscleGroup.CHEST)
        val after30h = calculator.calculate(listOf(set), T0 + 30 * HOUR_MILLIS)
            .getValue(MuscleGroup.CHEST)

        assertEquals(fresh / 2.0, after30h, TOLERANCE)
    }

    @Test
    fun mixedTypesShareOneBoundedHeadroom() {
        val sets = List(12) { loggedSet(MuscleGroup.CHEST) } +
            List(12) {
                loggedSet(MuscleGroup.CHEST, timestampMillis = HOUR_MILLIS, isCompound = true)
            }
        val now = T0 + HOUR_MILLIS

        val mixed = calculator.calculate(sets, now).getValue(MuscleGroup.CHEST)
        val isolationOnly = calculator.calculate(sets.filterNot { it.isCompound }, now)
            .getValue(MuscleGroup.CHEST)

        assertTrue(mixed > isolationOnly)
        assertTrue(mixed < 1.0)
    }

    @Test
    fun relativeLoadUsesOnlyEarlierSameExerciseSets() {
        val factors = calculator.relativeLoadFactors(
            listOf(
                loadedSet("bench", T0, 100.0),
                loadedSet("bench", T0 + HOUR_MILLIS, 100.0),
                loadedSet("squat", T0 + 2 * HOUR_MILLIS, 100.0)
            )
        )

        assertEquals(1.0, factors[0], TOLERANCE)
        assertEquals(
            (100.0 / OneRepMax.estimate(100.0, 5)) / 0.70,
            factors[1],
            TOLERANCE
        )
        assertEquals(1.0, factors[2], TOLERANCE)
    }

    @Test
    fun relativeLoadIgnoresSameTimestampAndFutureSets() {
        val factors = calculator.relativeLoadFactors(
            listOf(
                loadedSet("bench", T0, 100.0),
                loadedSet("bench", T0, 90.0),
                loadedSet("bench", T0 + HOUR_MILLIS, 90.0)
            )
        )

        assertEquals(1.0, factors[0], TOLERANCE)
        assertEquals(1.0, factors[1], TOLERANCE)
        assertEquals(
            (90.0 / OneRepMax.estimate(100.0, 5)) / 0.70,
            factors[2],
            TOLERANCE
        )
    }

    @Test
    fun relativeLoadExcludesHighRepCandidatesAndIncomparableSets() {
        val factors = calculator.relativeLoadFactors(
            listOf(
                loadedSet("bench", T0, 100.0, reps = 5),
                loadedSet("bench", T0 + HOUR_MILLIS, 100.0, reps = 20),
                loadedSet("squat", T0, 100.0, reps = 20),
                loadedSet("squat", T0 + HOUR_MILLIS, 100.0, reps = 5)
            )
        )

        assertEquals(1.0, factors[0], TOLERANCE)
        assertEquals(1.0, factors[1], TOLERANCE)
        assertEquals(1.0, factors[3], TOLERANCE)
    }

    @Test
    fun relativeLoadWindowExpiresReferences() {
        val factors = calculator.relativeLoadFactors(
            listOf(
                loadedSet("bench", T0, 100.0),
                loadedSet("bench", T0 + 90.days.inWholeMilliseconds - 1, 100.0),
                loadedSet("row", T0, 100.0),
                loadedSet("row", T0 + 90.days.inWholeMilliseconds + 1, 100.0)
            )
        )

        assertEquals((100.0 / OneRepMax.estimate(100.0, 5)) / 0.70, factors[1], TOLERANCE)
        assertEquals(1.0, factors[3], TOLERANCE)
    }

    @Test
    fun relativeLoadClampsToConfiguredBounds() {
        val factors = calculator.relativeLoadFactors(
            listOf(
                loadedSet("bench", T0, 100.0),
                loadedSet("bench", T0 + HOUR_MILLIS, 10.0),
                loadedSet("ohp", T0, 20.0),
                loadedSet("ohp", T0 + HOUR_MILLIS, 40.0)
            )
        )

        assertEquals(0.75, factors[1], TOLERANCE)
        assertEquals(1.25, factors[3], TOLERANCE)
    }

    @Test
    fun deletingAnEarlierSetChangesALaterReference() {
        val withReference = calculator.relativeLoadFactors(
            listOf(loadedSet("bench", T0, 100.0), loadedSet("bench", T0 + HOUR_MILLIS, 100.0))
        )
        val withoutReference = calculator.relativeLoadFactors(
            listOf(loadedSet("bench", T0 + HOUR_MILLIS, 100.0))
        )

        assertTrue(withReference[1] > 1.0)
        assertEquals(1.0, withoutReference[0], TOLERANCE)
    }

    @Test
    fun relativeLoadWidensTheGapBetweenHeavyAndLightSets() {
        val reference = loadedSet("bench", T0, 100.0)
        val later = T0 + 3 * HOUR_MILLIS

        val heavy = calculator.calculate(
            listOf(reference, loadedSet("bench", later, 100.0)),
            later
        ).getValue(MuscleGroup.CHEST)
        val light = calculator.calculate(
            listOf(reference, loadedSet("bench", later, 50.0)),
            later
        ).getValue(MuscleGroup.CHEST)

        assertTrue(heavy > light)
    }

    @Test
    fun effortMultiplierFollowsTheRirFormula() {
        assertEquals(1.0, calculator.effortMultiplier(null), TOLERANCE)
        assertEquals(1.0, calculator.effortMultiplier(2), TOLERANCE)
        assertEquals(2.0.pow(0.5), calculator.effortMultiplier(0), TOLERANCE)
        assertEquals(0.25, calculator.effortMultiplier(10), TOLERANCE)
        assertEquals(2.0.pow(0.5), calculator.effortMultiplier(-3), TOLERANCE)
        assertEquals(0.25, calculator.effortMultiplier(99), TOLERANCE)
    }

    @Test
    fun lowerRirRaisesFatigueForTheSameSet() {
        val nearFailure = calculator.calculate(
            listOf(loadedSet("bench", T0, 100.0).copy(rir = 0)),
            T0
        ).getValue(MuscleGroup.CHEST)
        val easy = calculator.calculate(
            listOf(loadedSet("bench", T0, 100.0).copy(rir = 10)),
            T0
        ).getValue(MuscleGroup.CHEST)

        assertTrue(nearFailure > easy)
    }

    private companion object {
        const val TOLERANCE = 1e-9
        const val HOUR_MILLIS = 60L * 60L * 1000L
        const val T0 = 0L

        fun loadedSet(
            exerciseId: String?,
            timestampMillis: Long,
            weightKg: Double?,
            reps: Int = 5,
            isWarmup: Boolean = false
        ) = LoggedSet(
            timestampMillis = timestampMillis,
            targets = listOf(MuscleTarget(MuscleGroup.CHEST, 1.0)),
            isWarmup = isWarmup,
            reps = reps,
            exerciseId = exerciseId,
            weightKg = weightKg
        )

        fun loggedSet(
            muscle: MuscleGroup,
            timestampMillis: Long = T0,
            weight: Double = MuscleInvolvement.PRIMARY.volumeWeight,
            isWarmup: Boolean = false,
            reps: Int = FatigueConfig.DEFAULT_REFERENCE_REPS,
            isCompound: Boolean = false
        ) = LoggedSet(
            timestampMillis = timestampMillis,
            targets = listOf(MuscleTarget(muscle, weight)),
            isWarmup = isWarmup,
            reps = reps,
            isCompound = isCompound
        )
    }
}
