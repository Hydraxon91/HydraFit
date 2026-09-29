package com.hydrafit.app.core.domain.engine

import com.hydrafit.app.core.domain.workout.WorkoutSet
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SuggestWeightsUseCaseTest {

    private val useCase = SuggestWeightsUseCase()

    @Test
    fun estimatesOneRepMaxAndAppliesTheGoalIntensity() {
        val sets = listOf(set("bench-press", reps = 5, weightKg = 100.0))

        // 100kg x 5 -> 100 * (1 + 5/30) = 116.667 -> 87.5% = 102.083 -> nearest 2.5 = 102.5
        val result = useCase(sets, TrainingGoal.STRENGTH)

        assertEquals(102.5, result.getValue("bench-press"))
    }

    @Test
    fun enduranceSuggestsLessThanStrengthForTheSameRecord() {
        val sets = listOf(set("bench-press", reps = 5, weightKg = 100.0))

        val strength = useCase(sets, TrainingGoal.STRENGTH).getValue("bench-press")
        val endurance = useCase(sets, TrainingGoal.ENDURANCE).getValue("bench-press")

        assertTrue(endurance < strength, "endurance $endurance should be below strength $strength")
    }

    @Test
    fun usesTheBestEstimateAcrossSessions() {
        val sets = listOf(
            set("squat", reps = 5, weightKg = 100.0),
            set("squat", reps = 1, weightKg = 110.0)
        )

        // 100x5 -> 116.667 beats 110x1 -> 113.667; 70% -> 81.667 -> nearest 2.5 = 82.5
        val result = useCase(sets, TrainingGoal.BALANCED)

        assertEquals(82.5, result.getValue("squat"))
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

        val result = useCase(sets, TrainingGoal.STRENGTH)

        assertEquals(102.5, result.getValue("bench-press"))
    }

    @Test
    fun omitsExercisesWithoutAUsableRecord() {
        val sets = listOf(set("bench-press", reps = 5, weightKg = null))

        val result = useCase(sets, TrainingGoal.STRENGTH)

        assertTrue(result.isEmpty())
    }

    @Test
    fun roundsToTheConfiguredIncrement() {
        val coarse = SuggestWeightsUseCase(SuggestedWeightConfig(roundToKg = 5.0))
        val sets = listOf(set("bench-press", reps = 5, weightKg = 100.0))

        // 116.667 * 0.875 = 102.083 -> nearest 5 = 100.0
        val result = coarse(sets, TrainingGoal.STRENGTH)

        assertEquals(100.0, result.getValue("bench-press"))
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
