package com.hydrafit.app.core.domain.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class VolumeAwareRepsTest {

    private val useCase = VolumeAwareReps()

    @Test
    fun returnsTheGoalsBandAtTheDefaultSetCount() {
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
    fun theRepBandSurvivesSetOverridesForEveryGoal() {
        TrainingGoal.entries.forEach { goal ->
            (1..8).forEach { sets ->
                assertEquals(
                    goal.compoundReps,
                    useCase.repsFor(goal, isCompound = true, sets = sets),
                    "goal=$goal sets=$sets"
                )
                assertEquals(
                    goal.isolationReps,
                    useCase.repsFor(goal, isCompound = false, sets = sets),
                    "goal=$goal sets=$sets"
                )
            }
        }
    }

    @Test
    fun enduranceStaysHighRepEvenAtHigherSetCounts() {
        val endurance = TrainingGoal.ENDURANCE

        assertEquals(
            endurance.compoundReps,
            useCase.repsFor(endurance, isCompound = true, sets = 6)
        )
        assertEquals(
            endurance.isolationReps,
            useCase.repsFor(endurance, isCompound = false, sets = 6)
        )
        assertTrue(endurance.compoundReps > TrainingGoal.STRENGTH.compoundReps)
    }

    @Test
    fun rejectsNonPositiveSets() {
        assertFailsWith<IllegalArgumentException> {
            useCase.repsFor(TrainingGoal.BALANCED, isCompound = true, sets = 0)
        }
    }
}
