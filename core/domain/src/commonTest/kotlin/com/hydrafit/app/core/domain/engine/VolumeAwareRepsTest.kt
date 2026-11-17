package com.hydrafit.app.core.domain.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class VolumeAwareRepsTest {

    private val useCase = VolumeAwareReps()

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
    fun rejectsNonPositiveSets() {
        assertFailsWith<IllegalArgumentException> {
            useCase.repsFor(TrainingGoal.BALANCED, isCompound = true, sets = 0)
        }
    }
}
