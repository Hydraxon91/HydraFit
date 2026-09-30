package com.hydrafit.app.core.domain.fatigue

import kotlin.test.Test
import kotlin.test.assertEquals

class CalculateMuscleFatigueUseCaseTest {

    @Test
    fun returnsZeroForUntrainedMuscles() {
        val scores = CalculateMuscleFatigueUseCase().invoke(emptyList(), nowMillis = 0L)

        assertEquals(MuscleGroup.entries.size, scores.size)
        assertEquals(0.0, scores.getValue(MuscleGroup.QUADS), TOLERANCE)
    }

    @Test
    fun matchesTheCalculatorResult() {
        val sets = listOf(
            LoggedSet(
                timestampMillis = 0L,
                targets = listOf(
                    MuscleTarget(MuscleGroup.CHEST, MuscleInvolvement.PRIMARY.volumeWeight)
                )
            )
        )

        val expected = FatigueCalculator().calculate(sets, nowMillis = 0L)
        val actual = CalculateMuscleFatigueUseCase().invoke(sets, nowMillis = 0L)

        assertEquals(expected, actual)
    }

    private companion object {
        const val TOLERANCE = 1e-9
    }
}
