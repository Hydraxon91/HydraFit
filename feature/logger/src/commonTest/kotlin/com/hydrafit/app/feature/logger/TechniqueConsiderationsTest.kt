package com.hydrafit.app.feature.logger

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The Logger's four instruction actions (manual, planned draft and both guided rows) share one
 * feature-local seam, [showsTechniqueConsiderations], which delegates to the shared stable-ID
 * predicate in `:core:exercisecontent`. These tests pin that seam only. Runtime flows cover the
 * manual, planned-draft and in-progress guided call sites; the all-complete guided call site has
 * not been exercised by those flows.
 */
class TechniqueConsiderationsTest {

    @Test
    fun theApprovedDeadliftIdentitiesAreIndicatedThroughTheLoggerSeam() {
        assertTrue(showsTechniqueConsiderations("conventional-deadlift"))
        assertTrue(showsTechniqueConsiderations("romanian-deadlift"))
        assertTrue(showsTechniqueConsiderations("dumbbell-rdl"))
    }

    @Test
    fun customIdentitiesAndNonExerciseIdentifiersAreNeverIndicated() {
        assertFalse(showsTechniqueConsiderations("user-conventional-deadlift"))
        assertFalse(showsTechniqueConsiderations("back-squat"))
        assertFalse(showsTechniqueConsiderations("42"))
        assertFalse(showsTechniqueConsiderations(null))
    }
}
