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
        "hanging-leg-raise",
        "incline-dumbbell-press",
        "incline-push-up",
        "cable-fly",
        "band-chest-press",
        "dips",
        "barbell-row",
        "pull-up",
        "chin-up",
        "band-row",
        "band-pull-apart",
        "dumbbell-shoulder-press",
        "cable-lateral-raise",
        "face-pull",
        "barbell-curl",
        "band-curl",
        "overhead-triceps-extension",
        "close-grip-bench-press",
        "front-squat",
        "bodyweight-squat",
        "bulgarian-split-squat",
        "leg-press",
        "dumbbell-rdl",
        "conventional-deadlift",
        "hip-thrust",
        "glute-bridge",
        "kettlebell-swing",
        "bodyweight-calf-raise",
        "plank",
        "cable-crunch",
        "hammer-curl",
        "supinated-curl",
        "preacher-curl",
        "close-grip-pulldown",
        "wide-grip-pulldown",
        "incline-barbell-press",
        "decline-barbell-press",
        "ez-bar-upright-row",
        "seated-ez-bar-curl",
        "trap-bar-deadlift",
        "dumbbell-lunge",
        "barbell-shoulder-press",
        "ab-roll",
        "leg-extension",
        "standing-dumbbell-side-bend",
        "dumbbell-triceps-overhead-extension",
        "dumbbell-front-to-lateral-raise-combo",
        "bench-press-with-chains",
        "cable-chest-press",
        "handstand-push-ups",
        "incline-push-up-medium",
        "incline-push-up-wide",
        "push-up-to-side-plank",
        "push-up-wide",
        "single-arm-push-up",
        "cable-shoulder-press",
        "dip-machine",
        "double-kettlebell-push-press",
        "push-press",
        "push-press-behind-the-neck",
        "seated-cable-shoulder-press",
        "shoulder-press-with-bands",
        "standing-military-press",
        "barbell-rear-delt-row",
        "bent-over-barbell-row",
        "dumbbell-incline-row",
        "one-arm-dumbbell-row",
        "shotgun-row",
        "t-bar-row-with-handle",
        "upright-barbell-row",
        "upright-cable-row",
        "close-grip-front-lat-pulldown",
        "full-range-of-motion-lat-pulldown",
        "one-arm-lat-pulldown",
        "underhand-cable-pulldowns",
        "v-bar-pulldown",
        "v-bar-pullup",
        "wide-grip-pulldown-behind-the-neck",
        "wide-grip-rear-pull-up",
        "box-squat",
        "dumbbell-squat",
        "hack-squat",
        "kneeling-squat",
        "olympic-squat",
        "overhead-squat",
        "squat-jerk",
        "squat-with-bands",
        "band-good-morning",
        "barbell-hip-thrust",
        "cable-deadlifts",
        "deadlift-with-bands",
        "deadlift-with-chains",
        "deficit-deadlift",
        "good-morning",
        "sumo-deadlift",
        "barbell-lunge",
        "barbell-step-ups",
        "barbell-walking-lunge",
        "dumbbell-rear-lunge",
        "dumbbell-step-ups",
        "elevated-back-lunge",
        "lunge-pass-through",
        "step-up-with-knee-raise",
        "barbell-seated-calf-raise",
        "calf-press",
        "calf-raise-on-a-dumbbell",
        "calf-raises-with-bands",
        "seated-calf-raise",
        "smith-machine-calf-raise",
        "standing-barbell-calf-raise",
        "standing-dumbbell-calf-raise",
        "cable-crossover",
        "cable-iron-cross",
        "decline-dumbbell-flyes",
        "dumbbell-flyes",
        "incline-cable-flye",
        "incline-dumbbell-flyes",
        "incline-dumbbell-flyes-with-a-twist",
        "low-cable-crossover",
        "cable-preacher-curl",
        "concentration-curls"
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
