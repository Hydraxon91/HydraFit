package com.hydrafit.app.core.domain.engine

import kotlin.test.Test
import kotlin.test.assertEquals

class VolumeAwareRepsTest {

    private val useCase = VolumeAwareReps()

    @Test
    fun holdsTheGoalsDefaultVolumeAtTheDefaultSetCount() {
        TrainingGoal.entries.forEach { goal ->
            assertEquals(
                goal.compoundReps,
                useCase.repsFor(goal, isCompound = true, sets = goal.defaultSets)
            )
            assertEquals(
                goal.isolationReps,
                useCase.repsFor(goal, isCompound = false, sets = goal.accessorySets)
            )
        }
    }

    @Test
    fun fewerSetsEarnMoreRepsAndMoreSetsEarnFewer() {
        val goal = TrainingGoal.BALANCED

        // Compound volume 3 x 6 = 18
        assertEquals(9, useCase.repsFor(goal, isCompound = true, sets = 2))
        assertEquals(6, useCase.repsFor(goal, isCompound = true, sets = 3))
        assertEquals(5, useCase.repsFor(goal, isCompound = true, sets = 4))
        assertEquals(4, useCase.repsFor(goal, isCompound = true, sets = 5))
        assertEquals(3, useCase.repsFor(goal, isCompound = true, sets = 6))
    }

    @Test
    fun usesTheAccessoryVolumeForAccessorySlots() {
        val goal = TrainingGoal.BALANCED

        // Accessory volume 2 x 12 = 24
        assertEquals(12, useCase.repsFor(goal, isCompound = false, sets = 2))
        assertEquals(8, useCase.repsFor(goal, isCompound = false, sets = 3))
        assertEquals(6, useCase.repsFor(goal, isCompound = false, sets = 4))
        assertEquals(4, useCase.repsFor(goal, isCompound = false, sets = 6))
    }

    @Test
    fun clampsToTheConfiguredBounds() {
        val narrow = VolumeAwareReps(VolumeAwareRepsConfig(minReps = 5, maxReps = 8))
        val goal = TrainingGoal.BALANCED

        assertEquals(8, narrow.repsFor(goal, isCompound = true, sets = 2))
        assertEquals(5, narrow.repsFor(goal, isCompound = true, sets = 6))
    }

    @Test
    fun everyGoalStaysWithinTheDefaultBounds() {
        val range = VolumeAwareRepsConfig.DEFAULT_MIN_REPS..VolumeAwareRepsConfig.DEFAULT_MAX_REPS
        TrainingGoal.entries.forEach { goal ->
            (2..6).forEach { sets ->
                listOf(true, false).forEach { compound ->
                    val reps = useCase.repsFor(goal, compound, sets)
                    assertEquals(
                        true,
                        reps in range,
                        "goal=$goal sets=$sets compound=$compound -> $reps"
                    )
                }
            }
        }
    }
}
