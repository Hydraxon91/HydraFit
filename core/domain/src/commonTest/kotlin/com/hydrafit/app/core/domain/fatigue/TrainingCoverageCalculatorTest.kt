package com.hydrafit.app.core.domain.fatigue

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TrainingCoverageCalculatorTest {
    private val day = 86_400_000L
    private val profile = mapOf(MuscleGroup.BICEPS to 1.0, MuscleGroup.FOREARMS to 0.3)

    @Test
    fun countsEachWorkingSetPerMuscleAndKeepsCreditsSeparate() {
        val sets = listOf(
            CoverageSet(10 * day, false, profile),
            CoverageSet(10 * day, false, profile),
            CoverageSet(10 * day, true, profile)
        )
        val coverage = calculate(sets)
        assertEquals(MuscleGroup.entries, coverage.entries.map { it.muscle })
        val biceps = coverage.entries.first { it.muscle == MuscleGroup.BICEPS }
        val forearms = coverage.entries.first { it.muscle == MuscleGroup.FOREARMS }
        assertEquals(2, biceps.workingSets)
        assertEquals(2, forearms.workingSets)
        assertEquals(2.0, biceps.estimatedInvolvementCredits)
        assertEquals(0.6, forearms.estimatedInvolvementCredits, 1e-12)
        val afterDelete = calculate(sets.drop(1)).entries.first {
            it.muscle == MuscleGroup.FOREARMS
        }
        assertEquals(1, afterDelete.workingSets)
        assertEquals(0.3, afterDelete.estimatedInvolvementCredits)
    }

    @Test
    fun windowIsCivilAndInclusiveWhileLastTrainedSpansAllHistory() {
        val now = 10 * day
        val offset = 2 * 3_600_000L
        val start = 4 * day - offset
        val result = TrainingCoverageCalculator.calculate(
            listOf(
                CoverageSet(start - 1, false, mapOf(MuscleGroup.CALVES to 1.0)),
                CoverageSet(start, false, profile),
                CoverageSet(now, false, profile),
                CoverageSet(now + 1, false, profile)
            ),
            now,
            offset
        )
        assertEquals(4L, result.fromEpochDay)
        assertEquals(10L, result.throughEpochDay)
        assertEquals(2, result.entries.first { it.muscle == MuscleGroup.BICEPS }.workingSets)
        val calves = result.entries.first { it.muscle == MuscleGroup.CALVES }
        assertEquals(0, calves.workingSets)
        assertEquals(start - 1, calves.lastTrainedAtMillis)
        val biceps = result.entries.first { it.muscle == MuscleGroup.BICEPS }
        assertEquals(now, biceps.lastTrainedAtMillis)
    }

    @Test
    fun nullSnapshotsRemainUnknownButExplicitEmptyMapsAreKnown() {
        val result = calculate(
            listOf(
                CoverageSet(1 * day, false, null),
                CoverageSet(10 * day, false, null),
                CoverageSet(10 * day, false, emptyMap()),
                CoverageSet(11 * day, false, null),
                CoverageSet(10 * day, true, null)
            )
        )
        assertEquals(1, result.unclassifiedSetsInWindow)
        assertTrue(result.hasUnclassifiedHistory)
        result.entries.forEach {
            assertEquals(0, it.workingSets)
            assertNull(it.lastTrainedAtMillis)
        }
        val knownEmpty = calculate(listOf(CoverageSet(10 * day, false, emptyMap())))
        assertFalse(knownEmpty.hasUnclassifiedHistory)
    }

    @Test
    fun onlyPositiveFiniteRecordedContributionsCountAndNegativeEpochDaysWork() {
        val result = TrainingCoverageCalculator.calculate(
            listOf(
                CoverageSet(
                    -day,
                    false,
                    mapOf(
                        MuscleGroup.BICEPS to 0.001,
                        MuscleGroup.TRICEPS to 0.0,
                        MuscleGroup.QUADS to -0.1,
                        MuscleGroup.CALVES to Double.NaN
                    )
                )
            ),
            -day,
            -3_600_000L
        )
        assertEquals(-2L, result.throughEpochDay)
        assertEquals(1, result.entries.first { it.muscle == MuscleGroup.BICEPS }.workingSets)
        assertTrue(
            result.entries.filter { it.muscle != MuscleGroup.BICEPS }.all {
                it.workingSets == 0 && it.lastTrainedAtMillis == null
            }
        )
    }

    private fun calculate(sets: List<CoverageSet>): TrainingCoverage =
        TrainingCoverageCalculator.calculate(sets, 10 * day, 0L)
}
