package com.hydrafit.app.core.domain.engine

import com.hydrafit.app.core.domain.workout.WorkoutSet
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BuildRecentWeightsUseCaseTest {

    private val useCase = BuildRecentWeightsUseCase()

    @Test
    fun keepsTheHeaviestSetFromEachOfTheTwoMostRecentDays() {
        val sets = listOf(
            set("bench-press", day = 0, weightKg = 110.0),
            set("bench-press", day = 1, weightKg = 100.0),
            set("bench-press", day = 1, weightKg = 105.0),
            set("bench-press", day = 2, weightKg = 90.0)
        )

        val history = useCase(sets)

        // Days 1 and 2 are the most recent two; day 1's heaviest set is 105, day 0 is dropped.
        assertEquals(2, history.size)
        assertEquals(listOf(105.0, 90.0), history.map { it.weightKg })
    }

    @Test
    fun ignoresWarmupsAndUnweightedSets() {
        val sets = listOf(
            set("bench-press", day = 0, weightKg = 200.0, isWarmup = true),
            set("bench-press", day = 0, weightKg = null),
            set("bench-press", day = 0, weightKg = 0.0),
            set("bench-press", day = 0, weightKg = 100.0)
        )

        val history = useCase(sets)

        assertEquals(listOf(100.0), history.map { it.weightKg })
    }

    @Test
    fun separatesExercisesAndSortsByTime() {
        val sets = listOf(
            set("squat", day = 1, weightKg = 120.0),
            set("squat", day = 0, weightKg = 100.0),
            set("bench-press", day = 0, weightKg = 80.0)
        )

        val history = useCase(sets)

        assertEquals(
            listOf("bench-press", "squat", "squat"),
            history.map { it.exerciseId }
        )
        assertEquals(listOf(80.0, 100.0, 120.0), history.map { it.weightKg })
    }

    @Test
    fun respectsTheConfiguredWindow() {
        val useCase = BuildRecentWeightsUseCase(RecentWeightsConfig(maxDatesPerExercise = 1))
        val sets = listOf(
            set("bench-press", day = 0, weightKg = 100.0),
            set("bench-press", day = 1, weightKg = 105.0)
        )

        val history = useCase(sets)

        assertEquals(1, history.size)
        assertEquals(105.0, history.single().weightKg)
    }

    @Test
    fun returnsNothingWhenThereIsNoUsableHistory() {
        assertTrue(useCase(emptyList()).isEmpty())
    }

    private fun set(exerciseId: String, day: Long, weightKg: Double?, isWarmup: Boolean = false) =
        WorkoutSet(
            exerciseId = exerciseId,
            reps = 5,
            weightKg = weightKg,
            performedAtMillis = day * 24L * 60L * 60L * 1000L,
            isWarmup = isWarmup
        )
}
