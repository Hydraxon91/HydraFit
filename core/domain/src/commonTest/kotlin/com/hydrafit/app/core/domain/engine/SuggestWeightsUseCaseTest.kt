package com.hydrafit.app.core.domain.engine

import com.hydrafit.app.core.domain.workout.WorkoutSet
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SuggestWeightsUseCaseTest {

    private val useCase = SuggestWeightsUseCase()

    @Test
    fun estimatesOneRepMaxWithTheEpleyFormula() {
        val sets = listOf(set("bench-press", reps = 5, weightKg = 100.0))

        // 100kg x 5 -> 100 * (1 + 5/30) = 116.666...
        val result = useCase(sets)

        assertEquals(116.66666666666667, result.getValue("bench-press"))
    }

    @Test
    fun usesTheBestEstimateAcrossSessions() {
        val sets = listOf(
            set("squat", reps = 5, weightKg = 100.0),
            set("squat", reps = 1, weightKg = 110.0)
        )

        // 100x5 -> 116.666 beats 110x1 -> 113.666
        val result = useCase(sets)

        assertEquals(116.66666666666667, result.getValue("squat"))
    }

    @Test
    fun ignoresWarmupsAndUnusableSets() {
        val sets = listOf(
            set("bench-press", reps = 5, weightKg = 200.0, isWarmup = true),
            set("bench-press", reps = 5, weightKg = null),
            set("bench-press", reps = 5, weightKg = 0.0),
            set("bench-press", reps = 20, weightKg = 300.0),
            set("bench-press", reps = 5, weightKg = 100.0)
        )

        val result = useCase(sets)

        assertEquals(116.66666666666667, result.getValue("bench-press"))
    }

    @Test
    fun omitsExercisesWithoutAUsableRecord() {
        val sets = listOf(set("bench-press", reps = 5, weightKg = null))

        val result = useCase(sets)

        assertTrue(result.isEmpty())
    }

    @Test
    fun honorsTheConfiguredMaxRepsForEstimate() {
        val narrow = SuggestWeightsUseCase(SuggestedWeightConfig(maxRepsForEstimate = 3))
        val sets = listOf(set("bench-press", reps = 5, weightKg = 100.0))

        assertTrue(narrow(sets).isEmpty())
    }

    private fun set(exerciseId: String, reps: Int, weightKg: Double?, isWarmup: Boolean = false) =
        WorkoutSet(
            exerciseId = exerciseId,
            reps = reps,
            weightKg = weightKg,
            performedAtMillis = 0L,
            isWarmup = isWarmup
        )
}
