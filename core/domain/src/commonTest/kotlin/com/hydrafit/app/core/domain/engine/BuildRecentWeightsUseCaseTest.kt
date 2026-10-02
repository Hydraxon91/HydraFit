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
    fun ignoresWarmupsAndZeroWeightSets() {
        val sets = listOf(
            set("bench-press", day = 0, weightKg = 200.0, isWarmup = true),
            set("bench-press", day = 0, weightKg = 0.0),
            set("bench-press", day = 0, weightKg = 100.0)
        )

        val history = useCase(sets)

        assertEquals(listOf(100.0), history.map { it.weightKg })
    }

    @Test
    fun keepsTheLatestBodyweightSetWhenNoWeightWasUsed() {
        val sets = listOf(
            set("pull-up", day = 0, weightKg = null, reps = 8, atHour = 1),
            set("pull-up", day = 0, weightKg = null, reps = 12, atHour = 2)
        )

        val history = useCase(sets)

        val entry = history.single()
        assertEquals(null, entry.weightKg)
        assertEquals(12, entry.reps)
    }

    @Test
    fun carriesRirAndPlanSnapshot() {
        val sets = listOf(
            WorkoutSet(
                exerciseId = "bench-press",
                reps = 5,
                weightKg = 100.0,
                performedAtMillis = 0L,
                rir = 2,
                weekNumber = 3,
                cycleNumber = 2,
                dayIndex = 1
            )
        )

        val history = useCase(sets)

        val entry = history.single()
        assertEquals(2, entry.rir)
        assertEquals(3, entry.weekNumber)
        assertEquals(1, entry.dayIndex)
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

    private fun set(
        exerciseId: String,
        day: Long,
        weightKg: Double?,
        isWarmup: Boolean = false,
        reps: Int = 5,
        atHour: Long = 0
    ) = WorkoutSet(
        exerciseId = exerciseId,
        reps = reps,
        weightKg = weightKg,
        performedAtMillis = day * 24L * 60L * 60L * 1000L + atHour * 60L * 60L * 1000L,
        isWarmup = isWarmup
    )
}
