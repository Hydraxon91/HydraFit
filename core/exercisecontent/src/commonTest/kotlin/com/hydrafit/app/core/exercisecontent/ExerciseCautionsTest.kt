package com.hydrafit.app.core.exercisecontent

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The public indication contract: the entry-point indication appears exactly for the approved caution
 * IDs and never for a custom identity, a display name or another seed.
 */
class ExerciseCautionsTest {

    @Test
    fun theApprovedDeadliftIdentitiesAreIndicated() {
        assertTrue(ExerciseCautions.hasTechniqueConsiderations("conventional-deadlift"))
        assertTrue(ExerciseCautions.hasTechniqueConsiderations("romanian-deadlift"))
        assertTrue(ExerciseCautions.hasTechniqueConsiderations("dumbbell-rdl"))
    }

    @Test
    fun blankCustomDisplayNameAndOtherSeedIdsAreNotIndicated() {
        assertFalse(ExerciseCautions.hasTechniqueConsiderations(null))
        assertFalse(ExerciseCautions.hasTechniqueConsiderations(""))
        assertFalse(ExerciseCautions.hasTechniqueConsiderations("   "))
        assertFalse(ExerciseCautions.hasTechniqueConsiderations("user-conventional-deadlift"))
        assertFalse(ExerciseCautions.hasTechniqueConsiderations("Conventional Deadlift"))
        assertFalse(ExerciseCautions.hasTechniqueConsiderations("barbell-bench-press"))
    }

    @Test
    fun theIndicationIsIndependentOfAuthoredInstructions() {
        assertFalse(ExerciseInstructionSource.keysByExerciseId.containsKey("conventional-deadlift"))
        assertTrue(ExerciseCautions.hasTechniqueConsiderations("conventional-deadlift"))
    }
}
