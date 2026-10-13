package com.hydrafit.app.core.exercisecontent

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ExerciseInstructionSourceTest {

    /**
     * The approved 0.7.4 first batch, written out independently of the production list so a change
     * to either must be made deliberately in both.
     */
    private val approvedFirstBatch = setOf(
        "barbell-bench-press",
        "dumbbell-bench-press",
        "push-up",
        "overhead-press",
        "dumbbell-row",
        "lat-pulldown",
        "seated-cable-row",
        "lateral-raise",
        "back-squat",
        "goblet-squat",
        "romanian-deadlift",
        "leg-curl",
        "dumbbell-curl",
        "triceps-pushdown",
        "standing-calf-raise",
        "standing-calf-raise-machine",
        "hanging-leg-raise"
    )

    @Test
    fun approvedBatchMatchesTheApprovedFirstBatch() {
        assertEquals(approvedFirstBatch, ExerciseInstructionSource.approvedBatch)
    }

    @Test
    fun authoredInstructionsMatchTheFirstEightExerciseBatch() {
        assertTrue(
            ExerciseInstructionSource.keysByExerciseId.keys.containsAll(
                approvedFirstBatch.take(8)
            )
        )
    }

    @Test
    fun authoredInstructionsCoverTheCompleteApprovedBatch() {
        assertEquals(
            ExerciseInstructionSource.approvedBatch,
            ExerciseInstructionSource.keysByExerciseId.keys
        )
    }

    @Test
    fun approvedBatchIdsAreStableCatalogIds() {
        ExerciseInstructionSource.approvedBatch.forEach { id ->
            assertTrue(id.isNotBlank(), "blank instruction ID")
            assertEquals(id.lowercase(), id, "instruction IDs stay lowercase: $id")
            assertTrue(id.none(Char::isWhitespace), "instruction IDs carry no whitespace: $id")
        }
    }

    @Test
    fun authoredInstructionsStayInsideTheApprovedBatch() {
        val unapproved = ExerciseInstructionSource.keysByExerciseId.keys -
            ExerciseInstructionSource.approvedBatch
        assertTrue(
            unapproved.isEmpty(),
            "authored instruction outside the approved batch: $unapproved"
        )
    }

    @Test
    fun authoredInstructionsUseDistinctNonBlankResourceKeys() {
        ExerciseInstructionSource.keysByExerciseId.forEach { (exerciseId, keys) ->
            assertTrue(keys.setup.key.isNotBlank(), "$exerciseId has a blank setup key")
            assertTrue(keys.execution.key.isNotBlank(), "$exerciseId has a blank execution key")
            assertNotEquals(
                keys.setup.key,
                keys.execution.key,
                "$exerciseId reuses one resource key for both sections"
            )
            val resourcePrefix = "exercise_instruction_${exerciseId.replace('-', '_')}"
            assertEquals("${resourcePrefix}_setup", keys.setup.key)
            assertEquals("${resourcePrefix}_execution", keys.execution.key)
        }
    }

    /**
     * Lookup is exactly the authored catalog. An approved exercise whose text is not authored yet
     * resolves to null, which is what makes the UI show the unavailable state instead of invented
     * guidance.
     */
    @Test
    fun lookupMatchesTheAuthoredCatalogExactly() {
        val authored = ExerciseInstructionSource.keysByExerciseId.keys
        authored.forEach { exerciseId ->
            assertNotNull(
                ExerciseInstructionSource.keysFor(exerciseId),
                "$exerciseId is authored but does not resolve"
            )
        }
        (ExerciseInstructionSource.approvedBatch - authored).forEach { exerciseId ->
            assertNull(
                ExerciseInstructionSource.keysFor(exerciseId),
                "$exerciseId has no authored text and must not resolve"
            )
        }
    }

    @Test
    fun blankUnknownAndCustomIdsHaveNoInstruction() {
        assertNull(ExerciseInstructionSource.keysFor(null))
        assertNull(ExerciseInstructionSource.keysFor(""))
        assertNull(ExerciseInstructionSource.keysFor("   "))
        assertNull(ExerciseInstructionSource.keysFor("custom-exercise-42"))
        assertNull(ExerciseInstructionSource.keysFor("Barbell Bench Press"))
    }
}
