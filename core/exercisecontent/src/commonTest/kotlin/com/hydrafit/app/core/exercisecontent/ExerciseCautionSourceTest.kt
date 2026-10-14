package com.hydrafit.app.core.exercisecontent

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ExerciseCautionSourceTest {

    /**
     * The approved 0.7.5 (EX-03) batch, written out independently of the production list so a
     * change to either must be made deliberately in both.
     */
    private val approvedBatch = setOf(
        "conventional-deadlift",
        "romanian-deadlift",
        "dumbbell-rdl"
    )

    @Test
    fun approvedBatchMatchesTheApprovedCautionSet() {
        assertEquals(approvedBatch, ExerciseCautionSource.approvedCautionBatch)
    }

    @Test
    fun authoredCautionsCoverTheCompleteApprovedBatch() {
        assertEquals(
            ExerciseCautionSource.approvedCautionBatch,
            ExerciseCautionSource.cautionByExerciseId.keys
        )
    }

    @Test
    fun approvedBatchIdsAreStableCatalogIds() {
        ExerciseCautionSource.approvedCautionBatch.forEach { id ->
            assertTrue(id.isNotBlank(), "blank caution ID")
            assertEquals(id.lowercase(), id, "caution IDs stay lowercase: $id")
            assertTrue(id.none(Char::isWhitespace), "caution IDs carry no whitespace: $id")
        }
    }

    @Test
    fun authoredCautionsStayInsideTheApprovedBatch() {
        val unapproved = ExerciseCautionSource.cautionByExerciseId.keys -
            ExerciseCautionSource.approvedCautionBatch
        assertTrue(
            unapproved.isEmpty(),
            "authored caution outside the approved batch: $unapproved"
        )
    }

    @Test
    fun authoredCautionsUseTheExpectedResourceKeys() {
        ExerciseCautionSource.cautionByExerciseId.forEach { (exerciseId, resource) ->
            assertTrue(resource.key.isNotBlank(), "$exerciseId has a blank caution key")
            assertEquals(
                "exercise_caution_${exerciseId.replace('-', '_')}",
                resource.key
            )
        }
    }

    /** Lookup is exactly the authored catalog; an approved ID without text must not resolve. */
    @Test
    fun lookupMatchesTheAuthoredCatalogExactly() {
        val authored = ExerciseCautionSource.cautionByExerciseId.keys
        authored.forEach { exerciseId ->
            assertNotNull(
                ExerciseCautionSource.cautionFor(exerciseId),
                "$exerciseId is authored but does not resolve"
            )
        }
        (ExerciseCautionSource.approvedCautionBatch - authored).forEach { exerciseId ->
            assertNull(
                ExerciseCautionSource.cautionFor(exerciseId),
                "$exerciseId has no authored caution and must not resolve"
            )
        }
    }

    /**
     * Lookup is by ID only. A blank or unknown ID, a custom `user-*` ID and a display name all
     * resolve to null; a renamed seeded row keeps its ID and therefore its caution, which is why the
     * display-name case below ("Conventional Deadlift") must not resolve.
     */
    @Test
    fun blankUnknownCustomAndDisplayNamesHaveNoCaution() {
        assertNull(ExerciseCautionSource.cautionFor(null))
        assertNull(ExerciseCautionSource.cautionFor(""))
        assertNull(ExerciseCautionSource.cautionFor("   "))
        assertNull(ExerciseCautionSource.cautionFor("custom-exercise-42"))
        assertNull(ExerciseCautionSource.cautionFor("Conventional Deadlift"))
        assertNull(ExerciseCautionSource.cautionFor("barbell-bench-press"))
    }

    /**
     * Cautions are independent of instructions: an exercise can carry a caution without an authored
     * instruction, which is the case for two of the three approved IDs.
     */
    @Test
    fun cautionsAreIndependentOfInstructions() {
        assertNull(ExerciseInstructionSource.keysFor("conventional-deadlift"))
        assertNull(ExerciseInstructionSource.keysFor("dumbbell-rdl"))
        assertNotNull(ExerciseCautionSource.cautionFor("conventional-deadlift"))
        assertNotNull(ExerciseCautionSource.cautionFor("dumbbell-rdl"))
        assertNotNull(ExerciseInstructionSource.keysFor("romanian-deadlift"))
        assertNotNull(ExerciseCautionSource.cautionFor("romanian-deadlift"))
    }
}
