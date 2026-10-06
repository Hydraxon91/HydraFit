package com.hydrafit.app.core.domain.fatigue

import kotlin.test.Test
import kotlin.test.assertEquals

class FatigueReplayTest {

    @Test
    fun ledgerReproducesSplitBackFigures() {
        val sets = FatigueReplayFixture.sets
        assertEquals(39, sets.count { !it.isWarmup })
        assertEquals(3, sets.count { it.isWarmup })

        assertBackScores(sets, FatigueReplayFixture.PEAK_MILLIS, 0.6937550, 0.6087211, 0.3925775)
        assertBackScores(
            sets,
            FatigueReplayFixture.EVALUATION_MILLIS,
            0.5487492,
            0.4814887,
            0.3105226
        )
    }

    @Test
    fun typedReplayReportsSplitBackFigures() {
        val typed = FatigueReplayFixture.typedSets

        assertBackScores(typed, FatigueReplayFixture.PEAK_MILLIS, 0.6987279, 0.6131770, 0.3955366)
        assertBackScores(
            typed,
            FatigueReplayFixture.EVALUATION_MILLIS,
            0.5751160,
            0.5043438,
            0.3247357
        )
    }

    @Test
    fun backfillSessionIdsReproduceTheSplitBackFigures() {
        val plain = FatigueReplayFixture.withBackfillSessionIds(FatigueReplayFixture.sets)
        val typed = FatigueReplayFixture.withBackfillSessionIds(FatigueReplayFixture.typedSets)

        assertBackScores(plain, FatigueReplayFixture.PEAK_MILLIS, 0.6937550, 0.6087211, 0.3925775)
        assertBackScores(
            plain,
            FatigueReplayFixture.EVALUATION_MILLIS,
            0.5487492,
            0.4814887,
            0.3105226
        )
        assertBackScores(typed, FatigueReplayFixture.PEAK_MILLIS, 0.6987279, 0.6131770, 0.3955366)
        assertBackScores(
            typed,
            FatigueReplayFixture.EVALUATION_MILLIS,
            0.5751160,
            0.5043438,
            0.3247357
        )
    }

    @Test
    fun typedReplayAtRirZeroAndFour() {
        assertBackScores(
            FatigueReplayFixture.typedSetsWithRir(0),
            FatigueReplayFixture.PEAK_MILLIS,
            0.7713062,
            0.6964393,
            0.4837233
        )
        assertBackScores(
            FatigueReplayFixture.typedSetsWithRir(0),
            FatigueReplayFixture.EVALUATION_MILLIS,
            0.6352209,
            0.5732217,
            0.3974324
        )
        assertBackScores(
            FatigueReplayFixture.typedSetsWithRir(4),
            FatigueReplayFixture.PEAK_MILLIS,
            0.6157135,
            0.5239225,
            0.3143473
        )
        assertBackScores(
            FatigueReplayFixture.typedSetsWithRir(4),
            FatigueReplayFixture.EVALUATION_MILLIS,
            0.5064408,
            0.4306071,
            0.2579048
        )
    }

    /**
     * Expected values were derived independently from the documented recurrence against the stored
     * BACK weights split by region (LATS 0.50 / UPPER_BACK 0.35 / LOWER_BACK 0.15); they are not
     * produced by running the calculator or the decoder and copying the output.
     */
    private fun assertBackScores(
        sets: List<LoggedSet>,
        instant: Long,
        lats: Double,
        upperBack: Double,
        lowerBack: Double
    ) {
        val scores = FatigueCalculator().calculate(sets, instant)
        assertEquals(lats, scores.getValue(MuscleGroup.LATS), TOLERANCE)
        assertEquals(upperBack, scores.getValue(MuscleGroup.UPPER_BACK), TOLERANCE)
        assertEquals(lowerBack, scores.getValue(MuscleGroup.LOWER_BACK), TOLERANCE)
    }

    private companion object {
        const val TOLERANCE = 0.0000005
    }
}
